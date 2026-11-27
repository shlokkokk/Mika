package com.wangzan101.mika.voice.ui

import com.wangzan101.mika.core.model.TtsRequest
import com.wangzan101.mika.core.model.TtsResponse
import com.wangzan101.mika.voice.SpeechEvent
import com.wangzan101.mika.voice.SpeechSession
import com.wangzan101.mika.voice.model.MigrationResult
import com.wangzan101.mika.voice.model.SttSelectionController
import com.wangzan101.mika.voice.model.TtsSelectionController
import com.wangzan101.mika.voice.model.VoiceAccountManager
import com.wangzan101.mika.voice.model.VoiceCapability
import com.wangzan101.mika.voice.model.VoiceCatalogSnapshot
import com.wangzan101.mika.voice.model.VoiceCatalogStore
import com.wangzan101.mika.voice.model.VoiceCredentials
import com.wangzan101.mika.voice.model.VoiceModel
import com.wangzan101.mika.voice.model.VoiceModelEnvironment
import com.wangzan101.mika.voice.model.VoiceModelError
import com.wangzan101.mika.voice.model.VoiceModelProbe
import com.wangzan101.mika.voice.model.VoiceModelRef
import com.wangzan101.mika.voice.model.VoiceService
import com.wangzan101.mika.voice.model.VoiceVerification
import com.wangzan101.mika.voice.model.toError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wangzan101.mika.settings.ui.models.providerVoiceUsage

/** A voice probe's outcome; only [Success] is gated on an unchanged configuration fingerprint. */
sealed interface ProbeResult {
    data object Success : ProbeResult
    data class Failed(val error: VoiceModelError) : ProbeResult
}

/** Per-fingerprint probe progress shown by the catalog page. */
sealed interface VoiceProbeUi {
    data object Idle : VoiceProbeUi
    data object Running : VoiceProbeUi
    data class Listening(
        val transcript: String,
        val level: Float = 0f,
        val canFinish: Boolean = false,
        val converting: Boolean = false,
    ) : VoiceProbeUi
    data object Playing : VoiceProbeUi
    data class Failed(val error: VoiceModelError) : VoiceProbeUi
    data object Verified : VoiceProbeUi

    val isActive: Boolean
        get() = this is Running || this is Listening || this is Playing
}

/**
 * Catalog page state and actions: CRUD with explicit per-capability protocols, save-unverified, type-specific
 * probes and account rules. Probe state is keyed by the configuration fingerprint captured when the probe began;
 * a probe that finishes after the configuration changed cannot mark a stale configuration as verified.
 */
