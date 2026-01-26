package net.damian.tablethub.service.display

import android.app.Activity
import android.view.WindowManager
import java.lang.ref.WeakReference

/**
 * Manages screen brightness and wake state.
 * Call setActivity() when the main activity is created.
 */
object ScreenManager {

    private var activityRef: WeakReference<Activity>? = null
    private var currentBrightness: Int = 255

    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        // Keep screen on
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun clearActivity() {
        activityRef = null
    }

    /**
     * Set screen brightness (0-255).
     * 0 = minimum brightness (not off)
     * 255 = maximum brightness
     * -1 = system default
     */
    fun setBrightness(brightness: Int) {
        currentBrightness = brightness
        activityRef?.get()?.let { activity ->
            activity.runOnUiThread {
                val layoutParams = activity.window.attributes
                layoutParams.screenBrightness = when {
                    brightness < 0 -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    brightness == 0 -> 0.01f // Minimum visible brightness
                    else -> brightness / 255f
                }
                activity.window.attributes = layoutParams
            }
        }
    }

    fun getBrightness(): Int = currentBrightness

    fun setKeepScreenOn(keepOn: Boolean) {
        activityRef?.get()?.let { activity ->
            activity.runOnUiThread {
                if (keepOn) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
}
