/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ui.components

import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalKeyListener
import org.connectbot.terminal.VTermKey
import org.connectbot.util.PreferenceConstants

private const val UI_OPACITY = 0.5f

/**
 * Height of a single row of virtual keyboard keys in dp.
 */
const val TERMINAL_KEYBOARD_HEIGHT_DP = 30

/**
 * Width of the virtual keyboard keys in dp.
 */
private const val TERMINAL_KEYBOARD_WIDTH_DP = 45

/**
 * Narrowest a key may get when a row shares the whole width between its keys. A row that would
 * have to squeeze past this keeps the keys at their natural width and scrolls instead.
 */
private const val TERMINAL_KEYBOARD_MIN_WIDTH_DP = 34

/**
 * Size of the content (icons and text) for the virtual keyboard keys in dp.
 */
private const val TERMINAL_KEYBOARD_CONTENT_SIZE_DP = 20

/**
 * Supported range and default for the number of special-key rows.
 */
const val TERMINAL_KEYBOARD_MIN_ROWS = 1
const val TERMINAL_KEYBOARD_MAX_ROWS = 3
const val TERMINAL_KEYBOARD_DEFAULT_ROWS = 2

/**
 * Supported and default number of function keys (F1 onwards) shown on the bar. Trimming the
 * function keys is what keeps their row from scrolling for people who only ever reach for the
 * first few.
 */
val TERMINAL_KEYBOARD_FUNCTION_KEY_CHOICES = listOf(0, 2, 4, 6, 8, 10, 12)
const val TERMINAL_KEYBOARD_MAX_FUNCTION_KEYS = 12
const val TERMINAL_KEYBOARD_DEFAULT_FUNCTION_KEYS = 12

/**
 * One special key, sized by the row that draws it: a stretched slot when the row fits on screen,
 * its natural width when the row scrolls.
 */
private typealias TerminalKeyContent = @Composable (Modifier) -> Unit

/**
 * Labels and key codes for F1-F12, in order.
 */
private val TERMINAL_KEYBOARD_FUNCTION_KEYS = listOf(
    R.string.button_key_f1 to VTermKey.FUNCTION_1,
    R.string.button_key_f2 to VTermKey.FUNCTION_2,
    R.string.button_key_f3 to VTermKey.FUNCTION_3,
    R.string.button_key_f4 to VTermKey.FUNCTION_4,
    R.string.button_key_f5 to VTermKey.FUNCTION_5,
    R.string.button_key_f6 to VTermKey.FUNCTION_6,
    R.string.button_key_f7 to VTermKey.FUNCTION_7,
    R.string.button_key_f8 to VTermKey.FUNCTION_8,
    R.string.button_key_f9 to VTermKey.FUNCTION_9,
    R.string.button_key_f10 to VTermKey.FUNCTION_10,
    R.string.button_key_f11 to VTermKey.FUNCTION_11,
    R.string.button_key_f12 to VTermKey.FUNCTION_12,
)

/**
 * Reads the user-configured number of special-key rows, clamped to the supported range.
 */
fun specialKeyboardRows(prefs: SharedPreferences): Int {
    val value = prefs.getString(PreferenceConstants.SPECIAL_KEY_ROWS, PreferenceConstants.SPECIAL_KEY_ROWS_DEFAULT)
    return (value?.toIntOrNull() ?: TERMINAL_KEYBOARD_DEFAULT_ROWS)
        .coerceIn(TERMINAL_KEYBOARD_MIN_ROWS, TERMINAL_KEYBOARD_MAX_ROWS)
}

/**
 * Reads the user-configured number of function keys, clamped to the supported range.
 */
fun specialKeyboardFunctionKeys(prefs: SharedPreferences): Int {
    val value = prefs.getString(
        PreferenceConstants.SPECIAL_KEY_FUNCTION_KEYS,
        PreferenceConstants.SPECIAL_KEY_FUNCTION_KEYS_DEFAULT,
    )
    return (value?.toIntOrNull() ?: TERMINAL_KEYBOARD_DEFAULT_FUNCTION_KEYS)
        .coerceIn(0, TERMINAL_KEYBOARD_MAX_FUNCTION_KEYS)
}

