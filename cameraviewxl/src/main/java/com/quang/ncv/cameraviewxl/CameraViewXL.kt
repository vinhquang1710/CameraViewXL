package com.quang.ncv.cameraviewxl

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Camera
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.LayoutInflater
import android.view.MotionEvent
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import com.quang.ncv.cameraviewxl.databinding.LayoutViewFinderBinding
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


typealias LumaListener = (luma: Double) -> Unit

class CameraViewXL : ConstraintLayout, Executor {

    interface OnCameraXLListener {
        fun onCapture(filePath: String)
    }

    private var binding: LayoutViewFinderBinding =
        LayoutViewFinderBinding.inflate(LayoutInflater.from(context), this, true)

    private lateinit var outputDirectory: File

    private var displayId = -1
    private var lensFacing = CameraX.LensFacing.BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var displayManager: DisplayManager

    private var callback: OnCameraXLListener? = null
    private var isImageCapture = true

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    private fun init() {
        // Every time the orientation of device changes, recompute layout
        displayManager = binding.textureView.context
            .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        // Determine the output directory
        outputDirectory = getOutputDirectory(context)

        // Wait for the views to be properly laid out
        binding.textureView.post {
            // Keep track of the display in which this view is attached
            displayId = binding.textureView.display.displayId

            // Build UI controls and bind all camera use cases
            bindCameraUseCases()
        }
    }

    fun setCallback(callback: OnCameraXLListener) {
        this.callback = callback
    }

    fun isImageCapture(isImageCapture: Boolean) {
        this.isImageCapture = isImageCapture
    }

    fun capture() {
        imageCapture?.let { imageCapture ->

            // Create output file to hold the image
            val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

            // Setup image capture metadata
            val metadata = ImageCapture.Metadata().apply {
                // Mirror image when using the front camera
                isReversedHorizontal = lensFacing == CameraX.LensFacing.FRONT
            }

            // Setup image capture listener which is triggered after photo has been taken
            imageCapture.takePicture(photoFile, metadata, this, imageSavedListener)
        }
    }

    @SuppressLint("RestrictedApi")
    fun startRecording() {
        videoCapture?.let { videoCapture ->
            val videoFile = createFile(outputDirectory, FILENAME, VIDEO_EXTENSION)
            val metadata = VideoCapture.Metadata().apply {
                // Mirror image when using the front camera

            }
            videoCapture.startRecording(videoFile, metadata, this, videoSavedListener)
        }
    }

    @SuppressLint("RestrictedApi")
    fun stopRecording() {
        videoCapture?.stopRecording()
    }

