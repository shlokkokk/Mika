package com.wangzan101.mika.voice.model

import android.content.ContextWrapper
import com.wangzan101.mika.core.model.Capabilities
import com.wangzan101.mika.core.model.ModelError
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.core.model.TtsCapabilities
import com.wangzan101.mika.core.model.TtsModel
import com.wangzan101.mika.core.model.TtsRequest
import com.wangzan101.mika.core.model.TtsResponse
import com.wangzan101.mika.settings.AesGcmKeyCipher
import com.wangzan101.mika.settings.ProviderConfig
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.configFor
import com.wangzan101.mika.settings.connect
import com.wangzan101.mika.settings.setMainFallback
import com.wangzan101.mika.voice.Wav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator

class VoiceFailoverSimulationTest {
    @get:Rule val tmp = TemporaryFolder()

    private val testAesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var startedServer: MockWebServer? = null
    private val server: MockWebServer
        get() = startedServer ?: MockWebServer().apply { start() }.also { startedServer = it }
    private val context = ContextWrapper(null)
    private val wav = Wav.silence(80)

    private class MapOwnedAccountSource : VoiceOwnedAccountSource {
        val credentials = mutableMapOf<String, VoiceCredentials>()
        override fun read(secretId: String): VoiceCredentials? = credentials[secretId]
        override fun credentialsConfigured(secretId: String): Boolean = credentials[secretId] != null
        override fun configurationToken(secretId: String): String = credentials[secretId]?.apiKey.orEmpty()
    }

    @After
    fun tearDown() {
        startedServer?.shutdown()
        startedServer = null
    }

    private fun newProviderStore(): ProviderStore = ProviderStore(
        tmp.newFolder(),
        AesGcmKeyCipher { testAesKey },
    )

    private fun envWith(
        store: ProviderStore,
        catalog: VoiceCatalogStore,
        owned: VoiceOwnedAccountSource = MapOwnedAccountSource(),
        registry: VoiceAdapterRegistry = VoiceAdapterRegistry(),
    ): VoiceModelEnvironment = VoiceModelEnvironment(
        catalog = catalog,
        accountResolver = VoiceAccountResolver(ProviderStoreVoiceAccountSource(store), owned),
        registry = registry,
        credentials = owned,
        credentialStore = VoiceCredentialStore(
            object : VoiceCredentialBackend {
                private val map = mutableMapOf<String, String>()
                override fun read(secretId: String): String? = map[secretId]
                override fun write(secretId: String, value: String): Boolean {
                    map[secretId] = value
                    return true
                }
                override fun delete(secretId: String) {
                    map.remove(secretId)
                }
            },
            AesGcmKeyCipher { testAesKey },
        ),
        migrationMarker = object : MigrationMarker {
            override fun markComplete() {}
            override fun clear() {}
        },
    )

