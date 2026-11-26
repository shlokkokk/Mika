package com.wangzan101.mika.voice.model

import android.content.Context
import com.wangzan101.mika.core.model.KeyPoolManager
import com.wangzan101.mika.core.model.ModelError
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.core.model.TtsCapabilities
import com.wangzan101.mika.core.model.TtsModel
import com.wangzan101.mika.core.model.TtsRequest
import com.wangzan101.mika.core.model.TtsResponse
import com.wangzan101.mika.settings.Provider
import com.wangzan101.mika.settings.ProviderConfig
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.activeApiKey
import com.wangzan101.mika.settings.clientHeaders
import com.wangzan101.mika.settings.configFor
import com.wangzan101.mika.settings.providerKey
import com.wangzan101.mika.voice.TranscribeMethod
import com.wangzan101.mika.voice.Transcriber
import com.wangzan101.mika.voice.model.adapters.legacyProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Key-pool rotation (429/401/403) and multi-model cascade failover (5xx server overload, rate limits, network timeouts)
 * for both STT (Voice Input / Call Mode Listen) and TTS (Speak).
 */
internal object VoiceFailover {

    private fun extractHttpStatus(error: Throwable): Int? = when (error) {
        is ModelError.RateLimited -> 429
        is ModelError.Auth -> 401
        is ModelError.Server -> error.status
        is VoiceModelError.Protocol -> error.cause?.let(::extractHttpStatus) ?: when (error.category) {
            VoiceErrorCategory.RATE_LIMITED -> 429
            VoiceErrorCategory.AUTH_FAILED -> 401
            else -> null
        }
        else -> null
    }

    private fun extractRetryAfterMs(error: Throwable): Long? = when (error) {
        is ModelError.RateLimited -> error.retryAfterMs
        is VoiceModelError.Protocol -> error.cause?.let(::extractRetryAfterMs)
        else -> null
    }

    private fun isTransientRetryable(error: Throwable): Boolean = when (error) {
        is CancellationException, is VoiceModelError.Cancelled -> false
        is ModelError.Server -> error.status >= 500 || error.status == 408
        is ModelError.Network, is IOException -> true
        is VoiceModelError.Protocol -> error.category in setOf(
            VoiceErrorCategory.PROTOCOL_ERROR,
            VoiceErrorCategory.NETWORK,
            VoiceErrorCategory.TIMEOUT,
            VoiceErrorCategory.SYSTEM_UNAVAILABLE,
        )
        else -> false
    }

    internal fun canFailoverToNextModel(error: Throwable): Boolean = when (error) {
        is CancellationException, is VoiceModelError.Cancelled -> false
        is ModelError.RateLimited, is ModelError.Auth, is ModelError.Server, is ModelError.Network,
        is ModelError.ModelNotFound, is ModelError.ModelNotAvailable, is ModelError.BadResponse,
        is IOException -> true
        is VoiceModelError.Protocol -> error.category in setOf(
            VoiceErrorCategory.RATE_LIMITED,
            VoiceErrorCategory.AUTH_FAILED,
            VoiceErrorCategory.PROTOCOL_ERROR,
            VoiceErrorCategory.NETWORK,
            VoiceErrorCategory.TIMEOUT,
            VoiceErrorCategory.SYSTEM_UNAVAILABLE,
            VoiceErrorCategory.UNSUPPORTED,
        )
        is VoiceModelError.MissingAccount,
        is VoiceModelError.MissingCredentials,
        is VoiceModelError.IncompleteConfig,
        is VoiceModelError.Unsupported -> true
        else -> false
    }

    /**
     * Resolves `(Provider, model, TranscribeMethod, headers)` candidates for a [ModelRole]
     * (used when Voice Input or Call Mode Listen is set to "Same as Main" or "Same as Light").
     */
    fun roleSttCandidates(store: ProviderStore, role: ModelRole): List<Triple<Provider, String, TranscribeMethod>> {
        val primary = store.configFor(role)
        val fallbackIds = store.roles.value.fallbacks(role)
        val configs = listOfNotNull(primary) + fallbackIds.mapNotNull { id ->
            store.configs.value.firstOrNull { it.id == id }
        }
        return configs.mapNotNull { cfg ->
            val p = store.provider(cfg.providerId) ?: return@mapNotNull null
            val method = if (p.kind == ProviderConfig.Kind.Gemini) TranscribeMethod.GeminiInline else TranscribeMethod.ChatInputAudio
            Triple(p, cfg.model, method)
        }
    }

