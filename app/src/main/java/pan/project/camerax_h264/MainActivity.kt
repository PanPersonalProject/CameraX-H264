package pan.project.camerax_h264

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import pan.lib.camera_record.test.FileUtil
import pan.lib.camera_record.ui.CameraXPreviewFragment
import pan.project.camerax_h264.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragment =
            supportFragmentManager.findFragmentById(R.id.fragment) as CameraXPreviewFragment

        fragment.setOutputBufferCallback { bytes ->
            // here process video frame buffer
//            Log.e("TAG", bytes.joinToString ())
//            FileUtil.writeBytesToFile(this, bytes, "test.h264")

        }

    }




}