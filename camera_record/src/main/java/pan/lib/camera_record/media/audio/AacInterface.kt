package pan.lib.camera_record.media.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * @author pan
 * @since 2024/7/21
 */
interface AacInterface {
  fun getAacData(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo)
  fun onAudioFormat(mediaFormat: MediaFormat)
}