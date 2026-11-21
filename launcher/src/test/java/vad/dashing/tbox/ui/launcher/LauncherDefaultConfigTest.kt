package vad.dashing.tbox.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherDefaultConfigTest {

    @Test
    fun verifyDefaultCarPaint() {
        assertEquals("cheqi04", LauncherCarPaint.defaultId)
    }

    @Test
    fun verifyDefaultCruisePresets() {
        assertEquals(listOf(80, 100, 110, 130), CRUISE_PRESET_DEFAULTS_KMH)
    }

    @Test
    fun verifyDefaultDimensionsAndScales() {
        assertEquals(40, TOP_BAR_HEIGHT_DEFAULT)
        assertEquals(65, BOTTOM_BAR_HEIGHT_DEFAULT)
        assertEquals(65, FLOATING_HOME_SIZE_DEFAULT)
        assertEquals(1.804931f, CAR_MODEL_SCALE_DEFAULT, 0.001f)
        assertEquals(1.10f, CAR_MODEL_DRIVE_SCALE_DEFAULT, 0.001f)
        assertEquals(1.3485069f, CLIMATE_SCALE_DEFAULT, 0.001f)
        assertEquals(1.25f, HOME_ICON_SCALE_DEFAULT, 0.001f)
        assertEquals(0.79613615f, MEDIA_CARD_ALPHA_DEFAULT, 0.001f)
        assertEquals(2.1f, TIRE_PRESSURE_LOW_THRESHOLD_DEFAULT, 0.001f)
        assertEquals(8, GRID_ROWS_DEFAULT)
        assertEquals(21, GRID_ROW_SPACING_DEFAULT)
        assertEquals(20, ADAS_ACC_SPEED_OFFSET_DEFAULT)
    }

    @Test
    fun verifyDefaultTopBarItems() {
        assertEquals(
            listOf(
                "headlights", "trunk", "mirrors", "rear_fog", "hdc", "pdc", "avm",
                "spacer_small_1794639492605", "gps", "combined_temp", "air_quality",
                "spacer_small_1794639527728", "avg_fuel", "fuel", "trip_cost", "trip_distance",
                "spacer_small_1794639591091", "engine_temp", "gearbox_temp", "voltage",
                "spacer_small_1794639626549", "vehicle_status", "maintenance"
            ),
            DEFAULT_TOP_BAR_ITEMS
        )
    }

    @Test
    fun verifyDefaultBottomSlots() {
        assertEquals(20, DEFAULT_BOTTOM_SLOTS_UNIFIED.size)
        assertEquals("all_apps", DEFAULT_BOTTOM_SLOTS_UNIFIED[1])
        assertEquals("car_dialer", DEFAULT_BOTTOM_SLOTS_UNIFIED[2])
        assertEquals("windscreen_heat", DEFAULT_BOTTOM_SLOTS_UNIFIED[4])
        assertEquals("steering_heat", DEFAULT_BOTTOM_SLOTS_UNIFIED[5])
        assertEquals("seat_heat_left", DEFAULT_BOTTOM_SLOTS_UNIFIED[6])
        assertEquals("temp_driver", DEFAULT_BOTTOM_SLOTS_UNIFIED[7])
        assertEquals("hvac_auto", DEFAULT_BOTTOM_SLOTS_UNIFIED[9])
        assertEquals("hvac_fan_speed", DEFAULT_BOTTOM_SLOTS_UNIFIED[10])
        assertEquals("hvac_sync", DEFAULT_BOTTOM_SLOTS_UNIFIED[11])
        assertEquals("temp_pass", DEFAULT_BOTTOM_SLOTS_UNIFIED[12])
        assertEquals("seat_heat_right", DEFAULT_BOTTOM_SLOTS_UNIFIED[14])
        assertEquals("front_defrost", DEFAULT_BOTTOM_SLOTS_UNIFIED[15])
        assertEquals("rear_defrost", DEFAULT_BOTTOM_SLOTS_UNIFIED[16])
        assertEquals("hvac_fan_direction", DEFAULT_BOTTOM_SLOTS_UNIFIED[17])
        assertEquals("recirc", DEFAULT_BOTTOM_SLOTS_UNIFIED[18])
        assertEquals("hvac_pm25", DEFAULT_BOTTOM_SLOTS_UNIFIED[19])
    }

    @Test
    fun verifyDefaultBottomSlotPresets() {
        assertEquals(
            "null,null,audio_volume,temp_driver,home_nav,hvac_auto,hvac_fan_direction,temp_pass,null,hvac_fan_speed,seat_vent_left,steering_heat,windscreen_heat,hvac_pm25,front_defrost,rear_defrost,null,undo_nav,null,null",
            DEFAULT_BOTTOM_SLOTS_PRESET_GENERAL
        )
        assertEquals(
            "null,undo_nav,null,temp_pass,null,seat_vent_left,null,null,hvac_ac,recirc,hvac_auto,hvac_fan_speed,hvac_fan_direction,null,null,null,null,seat_vent_right,temp_driver,null",
            DEFAULT_BOTTOM_SLOTS_PRESET_SUMMER
        )
        assertEquals(
            "null,null,audio_volume,temp_driver,null,hvac_auto,hvac_fan_direction,temp_pass,null,hvac_fan_speed,seat_vent_left,steering_heat,windscreen_heat,hvac_pm25,front_defrost,rear_defrost,null,undo_nav,null,null",
            DEFAULT_BOTTOM_SLOTS_PRESET_WINTER
        )
    }

    @Test
    fun verifyDefaultTireBadgeShape() {
        assertEquals(LauncherTireBadgeShape.ROUND, LauncherTireBadgeShape.fromCode(null))
        assertEquals(LauncherTireBadgeShape.ROUND, LauncherTireBadgeShape.fromCode("round"))
        assertEquals(LauncherTireBadgeShape.RECTANGLE, LauncherTireBadgeShape.fromCode("rectangle"))
    }

    @Test
    fun verifyDefaultTireLowTempAlertEnabled() {
        assertEquals(true, DEFAULT_TIRE_LOW_TEMP_ALERT_ENABLED)
    }

    @Test
    fun verifyDefaultTopShadeTilesCount() {
        assertEquals(18, LauncherTopShadeConfigStore.DEFAULT_TILES.size)
    }

    @Test
    fun verifyDefaultHomeItemsRaw() {
        assertEquals(
            "[{\"type\":\"app\",\"package\":\"ru.yandex.yandexnavi\",\"slot\":0}]",
            LauncherHomeStore.DEFAULT_HOME_ITEMS_RAW
        )
    }
}
