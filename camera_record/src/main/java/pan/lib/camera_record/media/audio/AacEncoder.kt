package pan.lib.camera_record.media.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * @author pan
 * @since 2024/7/21
 * 将PCM编码为AAC(可选带ADTS Header)
 * 注意:
 * - 在RTSP流中，AAC数据流通过RTP协议传输，ADTS头部信息不必要。RTP流协议已处理音频数据包的分隔与同步，因此RTSP推流时不需要ADTS头部。
 * - 在将AAC数据保存到文件时，需要ADTS头部信息，因为ADTS头部为每个AAC帧提供了同步和完整性验证功能，以确保音频数据的正确解码和播放。
 */
class AacEncoder(
    private val sampleRate: Int,
    private val channelCount: Int = 1,
    private val bitRate: Int = 128000,
    private val addAdts: Boolean = false, // 是否添加 ADTS 头部
    private val aacInterface: AacInterface
) {
    private val mediaCodec: MediaCodec by lazy { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC) }
    private var isStarted = false
    private val mIndexQueue: Queue<Int> = ConcurrentLinkedDeque()

    /**
     * 初始化编码器
     */
    fun initialize() {
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.setCallback(getMediaCodecCallBack())
        mediaCodec.start()
        isStarted = true
    }

    private fun getMediaCodecCallBack() = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            mIndexQueue.add(index)
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            val outputBuffer = codec.getOutputBuffer(index)
            outputBuffer?.let { aacBuffer ->
                // 设置 ByteBuffer 的起始位置和限制
                aacBuffer.position(info.offset)
                aacBuffer.limit(info.offset + info.size)

                // 创建包含 ADTS 头部和 AAC 数据的新 ByteBuffer
                val accBufferWithADTS = if (addAdts) {
                    ByteBuffer.allocate(info.size + 7).apply {
                        // 添加 ADTS 头信息
                        addAdtsHeader(this, info.size + 7)
                        // 添加 AAC 数据
                        put(aacBuffer)
                        // 设置 ByteBuffer 的起始位置和限制
                        flip()
                    }
                } else {
                    aacBuffer
                }

                // 调整info的大小以包含ADTS头部
                if (addAdts) {
                    info.size += 7
                }

                // 回调aac数据
                aacInterface.getAacData(accBufferWithADTS, info)
            }
            codec.releaseOutputBuffer(index, false)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e("AacEncoder", "onError: ${e.message}")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            aacInterface.onAudioFormat(format)
        }
    }

    /**
     * 编码PCM数据
     * @param pcmData PCM数据
     * @param size PCM数据的大小
     */
    fun encode(pcmData: ByteArray, size: Int) {
        if (!isStarted) return
        val index = mIndexQueue.poll() ?: return
        val inputBuffer: ByteBuffer? = mediaCodec.getInputBuffer(index)
        inputBuffer?.clear()
        inputBuffer?.put(pcmData, 0, size)
        mediaCodec.queueInputBuffer(index, 0, size, System.nanoTime() / 1000, 0)
    }

    /**
     * 完成编码
     */
    fun finalizeEncoding() {
        isStarted = false
        mediaCodec.stop()
        mediaCodec.release()
    }

    /**
     * 添加ADTS头部信息
     * @param buffer 输出数据缓冲区
     * @param packetLen 包长度
     */
    private fun addAdtsHeader(buffer: ByteBuffer, packetLen: Int) {
        val profile = 2 // AAC LC (低复杂度)
        val freqIdx = 4 // 44100Hz
        val chanCfg = channelCount // 通道配置，通常1为单声道，2为立体声

        // 填充 ADTS 头信息
        buffer.put(0xFF.toByte()) // 11111111 - 同步字（所有位都设为1）
        buffer.put(0xF9.toByte()) // 1111 1 00 1 - 同步字（7位）+ MPEG-2版本（1位）+ 层（2位）+ 无CRC（1位）
        buffer.put((((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()) // 配置文件（2位），采样频率索引（4位），通道配置（高2位）
        buffer.put((((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte()) // 通道配置（低2位）+ 帧长度（高3位）
        buffer.put(((packetLen and 0x7FF) shr 3).toByte()) // 帧长度（中间8位）
        buffer.put((((packetLen and 7) shl 5) + 0x1F).toByte()) // 帧长度（低3位）+ 缓冲区满度（高5位）
        buffer.put(0xFC.toByte()) // 缓冲区满度（低6位）+ AAC帧数（始终为0，表示1个AAC帧）
    }
}