/**
 * Virtual keyboard with terminal special keys (Ctrl, Esc, arrows, function keys, etc.)
 * Positioned at the bottom of the console screen with the keys laid out on a configurable
 * number of rows (see [PreferenceConstants.SPECIAL_KEY_ROWS]). Each row shares the full width
 * between its keys, and only a row that cannot fit scrolls, which the function keys can avoid
 * too by showing fewer of them (see [PreferenceConstants.SPECIAL_KEY_FUNCTION_KEYS]).
 * Auto-hide timer is managed by parent ConsoleScreen
 */
@Composable
fun TerminalKeyboard(
    bridge: TerminalBridge,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    onHideIme: () -> Unit = {},
    onShowIme: () -> Unit = {},
    onOpenTextInput: () -> Unit = {},
    onScrollInProgressChange: (Boolean) -> Unit = {},
    imeVisible: Boolean = false,
    playAnimation: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val keyHandler = bridge.keyHandler
    val modifierState by keyHandler.modifierState.collectAsState()
    val bumpyArrows by remember {
        mutableStateOf(prefs.getBoolean(PreferenceConstants.BUMPY_ARROWS, false))
    }
    val rows by remember { mutableIntStateOf(specialKeyboardRows(prefs)) }
    val functionKeyCount by remember { mutableIntStateOf(specialKeyboardFunctionKeys(prefs)) }

    TerminalKeyboardContent(
        modifierState = modifierState,
        onCtrlPress = {
            keyHandler.metaPress(TerminalKeyListener.CTRL_ON, true)
            onInteraction()
        },
        onShiftPress = {
            keyHandler.metaPress(TerminalKeyListener.SHIFT_ON, true)
            onInteraction()
        },
        onEscPress = {
            keyHandler.sendEscape()
            onInteraction()
        },
        onTabPress = {
            keyHandler.sendTab()
            onInteraction()
        },
        onKeyPress = { key ->
            keyHandler.sendPressedKey(key)
            onInteraction()
        },
        onInteraction = onInteraction,
        onHideIme = onHideIme,
        onShowIme = onShowIme,
        onOpenTextInput = onOpenTextInput,
        onScrollInProgressChange = onScrollInProgressChange,
        imeVisible = imeVisible,
        playAnimation = playAnimation,
        bumpyArrows = bumpyArrows,
        rows = rows,
        functionKeyCount = functionKeyCount,
        modifier = modifier,
    )
}

/**
 * Stateless UI component for the terminal keyboard.
 * Separated from [TerminalKeyboard] to enable preview without TerminalBridge dependency.
 */
