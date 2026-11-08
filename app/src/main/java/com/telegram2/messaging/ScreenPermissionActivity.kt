package com.telegram2.messaging

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.telegram2.messaging.remote.ScreenCaptureService

class ScreenPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("projection", data)
                    putExtra("resultcode", resultCode)
                }
            )
        }
        finish()
    }
}
