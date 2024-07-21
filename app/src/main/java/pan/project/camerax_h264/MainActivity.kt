package pan.project.camerax_h264

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import pan.project.camerax_h264.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }




}