    private fun focus() {

    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) =
            getViewById(android.R.id.content)?.let { view ->
                if (displayId == this@CameraViewXL.displayId) {
                    Log.e(TAG, "Rotation changed: ${view.display.rotation}")
                    preview?.setTargetRotation(view.display.rotation)
                    imageCapture?.setTargetRotation(view.display.rotation)
                    imageAnalyzer?.setTargetRotation(view.display.rotation)
                }
            } ?: Unit
    }

    private val imageSavedListener = object : ImageCapture.OnImageSavedListener {
        override fun onError(
            error: ImageCapture.ImageCaptureError, message: String, exc: Throwable?
        ) {
            Log.e(TAG, "Photo capture failed: $message")
            exc?.printStackTrace()
        }

        override fun onImageSaved(photoFile: File) {
            Log.e(TAG, "Photo capture succeeded: ${photoFile.absolutePath}")
            callback?.onCapture(photoFile.absolutePath)

            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                // Update the gallery thumbnail with latest picture taken
                /*setGalleryThumbnail(photoFile)*/
            }

            // Implicit broadcasts will be ignored for devices running API
            // level >= 24, so if you only target 24+ you can remove this statement
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                /*requireActivity().sendBroadcast(
                    Intent(Camera.ACTION_NEW_PICTURE, Uri.fromFile(photoFile))
                )*/
            }

            // If the folder selected is an external media directory, this is unnecessary
            // but otherwise other apps will not be able to access our images unless we
            // scan them using [MediaScannerConnection]
            /* val mimeType = MimeTypeMap.getSingleton()
                 .getMimeTypeFromExtension(photoFile.extension)
             MediaScannerConnection.scanFile(
                 context, arrayOf(photoFile.absolutePath), arrayOf(mimeType), null)*/
        }
    }

    private val videoSavedListener = object : VideoCapture.OnVideoSavedListener {
        override fun onVideoSaved(file: File) {
            Log.e(TAG, "Video capture succeeded: ${file.absolutePath}")
        }

        override fun onError(
            videoCaptureError: VideoCapture.VideoCaptureError,
            message: String,
            cause: Throwable?
        ) {
            Log.e(TAG, "Video capture failed: $message")
            cause?.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun bindCameraUseCases() {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { binding.textureView.display.getRealMetrics(it) }
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
        Log.e(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        // Set up the view finder use case to display camera preview
        val viewFinderConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            // We request aspect ratio but no resolution to let CameraX optimize our use cases
            setTargetAspectRatioCustom(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(binding.textureView.display.rotation)
        }.build()

        // Use the auto-fit preview builder to automatically handle size and orientation changes
        preview = AutoFitPreviewBuilder.build(viewFinderConfig, binding.textureView)

        // Set up the capture use case to allow users to take photos
        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(lensFacing)
            setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            // We request aspect ratio but no resolution to match preview config but letting
            // CameraX optimize for whatever specific resolution best fits requested capture mode
            setTargetAspectRatioCustom(screenAspectRatio)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(binding.textureView.display.rotation)
        }.build()

        imageCapture = ImageCapture(imageCaptureConfig)

        val videoCaptureConfig = VideoCaptureConfig.Builder().apply {
            setTargetAspectRatioCustom(screenAspectRatio)
            setTargetRotation(binding.textureView.display.rotation)
        }.build()

        videoCapture = VideoCapture(videoCaptureConfig)

        // Setup image analysis pipeline that computes average pixel luminance in real time
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setLensFacing(lensFacing)
            // In our analysis, we care more about the latest image than analyzing *every* image
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            // Set initial target rotation, we will have to call this again if rotation changes
            // during the lifecycle of this use case
            setTargetRotation(binding.textureView.display.rotation)
        }.build()

       /* imageAnalyzer = ImageAnalysis(analyzerConfig).apply {
            analyzer = LuminosityAnalyzer { luma ->
                // Values returned from our analyzer are passed to the attached listener
                // We log image analysis results here -- you should do something useful instead!
                val fps = (analyzer as LuminosityAnalyzer).framesPerSecond
                Log.e(
                    TAG, "Average luminosity: $luma. " +
                            "Frames per second: ${"%.01f".format(fps)}"
                )
            }
        }*/

        // Apply declared configs to CameraX using the same lifecycle owner
        if (isImageCapture) {
            CameraX.bindToLifecycle(
                context as LifecycleOwner, preview, imageCapture
            )
        } else {
            Log.e("videoCapture", "videoCapture")
            CameraX.bindToLifecycle(
                context as LifecycleOwner, preview, videoCapture
            )
        }
    }

    private class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L
        var framesPerSecond: Double = -1.0
            private set

        /**
         * Used to add listeners that will be called with each luma computed
         */
        fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: do not close the image, it will be
         * automatically closed after this method returns
         * @return the image analysis result
         */
        override fun analyze(image: ImageProxy, rotationDegrees: Int) {
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) return

            // Keep track of frames analyzed
            frameTimestamps.push(System.currentTimeMillis())

            // Compute the FPS using a moving average
            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
            framesPerSecond = 1.0 / ((frameTimestamps.peekFirst() -
                    frameTimestamps.peekLast()) / frameTimestamps.size.toDouble()) * 1000.0

            // Calculate the average luma no more often than every second
            if (frameTimestamps.first - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
                lastAnalyzedTimestamp = frameTimestamps.first

                // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance
                //  plane
                val buffer = image.planes[0].buffer

                // Extract image data from callback object
                val data = buffer.toByteArray()

                // Convert the data into an array of pixel values ranging 0-255
                val pixels = data.map { it.toInt() and 0xFF }

                // Compute average luminance for the image
                val luma = pixels.average()

                // Call all listeners with new value
                listeners.forEach { it(luma) }
            }
        }
    }

    companion object {
        private const val TAG = "CameraXL"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val VIDEO_EXTENSION = ".mp4"

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension
            )

        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, "CameraXL").apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }
    }

    override fun execute(command: Runnable) {
        command.run()
    }
}