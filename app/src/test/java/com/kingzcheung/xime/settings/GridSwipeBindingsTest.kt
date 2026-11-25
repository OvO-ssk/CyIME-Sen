package com.kingzcheung.xime.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 九键 / 笔画主键位四向编辑快捷键的持久化：往返、过滤、两套分开。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GridSwipeBindingsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun clear() { SettingsPreferences.getPrefsPublic(app).edit().clear().commit() }

    @Test fun saveLoadRoundTrip() {
        val bindings = mapOf("2.left" to "copy", "2.right" to "text:@", "9.up" to "undo", "5.down" to "paste")
        GridSwipeBindings.save(app, stroke = false, bindings)
        assertEquals(bindings, GridSwipeBindings.load(app, stroke = false))
        assertTrue(GridSwipeBindings.load(app, stroke = true).isEmpty())
    }

    @Test fun allFourDirections() {
        val bindings = mapOf("1.left" to "undo", "2.right" to "copy", "3.up" to "cut", "4.down" to "paste")
        GridSwipeBindings.save(app, stroke = false, bindings)
        assertEquals(bindings, GridSwipeBindings.load(app, stroke = false))
    }

    @Test fun strokeAndT9AreSeparate() {
        GridSwipeBindings.save(app, stroke = true, mapOf("一.left" to "paste", "乛.up" to "cut"))
        assertEquals("paste", GridSwipeBindings.load(app, stroke = true)["一.left"])
        assertEquals("cut", GridSwipeBindings.load(app, stroke = true)["乛.up"])
        assertTrue(GridSwipeBindings.load(app, stroke = false).isEmpty())
    }

    @Test fun invalidSlotsAndActionsAreDropped() {
        // 汉字只能进笔画套、数字只能进九键套；未知方向与动作、空 text:、0 键都不收。
        GridSwipeBindings.save(app, stroke = false, mapOf(
            "2.left" to "copy",          // 合法
            "3.up" to "undo",            // 上滑合法
            "一.left" to "undo",         // 汉字不在九键键集
            "2.sideways" to "paste",     // 未知方向
            "8.left" to "bogus",         // 未知动作
            "9.right" to "text:",        // 空文字
            "0.left" to "cut",           // 0 键不纳入（左右滑是候选导航）
        ))
        assertEquals(mapOf("2.left" to "copy", "3.up" to "undo"),
            GridSwipeBindings.load(app, stroke = false))
    }

    @Test fun labelResolvesActionOrText() {
        assertEquals("复制", GridSwipeBindings.label("copy"))
        assertEquals("@", GridSwipeBindings.label("text:@"))
        assertNull(GridSwipeBindings.label(null))
    }
}
