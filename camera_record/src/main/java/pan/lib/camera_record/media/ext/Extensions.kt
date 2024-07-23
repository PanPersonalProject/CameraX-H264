package pan.lib.camera_record.media.ext

import android.media.MediaCodec.BufferInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * 将MediaCodec.BufferInfo的presentationTimeUs转换为日期时间字符串格式
 */
fun BufferInfo.getFormattedPresentationTime(): String {
  val date = Date(this.presentationTimeUs / 1000) // 将微秒转换为毫秒
  val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
  return format.format(date)
}