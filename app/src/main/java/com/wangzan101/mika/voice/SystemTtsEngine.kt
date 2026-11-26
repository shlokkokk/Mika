package com.wangzan101.mika.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * 系统 [TextToSpeech] 的一次性门面。每个合成操作新建一个引擎、用完即 [shutdown]，
 * 所以这里把引擎做成可注入的接口:JVM 测试用一个假引擎驱动合成状态机,真机才碰 [TextToSpeech]。
 * 接口只用 JVM 类型(Locale/File/函数),回调是普通的 lambda,不在接口里出现 Android 监听器。
 */
interface SystemTtsEngine {
    /** 异步初始化;完成后回调一次 [onInit](TextToSpeech.SUCCESS 或 TextToSpeech.ERROR)。 */
    fun init(onInit: (status: Int) -> Unit)

    /** 返回 TextToSpeech.LANG_AVAILABLE / LANG_MISSING_DATA / LANG_NOT_SUPPORTED。 */
    fun setLanguage(locale: Locale): Int

    fun setSpeechRate(rate: Float): Int

    /** 设置特定音色;成功返回 TextToSpeech.SUCCESS。默认空实现以兼容测试。 */
    fun setVoice(voiceName: String): Int = TextToSpeech.SUCCESS

    /** 注册合成进度回调;[onError] 只带 utteranceId,错误码由真实引擎自行归一。 */
    fun setOnUtteranceProgressListener(onDone: (utteranceId: String) -> Unit, onError: (utteranceId: String) -> Unit)

    /** 合成到 [file];返回 TextToSpeech.SUCCESS 或 TextToSpeech.ERROR。 */
    fun synthesizeToFile(text: String, file: File, utteranceId: String): Int

    fun stop()

    fun shutdown()
}

/** 真实实现:每个实例持有自己的 [TextToSpeech],调用方保证一次合成用完即关。 */
class AndroidSystemTtsEngine(
    context: Context,
    private val enginePackage: String? = null,
) : SystemTtsEngine {
    private val context = context.applicationContext
    private var tts: TextToSpeech? = null

    override fun init(onInit: (status: Int) -> Unit) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                onInit(status)
            } else if (!enginePackage.isNullOrBlank()) {
                // 如果指定的第三方引擎初始化失败，自动回退到系统默认 TTS 引擎
                runCatching { tts?.shutdown() }
                tts = TextToSpeech(context) { fallbackStatus -> onInit(fallbackStatus) }
            } else {
                onInit(status)
            }
        }
        tts = if (!enginePackage.isNullOrBlank()) {
            TextToSpeech(context, listener, enginePackage)
        } else {
            TextToSpeech(context, listener)
        }
    }

    override fun setLanguage(locale: Locale): Int = tts?.setLanguage(locale) ?: TextToSpeech.ERROR

    override fun setVoice(voiceName: String): Int {
        if (voiceName.isBlank()) return TextToSpeech.SUCCESS
        val t = tts ?: return TextToSpeech.ERROR
        return try {
            val matched = t.voices?.firstOrNull { it.name == voiceName }
            if (matched != null) {
                t.voice = matched
                TextToSpeech.SUCCESS
            } else {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
        } catch (_: Throwable) {
            TextToSpeech.ERROR
        }
    }

    override fun setSpeechRate(rate: Float): Int = tts?.setSpeechRate(rate) ?: TextToSpeech.ERROR

    override fun setOnUtteranceProgressListener(onDone: (String) -> Unit, onError: (String) -> Unit) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) = onDone(utteranceId)
            override fun onError(utteranceId: String) = onError(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) = onError(utteranceId)
        })
    }

    override fun synthesizeToFile(text: String, file: File, utteranceId: String): Int =
        tts?.synthesizeToFile(text, null, file, utteranceId) ?: TextToSpeech.ERROR

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
    }
}

data class SystemTtsEngineInfo(
    val packageName: String,
    val label: String,
    val isDefault: Boolean = false,
)

data class SystemTtsVoiceInfo(
    val name: String,
    val locale: Locale? = null,
    val displayName: String = name,
)

object SystemTtsDiscovery {
    fun getInstalledEngines(context: Context): List<SystemTtsEngineInfo> {
        val pm = context.packageManager
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(intent, 0)
            }
        } catch (_: Throwable) {
            emptyList()
        }

        val defaultPkg = try {
            Settings.Secure.getString(context.contentResolver, "tts_default_synth")
        } catch (_: Throwable) {
            null
        }

        val engines = resolveInfos.mapNotNull { ri ->
            val pkg = ri.serviceInfo?.packageName ?: return@mapNotNull null
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull()?.ifBlank { pkg } ?: pkg
            SystemTtsEngineInfo(packageName = pkg, label = label, isDefault = pkg == defaultPkg)
        }.distinctBy { it.packageName }

        return engines.sortedWith(compareByDescending<SystemTtsEngineInfo> { it.isDefault }.thenBy { it.label })
    }

    suspend fun queryVoices(context: Context, enginePackage: String? = null): List<SystemTtsVoiceInfo> =
        withContext(Dispatchers.IO) {
            val ready = CompletableDeferred<List<SystemTtsVoiceInfo>>()
            var tts: TextToSpeech? = null
            try {
                val listener = TextToSpeech.OnInitListener { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val voices = runCatching {
                            tts?.voices?.map { v ->
                                val label = buildString {
                                    val lang = v.locale?.displayName?.ifBlank { null }
                                    if (lang != null) append(lang).append(" (").append(v.name).append(")")
                                    else append(v.name)
                                }
                                SystemTtsVoiceInfo(name = v.name, locale = v.locale, displayName = label)
                            }?.sortedBy { it.displayName } ?: emptyList()
                        }.getOrDefault(emptyList())
                        ready.complete(voices)
                    } else {
                        ready.complete(emptyList())
                    }
                }
                tts = if (!enginePackage.isNullOrBlank()) {
                    TextToSpeech(context.applicationContext, listener, enginePackage)
                } else {
                    TextToSpeech(context.applicationContext, listener)
                }

                withTimeoutOrNull(2500) { ready.await() } ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            } finally {
                runCatching { tts?.shutdown() }
            }
        }
}

