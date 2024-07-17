package pan.lib.camera_record.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * 将PCM编码为AAC(带ADTS Header)
 * @author pan
 * @since 2024/7/18
 */
class AacEncoder(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val bitRate: Int
) {
    private var mediaCodec: MediaCodec? = null

    fun initialize() {
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec?.start()
    }

    fun encode(pcmData: ByteArray, size: Int, onEncoded: (ByteBuffer) -> Unit) {
        val inputBufferIndex = mediaCodec?.dequeueInputBuffer(10000) ?: -1
        if (inputBufferIndex >= 0) {
            val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            inputBuffer?.put(pcmData, 0, size)
            mediaCodec?.queueInputBuffer(inputBufferIndex, 0, size, System.nanoTime() / 1000, 0)
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 10000) ?: -1
        while (outputBufferIndex >= 0) {
            val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferIndex)
            outputBuffer?.let {
                // 设置 ByteBuffer 的起始位置和限制
                it.position(bufferInfo.offset)
                it.limit(bufferInfo.offset + bufferInfo.size)

                // 创建包含 ADTS 头的输出数据数组
                val outData = ByteArray(bufferInfo.size + 7)
                // 添加 ADTS 头信息
                addAdtsHeader(outData, outData.size)
                // 从 ByteBuffer 中读取编码后的 AAC 数据到输出数据数组中
                it.get(outData, 7, bufferInfo.size)

                // 调用回调函数，传递包含 ADTS 头的编码数据
                onEncoded(ByteBuffer.wrap(outData))
            }

            mediaCodec?.releaseOutputBuffer(outputBufferIndex, false)
            outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
        }
    }

    fun finalizeEncoding() {
        mediaCodec?.stop()
        mediaCodec?.release()
    }

    private fun addAdtsHeader(packet: ByteArray, packetLen: Int) {
        val profile = 2 // AAC LC (低复杂度)
        val freqIdx = 4 // 44100Hz
        val chanCfg = channelCount // 通道配置，通常1为单声道，2为立体声

        // 填充 ADTS 头信息
        packet[0] = 0xFF.toByte() // 11111111 - 同步字（所有位都设为1）
        packet[1] = 0xF9.toByte() // 1111 1 00 1 - 同步字（7位）+ MPEG-2版本（1位）+ 层（2位）+ 无CRC（1位）
        packet[2] =
            (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte() // 配置文件（2位），采样频率索引（4位），通道配置（高2位）
        packet[3] = (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte() // 通道配置（低2位）+ 帧长度（高3位）
        packet[4] = ((packetLen and 0x7FF) shr 3).toByte() // 帧长度（中间8位）
        packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte() // 帧长度（低3位）+ 缓冲区满度（高5位）
        packet[6] = 0xFC.toByte() // 缓冲区满度（低6位）+ AAC帧数（始终为0，表示1个AAC帧）
    }

}