@Composable
internal fun TerminalKeyboardContent(
    modifierState: ModifierState,
    onCtrlPress: () -> Unit,
    onShiftPress: () -> Unit,
    onEscPress: () -> Unit,
    onTabPress: () -> Unit,
    onKeyPress: (Int) -> Unit,
    onInteraction: () -> Unit,
    onHideIme: () -> Unit,
    onShowIme: () -> Unit,
    onOpenTextInput: () -> Unit,
    onScrollInProgressChange: (Boolean) -> Unit,
    imeVisible: Boolean,
    playAnimation: Boolean,
    bumpyArrows: Boolean,
    modifier: Modifier = Modifier,
    rows: Int = TERMINAL_KEYBOARD_DEFAULT_ROWS,
    functionKeyCount: Int = TERMINAL_KEYBOARD_DEFAULT_FUNCTION_KEYS,
) {
    // One scroll state per possible row: rows that fit are drawn without a scroll and never use
    // theirs, and the rows that overflow scroll on their own instead of dragging the others along.
    val scrollStates = List(TERMINAL_KEYBOARD_MAX_ROWS) { rememberScrollState() }
    val currentOnScrollInProgressChange by rememberUpdatedState(onScrollInProgressChange)
    val view = LocalView.current
    val rowCount = rows.coerceIn(TERMINAL_KEYBOARD_MIN_ROWS, TERMINAL_KEYBOARD_MAX_ROWS)
    val shownFunctionKeys = functionKeyCount.coerceIn(0, TERMINAL_KEYBOARD_MAX_FUNCTION_KEYS)

    if (bumpyArrows) {
        view.isHapticFeedbackEnabled = true
    }

    // Notify parent when scroll state changes
    val scrollInProgress = scrollStates.any { it.isScrollInProgress }
    LaunchedEffect(scrollInProgress) {
        currentOnScrollInProgressChange(scrollInProgress)
    }

    // Ctrl, Shift, Esc and Tab: the keys that qualify what is typed next.
    val modifierKeys: List<TerminalKeyContent> = buildList {
        // Ctrl key (sticky modifier)
        add { keyModifier ->
            ModifierKeyButton(
                text = stringResource(R.string.button_key_ctrl),
                contentDescription = stringResource(R.string.image_description_toggle_control_character),
                modifierLevel = modifierState.ctrlState,
                onClick = onCtrlPress,
                modifier = keyModifier,
            )
        }
        // Shift key (sticky modifier)
        add { keyModifier ->
            ModifierKeyButton(
                text = stringResource(R.string.button_key_shift),
                contentDescription = stringResource(R.string.image_description_toggle_shift_key),
                modifierLevel = modifierState.shiftState,
                onClick = onShiftPress,
                modifier = keyModifier,
            )
        }
        // Esc key
        add { keyModifier ->
            KeyButton(
                text = stringResource(R.string.button_key_esc),
                contentDescription = stringResource(R.string.image_description_send_escape_character),
                onClick = onEscPress,
                modifier = keyModifier,
            )
        }
        // Tab key
        add { keyModifier ->
            KeyButton(
                text = "⇥", // Tab symbol
                contentDescription = stringResource(R.string.image_description_send_tab_character),
                onClick = onTabPress,
                modifier = keyModifier,
            )
        }
    }

    // Arrow keys (repeatable)
    val arrowKeys: List<TerminalKeyContent> = buildList {
        add { keyModifier ->
            RepeatableKeyButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.image_description_up),
                onPress = {
                    onKeyPress(VTermKey.UP)
                    if (bumpyArrows) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                },
                modifier = keyModifier,
            )
        }
        add { keyModifier ->
            RepeatableKeyButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.image_description_down),
                onPress = {
                    onKeyPress(VTermKey.DOWN)
                    if (bumpyArrows) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                },
                modifier = keyModifier,
            )
        }
        add { keyModifier ->
            RepeatableKeyButton(
                icon = Icons.Default.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.image_description_left),
                onPress = {
                    onKeyPress(VTermKey.LEFT)
                    if (bumpyArrows) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                },
                modifier = keyModifier,
            )
        }
        add { keyModifier ->
            RepeatableKeyButton(
                icon = Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.image_description_right),
                onPress = {
                    onKeyPress(VTermKey.RIGHT)
                    if (bumpyArrows) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                },
                modifier = keyModifier,
            )
        }
    }

    // Home/End and Page Up/Down
    val pagingKeys: List<TerminalKeyContent> = buildList {
        add { keyModifier ->
            KeyButton(
                text = stringResource(R.string.button_key_home),
                contentDescription = null,
                onClick = { onKeyPress(VTermKey.HOME) },
                modifier = keyModifier,
            )
        }
        add { keyModifier ->
            KeyButton(
                text = stringResource(R.string.button_key_end),
                contentDescription = null,
                onClick = { onKeyPress(VTermKey.END) },
                modifier = keyModifier,
            )
        }
        add { keyModifier ->
            KeyButton(
                text = stringResource(R.string.button_key_pgup),
                contentDescription = null,
                onClick = { onKeyPress(VTermKey.PAGEUP) },
                modifier = keyModifier,
            )
        }
        add { keyModifier ->
            KeyButton(
                text = stringResource(R.string.button_key_pgdn),
                contentDescription = null,
                onClick = { onKeyPress(VTermKey.PAGEDOWN) },
                modifier = keyModifier,
            )
        }
    }

    val enterKey: TerminalKeyContent = { keyModifier ->
        KeyButton(
            text = stringResource(R.string.button_key_enter),
            contentDescription = null,
            onClick = { onKeyPress(VTermKey.ENTER) },
            modifier = keyModifier,
        )
    }

    val functionKeys: List<TerminalKeyContent> =
        TERMINAL_KEYBOARD_FUNCTION_KEYS.take(shownFunctionKeys).map { (labelRes, key) ->
            val content: TerminalKeyContent = { keyModifier ->
                KeyButton(
                    text = stringResource(labelRes),
                    contentDescription = null,
                    onClick = { onKeyPress(key) },
                    modifier = keyModifier,
                )
            }
            content
        }

    // On three rows the function keys get a row to themselves, the only one that can still
    // overflow, and Enter closes the bottom row so it sits bottom right like on the IME. Fewer
    // rows keep the historical order, with Enter immediately to the left of F1.
    val keyRows: List<List<TerminalKeyContent>> = when {
        rowCount == 1 -> listOf(modifierKeys + arrowKeys + pagingKeys + listOf(enterKey) + functionKeys)
        rowCount == 2 && functionKeys.isEmpty() -> listOf(modifierKeys + pagingKeys, arrowKeys + listOf(enterKey))
        rowCount == 2 -> listOf(modifierKeys + arrowKeys + pagingKeys, listOf(enterKey) + functionKeys)
        functionKeys.isEmpty() -> listOf(modifierKeys, pagingKeys, arrowKeys + listOf(enterKey))
        else -> listOf(modifierKeys + pagingKeys, functionKeys, arrowKeys + listOf(enterKey))
    }

    // Action buttons (always visible). The caller decides their size via the modifier.
    val textInputButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        TerminalKeyboardActionButton(
            icon = Icons.Default.Edit,
            contentDescription = stringResource(R.string.terminal_keyboard_text_input_button),
            onClick = {
                onOpenTextInput()
                onInteraction()
            },
            modifier = buttonModifier,
        )
    }
    val keyboardToggleButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        TerminalKeyboardActionButton(
            icon = if (imeVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
            contentDescription = stringResource(
                if (imeVisible) {
                    R.string.image_description_hide_keyboard
                } else {
                    R.string.image_description_show_keyboard
                },
            ),
            onClick = {
                if (imeVisible) {
                    onHideIme()
                } else {
                    onShowIme()
                }
                onInteraction()
            },
            modifier = buttonModifier,
        )
    }

    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                // Reset timer on any touch interaction
                detectTapGestures(
                    onPress = {
                        onInteraction()
                        tryAwaitRelease()
                    },
                )
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
        tonalElevation = 8.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height((rowCount * TERMINAL_KEYBOARD_HEIGHT_DP).dp),
        ) {
            // The action buttons on the right take one key width per column: two columns on a
            // single row, one column of stacked buttons otherwise
            val actionButtonColumns = if (rowCount == 1) 2 else 1
            val keyAreaWidth = maxWidth - TERMINAL_KEYBOARD_WIDTH_DP.dp * actionButtonColumns
            val overflowingRows = keyRows.indices.filter { index ->
                !rowFitsWidth(keyRows[index].size, keyAreaWidth)
            }

            // Auto-scroll animation on first appearance (only if playAnimation is true), on
            // whichever rows have keys hidden past their right edge
            LaunchedEffect(playAnimation, overflowingRows) {
                if (playAnimation && overflowingRows.isNotEmpty()) {
                    // Wait a moment for layout to complete
                    delay(100)

                    // Scroll all the way to the right to show all keys
                    overflowingRows
                        .map { index -> launch { scrollStates[index].animateScrollToEnd() } }
                        .joinAll()

                    // Then scroll back to the left
                    delay(300)
                    overflowingRows
                        .map { index -> launch { scrollStates[index].animateScrollToStart() } }
                        .joinAll()
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Special keys, split across the configured number of rows
                Column(modifier = Modifier.weight(1f)) {
                    keyRows.forEachIndexed { index, rowKeys ->
                        TerminalKeyRow(
                            keys = rowKeys,
                            // A row that fits stretches its keys, so it needs no scrolling
                            scrollState = if (index in overflowingRows) scrollStates[index] else null,
                        )
                    }
                }

                if (rowCount == 1) {
                    // Single compact row: place the action buttons side by side
                    textInputButton(
                        Modifier.size(
                            width = TERMINAL_KEYBOARD_WIDTH_DP.dp,
                            height = TERMINAL_KEYBOARD_HEIGHT_DP.dp,
                        ),
                    )
                    keyboardToggleButton(
                        Modifier.size(
                            width = TERMINAL_KEYBOARD_WIDTH_DP.dp,
                            height = TERMINAL_KEYBOARD_HEIGHT_DP.dp,
                        ),
                    )
                } else {
                    // Multiple rows: stack the action buttons, splitting the height evenly
                    Column(modifier = Modifier.fillMaxHeight()) {
                        textInputButton(Modifier.width(TERMINAL_KEYBOARD_WIDTH_DP.dp).weight(1f))
                        keyboardToggleButton(Modifier.width(TERMINAL_KEYBOARD_WIDTH_DP.dp).weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * True when [keyCount] keys can share [availableWidth] without any of them getting too narrow to
 * hit. Keys stretch past their natural width when there is room to spare, and squeeze a little
 * below it rather than push the row into a scroll.
 */
private fun rowFitsWidth(keyCount: Int, availableWidth: Dp): Boolean = keyCount > 0 && availableWidth / keyCount >= TERMINAL_KEYBOARD_MIN_WIDTH_DP.dp

private suspend fun ScrollState.animateScrollToEnd() = animateScrollTo(value = maxValue, animationSpec = tween(durationMillis = 500))

private suspend fun ScrollState.animateScrollToStart() = animateScrollTo(value = 0, animationSpec = tween(durationMillis = 500))

/**
 * A single row of special keys. Keys share the whole width when the row fits, so no space is left
 * unused; a row that cannot fit (typically the function keys) keeps them at their natural width
 * and scrolls horizontally on its own.
 */
@Composable
private fun TerminalKeyRow(
    keys: List<TerminalKeyContent>,
    scrollState: ScrollState?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TERMINAL_KEYBOARD_HEIGHT_DP.dp)
            .then(if (scrollState != null) Modifier.horizontalScroll(scrollState) else Modifier),
    ) {
        keys.forEach { key ->
            key(if (scrollState != null) Modifier else Modifier.weight(1f))
        }
    }
}

/**
 * A button for single-press keys (Ctrl, Esc, Tab, Home, End, PgUp, PgDn, F1-F12)
 * Styled to match the old keyboard layout: rectangular 45dp × 30dp with border.
 * The natural size is a default: a [modifier] carrying a width, such as the weight given by a row
 * that stretches its keys, wins over it.
 */
@Composable
private fun KeyButton(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val surfaceModifier = Modifier
        .size(width = TERMINAL_KEYBOARD_WIDTH_DP.dp, height = TERMINAL_KEYBOARD_HEIGHT_DP.dp)
        .then(modifier)

    val content: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    // A squeezed key is only one row tall, so a long label has to stay on one line
                    maxLines = 1,
                    softWrap = false,
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp),
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = backgroundColor,
            content = content,
        )
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = backgroundColor,
            content = content,
        )
    }
}