    /**
     * Transcribes [wav] across an ordered list of [VoiceModelRef] candidates, rotating through each candidate's
     * Key Pool on 429/401/403 and failing over to backup models on 5xx overload, rate limits, or network errors.
     */
    suspend fun transcribeRefs(
        environment: VoiceModelEnvironment,
        store: ProviderStore,
        refs: List<VoiceModelRef>,
        wav: ByteArray,
        languageIso: String?,
    ): String {
        var lastError: Throwable? = null
        for (ref in refs) {
            val resolved = try {
                SttSelectionController(environment).requireModel(ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                continue
            }
            val stt = resolved.model.stt ?: continue
            val method = when (stt.protocol) {
                SttProtocol.AudioTranscriptions -> TranscribeMethod.AudioTranscriptions
                SttProtocol.GeminiInline -> TranscribeMethod.GeminiInline
                SttProtocol.ChatInputAudio -> TranscribeMethod.ChatInputAudio
                else -> continue
            }
            val provider = resolved.legacyProvider(stt.endpoint, forceGemini = stt.protocol == SttProtocol.GeminiInline)
            val headers = (resolved.account as? ResolvedAccount.ExistingProvider)?.headers.orEmpty()
            val poolId = when (val acc = resolved.account) {
                is ResolvedAccount.ExistingProvider -> acc.provider.id
                is ResolvedAccount.VoiceOwned -> "voice:${acc.secretId}"
                ResolvedAccount.None -> null
            }
            val rawKeys = when (val acc = resolved.account) {
                is ResolvedAccount.ExistingProvider -> store.providerKey(acc.provider.id).orEmpty()
                is ResolvedAccount.VoiceOwned -> environment.credentials.read(acc.secretId)?.apiKey.orEmpty()
                ResolvedAccount.None -> ""
            }
            val keyCount = KeyPoolManager.shared.parseKeys(rawKeys).size.coerceAtLeast(1)
            val transcriber = Transcriber(ProviderStore.modelHttp, headers)

            val initialKey = resolved.account.credentials.apiKey.ifBlank { null }
            val result = tryWithKeyPoolAndRetry(
                poolId = poolId,
                keyCount = keyCount,
                initialKey = initialKey,
                resolveKey = {
                    when (val acc = resolved.account) {
                        is ResolvedAccount.ExistingProvider -> store.activeApiKey(acc.provider.id)
                        is ResolvedAccount.VoiceOwned -> poolId?.let { KeyPoolManager.shared.getActiveKey(it) } ?: acc.credentials.apiKey.ifBlank { null }
                        ResolvedAccount.None -> null
                    }
                },
                block = { key ->
                    transcriber.transcribe(provider, key, resolved.model.id, method, wav, languageIso)
                },
            )
            result.onSuccess { return it }
            val err = result.exceptionOrNull()!!
            lastError = err
            if (!canFailoverToNextModel(err)) throw err
        }
        throw lastError ?: VoiceModelError.IncompleteConfig("No working speech model available")
    }

    /**
     * Transcribes [wav] using a chat [ModelRole]'s primary + fallback cascade + key pools.
     */
    suspend fun transcribeRole(
        store: ProviderStore,
        role: ModelRole,
        wav: ByteArray,
        languageIso: String?,
    ): String {
        val candidates = roleSttCandidates(store, role)
        if (candidates.isEmpty()) throw VoiceModelError.IncompleteConfig("No model configured for $role")
        var lastError: Throwable? = null
        for ((provider, model, method) in candidates) {
            val headers = store.clientHeaders(provider)
            val keyCount = KeyPoolManager.shared.parseKeys(store.providerKey(provider.id).orEmpty()).size.coerceAtLeast(1)
            val transcriber = Transcriber(ProviderStore.modelHttp, headers)
            val result = tryWithKeyPoolAndRetry(
                poolId = provider.id,
                keyCount = keyCount,
                resolveKey = { store.activeApiKey(provider.id) },
                block = { key ->
                    transcriber.transcribe(provider, key, model, method, wav, languageIso)
                },
            )
            result.onSuccess { return it }
            val err = result.exceptionOrNull()!!
            lastError = err
            if (!canFailoverToNextModel(err)) throw err
        }
        throw lastError ?: VoiceModelError.IncompleteConfig("No working speech model in $role cascade")
    }

    internal suspend fun <T> tryWithKeyPoolAndRetry(
        poolId: String?,
        keyCount: Int,
        initialKey: String? = null,
        resolveKey: () -> String?,
        block: suspend (String?) -> T,
    ): Result<T> {
        var transientRetries = 1
        var keyAttempts = 0
        var firstAttempt = true
        val maxKeyAttempts = keyCount.coerceAtLeast(1)
        var lastError: Throwable? = null
        while (keyAttempts < maxKeyAttempts) {
            val key = if (firstAttempt && initialKey != null) {
                firstAttempt = false
                initialKey
            } else {
                firstAttempt = false
                resolveKey()
            }
            try {
                return Result.success(block(key))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                val status = extractHttpStatus(e)
                if (poolId != null && key != null && status in setOf(401, 403, 429)) {
                    val retryAfter = extractRetryAfterMs(e)
                    KeyPoolManager.shared.markRejected(poolId, key, status!!, retryAfter)
                    keyAttempts++
                    continue
                }
                if (transientRetries > 0 && isTransientRetryable(e)) {
                    transientRetries--
                    delay(250)
                    continue
                }
                break
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Unknown voice error"))
    }

    /** Creates a [TtsModel] that rotates keys on 429/401/403 and fails over across [refs] (`[primary] + ttsFallbacks`) on 5xx/network errors. */
    fun createFailoverTts(
        context: Context,
        environment: VoiceModelEnvironment,
        refs: List<VoiceModelRef>,
        capabilities: TtsCapabilities,
    ): TtsModel {
        val controller = TtsSelectionController(environment)
        return FailoverTtsModel(context, environment, controller, refs, capabilities)
    }
}

private class FailoverTtsModel(
    private val context: Context,
    private val environment: VoiceModelEnvironment,
    private val controller: TtsSelectionController,
    private val refs: List<VoiceModelRef>,
    override val capabilities: TtsCapabilities,
) : TtsModel {
    override val name: String get() = refs.first().modelId

    override suspend fun synthesize(request: TtsRequest): TtsResponse {
        var lastError: Throwable? = null
        for (ref in refs) {
            val initialResolved = try {
                controller.requireModel(ref)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                continue
            }
            val poolId = when (val acc = initialResolved.account) {
                is ResolvedAccount.ExistingProvider -> acc.provider.id
                is ResolvedAccount.VoiceOwned -> "voice:${acc.secretId}"
                ResolvedAccount.None -> null
            }
            val service = environment.catalog.state.value.services
                .firstOrNull { it.id == ref.serviceId }
            val keyCount = service?.let { environment.accountResolver.poolCount(it) }?.coerceAtLeast(1) ?: 1
            val initialKey = initialResolved.account.credentials.apiKey.ifBlank { null }

            val result = VoiceFailover.tryWithKeyPoolAndRetry(
                poolId = poolId,
                keyCount = keyCount,
                initialKey = initialKey,
                resolveKey = {
                    when (val acc = initialResolved.account) {
                        is ResolvedAccount.ExistingProvider -> poolId?.let { KeyPoolManager.shared.getActiveKey(it) }
                        is ResolvedAccount.VoiceOwned -> poolId?.let { KeyPoolManager.shared.getActiveKey(it) } ?: acc.credentials.apiKey.ifBlank { null }
                        ResolvedAccount.None -> null
                    }
                },
                block = { key ->
                    val updatedCredentials = VoiceCredentials(
                        apiKey = key.orEmpty(),
                        appId = initialResolved.account.credentials.appId,
                        accessKey = initialResolved.account.credentials.accessKey,
                    )
                    val updatedAccount = when (val acc = initialResolved.account) {
                        is ResolvedAccount.ExistingProvider -> acc.copy(credentials = updatedCredentials)
                        is ResolvedAccount.VoiceOwned -> acc.copy(credentials = updatedCredentials)
                        ResolvedAccount.None -> ResolvedAccount.None
                    }
                    val resolved = initialResolved.copy(account = updatedAccount)
                    val model = environment.registry.tts(resolved).create(context, resolved, capabilities)
                    model.synthesize(request)
                },
            )
            result.onSuccess { return it }
            val err = result.exceptionOrNull()!!
            lastError = err
            if (!VoiceFailover.canFailoverToNextModel(err)) throw err
        }
        throw lastError ?: VoiceModelError.IncompleteConfig("No TTS model succeeded")
    }
}
