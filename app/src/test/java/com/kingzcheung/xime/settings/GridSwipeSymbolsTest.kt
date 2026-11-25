package com.kingzcheung.xime.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kingzcheung.xime.keyboard.GestureAction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 九键 / 笔画主键位符号覆盖：往返、全角半角、两套隔离、未改的格子照原手势走。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GridSwipeSymbolsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun clear() { SettingsPreferences.getPrefsPublic(app).edit().clear().commit() }

    @Test fun saveLoadRoundTripAndSeparate() {
        GridSwipeSymbols.save(app, false, mapOf("up.2" to "②", "down.2" to "@"), setOf("down.2"))
        assertEquals(mapOf("up.2" to "②", "down.2" to "@"), GridSwipeSymbols.load(app, false))
        assertEquals(setOf("down.2"), GridSwipeSymbols.loadFollows(app, false))
        assertTrue(GridSwipeSymbols.load(app, true).isEmpty())
        assertTrue(GridSwipeSymbols.loadFollows(app, true).isEmpty())

        GridSwipeSymbols.save(app, true, mapOf("up.一" to "1", "down.一" to "$"), setOf("down.一"))
        assertEquals("$", GridSwipeSymbols.load(app, true)["down.一"])
        assertEquals("②", GridSwipeSymbols.load(app, false)["up.2"])
    }

    @Test fun invalidSlotsAreDroppedOnLoad() {
        val prefs = SettingsPreferences.getPrefsPublic(app)
        prefs.edit().putStringSet(GridSwipeSymbols.T9,
            setOf("up.2\tA", "up.0\tB", "up.一\tC", "sideways.2\tD", "down.9\t")).commit()
        val loaded = GridSwipeSymbols.load(app, false)
        assertEquals("A", loaded["up.2"])
        assertEquals("", loaded["down.9"])
        assertFalse("0 键不纳入", "up.0" in loaded)
        assertFalse("汉字不在九键套", "up.一" in loaded)
        assertFalse("未知方向不收", "sideways.2" in loaded)
    }

    @Test fun applyOnlyTouchesOverriddenDirections() {
        val base = KeyGestureConfig(
            swipeUp = GestureDef(label = "2", value = "2", display = DisplayMode.BOTH, action = GestureAction.COMMIT),
            swipeDown = null)
        val unchanged = GridSwipeSymbols.apply("2", base, emptyMap(), emptySet(), true)
        assertSame(base, unchanged)

        val applied = GridSwipeSymbols.apply("2", base, mapOf("down.2" to "!"), emptySet(), true)!!
        assertEquals("2", applied.swipeUp!!.value)
        assertEquals("!", applied.swipeDown!!.value)
        assertEquals(DisplayMode.BOTH, applied.swipeUp!!.display)
        assertEquals(GestureAction.COMMIT, applied.swipeDown!!.action)
    }

    @Test fun applySwitchesWidthOnlyForFollowedSlots() {
        val base = KeyGestureConfig(swipeUp = GestureDef(value = "2"), swipeDown = null)
        val overrides = mapOf("up.2" to "！", "down.2" to "￥")
        val full = GridSwipeSymbols.apply("2", base, overrides, setOf("up.2"), true)!!
        assertEquals("！", full.swipeUp!!.value)   // 全角写法原样（没有另一种写法时不变）
        assertEquals("￥", full.swipeDown!!.value) // 没开跟随：原样
        val half = GridSwipeSymbols.apply("2", base, overrides, setOf("down.2"), false)!!
        assertEquals("$", half.swipeDown!!.value)  // 开了跟随：￥ → $
    }

    @Test fun emptyOverrideDisablesThatDirection() {
        val base = KeyGestureConfig(swipeUp = GestureDef(value = "2", action = GestureAction.COMMIT))
        val applied = GridSwipeSymbols.apply("2", base, mapOf("up.2" to ""), emptySet(), null)!!
        assertEquals("", applied.swipeUp!!.value)
        assertEquals(GestureAction.NONE, applied.swipeUp!!.action)
    }

    @Test fun widthMappingSharedWithQwerty() {
        assertTrue(GridSwipeSymbols.hasWidthPair("$"))
        assertEquals("￥", GridSwipeSymbols.width("$", true))
        assertEquals("$", GridSwipeSymbols.width("￥", false))
        assertFalse(GridSwipeSymbols.hasWidthPair("@"))
    }
}
