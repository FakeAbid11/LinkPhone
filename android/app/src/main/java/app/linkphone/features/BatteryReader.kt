package app.linkphone.features

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.roundToInt

/** The phone's charge level, as a percentage, and whether it is charging. */
data class BatteryStatus(val level: Int, val charging: Boolean)

/** Turns the raw values from the battery broadcast into a charge level. */
object BatteryParser {
    fun parse(level: Int, scale: Int, status: Int): BatteryStatus? {
        if (level < 0 || scale <= 0) return null
        val percent = (level * 100.0 / scale).roundToInt().coerceIn(0, 100)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryStatus(percent, charging)
    }
}

/** Reads the phone's battery. No permission is needed for this broadcast. */
object BatteryReader {
    /** The charge level carried by a battery broadcast. */
    fun parse(intent: Intent): BatteryStatus? = BatteryParser.parse(
        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
    )

    /** The charge level right now, read from the sticky battery broadcast. */
    fun read(context: Context): BatteryStatus? {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return null
        return parse(intent)
    }
}
