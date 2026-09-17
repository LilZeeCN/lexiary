package com.lilzee.zee.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 进程级 TTS 单例。
 * 调研结论（2026-09）：
 * - 引擎初始化是异步的，部分设备要 1–5 秒，必须提前预热，不能在点击时才创建；
 * - speak() 必须等 onInit(SUCCESS) 且 setLanguage 非 MISSING_DATA/NOT_SUPPORTED，否则静默失败；
 * - 国行 ROM（如澎湃OS）普遍没有 Google TTS，但多有厂商引擎；语言不可用时 UI 整体隐藏发音入口，
 *   不报错、不引导安装，保持零打扰；
 * - 重复点击用 QUEUE_FLUSH 打断上一段；Android 14 有引擎启动崩溃的案例，所有调用包 try/catch。
 */
object Speaker {

    /** 引擎就绪且支持英文；UI 据此显示/隐藏发音入口 */
    var ready by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null
    private var initSettled = false

    /** 提前创建引擎；失败后（如用户后来才装引擎）每次 onResume 自动重试 */
    fun warmup(context: Context) {
        if (ready) return
        if (tts != null) {
            if (!initSettled) return // 上一次初始化还没回调，别叠加创建
            try {
                tts?.shutdown()
            } catch (_: Exception) {
            }
            tts = null
        }
        initSettled = false
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                initSettled = true
                if (status == TextToSpeech.SUCCESS) {
                    val result = try {
                        tts?.setLanguage(Locale.US)
                    } catch (_: Exception) {
                        TextToSpeech.LANG_NOT_SUPPORTED
                    }
                    ready = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        } catch (_: Exception) {
            initSettled = true
            ready = false
        }
    }

    /** 打断式朗读：连点只播最新一次 */
    fun speak(text: String) {
        if (!ready) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zee:${text.hashCode()}")
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }
}
