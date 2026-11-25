package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.keyboard.GestureAction

/**
 * 九键 / 笔画主键位的上下滑符号覆盖，和 26 键 [QwertySwipeSymbols] 同一套交互：
 * 每格可单独改符号、单独设「跟随全角半角」，设置页按当前生效值显示，改完即生效。
 *
 * 与 26 键的差别是符号本体不在代码预设里，而在 xime.yaml 的 keyboard.t9/stroke.keys：
 * 上滑默认印着角标数字、下滑默认没有。所以这里**只存用户改过的格子**（"up.<键>" / "down.<键>" → 符号），
 * 没改的照 xime.yaml 走，升级换预设时跟着变；"跟随全角半角"的格子另存一份（默认空）。
 *
 * 空格子是用户明确要"留空不放符号"：存空串，键盘上这一方向不再输入（不是"没改过"）。
 * 左右滑不归这里，它们是 [GridSwipeBindings] 的编辑快捷键。
 */
internal object GridSwipeSymbols {
    const val T9 = "grid_symbols_t9"
    const val STROKE = "grid_symbols_stroke"
    const val T9_FOLLOW = "grid_symbols_follow_t9"
    const val STROKE_FOLLOW = "grid_symbols_follow_stroke"

    fun key(stroke: Boolean) = if (stroke) STROKE else T9
    fun followKey(stroke: Boolean) = if (stroke) STROKE_FOLLOW else T9_FOLLOW
    fun keys(stroke: Boolean) = GridSwipeBindings.keys(stroke)

    fun load(context: Context, stroke: Boolean): Map<String, String> {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val valid = (listOf("up", "down").flatMap { direction ->
            keys(stroke).map { "$direction.$it" }
        }).toSet()
        return buildMap {
            for (entry in prefs.getStringSet(key(stroke), null).orEmpty()) {
                val slot = entry.substringBefore('\t')
                if (slot in valid) put(slot, entry.substringAfter('\t', ""))
            }
        }
    }

    /** 开着「跟随全角半角」的格子（slot id 集合）。 */
    fun loadFollows(context: Context, stroke: Boolean): Set<String> =
        SettingsPreferences.getPrefsPublic(context).getStringSet(followKey(stroke), null).orEmpty()

    fun save(context: Context, stroke: Boolean, values: Map<String, String>, follows: Set<String>) {
        val prefs = SettingsPreferences.getPrefsPublic(context).edit()
        prefs.putStringSet(key(stroke), values.map { (slot, value) -> "$slot\t$value" }.toSet())
        prefs.putStringSet(followKey(stroke), follows)
        check(prefs.commit()) { "无法保存九键/笔画符号" }
        // 键盘侧的手势缓存要在下次重画时看到新符号。
        KeysConfigHelper.loadConfig(context)
    }

    /** 有全角、半角两种写法时才能开「跟随」（映射表与 26 键同一份）。 */
    fun hasWidthPair(text: String) = QwertySwipeSymbols.hasWidthPair(text)
    fun width(text: String, full: Boolean) = QwertySwipeSymbols.width(text, full)

    /**
     * 把修改过的格子套到 xime.yaml 的手势上，规则同 [QwertySwipeSymbols.apply]：
     * [fullWidth] 不为 null 时，"跟随"的格子按全角或半角换成对应写法；null（设置页）给出存着的原样。
     * 没改过的方向原样返回，连 display、action 都不动，保证九键数字上滑、笔画回退等既有行为不变。
     */
    fun apply(key: String, original: KeyGestureConfig?, overrides: Map<String, String>,
        follows: Set<String>, fullWidth: Boolean? = null): KeyGestureConfig? {
        fun gesture(direction: String, configured: GestureDef?): GestureDef? {
            val stored = overrides["$direction.$key"] ?: return configured
            val value = if (fullWidth != null && "$direction.$key" in follows) width(stored, fullWidth) else stored
            return GestureDef(label = value, value = value,
                display = configured?.display ?: DisplayMode.BOTH,
                action = if (value.isEmpty()) GestureAction.NONE else GestureAction.COMMIT)
        }
        val up = gesture("up", original?.swipeUp)
        val down = gesture("down", original?.swipeDown)
        if (up == original?.swipeUp && down == original?.swipeDown) return original
        return (original ?: KeyGestureConfig()).copy(swipeUp = up, swipeDown = down)
    }
}
