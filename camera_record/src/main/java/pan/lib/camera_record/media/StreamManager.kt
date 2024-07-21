package pan.lib.camera_record.media

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import pan.lib.camera_record.file.FileUtil
import pan.lib.camera_record.media.audio.AudioRecorder
import pan.lib.camera_record.media.video.CameraPreviewInterface
import pan.lib.camera_record.media.video.CameraRecorder
import java.nio.ByteBuffer

/**
 * StreamManager 管理音频和视频录制，使用 AudioRecorder 和 CameraRecorder。
 * 它提供了用于启动、停止和切换摄像头的功能。
 *
 * @param context 应用上下文。
 * @param lifecycleOwner 用于管理摄像头生命周期的 LifecycleOwner。
 * @param previewView 提供摄像头预览的 PreviewView。
 * @param onNv21FrameCallback 处理 NV21 帧的回调函数。
 */
open class StreamManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onNv21FrameCallback: (nv21: ByteArray, imageProxy: ImageProxy) -> Unit
) {
    private val audioRecorder: AudioRecorder by lazy { AudioRecorder(context) }
    private val cameraRecorder: CameraRecorder by lazy {
        val needSaveH264ToLocal = false // 是否保存到本地

        CameraRecorder(
            context,
            lifecycleOwner,
            object : CameraPreviewInterface {

                override fun getPreviewView(): PreviewView = previewView

                override fun onNv21Frame(nv21: ByteArray, imageProxy: ImageProxy) {
                    onNv21FrameCallback(nv21, imageProxy)
                }

                override fun onSpsPpsVps(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
                    logByteBufferContent("StreamManager", "SPS", sps)
                    logByteBufferContent("StreamManager", "PPS", pps)
                    logByteBufferContent("StreamManager", "VPS", vps)

                }

                override fun onVideoBuffer(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                    val data: ByteArray
                    if (h264Buffer.hasArray()) {
                        data = h264Buffer.array()
                    } else {
                        data = ByteArray(h264Buffer.remaining())
                        h264Buffer.get(data)
                    }
                    if (needSaveH264ToLocal) {
                        FileUtil.writeBytesToFile(context, data, "test.h264")
                    }
                }

                /**
                 * 打印ByteBuffer内容的辅助函数
                 */
                fun logByteBufferContent(tag: String, name: String, byteBuffer: ByteBuffer?) {
                    if (byteBuffer == null) {
                        Log.d(tag, "$name is null")
                        return
                    }

                    val byteData = ByteArray(byteBuffer.remaining())
                    byteBuffer.rewind() // 确保从头开始读取
                    byteBuffer.get(byteData)

                    val hexString = byteData.joinToString(separator = " ") { "%02X".format(it) }
                    Log.d(tag, "$name: $hexString")
                }
            }
        )
    }

    init {
        audioRecorder.needSaveAacToLocal = false // 默认不保存 aac 到本地
    }

    /**
     * 启动摄像头和音频录制。
     */
    fun start() {
        cameraRecorder.startRecording()
        audioRecorder.startRecording()
    }

    /**
     * 停止摄像头和音频录制。
     */
    fun stop() {
        cameraRecorder.stopRecording()
        audioRecorder.stopRecording()
    }

    /**
     * 切换前后摄像头。
     */
    fun switchCamera() {
        cameraRecorder.switchCamera()
    }
}