    @Test
    fun `standalone VoiceOwned service rotates through 3-key pool on 429 and 401 before succeeding`() = runBlocking {
        val store = newProviderStore()
        val catalog = VoiceCatalogStore(MemoryVoiceCatalogBackend(), migrated = true)
        val owned = MapOwnedAccountSource()
        val secretId = "sec-pool-rotate-1"
        owned.credentials[secretId] = VoiceCredentials(apiKey = "vk-1, vk-2\nvk-3")

        val svc = VoiceService("svc-owned-1", "Groq Voice", VoiceAccount.VoiceOwned(secretId))
        val model = VoiceModel(
            serviceId = svc.id,
            id = "whisper-large-v3-turbo",
            displayName = "Whisper Turbo",
            stt = SttBinding(
                protocol = SttProtocol.AudioTranscriptions,
                endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            ),
        )
        catalog.upsertService(svc)
        catalog.upsertModel(model)
        val ref = VoiceModelRef(svc.id, model.id, VoiceCapability.STT)
        val env = envWith(store, catalog, owned)

        val seenKeys = mutableListOf<String?>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val auth = request.getHeader("Authorization")
                seenKeys += auth
                return when (seenKeys.size) {
                    1 -> MockResponse().setResponseCode(429).addHeader("Retry-After", "5").setBody("""{"error":{"message":"rate limit"}}""")
                    2 -> MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid key"}}""")
                    else -> MockResponse().setResponseCode(200).setBody("""{"text":"Namaste from key 3"}""")
                }
            }
        }

        val transcript = VoiceFailover.transcribeRefs(env, store, listOf(ref), wav, "hi")
        assertEquals("Namaste from key 3", transcript)
        assertEquals(3, seenKeys.size)
        assertEquals(3, seenKeys.distinct().size)
        assertEquals(3, env.accountResolver.poolCount(svc))
    }

    @Test
    fun `primary key pool exhaustion + 5xx overload + 404 model missing cascades across 3 STT models`() = runBlocking {
        val store = newProviderStore()
        val catalog = VoiceCatalogStore(MemoryVoiceCatalogBackend(), migrated = true)
        val owned = MapOwnedAccountSource()

        // Primary: ExistingProvider with 2 keys -> p1-k1 gets 429, p1-k2 gets 503 twice (transient retry then failover)
        val p1 = store.connect(
            name = "Primary STT",
            kind = ProviderConfig.Kind.OpenAiCompat,
            baseUrl = server.url("/primary/v1").toString().trimEnd('/'),
            apiKey = "p1-k1, p1-k2",
            models = emptyList(),
        )
        val svc1 = VoiceService("provider:${p1.id}", "Primary STT", VoiceAccount.ExistingProvider(p1.id))
        val m1 = VoiceModel(svc1.id, "whisper-1", "Whisper 1", stt = SttBinding(SttProtocol.AudioTranscriptions))
        catalog.upsertService(svc1)
        catalog.upsertModel(m1)

        // Backup 1: VoiceOwned -> returns 404 ModelNotFound -> should immediately fail over without wasting retries
        val sec2 = "sec-backup-404"
        owned.credentials[sec2] = VoiceCredentials(apiKey = "b1-key")
        val svc2 = VoiceService("svc-b1", "Backup 1", VoiceAccount.VoiceOwned(sec2))
        val m2 = VoiceModel(svc2.id, "deleted-model", "Deleted", stt = SttBinding(SttProtocol.AudioTranscriptions, endpoint = server.url("/b1/v1").toString().trimEnd('/')))
        catalog.upsertService(svc2)
        catalog.upsertModel(m2)

        // Backup 2: Gemini provider -> succeeds
        val p3 = store.connect(
            name = "Gemini Backup",
            kind = ProviderConfig.Kind.Gemini,
            baseUrl = server.url("/gemini").toString().trimEnd('/'),
            apiKey = "gem-key",
            models = emptyList(),
        )
        val svc3 = VoiceService("provider:${p3.id}", "Gemini Backup", VoiceAccount.ExistingProvider(p3.id))
        val m3 = VoiceModel(svc3.id, "gemini-2.5-flash", "Gemini Flash", stt = SttBinding(SttProtocol.GeminiInline))
        catalog.upsertService(svc3)
        catalog.upsertModel(m3)

        val trace = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/primary/") -> {
                        val auth = request.getHeader("Authorization")
                        trace += "primary:$auth"
                        if (auth == "Bearer p1-k1") {
                            MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate limited"}}""")
                        } else {
                            MockResponse().setResponseCode(503).setBody("""{"error":{"message":"overloaded"}}""")
                        }
                    }
                    path.startsWith("/b1/") -> {
                        trace += "b1:${request.getHeader("Authorization")}"
                        MockResponse().setResponseCode(404).setBody("""{"error":{"message":"model_not_found"}}""")
                    }
                    path.startsWith("/gemini/") -> {
                        trace += "gemini:${request.getHeader("x-goog-api-key")}"
                        MockResponse().setResponseCode(200).setBody("""{"candidates":[{"content":{"parts":[{"text":"Backup Gemini ok"}]}}]}""")
                    }
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val env = envWith(store, catalog, owned)
        val refs = listOf(
            VoiceModelRef(svc1.id, m1.id, VoiceCapability.STT),
            VoiceModelRef(svc2.id, m2.id, VoiceCapability.STT),
            VoiceModelRef(svc3.id, m3.id, VoiceCapability.STT),
        )
        val text = VoiceFailover.transcribeRefs(env, store, refs, wav, "en")
        assertEquals("Backup Gemini ok", text)
        assertEquals(
            listOf(
                "primary:Bearer p1-k1",
                "primary:Bearer p1-k2",
                "primary:Bearer p1-k2", // 1 transient 250ms retry on 503
                "b1:Bearer b1-key",
                "gemini:gem-key",
            ),
            trace,
        )
    }

    @Test
    fun `502 transient glitch recovers on automatic retry without failing over to backup`() = runBlocking {
        val store = newProviderStore()
        val catalog = VoiceCatalogStore(MemoryVoiceCatalogBackend(), migrated = true)
        val p1 = store.connect(
            name = "Primary",
            kind = ProviderConfig.Kind.OpenAiCompat,
            baseUrl = server.url("/p1/v1").toString().trimEnd('/'),
            apiKey = "k-only",
            models = emptyList(),
        )
        val svc1 = VoiceService("provider:${p1.id}", "Primary", VoiceAccount.ExistingProvider(p1.id))
        val m1 = VoiceModel(svc1.id, "whisper-1", "Whisper", stt = SttBinding(SttProtocol.AudioTranscriptions))
        catalog.upsertService(svc1)
        catalog.upsertModel(m1)

        val p2 = store.connect(
            name = "Backup",
            kind = ProviderConfig.Kind.OpenAiCompat,
            baseUrl = server.url("/p2/v1").toString().trimEnd('/'),
            apiKey = "k-b",
            models = emptyList(),
        )
        val svc2 = VoiceService("provider:${p2.id}", "Backup", VoiceAccount.ExistingProvider(p2.id))
        val m2 = VoiceModel(svc2.id, "whisper-2", "Whisper 2", stt = SttBinding(SttProtocol.AudioTranscriptions))
        catalog.upsertService(svc2)
        catalog.upsertModel(m2)

        val p1Hits = AtomicInteger(0)
        val p2Hits = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path?.startsWith("/p1/") == true) {
                    if (p1Hits.incrementAndGet() == 1) {
                        MockResponse().setResponseCode(502).setBody("Bad Gateway")
                    } else {
                        MockResponse().setResponseCode(200).setBody("""{"text":"Recovered on retry"}""")
                    }
                } else {
                    p2Hits.incrementAndGet()
                    MockResponse().setResponseCode(200).setBody("""{"text":"Should not hit backup"}""")
                }
            }
        }

        val env = envWith(store, catalog)
        val text = VoiceFailover.transcribeRefs(
            env,
            store,
            listOf(VoiceModelRef(svc1.id, m1.id, VoiceCapability.STT), VoiceModelRef(svc2.id, m2.id, VoiceCapability.STT)),
            wav,
            null,
        )
        assertEquals("Recovered on retry", text)
        assertEquals(2, p1Hits.get())
        assertEquals(0, p2Hits.get())
    }

    @Test
    fun `Same as Main and Same as Light inherit full role fallback cascade and key pools`() = runBlocking {
        val store = newProviderStore()
        val mainPrimary = store.connect(
            name = "Main Primary",
            kind = ProviderConfig.Kind.OpenAiCompat,
            baseUrl = server.url("/main-p/v1").toString().trimEnd('/'),
            apiKey = "mp-1, mp-2",
            models = emptyList(),
        )
        val mainFallback = store.connect(
            name = "Main Fallback",
            kind = ProviderConfig.Kind.Gemini,
            baseUrl = server.url("/main-fb").toString().trimEnd('/'),
            apiKey = "mf-gem",
            models = emptyList(),
        )
        val lightPrimary = store.connect(
            name = "Light Primary",
            kind = ProviderConfig.Kind.OpenAiCompat,
            baseUrl = server.url("/light-p/v1").toString().trimEnd('/'),
            apiKey = "lp-1",
            models = emptyList(),
        )

        val none = Capabilities(toolCalls = false, vision = false, contextTokens = 128_000, reasoning = false)
        // Temporarily assign Main Fallback to Vision so we get a ModelConfig ID for setMainFallback
        store.use(ModelRole.Vision, mainFallback.id, "gemini-2.5-pro", none)
        val mfCfgId = store.configFor(ModelRole.Vision)!!.id
        store.use(ModelRole.Main, mainPrimary.id, "gpt-4o-audio", none)
        store.setMainFallback(0, mfCfgId)
        store.use(ModelRole.Light, lightPrimary.id, "gpt-4o-mini-audio", none)

        val trace = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/main-p/") -> {
                        val auth = request.getHeader("Authorization")
                        trace += "main-p:$auth"
                        if (auth == "Bearer mp-1") {
                            MockResponse().setResponseCode(429).setBody("""{"error":{"message":"429"}}""")
                        } else {
                            MockResponse().setResponseCode(500).setBody("""{"error":{"message":"500"}}""")
                        }
                    }
                    path.startsWith("/main-fb/") -> {
                        trace += "main-fb:${request.getHeader("x-goog-api-key")}"
                        MockResponse().setResponseCode(200).setBody("""{"candidates":[{"content":{"parts":[{"text":"Main fallback heard Hindi"}]}}]}""")
                    }
                    path.startsWith("/light-p/") -> {
                        trace += "light-p:${request.getHeader("Authorization")}"
                        MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"Light heard English"}}]}""")
                    }
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val mainText = VoiceFailover.transcribeRole(store, ModelRole.Main, wav, "hi")
        assertEquals("Main fallback heard Hindi", mainText)
        assertEquals(
            listOf(
                "main-p:Bearer mp-1",
                "main-p:Bearer mp-2",
                "main-p:Bearer mp-2",
                "main-fb:mf-gem",
            ),
            trace,
        )

        val lightText = VoiceFailover.transcribeRole(store, ModelRole.Light, wav, "en")
        assertEquals("Light heard English", lightText)
        assertEquals("light-p:Bearer lp-1", trace.last())
    }

    @Test
    fun `network disconnect on primary fails over to backup STT`() = runBlocking {
        val store = newProviderStore()
        val catalog = VoiceCatalogStore(MemoryVoiceCatalogBackend(), migrated = true)
        val p1 = store.connect("Drop", ProviderConfig.Kind.OpenAiCompat, server.url("/drop/v1").toString().trimEnd('/'), "k1", emptyList())
        val svc1 = VoiceService("provider:${p1.id}", "Drop", VoiceAccount.ExistingProvider(p1.id))
        val m1 = VoiceModel(svc1.id, "m1", "M1", stt = SttBinding(SttProtocol.AudioTranscriptions))
        catalog.upsertService(svc1)
        catalog.upsertModel(m1)

        val p2 = store.connect("Ok", ProviderConfig.Kind.OpenAiCompat, server.url("/ok/v1").toString().trimEnd('/'), "k2", emptyList())
        val svc2 = VoiceService("provider:${p2.id}", "Ok", VoiceAccount.ExistingProvider(p2.id))
        val m2 = VoiceModel(svc2.id, "m2", "M2", stt = SttBinding(SttProtocol.AudioTranscriptions))
        catalog.upsertService(svc2)
        catalog.upsertModel(m2)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.path?.startsWith("/drop/") == true) {
                    MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                } else {
                    MockResponse().setResponseCode(200).setBody("""{"text":"Saved by backup"}""")
                }
            }
        }

        val env = envWith(store, catalog)
        val out = VoiceFailover.transcribeRefs(
            env,
            store,
            listOf(VoiceModelRef(svc1.id, m1.id, VoiceCapability.STT), VoiceModelRef(svc2.id, m2.id, VoiceCapability.STT)),
            wav,
            null,
        )
        assertEquals("Saved by backup", out)
    }

    @Test
    fun `TTS single model with multi-key pool rotates on 429 and succeeds without backup`() = runBlocking {
        val store = newProviderStore()
        val catalog = VoiceCatalogStore(MemoryVoiceCatalogBackend(), migrated = true)
        val owned = MapOwnedAccountSource()
        val secId = "sec-tts-pool-only"
        owned.credentials[secId] = VoiceCredentials(apiKey = "tk-1, tk-2")

        val svc = VoiceService("svc-tts-1", "TTS Pool", VoiceAccount.VoiceOwned(secId))
        val m = VoiceModel(svc.id, "tts-1", "TTS 1", tts = TtsBinding(TtsProtocol.OpenAiSpeech))
        catalog.upsertService(svc)
        catalog.upsertModel(m)
        val ref = VoiceModelRef(svc.id, m.id, VoiceCapability.TTS)

        val usedKeys = mutableListOf<String>()
        val fakeRegistry = VoiceAdapterRegistry(
            ttsAdapters = listOf(
                object : com.wangzan101.mika.voice.model.adapters.TtsAdapter {
                    override val protocol = TtsProtocol.OpenAiSpeech
                    override fun create(context: android.content.Context, model: ResolvedVoiceModel, capabilities: TtsCapabilities): TtsModel {
                        val key = model.account.credentials.apiKey
                        return object : TtsModel {
                            override val name = model.model.id
                            override val capabilities = capabilities
                            override suspend fun synthesize(request: TtsRequest): TtsResponse {
                                usedKeys += key
                                if (key == "tk-1") {
                                    throw VoiceModelError.from(ModelError.RateLimited(retryAfterMs = 2_000L))
                                }
                                return TtsResponse(byteArrayOf(1, 2, 3), "audio/wav")
                            }
                        }
                    }
                },
            ),
        )

        val env = envWith(store, catalog, owned, fakeRegistry)
        val tts = env.tts.createTts(context, ref, TtsCapabilities(formats = setOf("wav")))
        val response = tts.synthesize(TtsRequest("Hello", format = "wav"))

        assertArrayEquals(byteArrayOf(1, 2, 3), response.bytes)
        assertEquals(listOf("tk-1", "tk-2"), usedKeys)
    }

    @Test
    fun `TTS 5xx overload fails over to ttsFallbacks and propagates CancellationException untouched`() = runBlocking {
        val store = newProviderStore()
        val catalog = VoiceCatalogStore(MemoryVoiceCatalogBackend(), migrated = true)
        val p1 = store.connect("Primary TTS", ProviderConfig.Kind.OpenAiCompat, "https://t1.test/v1", "k1", emptyList())
        val p2 = store.connect("Backup TTS", ProviderConfig.Kind.Gemini, "https://t2.test", "k2", emptyList())
        val svc1 = VoiceService("provider:${p1.id}", "Primary TTS", VoiceAccount.ExistingProvider(p1.id))
        val svc2 = VoiceService("provider:${p2.id}", "Backup TTS", VoiceAccount.ExistingProvider(p2.id))
        val m1 = VoiceModel(svc1.id, "tts-primary", "TTS Primary", tts = TtsBinding(TtsProtocol.OpenAiSpeech))
        val m2 = VoiceModel(svc2.id, "tts-backup", "TTS Backup", tts = TtsBinding(TtsProtocol.GeminiTts))
        catalog.upsertService(svc1)
        catalog.upsertService(svc2)
        catalog.upsertModel(m1)
        catalog.upsertModel(m2)

        val ref1 = VoiceModelRef(svc1.id, m1.id, VoiceCapability.TTS)
        val ref2 = VoiceModelRef(svc2.id, m2.id, VoiceCapability.TTS)
        catalog.setTtsSelection(ref1)
        catalog.setTtsFallbacks(listOf(ref2))

        val events = mutableListOf<String>()
        var throwCancelOnPrimary = false
        val fakeRegistry = VoiceAdapterRegistry(
            ttsAdapters = listOf(
                object : com.wangzan101.mika.voice.model.adapters.TtsAdapter {
                    override val protocol = TtsProtocol.OpenAiSpeech
                    override fun create(context: android.content.Context, model: ResolvedVoiceModel, capabilities: TtsCapabilities) = object : TtsModel {
                        override val name = model.model.id
                        override val capabilities = capabilities
                        override suspend fun synthesize(request: TtsRequest): TtsResponse {
                            events += "primary"
                            if (throwCancelOnPrimary) throw CancellationException("user hung up")
                            throw VoiceModelError.from(ModelError.Server(503, "overload"))
                        }
                    }
                },
                object : com.wangzan101.mika.voice.model.adapters.TtsAdapter {
                    override val protocol = TtsProtocol.GeminiTts
                    override fun create(context: android.content.Context, model: ResolvedVoiceModel, capabilities: TtsCapabilities) = object : TtsModel {
                        override val name = model.model.id
                        override val capabilities = capabilities
                        override suspend fun synthesize(request: TtsRequest): TtsResponse {
                            events += "backup"
                            return TtsResponse(byteArrayOf(9, 8), "audio/wav")
                        }
                    }
                },
            ),
        )

        val env = envWith(store, catalog, registry = fakeRegistry)
        val tts = env.tts.createTts(context, ref1, TtsCapabilities(formats = setOf("wav")))
        val audio = tts.synthesize(TtsRequest("Test", format = "wav"))
        assertArrayEquals(byteArrayOf(9, 8), audio.bytes)
        assertEquals(listOf("primary", "primary", "backup"), events)

        throwCancelOnPrimary = true
        events.clear()
        try {
            tts.synthesize(TtsRequest("Cancel", format = "wav"))
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            assertEquals(listOf("primary"), events)
        }
    }

    @Test
    fun `VoiceCatalogStore clamps backup chains to 3, deduplicates, and persists selections`() {
        val backend = MemoryVoiceCatalogBackend()
        val store = VoiceCatalogStore(backend, migrated = true)
        val r1 = VoiceModelRef("s1", "m1", VoiceCapability.STT)
        val r2 = VoiceModelRef("s2", "m2", VoiceCapability.STT)
        val r3 = VoiceModelRef("s3", "m3", VoiceCapability.STT)
        val r4 = VoiceModelRef("s4", "m4", VoiceCapability.STT)

        store.setSttFallbacks(listOf(r1, r2, r1, r3, r4))
        assertEquals(listOf(r1, r2, r3), store.state.value.selections.sttFallbacks)

        store.setSttSelection(SttSelection.SameAsMain)
        val reloaded = VoiceCatalogStore(backend, migrated = true)
        assertEquals(SttSelection.SameAsMain, reloaded.state.value.selections.stt)
        assertEquals(listOf(r1, r2, r3), reloaded.state.value.selections.sttFallbacks)
    }

    @Test
    fun `all keys and backups exhausted throws last error cleanly`() = runBlocking {
        val store = newProviderStore()
        val catalog = VoiceCatalogStore(MemoryVoiceCatalogBackend(), migrated = true)
        val p1 = store.connect("Exhaust", ProviderConfig.Kind.OpenAiCompat, server.url("/ex/v1").toString().trimEnd('/'), "e1, e2", emptyList())
        val svc1 = VoiceService("provider:${p1.id}", "Exhaust", VoiceAccount.ExistingProvider(p1.id))
        val m1 = VoiceModel(svc1.id, "m1", "M1", stt = SttBinding(SttProtocol.AudioTranscriptions))
        catalog.upsertService(svc1)
        catalog.upsertModel(m1)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(429).setBody("""{"error":{"message":"quota gone"}}""")
        }

        val env = envWith(store, catalog)
        try {
            VoiceFailover.transcribeRefs(env, store, listOf(VoiceModelRef(svc1.id, m1.id, VoiceCapability.STT)), wav, null)
            fail("Expected ModelError.RateLimited")
        } catch (_: ModelError.RateLimited) {
            assertTrue(true)
        }
    }
}
