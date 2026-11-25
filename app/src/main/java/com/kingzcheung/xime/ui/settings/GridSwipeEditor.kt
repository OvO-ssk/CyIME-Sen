package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.keyboard.SideSymbolTarget
import com.kingzcheung.xime.settings.GridSwipeBindings
import com.kingzcheung.xime.settings.GridSwipeSymbols
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.LetterSwipeBindings
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.SymbolInputMode
import com.kingzcheung.xime.ui.keyboard.rememberKeyboardInputPreferences
import com.kingzcheung.xime.ui.keyboard.rememberSwipeHintsEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 空绑定（含空串）一律写「沿用原动作」——[LetterSwipeBindings.label] 对空串返回空串，不能直接拿来做按钮文字。 */
private fun gridActionText(binding: String): String =
    GridSwipeBindings.label(binding.takeIf { it.isNotEmpty() }) ?: "沿用原动作"

/**
 * 「按键符号 → 九键侧栏 / 笔画侧栏」页的主键位配置：把上方那张键盘草图的主键格子做成可点按钮，
 * 点哪个键弹哪个键的框——和 26 键一样，上下滑符号、跟随全角半角、四向编辑动作都在框里。
 * 侧栏格子仍由 [sideCell] 渲染（侧栏本身不在这里改）。
 */
@Composable
internal fun GridSwipeEditor(
    target: SideSymbolTarget, count: Int, canGrow: Boolean,
    onResetReady: ((() -> Unit)?) -> Unit = {},
    sideCell: @Composable (Int, Modifier) -> Unit,
) {
    val context = LocalContext.current
    val stroke = target == SideSymbolTarget.STROKE
    val hints = rememberSwipeHintsEnabled()
    val inputSettings = rememberKeyboardInputPreferences()
    val reverseSwipe = inputSettings.reverseSymbolSwipe
    // 和 26 键同一套文案：默认上方角标靠下滑输入，开了「反转上下滑方向」才靠上拉。
    val topGesture = if (inputSettings.symbolInputMode == SymbolInputMode.LONG_PRESS) "长按"
        else if (reverseSwipe) "上拉" else "下拉"
    val bottomGesture = if (reverseSwipe) "下拉" else "上拉"
    // 上方角标被哪个方向的编辑动作占着（26 键 LetterSwipeBindings.upperDirection 的同一规则）。
    val upperSlot = LetterSwipeBindings.upperDirection(reverseSwipe)
    val lowerSlot = LetterSwipeBindings.lowerDirection(reverseSwipe)

    var symbols by remember(stroke) { mutableStateOf(emptyMap<String, String>()) }
    var originalSymbols by remember(stroke) { mutableStateOf(emptyMap<String, String>()) }
    var overrides by remember(stroke) { mutableStateOf(emptyMap<String, String>()) }
    var swipes by remember(stroke) { mutableStateOf(emptyMap<String, String>()) }
    var followOverrides by remember(stroke) { mutableStateOf(emptyMap<String, Boolean>()) }
    var loaded by remember(stroke) { mutableStateOf(false) }
    var editingKey by remember(stroke) { mutableStateOf<String?>(null) }
    val board = if (stroke) "stroke" else "t9"

    LaunchedEffect(stroke) {
        // 设置页和键盘读同一份生效值：先确保手势缓存已加载用户覆盖。
        withContext(Dispatchers.IO) { KeysConfigHelper.loadConfig(context) }
        symbols = KeysConfigHelper.gridSymbolEditorValues(stroke)
        originalSymbols = symbols
        overrides = GridSwipeSymbols.load(context, stroke)
        followOverrides = GridSwipeSymbols.loadFollows(context, stroke).associateWith { true }
        swipes = GridSwipeBindings.load(context, stroke)
        loaded = true
    }
    // 跟随全角半角的格子：这里没有 26 键那样的预设，只算用户开着的。
    val follows = remember(followOverrides) { followOverrides.filterValues { it }.keys.toSet() }

    // 和 26 键 persist 一样：只写和当前生效值不同的格子，其余沿用已有覆盖（yaml 升级时跟着变）。
    fun persist(next: Map<String, String>, nextSwipes: Map<String, String>, nextFollows: Map<String, Boolean>) {
        val snapshot = overrides + next.filter { (slot, value) -> originalSymbols[slot] != value }
        GridSwipeSymbols.save(context, stroke, snapshot, nextFollows.filterValues { it }.keys.toSet())
        GridSwipeBindings.save(context, stroke, nextSwipes)
        symbols = next
        originalSymbols = next
        overrides = snapshot
        swipes = nextSwipes
        followOverrides = nextFollows
    }

    // 右上角「恢复默认」要连主键配置一起清：清覆盖后重新读 yaml 生效值，键盘也跟着热重载。
    fun resetGrid() {
        GridSwipeSymbols.save(context, stroke, emptyMap(), emptySet())
        GridSwipeBindings.save(context, stroke, emptyMap())
        symbols = KeysConfigHelper.gridSymbolEditorValues(stroke)
        originalSymbols = symbols
        overrides = emptyMap()
        swipes = emptyMap()
        followOverrides = emptyMap()
        editingKey = null
    }
    DisposableEffect(stroke) {
        onResetReady(::resetGrid)
        onDispose { onResetReady(null) }
    }

    val keys = GridSwipeBindings.keys(stroke)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 这两个就是 26 键那两个全局开关，存同一份偏好，改完全键盘生效。
        GridHintSwitchRow("上方角标提示", "显示按键右上角的小符号", hints.up, "grid-symbol-switch-up") {
            SettingsPreferences.setSwipeUpHintsEnabled(context, it) }
        GridHintSwitchRow("下方角标提示", "显示按键左下角的小符号", hints.down, "grid-symbol-switch-down") {
            SettingsPreferences.setSwipeDownHintsEnabled(context, it) }
        SketchCaption(target.where)
        NineGridSketch(target, count, canGrow, sideCell) { index, label, modifier ->
            val id = keys.getOrNull(index) ?: return@NineGridSketch
            val upAction = GridSwipeBindings.label(swipes["$id.$upperSlot"])
            val downAction = GridSwipeBindings.label(swipes["$id.$lowerSlot"])
            val leftAction = GridSwipeBindings.label(swipes["$id.left"])
            val rightAction = GridSwipeBindings.label(swipes["$id.right"])
            MainGridKey(
                label = label,
                up = upAction ?: symbols["up.$id"].orEmpty(),
                down = downAction ?: symbols["down.$id"].orEmpty(),
                left = leftAction.orEmpty(), right = rightAction.orEmpty(),
                upIsAction = upAction != null, downIsAction = downAction != null,
                modifier = modifier.testTag("grid-swipe-key:$board:$id")
                    .clickable(enabled = loaded) { editingKey = id },
            )
        }
    }

    editingKey?.let { key ->
        GridSwipeKeyDialog(
            key, symbols["up.$key"].orEmpty(), symbols["down.$key"].orEmpty(),
            topGesture, bottomGesture, swipes,
            followUp = "up.$key" in follows, followDown = "down.$key" in follows,
            upperSlot = upperSlot, lowerSlot = lowerSlot,
            onDismiss = { editingKey = null },
            onDone = { up, down, nextSwipes, upFollows, downFollows ->
                editingKey = null
                val next = symbols + ("up.$key" to up) + ("down.$key" to down)
                val nextFollows = followOverrides +
                    ("up.$key" to upFollows) + ("down.$key" to downFollows)
                if (next != symbols || nextSwipes != swipes || nextFollows != followOverrides)
                    persist(next, nextSwipes, nextFollows)
            })
    }
}

