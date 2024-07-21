package pan.lib.camera_record.file

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object FileUtil {
    fun writeBytesToFile(context: Context, bytes: ByteArray, fileName: String) {
        val internalDir = context.filesDir
        val file = File(internalDir, fileName)
        val fos = FileOutputStream(file, true) // true表示使用append模式

        fos.write(bytes)
        fos.flush()
        fos.close()
    }
}