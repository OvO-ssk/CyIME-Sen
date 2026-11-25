package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 九键 / 笔画主键位的滑动编辑快捷键绑定（上／下／左／右四向）。
 *
 * 与 26 键 [LetterSwipeBindings] 共用同一套动作 ID 和同一条执行通道（onLetterSwipeAction）。
 * 某个方向只要设了动作，就覆盖该方向原来的行为：上下滑不再输入原本的角标符号、左右滑不再留给候选导航；
 * 没设（＝配置里选「沿用原动作」）则完全保持键盘原有逻辑，包括上下滑符号与 0/1 键的候选导航。
 *
 * 存储格式与 [LetterSwipeBindings] 一致：每个条目 "<键id>.<方向>\t<动作>"，动作为
 * [LetterSwipeBindings.actions] 里的 ID，或 "text:..." 前缀的直接上屏文字。键 id：
 * 九键为 "1".."9"，笔画为 "一丨丿丶乛*分词，。"。
 *
 * 默认空：没有绑定时四个方向都不触发编辑动作，不改变现有行为。
 */
internal object GridSwipeBindings {
    const val T9 = "grid_swipes_t9"
    const val STROKE = "grid_swipes_stroke"

    /** 四个方向都能设编辑快捷键；和 26 键同一套方向 ID。 */
    val directions = LetterSwipeBindings.directions

    /** 九键可绑定的主键 id（1..9；0 键的左右滑是候选导航，不纳入）。 */
    val t9Keys = (1..9).map(Int::toString)
    /** 笔画主键 id，与 StrokeKeyboardLayout.strokeMainKeys 的键面字一致。 */
    val strokeKeys = listOf("一", "丨", "丿", "丶", "乛", "*", "分词", "，", "。")

    val actions get() = LetterSwipeBindings.actions

    fun key(stroke: Boolean) = if (stroke) STROKE else T9
    fun keys(stroke: Boolean) = if (stroke) strokeKeys else t9Keys

    fun label(value: String?): String? = LetterSwipeBindings.label(value)

    fun load(context: Context, stroke: Boolean): Map<String, String> =
        parse(SettingsPreferences.getPrefsPublic(context).getStringSet(key(stroke), null).orEmpty())

    fun save(context: Context, stroke: Boolean, bindings: Map<String, String>) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val valid = keys(stroke).toSet()
        val cleaned = bindings.filter { (slot, value) ->
            val id = slot.substringBefore('.')
            val direction = slot.substringAfter('.', "")
            id in valid && direction in directions &&
                (value in actions || value.startsWith("text:") && value.length > 5)
        }
        check(prefs.edit().putStringSet(key(stroke), encode(cleaned)).commit()) { "无法保存九键/笔画滑动快捷键" }
    }

    private fun parse(entries: Set<String>): Map<String, String> = buildMap {
        val valid = (t9Keys + strokeKeys).toSet()
        for (entry in entries) {
            val slot = entry.substringBefore('\t')
            val value = entry.substringAfter('\t', "")
            val id = slot.substringBefore('.')
            val direction = slot.substringAfter('.', "")
            if (id in valid && direction in directions &&
                (value in actions || value.startsWith("text:") && value.length > 5)) put(slot, value)
        }
    }

    private fun encode(bindings: Map<String, String>): Set<String> =
        bindings.map { (slot, value) -> "$slot\t$value" }.toSet()
}