/** 和 26 键 SymbolSwitchRow 同款（设置页的角标提示开关），只是放在九键/笔画这一页。 */
@Composable
private fun GridHintSwitchRow(title: String, description: String, checked: Boolean, tag: String,
    onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, colors = settingsSwitchColors())
    }
}

/**
 * 键盘草图里的主键：有颜色＝可点；上下小字显示生效的符号或动作名。
 * 左/右滑不是角标，放在格子左右两侧竖排小字（一个汉字一行），横向不挤主标签。
 * 这里的小字字号只用于设置页预览，不改变实操键盘的字号。
 */
@Composable
private fun MainGridKey(label: String, up: String, down: String, left: String, right: String,
    upIsAction: Boolean, downIsAction: Boolean, modifier: Modifier) {
    val sideLabel = @Composable { text: String, modifier: Modifier ->
        Text(text, fontSize = 7.sp, lineHeight = 7.sp, maxLines = 4, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary, modifier = modifier)
    }
    Box(modifier.padding(2.dp).clip(RoundedCornerShape(7.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center) {
        // 左/右滑动作竖排，像 26 键角标一样一眼能看出哪边绑了什么。
        if (left.isNotEmpty()) sideLabel(left,
            Modifier.align(Alignment.CenterStart).width(10.dp).padding(horizontal = 1.dp))
        if (right.isNotEmpty()) sideLabel(right,
            Modifier.align(Alignment.CenterEnd).width(10.dp).padding(horizontal = 1.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.padding(
                horizontal = if (left.isNotEmpty() || right.isNotEmpty()) 10.dp else 2.dp,
                vertical = 2.dp)) {
            if (up.isNotEmpty()) Text(up, fontSize = 9.sp, maxLines = 1,
                color = if (upIsAction) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            // 下角标略微上提、字号缩小，保证「。」这类符号在预览格里完整显示。
            if (down.isNotEmpty()) Text(down, fontSize = 9.sp, maxLines = 1,
                modifier = Modifier.padding(bottom = 3.dp),
                color = if (downIsAction) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/**
 * 点一个主键弹出的小框，和 26 键 KeySymbolDialog 一样：上方／下方符号各自可改、各自可设「跟随全角半角」，
 * 下面四个方向各占一行（纯文字方向名 + 26 键同款动作展开菜单）。框里是草稿，点「完成」才写入，「取消」不改。
 */
@Composable
private fun GridSwipeKeyDialog(
    key: String, up: String, down: String, topGesture: String, bottomGesture: String,
    swipes: Map<String, String>, followUp: Boolean, followDown: Boolean,
    upperSlot: String, lowerSlot: String,
    onDismiss: () -> Unit,
    onDone: (String, String, Map<String, String>, Boolean, Boolean) -> Unit,
) {
    var upText by remember(key) { mutableStateOf(up) }
    var downText by remember(key) { mutableStateOf(down) }
    var upFollows by remember(key) { mutableStateOf(followUp) }
    var downFollows by remember(key) { mutableStateOf(followDown) }
    var swipeDraft by remember(key) { mutableStateOf(swipes) }
    // 四个方向固定顺序：上滑、下滑、左滑、右滑；每项后面的动作菜单沿用 26 键的展开方式。
    val directions = listOf("up" to "上滑", "down" to "下滑", "left" to "左滑", "right" to "右滑")
    var expandedSlot by remember(key) { mutableStateOf<String?>(null) }

    // 这个方向设了编辑动作时，对应的角标符号暂时用不上——框里说明，免得以为改了符号没反应。
    fun shadowedBy(dir: String): String? = swipeDraft["$key.$dir"]?.let { action ->
        "现在是「${GridSwipeBindings.label(action)}」，清掉这一格的滑动动作才输入符号"
    }
    val upNote = shadowedBy(upperSlot)
    val downNote = shadowedBy(lowerSlot)
    val changed = upText != up || downText != down || swipeDraft != swipes ||
        upFollows != followUp || downFollows != followDown
    var leaving by remember(key) { mutableStateOf(false) }

    fun finish() = onDone(upText, downText, swipeDraft, upFollows, downFollows)
    AlertDialog(
        // 和 26 键一样：改了还没点「完成」时点框外先问一句，不直接丢。
        onDismissRequest = { if (changed) leaving = true else onDismiss() },
        modifier = Modifier.testTag("grid-swipe-dialog"),
        title = { Text(key) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = upText, onValueChange = { upText = it }, singleLine = true,
                    label = { Text("上方（$topGesture）") }, placeholder = { Text("留空不放符号") },
                    supportingText = upNote?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("grid-swipe-up"))
                GridFollowWidthRow(upText, upFollows, "grid-swipe-up-follow") { upFollows = it }
                OutlinedTextField(value = downText, onValueChange = { downText = it }, singleLine = true,
                    label = { Text("下方（$bottomGesture）") }, placeholder = { Text("留空不放符号") },
                    supportingText = downNote?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("grid-swipe-down"))
                GridFollowWidthRow(downText, downFollows, "grid-swipe-down-follow") { downFollows = it }
                Text("滑动动作", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    directions.forEach { (dir, name) ->
                        val slot = "$key.$dir"
                        val binding = swipeDraft[slot].orEmpty()
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f))
                                Box {
                                    OutlinedButton(onClick = { expandedSlot = slot },
                                        modifier = Modifier.testTag("grid-swipe-action:$slot")) {
                                        Text(gridActionText(binding))
                                    }
                                    DropdownMenu(expandedSlot == slot, onDismissRequest = { expandedSlot = null }) {
                                        (mapOf("" to "沿用原动作", "text:" to "输入文字") + GridSwipeBindings.actions)
                                            .forEach { (id, label) ->
                                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                                    swipeDraft = if (id.isEmpty()) swipeDraft - slot
                                                        else swipeDraft + (slot to id)
                                                    expandedSlot = null
                                                })
                                            }
                                    }
                                }
                            }
                            if (binding.startsWith("text:")) OutlinedTextField(
                                value = binding.removePrefix("text:"),
                                onValueChange = { swipeDraft = swipeDraft + (slot to "text:$it") },
                                label = { Text("${name}输入的文字") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(modifier = Modifier.testTag("grid-swipe-done"),
            onClick = ::finish) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    if (leaving) UnsavedChangesDialog(
        onSave = { leaving = false; finish() },
        onDiscard = { leaving = false; onDismiss() },
        onKeepEditing = { leaving = false })
}

/** 和 26 键 FollowWidthRow 同款（说明文案、开关判定），只是映射表来自九键/笔画的覆盖对象。 */
@Composable
private fun GridFollowWidthRow(symbol: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val paired = GridSwipeSymbols.hasWidthPair(symbol)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .toggleable(value = checked, enabled = paired, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("跟随全角半角", style = MaterialTheme.typography.titleSmall)
            Text(if (paired)
                "全角时出 ${GridSwipeSymbols.width(symbol, true)}，半角时出 ${GridSwipeSymbols.width(symbol, false)}"
                else "这个符号只有一种写法，全角半角都出它",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked && paired, onCheckedChange = null, enabled = paired,
            colors = settingsSwitchColors())
    }
}
