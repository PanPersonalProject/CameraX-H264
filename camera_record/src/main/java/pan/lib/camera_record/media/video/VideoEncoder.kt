package pan.lib.camera_record.media.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 这个类的主要功能是将输入的YUV数据编码为H.264数据。
 * @author pan qi
 * @since 2024/2/3
 */
class VideoEncoder(private val cameraPreviewInterface: CameraPreviewInterface) {
    private var isStarted = false  // 用于标记编码器是否已经启动


    private var mediaType = MediaFormat.MIMETYPE_VIDEO_AVC

    // MediaCodec用于编码视频数据
    private lateinit var codec: MediaCodec

    // MediaFormat用于配置MediaCodec
    private lateinit var format: MediaFormat

    private var context: Context? = null
    private var width = 0
    private var height = 0

    private var sps: ByteBuffer? = null
    private var pps: ByteBuffer? = null

    private val mIndexQueue: Queue<Int> = ConcurrentLinkedDeque()

    // 初始化方法，用于创建和配置MediaCodec
    fun init(context: Context, width: Int, height: Int) {
        this.width = width
        this.height = height
        this.context = context
        // 创建一个MediaCodec用于编码，编码类型为H.264
        codec = MediaCodec.createEncoderByType(mediaType)

        // 创建一个MediaFormat用于配置MediaCodec，设置视频的宽度、高度、比特率、帧率、颜色格式和I帧间隔
        format =
            MediaFormat.createVideoFormat(mediaType, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, height * width * 5)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

        // 配置MediaCodec，将MediaFormat传入
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        codec.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                mIndexQueue.add(index)
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                val outputBuffer = codec.getOutputBuffer(index)
                if (outputBuffer != null) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                        // Insert SPS and PPS before each I-frame
                        sps?.let { spsData ->
                            pps?.let { ppsData ->
                                val combinedBuffer =
                                    ByteBuffer.allocate(spsData.remaining() + ppsData.remaining() + info.size)
                                combinedBuffer.put(spsData)
                                combinedBuffer.put(ppsData)
                                combinedBuffer.put(outputBuffer)

                                combinedBuffer.flip()

                                val newInfo = MediaCodec.BufferInfo().apply {
                                    offset = 0
                                    size = combinedBuffer.remaining()
                                    presentationTimeUs = info.presentationTimeUs
                                    flags = info.flags
                                }

                                cameraPreviewInterface.onVideoBuffer(combinedBuffer, newInfo)
                            }
                        }
                    } else {
                        cameraPreviewInterface.onVideoBuffer(outputBuffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                }
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                Log.e("Encoder", "onError: ${e.message}")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                sps = format.getByteBuffer("csd-0")
                pps = format.getByteBuffer("csd-1")
                cameraPreviewInterface.onSpsPpsVps(sps!!, pps, null)
            }
        })
    }

    // 开始编码
    fun start() {
        codec.start()
        isStarted = true
    }

    /**
    作用：将输入的yuv数据编码为h264
     */
    fun encode(yuvBytes: ByteArray) {
        if (!isStarted) return
        val index = mIndexQueue.poll() ?: return
        val inputBuffer: ByteBuffer? = codec.getInputBuffer(index)
        inputBuffer?.clear()
        inputBuffer?.put(yuvBytes)
        codec.queueInputBuffer(index, 0, yuvBytes.size, System.nanoTime(), 0)
    }


    // 停止编码
    @OptIn(DelicateCoroutinesApi::class)
    fun stop() {
        isStarted = false

        GlobalScope.launch {
            delay(1000L)  // 延迟1秒
            codec.stop()
            codec.release()
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val bytes = ByteArray(remaining())
        get(bytes)
        return bytes
    }
}
