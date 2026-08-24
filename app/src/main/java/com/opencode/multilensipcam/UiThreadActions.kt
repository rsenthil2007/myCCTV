package com.opencode.multilensipcam

import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object UiThreadActions {
    fun runBlocking(
        activity: AppCompatActivity,
        timeoutSeconds: Long = 5,
        timeoutMessage: String = "Timed out waiting for UI thread action.",
        action: () -> Unit
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
            return
        }

        val finished = CountDownLatch(1)
        var failure: Throwable? = null
        activity.runOnUiThread {
            try {
                action()
            } catch (throwable: Throwable) {
                failure = throwable
            } finally {
                finished.countDown()
            }
        }

        if (!finished.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw IllegalStateException(timeoutMessage)
        }
        failure?.let { throw it }
    }
}
