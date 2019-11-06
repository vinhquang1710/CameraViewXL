package com.quangncv.cameraviewxl

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.databinding.DataBindingUtil
import com.quangncv.cameraviewxl.databinding.ActivityVideoRecorderBinding

class VideoRecorderActivity : AppCompatActivity() {

    private lateinit var binding:ActivityVideoRecorderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_video_recorder)

        binding.camera.isImageCapture(false)

        binding.btnRecord.setOnClickListener {
            binding.camera.startRecording()
            binding.btnRecord.visibility = View.GONE
            binding.btnStop.visibility = View.VISIBLE
        }

        binding.btnStop.setOnClickListener {
            binding.camera.stopRecording()
            binding.btnRecord.visibility = View.VISIBLE
            binding.btnStop.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        binding.flPreview.systemUiVisibility = MainActivity.FLAGS_FULLSCREEN
    }
}
