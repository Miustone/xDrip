package dev.xdripyou.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import dev.xdripyou.app.domain.GlucoseReading
import dev.xdripyou.app.domain.GlucoseUnit
import java.util.Locale

class SpeechHelper(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    @Volatile
    private var ready = false

    private fun ensureInitialized(onReady: () -> Unit) {
        if (ready && tts != null) {
            onReady()
            return
        }
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ready = true
                onReady()
            }
        }
    }

    fun speakText(text: String, speed: Float = 1.0f) {
        ensureInitialized {
            tts?.setSpeechRate(speed.coerceIn(0.6f, 1.8f))
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "xdrip2you_${System.currentTimeMillis()}")
        }
    }

    fun speakReading(reading: GlucoseReading, useMgdl: Boolean, includeTrend: Boolean, speed: Float) {
        val valueText = GlucoseUnit.formatValue(reading.mgdl, useMgdl).replace('.', ',')
        val unitText = if (useMgdl) "Milligramm pro Deziliter" else "Millimol pro Liter"
        val message = if (includeTrend) {
            "$valueText $unitText, ${reading.trend.labelDe}"
        } else {
            "$valueText $unitText"
        }
        speakText(message, speed)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
