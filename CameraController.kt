package com.procam.camera.camera

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

class CameraController(private val context: Context) {
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: Camera? = null
    private var flashEnabled = false
    private val executor: Executor = ContextCompat.getMainExecutor(context)
    fun bind(owner: LifecycleOwner, preview: Preview, selector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA, onReady: (Camera?) -> Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF).build()
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
            val video = VideoCapture.withOutput(recorder)
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(owner, selector, preview, capture, video)
                imageCapture = capture; videoCapture = video
                onReady(camera)
            } catch (_: Exception) { onReady(null) }
        }, executor)
    }
    fun takePhoto(onSaved: (Boolean) -> Unit) {
        val capture = imageCapture ?: return onSaved(false)
        val name = "ProCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProCam") }
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        capture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) = onSaved(true)
            override fun onError(exception: ImageCaptureException) = onSaved(false)
        })
    }
    fun toggleRecording(onState: (Boolean, String?) -> Unit) {
        val video = videoCapture ?: return onState(false, "Video capture unavailable")
        if (recording != null) { recording?.stop(); recording = null; onState(false, null); return }
        val name = "ProCam_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4"); put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ProCam") }
        val output = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        try {
            val pending = video.output.prepareRecording(context, output)
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) pending.withAudioEnabled()
            recording = pending.start(executor) { event ->
                if (event is VideoRecordEvent.Finalize) { recording = null; onState(false, event.error.takeIf { it != VideoRecordEvent.Finalize.ERROR_NONE }?.let { "Recording failed ($it)" }) }
            }
            onState(true, null)
        } catch (e: Exception) { onState(false, e.message) }
    }
    fun setZoom(ratio: Float) { camera?.cameraControl?.setZoomRatio(ratio) }
    fun setFlash(enabled: Boolean) { flashEnabled = enabled; imageCapture?.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }
    fun focus(x: Float, y: Float, width: Int, height: Int) {
        val factory = SurfaceOrientedMeteringPointFactory(width.toFloat(), height.toFloat())
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(factory.createPoint(x, y)).build())
    }
}