/**
 * A square action button (text input, keyboard toggle) shown to the right of the keys.
 * The caller supplies the size via [modifier].
 */
@Composable
private fun TerminalKeyboardActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp),
            )
        }
    }
}

/**
 * A button for repeatable keys (arrow keys)
 * Starts repeating after initial delay when held down
 * Styled to match the old keyboard layout: rectangular 45dp × 30dp with border
 */
@Composable
private fun RepeatableKeyButton(
    icon: ImageVector,
    contentDescription: String?,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    // Cleanup on unmount
    DisposableEffect(Unit) {
        onDispose {
            repeatJob?.cancel()
        }
    }

    val backgroundColor =
        if (isPressed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = UI_OPACITY)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY)
        }

    KeyButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = null,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    var sentPress = false

                    // Start a job that handles initial delay, first press, and repeat
                    val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
                    repeatJob = coroutineScope.launch {
                        // Delay before first press to allow scroll gestures to steal touch
                        delay(tapTimeout)
                        if (!isPressed) return@launch

                        // First press after initial tap delay
                        sentPress = true
                        onPress()

                        // Wait before starting repeat
                        delay(500 - tapTimeout)
                        while (isPressed) {
                            sentPress = true
                            onPress()
                            delay(50) // Repeat interval
                        }
                    }

                    // Wait for release - returns true if normal release, false if gesture stolen
                    val released = tryAwaitRelease()
                    isPressed = false

                    if (released && !sentPress) {
                        // User released but key hasn't been sent yet (quick tap) - send it now
                        repeatJob?.cancel()
                        onPress()
                    } else {
                        repeatJob?.cancel()
                    }
                },
            )
        },
        backgroundColor = backgroundColor,
    )
}