class VoiceCatalogViewModel(
    private val environment: VoiceModelEnvironment,
    private val manager: VoiceAccountManager,
    private val probe: VoiceModelProbe,
    private val scope: CoroutineScope,
    private val playAudio: suspend (ByteArray, String) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val playAudioWithStatus: (suspend (ByteArray, String, () -> Unit) -> Unit)? = null,
    private val onStopAudio: () -> Unit = {},
) {
    val catalog: VoiceCatalogStore = environment.catalog
    val snapshot: StateFlow<VoiceCatalogSnapshot> = catalog.state

    /** The migration failure, if the one-time legacy migration did not complete. */
    val migrationError: VoiceModelError? get() = environment.migrationError

    /** Clears a corrupt snapshot and re-runs the legacy migration from retained rollback data. */
    suspend fun repairCorruptCatalog(): MigrationResult = withContext(io) { environment.repairCorruptCatalog() }

    private val _probes = MutableStateFlow<Map<String, VoiceProbeUi>>(emptyMap())
    val probes: StateFlow<Map<String, VoiceProbeUi>> = _probes.asStateFlow()
    private val _transcripts = MutableStateFlow<Map<String, String>>(emptyMap())
    val transcripts: StateFlow<Map<String, String>> = _transcripts.asStateFlow()
    private val activeJobs = mutableMapOf<String, Job>()
    private val activeSttSessions = mutableMapOf<String, SpeechSession>()

    // ---------- accounts, models and services ----------

    fun saveService(service: VoiceService, credentials: VoiceCredentials? = null) {
        manager.saveVoiceAccount(service, credentials)
    }

    fun upsertModel(model: VoiceModel) {
        catalog.upsertModel(model)
    }

    /** Save without probing; the model stays unverified until a type-specific test succeeds. */
    fun saveUnverified(model: VoiceModel) {
        catalog.upsertModel(model)
    }

    fun deleteModel(ref: VoiceModelRef) {
        if (ref == SttSelectionController.SYSTEM_STT_REF) return
        if (ref == TtsSelectionController.SYSTEM_TTS_REF) return
        catalog.deleteModel(ref)
    }

    fun deleteService(serviceId: String) {
        if (serviceId == SttSelectionController.SYSTEM_STT_SERVICE.id) return
        catalog.deleteService(serviceId)
    }

    fun deleteProvider(providerId: String): List<VoiceModelRef> = manager.deleteProviderWithVoiceUsage(providerId)

    fun providerVoiceUsage(providerId: String): List<VoiceModelRef> = manager.providerVoiceUsage(providerId)

    /** Edits one capability's endpoint; also invalidates that capability's verification in the catalog. */
    fun updateEndpoint(ref: VoiceModelRef, endpoint: String) {
        val model = catalog.requireModel(ref)
        val updated = when (ref.capability) {
            VoiceCapability.STT -> model.copy(stt = model.stt?.copy(endpoint = endpoint))
            VoiceCapability.TTS -> model.copy(tts = model.tts?.copy(endpoint = endpoint))
            VoiceCapability.REALTIME -> model.copy(realtime = model.realtime?.copy(endpoint = endpoint))
        }
        catalog.upsertModel(updated)
    }

    /** The current (non-decrypting) configuration fingerprint for [ref], or null while the catalog is unreadable. */
    fun probeFingerprint(ref: VoiceModelRef): String? = runCatching { probe.connection(ref) }.getOrNull()

    /** Current probe progress for [ref], looked up by its configuration fingerprint. */
    fun probeState(ref: VoiceModelRef): VoiceProbeUi {
        val fingerprint = probeFingerprint(ref) ?: return VoiceProbeUi.Idle
        return _probes.value[fingerprint] ?: VoiceProbeUi.Idle
    }

    // ---------- probes ----------

    /**
     * Starts an async probe, capturing the current configuration fingerprint. Completing the returned deferred
     * with [ProbeResult.Success] verifies the model only while that fingerprint still matches; cancellation and
     * late completions leave the verification unchanged.
     */
    fun probeAsync(ref: VoiceModelRef): CompletableDeferred<ProbeResult> = startProbe(ref).first

    private fun startProbe(ref: VoiceModelRef): Pair<CompletableDeferred<ProbeResult>, String> {
        val fingerprint = probe.connection(ref)
        stopOtherProbes(except = fingerprint)
        setProbe(fingerprint, VoiceProbeUi.Running)
        val pending = CompletableDeferred<ProbeResult>()
        pending.invokeOnCompletion {
            activeJobs.remove(fingerprint)
            if (pending.isCancelled) return@invokeOnCompletion
            when (val result = pending.getCompleted()) {
                is ProbeResult.Success -> {
                    val stillCurrent = runCatching { probe.connection(ref) == fingerprint }.getOrDefault(false)
                    if (stillCurrent) catalog.markVerification(ref, VoiceVerification.Verified)
                    setProbe(fingerprint, if (stillCurrent) VoiceProbeUi.Verified else VoiceProbeUi.Idle)
                }
                is ProbeResult.Failed -> setProbe(fingerprint, VoiceProbeUi.Failed(result.error))
            }
        }
        return pending to fingerprint
    }

    private fun stopOtherProbes(except: String? = null) {
        val toStop = activeJobs.keys.filter { it != except }
        toStop.forEach { fp ->
            activeSttSessions.remove(fp)?.cancel()
            activeJobs.remove(fp)?.cancel()
            if (_probes.value[fp]?.isActive == true) setProbe(fp, VoiceProbeUi.Idle)
        }
        onStopAudio()
    }

    /** Stops all active probes (e.g. when leaving the screen so mic and audio always release). */
    fun stopAllProbes() {
        stopOtherProbes(except = null)
    }

    /** Stops an in-progress STT capture, TTS synthesis/playback, or Realtime connection probe for [ref]. */
    fun stopProbe(ref: VoiceModelRef) {
        val fingerprint = probeFingerprint(ref)
        if (fingerprint != null) {
            activeSttSessions.remove(fingerprint)?.cancel()
            activeJobs.remove(fingerprint)?.cancel()
            _transcripts.update { it - fingerprint }
            if (_probes.value[fingerprint]?.isActive == true) setProbe(fingerprint, VoiceProbeUi.Idle)
        }
        onStopAudio()
    }

    /** Ends an active non-live STT recording early so it transcribes immediately. */
    fun finishStt(ref: VoiceModelRef) {
        val fingerprint = probeFingerprint(ref) ?: return
        val session = activeSttSessions[fingerprint] ?: return
        val current = _probes.value[fingerprint] as? VoiceProbeUi.Listening
        if (current != null) setProbe(fingerprint, current.copy(level = 0f, canFinish = false, converting = true))
        session.release()
    }

    private fun setProbe(fingerprint: String, state: VoiceProbeUi) {
        _probes.update { it + (fingerprint to state) }
    }

    /** Holds a real microphone capture, drives live waveform levels, and finishes automatically on pause or Done. */
    fun verifyStt(ref: VoiceModelRef) {
        val (pending, fingerprint) = startProbe(ref)
        val job = scope.launch {
            val session = try {
                probe.openSttSession(ref)
            } catch (e: CancellationException) {
                pending.cancel()
                throw e
            } catch (e: Exception) {
                pending.complete(ProbeResult.Failed(VoiceModelError.from(e)))
                return@launch
            }
            activeSttSessions[fingerprint] = session
            var currentTranscript = ""
            var currentLevel = 0f
            var heardSpeech = false
            val startedMs = System.currentTimeMillis()
            var lastLoudMs = startedMs
            var released = false
            try {
                session.events.collect { event ->
                    when (event) {
                        is SpeechEvent.Level -> {
                            currentLevel = event.value
                            val now = System.currentTimeMillis()
                            if (event.value >= 0.08f) {
                                heardSpeech = true
                                lastLoudMs = now
                            }
                            setProbe(
                                fingerprint,
                                VoiceProbeUi.Listening(
                                    transcript = currentTranscript,
                                    level = currentLevel,
                                    canFinish = !session.live && !released,
                                    converting = released,
                                ),
                            )
                            if (!session.live && !released) {
                                val silenceElapsed = now - lastLoudMs
                                val totalElapsed = now - startedMs
                                if (!heardSpeech && totalElapsed >= 6_000L) {
                                    released = true
                                    runCatching { session.cancel() }
                                    _transcripts.update { it - fingerprint }
                                    pending.complete(
                                        ProbeResult.Failed(
                                            VoiceModelError.Protocol(
                                                com.wangzan101.mika.voice.model.VoiceErrorCategory.SYSTEM_UNAVAILABLE,
                                                "No speech detected",
                                            ),
                                        ),
                                    )
                                } else if ((heardSpeech && silenceElapsed >= 1_600L && totalElapsed >= 900L) || totalElapsed >= 15_000L) {
                                    released = true
                                    setProbe(
                                        fingerprint,
                                        VoiceProbeUi.Listening(
                                            transcript = currentTranscript,
                                            level = 0f,
                                            canFinish = false,
                                            converting = true,
                                        ),
                                    )
                                    session.release()
                                }
                            }
                        }
                        is SpeechEvent.Partial -> {
                            currentTranscript = event.stable + event.tail
                            setProbe(
                                fingerprint,
                                VoiceProbeUi.Listening(
                                    transcript = currentTranscript,
                                    level = currentLevel,
                                    canFinish = !session.live && !released,
                                    converting = released,
                                ),
                            )
                        }
                        SpeechEvent.Converting -> {
                            released = true
                            setProbe(
                                fingerprint,
                                VoiceProbeUi.Listening(
                                    transcript = currentTranscript,
                                    level = 0f,
                                    canFinish = false,
                                    converting = true,
                                ),
                            )
                        }
                        is SpeechEvent.Final -> {
                            if (event.text.isBlank()) {
                                _transcripts.update { it - fingerprint }
                                pending.complete(
                                    ProbeResult.Failed(
                                        VoiceModelError.Protocol(
                                            com.wangzan101.mika.voice.model.VoiceErrorCategory.SYSTEM_UNAVAILABLE,
                                            "No speech detected",
                                        ),
                                    ),
                                )
                            } else {
                                _transcripts.update { it + (fingerprint to event.text) }
                                pending.complete(ProbeResult.Success)
                            }
                        }
                        is SpeechEvent.Failed -> {
                            _transcripts.update { it - fingerprint }
                            pending.complete(ProbeResult.Failed(event.why.toError()))
                        }
                    }
                }
            } catch (e: CancellationException) {
                runCatching { session.cancel() }
                pending.cancel()
                throw e
            } catch (e: Exception) {
                pending.complete(ProbeResult.Failed(VoiceModelError.from(e)))
            } finally {
                activeSttSessions.remove(fingerprint)
                runCatching { session.release() }
            }
        }
        activeJobs[fingerprint] = job
    }

    /** Synthesizes once, then plays through the injected player before reporting success. */
    fun verifyTts(ref: VoiceModelRef, request: TtsRequest) {
        val (pending, fingerprint) = startProbe(ref)
        val job = scope.launch {
            val response = try {
                withContext(io) { probe.verifyTts(ref, request) }
            } catch (e: CancellationException) {
                pending.cancel()
                throw e
            } catch (e: Exception) {
                pending.complete(ProbeResult.Failed(VoiceModelError.from(e)))
                return@launch
            }
            try {
                if (playAudioWithStatus != null) {
                    withContext(io) {
                        playAudioWithStatus(response.bytes, response.mime) {
                            setProbe(fingerprint, VoiceProbeUi.Playing)
                        }
                    }
                } else {
                    setProbe(fingerprint, VoiceProbeUi.Playing)
                    withContext(io) { playAudio(response.bytes, response.mime) }
                }
            } catch (e: CancellationException) {
                pending.cancel()
                throw e
            } catch (e: Exception) {
                pending.complete(
                    ProbeResult.Failed(
                        VoiceModelError.Protocol(
                            com.wangzan101.mika.voice.model.VoiceErrorCategory.SYSTEM_UNAVAILABLE,
                            "Playback failed: ${e.message.orEmpty()}",
                        ),
                    ),
                )
                return@launch
            }
            pending.complete(ProbeResult.Success)
        }
        activeJobs[fingerprint] = job
    }

    /** Records a Realtime handshake outcome; success is gated on an unchanged fingerprint like every probe. */
    fun verifyRealtime(ref: VoiceModelRef) {
        val (pending, fingerprint) = startProbe(ref)
        val job = scope.launch {
            try {
                withContext(io) { probe.verifyRealtimeConnection(ref) }
            } catch (e: CancellationException) {
                pending.cancel()
                throw e
            } catch (e: Exception) {
                pending.complete(ProbeResult.Failed(VoiceModelError.from(e)))
                return@launch
            }
            pending.complete(ProbeResult.Success)
        }
        activeJobs[fingerprint] = job
    }
}
