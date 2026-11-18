package com.sukhan.poetry

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

internal object PersianTts {
    interface Listener {
        fun onPreparing() {}
        fun onStarted() {}
        fun onFinished() {}
        fun onError() {}
    }

    private const val MODEL_DIR = "assets/tts/piper-fa"
    private const val MODEL_NAME = "fa_IR-mana-medium.onnx"
    private const val MAX_LINES_PER_CHUNK = 4
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val sequence = AtomicInteger(0)
    private val engineLock = Any()

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var active = false
    @Volatile private var currentTrack: AudioTrack? = null

    val isActive: Boolean get() = active

    fun speak(context: Context, text: String, speed: Float, pitch: Float, listener: Listener) {
        stop()
        val value = text.trim()
        if (value.isEmpty()) {
            main.post { listener.onError() }
            return
        }
        val requestedSpeed = speed.coerceIn(0.75f, 1.25f)
        val pitchScale = pitch.coerceIn(0.80f, 1.20f)
        val chunks = value.split(Regex("[\\r\\n]+"))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .chunked(MAX_LINES_PER_CHUNK)
            .map { it.joinToString("\n") }
        val request = sequence.incrementAndGet()
        active = true
        main.post { if (sequence.get() == request) listener.onPreparing() }
        worker.execute {
            var track: AudioTrack? = null
            var totalFrames = 0
            try {
                val tts = getEngine(context.applicationContext)
                for (chunk in chunks) {
                    if (request != sequence.get()) return@execute
                    val phonemes = piperPhonemizeText(chunk, "fa")
                        ?: throw IllegalStateException("Persian phonemization failed")
                    val codepoints = try {
                        Array(phonemes.numSentences) { index -> phonemes.getPhonemes(index) }
                    } finally {
                        phonemes.close()
                    }
                    if (request != sequence.get()) return@execute
                    val generated = tts.generateWithConfig(
                        text = "",
                        // AudioTrack's sample-rate adjustment changes pitch and duration together.
                        // Compensate in the TTS rate so the selected speed remains independent.
                        config = GenerationConfig(sid = 0, speed = requestedSpeed / pitchScale, phonemeCodepoints = codepoints)
                    )
                    if (request != sequence.get()) return@execute
                    val pcm = ShortArray(generated.samples.size) { index ->
                        (generated.samples[index].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    if (pcm.isEmpty()) continue
                    if (track == null) {
                        val minBuffer = AudioTrack.getMinBufferSize(
                            generated.sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        track = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(generated.sampleRate)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build()
                            )
                            .setBufferSizeInBytes(max(minBuffer, 8192))
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                        if (track.state != AudioTrack.STATE_INITIALIZED) throw IllegalStateException("Audio output unavailable")
                        if (pitchScale != 1.0f) {
                            track.setPlaybackRate((generated.sampleRate * pitchScale).toInt())
                        }
                        currentTrack = track
                        if (request != sequence.get()) return@execute
                        track.play()
                        main.post { if (sequence.get() == request) listener.onStarted() }
                    }
                    var offset = 0
                    while (offset < pcm.size && request == sequence.get()) {
                        val written = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                        if (written < 0) throw IllegalStateException("Audio output failed: $written")
                        if (written == 0) Thread.sleep(10)
                        else offset += written
                    }
                    totalFrames += pcm.size
                }
                val output = track ?: throw IllegalStateException("Speech engine returned no audio")
                while (request == sequence.get() && output.playbackHeadPosition < totalFrames) Thread.sleep(24)
                if (request == sequence.get()) {
                    active = false
                    main.post { if (sequence.get() == request) listener.onFinished() }
                }
            } catch (_: Exception) {
                if (request == sequence.get()) {
                    active = false
                    main.post { if (sequence.get() == request) listener.onError() }
                }
            } finally {
                if (currentTrack === track) currentTrack = null
                try { track?.release() } catch (_: Exception) { }
            }
        }
    }

    fun stop() {
        if (!active && currentTrack == null) return
        sequence.incrementAndGet()
        active = false
        val track = currentTrack
        currentTrack = null
        try { track?.pause() } catch (_: Exception) { }
        try { track?.flush() } catch (_: Exception) { }
        try { track?.stop() } catch (_: Exception) { }
        try { track?.release() } catch (_: Exception) { }
    }

    private fun getEngine(context: Context): OfflineTts = engine ?: synchronized(engineLock) {
        engine ?: run {
            piperPhonemizeInitialize(context)
            val config = getOfflineTtsConfig(
                modelDir = MODEL_DIR,
                modelName = MODEL_NAME,
                numThreads = 2
            )
            OfflineTts(assetManager = context.assets, config = config).also { engine = it }
        }
    }
}
