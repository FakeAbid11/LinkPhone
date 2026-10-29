package app.linkphone.features

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryParserTest {
    @Test
    fun turnsTheLevelIntoAPercentage() {
        val status = BatteryParser.parse(
            level = 87,
            scale = 100,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
        )
        assertEquals(87, status?.level)
        assertFalse(status?.charging == true)
    }

    @Test
    fun roundsThePercentage() {
        assertEquals(33, BatteryParser.parse(1, 3, BatteryManager.BATTERY_STATUS_DISCHARGING)?.level)
        assertEquals(67, BatteryParser.parse(2, 3, BatteryManager.BATTERY_STATUS_DISCHARGING)?.level)
    }

    @Test
    fun reportsCharging() {
        assertTrue(
            BatteryParser.parse(50, 100, BatteryManager.BATTERY_STATUS_CHARGING)?.charging == true,
        )
        assertTrue(
            BatteryParser.parse(100, 100, BatteryManager.BATTERY_STATUS_FULL)?.charging == true,
        )
    }

    @Test
    fun keepsTheLevelInRange() {
        assertEquals(100, BatteryParser.parse(500, 100, BatteryManager.BATTERY_STATUS_DISCHARGING)?.level)
    }

    @Test
    fun rejectsUnusableReadings() {
        assertNull(BatteryParser.parse(-1, 100, BatteryManager.BATTERY_STATUS_DISCHARGING))
        assertNull(BatteryParser.parse(50, 0, BatteryManager.BATTERY_STATUS_DISCHARGING))
        assertNull(BatteryParser.parse(50, -1, BatteryManager.BATTERY_STATUS_DISCHARGING))
    }
}
