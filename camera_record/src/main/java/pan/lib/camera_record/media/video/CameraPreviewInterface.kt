package pan.lib.camera_record.media.video

import android.media.MediaCodec
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import java.nio.ByteBuffer


interface CameraPreviewInterface {
    fun getPreviewView(): PreviewView
    fun onNv21Frame(nv21: ByteArray, imageProxy: ImageProxy) {}

    fun onSpsPpsVps(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?)

    fun onVideoBuffer(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo)
}
