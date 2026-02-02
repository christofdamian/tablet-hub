package net.damian.tablethub.service.display

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages color temperature overlay for sunrise simulation.
 * Applies a warm color tint overlay on top of the content.
 *
 * Warmth scale: 0-100
 * - 0 = neutral (no overlay)
 * - 100 = warm amber tint
 *
 * Controlled via HA number entity for sunrise automation.
 */
@Singleton
class ColorTemperatureManager @Inject constructor() {
    companion object {
        private const val TAG = "ColorTempManager"
        private const val MAX_OVERLAY_ALPHA = 0.30f  // Maximum 30% opacity at warmth=100
    }

    private val _colorTemp = MutableStateFlow(0)
    val colorTemp: StateFlow<Int> = _colorTemp.asStateFlow()

    private var activityRef: WeakReference<Activity>? = null
    private var overlayView: View? = null

    /**
     * Initialize with the main activity.
     * Creates the overlay view and adds it to the window.
     */
    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)

        activity.runOnUiThread {
            // Create overlay view
            overlayView = View(activity).apply {
                setBackgroundColor(Color.TRANSPARENT)
                // Make overlay non-interactive - touches pass through
                isClickable = false
                isFocusable = false
            }

            // Add as overlay on top of content
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            activity.addContentView(overlayView, params)
            Log.d(TAG, "Color temperature overlay initialized")

            // Apply current color temp if any
            updateOverlay()
        }
    }

    /**
     * Clear activity reference and remove overlay.
     */
    fun clearActivity() {
        activityRef?.get()?.runOnUiThread {
            overlayView?.let { view ->
                (view.parent as? android.view.ViewGroup)?.removeView(view)
            }
        }
        overlayView = null
        activityRef = null
    }

    /**
     * Set color temperature warmth level.
     * @param warmth 0-100 where 0 is neutral and 100 is warm amber
     */
    fun setColorTemperature(warmth: Int) {
        val clampedWarmth = warmth.coerceIn(0, 100)
        Log.d(TAG, "Setting color temperature: $clampedWarmth")
        _colorTemp.value = clampedWarmth
        updateOverlay()
    }

    private fun updateOverlay() {
        val warmth = _colorTemp.value

        activityRef?.get()?.runOnUiThread {
            overlayView?.let { view ->
                val color = if (warmth == 0) {
                    Color.TRANSPARENT
                } else {
                    // Calculate alpha based on warmth (0-100 -> 0-MAX_OVERLAY_ALPHA)
                    val alpha = (warmth * MAX_OVERLAY_ALPHA * 255 / 100).toInt()
                    // Warm amber color: RGB(255, 140, 0) - similar to candlelight
                    Color.argb(alpha, 255, 140, 0)
                }
                view.setBackgroundColor(color)
                Log.d(TAG, "Overlay color updated: warmth=$warmth, alpha=${Color.alpha(color)}")
            }
        }
    }
}