@Composable
private fun ModifierKeyButton(
    text: String,
    contentDescription: String?,
    modifierLevel: ModifierLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when (modifierLevel) {
        ModifierLevel.OFF -> MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY)
        ModifierLevel.TRANSIENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ModifierLevel.LOCKED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    }

    val textColor = when (modifierLevel) {
        ModifierLevel.OFF -> MaterialTheme.colorScheme.onSurface
        ModifierLevel.TRANSIENT -> MaterialTheme.colorScheme.onPrimaryContainer
        ModifierLevel.LOCKED -> MaterialTheme.colorScheme.onPrimary
    }

    KeyButton(
        text = text,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        backgroundColor = backgroundColor,
        tint = textColor,
    )
}

@Preview(name = "Terminal Keyboard - Default State", showBackground = true)
@Composable
private fun TerminalKeyboardPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onCtrlPress = {},
            onShiftPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}

@Preview(name = "Terminal Keyboard - Ctrl Pressed", showBackground = true)
@Composable
private fun TerminalKeyboardCtrlPressedPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.TRANSIENT,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onCtrlPress = {},
            onShiftPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}

@Preview(name = "Terminal Keyboard - Ctrl Locked", showBackground = true)
@Composable
private fun TerminalKeyboardCtrlLockedPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.LOCKED,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onCtrlPress = {},
            onShiftPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}

@Preview(name = "Terminal Keyboard - Three Rows", showBackground = true, widthDp = 393)
@Composable
private fun TerminalKeyboardThreeRowsPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onCtrlPress = {},
            onShiftPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
            rows = 3,
        )
    }
}

@Preview(name = "Terminal Keyboard - Three Rows, Two Function Keys", showBackground = true, widthDp = 393)
@Composable
private fun TerminalKeyboardThreeRowsTwoFunctionKeysPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onCtrlPress = {},
            onShiftPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
            rows = 3,
            functionKeyCount = 2,
        )
    }
}

@Preview(name = "Terminal Keyboard - IME Visible", showBackground = true)
@Composable
private fun TerminalKeyboardImeVisiblePreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onCtrlPress = {},
            onShiftPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = true,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}
