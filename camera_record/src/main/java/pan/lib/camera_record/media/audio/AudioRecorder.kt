package pan.lib.camera_record.media.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class AudioRecorder(aacInterface: AacInterface) {
    private val source = MediaRecorder.AudioSource.MIC // 通过麦克风采集音频
    private val sampleRate = 44100 // 采样频率为 44100 Hz
    private val channelInMono = AudioFormat.CHANNEL_IN_MONO // 单声道
    private val encodingPcm16Bit = AudioFormat.ENCODING_PCM_16BIT // 量化精度为 16 位
    private val recordStarted = AtomicBoolean(false) // 是否开始录音
    private val minBufferSize: Int =
        AudioRecord.getMinBufferSize(sampleRate, channelInMono, encodingPcm16Bit) // 音频最小缓冲区大小

    private val audioRecord =
        AudioRecord(source, sampleRate, channelInMono, encodingPcm16Bit, minBufferSize)

    private val scope = CoroutineScope(Dispatchers.IO)
    private val aacEncoder = AacEncoder(sampleRate, 1, 128000, true, aacInterface) // 比特率为 128 kbps

    fun startRecording() {
        if (recordStarted.get()) return // 已经在录音中

        recordStarted.set(true)
        aacEncoder.initialize()

        scope.launch {
            audioRecord.startRecording()
            val pcmBufferSize = 2000
            val buffer = ByteArray(pcmBufferSize)
            try {
                while (recordStarted.get()) {
//                    Log.d(
//                        "AudioRecorder",
//                        "Before read: state=${audioRecord.recordingState}, bufferSize=$pcmBufferSize"
//                    )

                    val read = audioRecord.read(buffer, 0, pcmBufferSize)
                    if (read > 0) {
                        aacEncoder.encode(buffer, read)
                    } else {
                        Log.e("AudioRecorder", "Error reading from AudioRecord: $read")
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioRecorder", "AudioRecorder error: $e")
            } finally {
                aacEncoder.finalizeEncoding()
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }

    fun stopRecording() {
        recordStarted.set(false)
    }

}
