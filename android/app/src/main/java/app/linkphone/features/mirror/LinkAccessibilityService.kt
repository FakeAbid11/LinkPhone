package app.linkphone.features.mirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import app.linkphone.core.util.SafeInput
import app.linkphone.core.util.LinkStatus

class LinkAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LinkStatus.log("Mirror", "Accessibility gesture service connected")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LinkAccessibilityService? = null

        val isConnected: Boolean get() = instance != null

        fun performTouch(
            action: String,
            x1: Float,
            y1: Float,
            x2: Float?,
            y2: Float?,
            durationMs: Int,
        ): Boolean {
            val service = instance ?: return false
            if (!SafeInput.validNormalizedCoordinate(x1) ||
                !SafeInput.validNormalizedCoordinate(y1)
            ) {
                return false
            }
            val (width, height) = service.screenSize()
            val path = Path().apply { moveTo(x1 * width, y1 * height) }
            if (action == "swipe") {
                if (x2 == null || y2 == null ||
                    !SafeInput.validNormalizedCoordinate(x2) ||
                    !SafeInput.validNormalizedCoordinate(y2)
                ) {
                    return false
                }
                path.lineTo(x2 * width, y2 * height)
            }
            val duration = durationMs.coerceIn(20, 1_500).toLong()
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            return runCatching { service.dispatchGesture(gesture, null, null) }
                .getOrDefault(false)
        }
    }

    private fun screenSize(): Pair<Float, Float> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.bounds
            if (bounds != null) return bounds.width().toFloat() to bounds.height().toFloat()
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels.toFloat() to metrics.heightPixels.toFloat()
    }
}
