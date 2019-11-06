package com.quangncv.cameraviewxl

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.databinding.DataBindingUtil
import com.bumptech.glide.Glide
import com.quang.ncv.cameraviewxl.CameraViewXL
import com.quangncv.cameraviewxl.databinding.ActivityImageCaptureBinding

class ImageCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageCaptureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_image_capture)

        binding.camera.isImageCapture(true)

        binding.btnCapture.setOnClickListener {
            binding.camera.capture()
        }

        binding.btnBack.setOnClickListener {
            binding.flPreview.visibility = View.VISIBLE
            binding.flImage.visibility = View.GONE
        }

        binding.camera.setCallback(object : CameraViewXL.OnCameraXLListener{
            override fun onCapture(filePath: String) {
                runOnUiThread {
                    binding.flPreview.visibility = View.GONE
                    binding.flImage.visibility = View.VISIBLE
                    Glide.with(this@ImageCaptureActivity).load(filePath).into(binding.imgPreview)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        binding.flPreview.systemUiVisibility = MainActivity.FLAGS_FULLSCREEN
        binding.flImage.systemUiVisibility = MainActivity.FLAGS_FULLSCREEN
    }
}
