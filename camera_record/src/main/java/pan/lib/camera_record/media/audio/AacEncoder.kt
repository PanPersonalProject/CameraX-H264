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
    private val addAdts: Boolean = false // 是否添加 ADTS 头部
) {
    private val mediaCodec: MediaCodec by lazy { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC) }
    private var isStarted = false
    private val mIndexQueue: Queue<Int> = ConcurrentLinkedDeque()

    /**
     * 初始化编码器
     * @param onEncoded 回调函数，用于接收aac数据
     */
    fun initialize(onEncoded: (ByteBuffer) -> Unit) {
        val format =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        mediaCodec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                mIndexQueue.add(index)
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                val outputBuffer = codec.getOutputBuffer(index)
                outputBuffer?.let {
                    // 设置 ByteBuffer 的起始位置和限制
                    it.position(info.offset)
                    it.limit(info.offset + info.size)

                    // 创建输出数据数组
                    val outData = if (addAdts) {
                        ByteArray(info.size + 7).also { packet ->
                            // 添加 ADTS 头信息
                            addAdtsHeader(packet, packet.size)
                            // 从 ByteBuffer 中读取编码后的 AAC 数据到输出数据数组中
                            it.get(packet, 7, info.size)
                        }
                    } else {
                        ByteArray(info.size).also { packet ->
                            // 直接从 ByteBuffer 中读取编码后的 AAC 数据
                            it.get(packet, 0, info.size)
                        }
                    }

                    // 回调aac数据
                    onEncoded(ByteBuffer.wrap(outData))
                }
                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e("AacEncoder", "onError: ${e.message}")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                // 处理格式变化，如果需要的话
            }
        })

        mediaCodec.start()
        isStarted = true
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
     * @param packet 输出数据数组
     * @param packetLen 包长度
     */
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
