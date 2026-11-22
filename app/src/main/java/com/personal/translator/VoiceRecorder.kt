package com.personal.translator

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class VoiceRecorder {
    @Volatile private var running = false
    @Volatile private var failure: String? = null
    private var audio: AudioRecord? = null
    private var worker: Thread? = null
    private var data = ByteArrayOutputStream()
    @SuppressLint("MissingPermission")
    fun start(onLimit: () -> Unit) {
        check(!running)
        data = ByteArrayOutputStream()
        failure = null
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(min > 0) { "此设备不支持所需的录音格式。" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 4096))
        if (recorder.state != AudioRecord.STATE_INITIALIZED) { recorder.release(); error("无法打开麦克风。") }
        audio = recorder
        recorder.startRecording()
        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风正在被其他应用使用。" }
        running = true
        worker = thread(name = "voice-recorder") {
            val buffer = ByteArray(4096)
            try {
                while (running && data.size() < 16000 * 2 * 60) {
                    val count = recorder.read(buffer, 0, buffer.size)
                    if (count > 0) data.write(buffer, 0, count)
                    else if (running) { failure = "录音中断，请重试。"; break }
                }
                if (running) onLimit()
            } catch (_: Exception) { if (running) { failure = "录音失败，请重试。"; onLimit() } }
        }
    }
    fun stop(): ByteArray {
        running = false
        runCatching { audio?.stop() }
        worker?.join(2000)
        audio?.release(); audio = null; worker = null
        failure?.let { error(it) }
        check(data.size() >= 6400) { "录音太短，请按住按钮说完一句话。" }
        return Wav.encode(data.toByteArray())
    }
    fun cancel() { runCatching { stop() } }
}
