/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package org.connectbot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.terminal.VTermKey
import org.connectbot.ui.components.TerminalKeyboardContent
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TerminalKeyboardContentTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun terminalKeyboardContent_displaysCoreKeysAndInvokesCallbacks() {
        var ctrlPressed = false
        var shiftPressed = false
        var escapePressed = false
        var tabPressed = false
        var interactionCount = 0
        var textInputOpened = false
        var showImeCalled = false

        setKeyboardContent(
            onCtrlPress = { ctrlPressed = true },
            onShiftPress = { shiftPressed = true },
            onEscPress = { escapePressed = true },
            onTabPress = { tabPressed = true },
            onInteraction = { interactionCount++ },
            onOpenTextInput = { textInputOpened = true },
            onShowIme = { showImeCalled = true },
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_ctrl))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_shift))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_esc))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText("⇥")
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.terminal_keyboard_text_input_button))
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_show_keyboard))
            .performClick()

        assertTrue(ctrlPressed)
        assertTrue(shiftPressed)
        assertTrue(escapePressed)
        assertTrue(tabPressed)
        assertTrue(textInputOpened)
        assertTrue(showImeCalled)
        assertEquals(2, interactionCount)
    }

    @Test
    fun terminalKeyboardContent_imeVisibleInvokesHideKeyboard() {
        var hideImeCalled = false
        var interactionCount = 0

        setKeyboardContent(
            imeVisible = true,
            modifierState = ModifierState(
                ctrlState = ModifierLevel.LOCKED,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onHideIme = { hideImeCalled = true },
            onInteraction = { interactionCount++ },
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_hide_keyboard))
            .assertIsDisplayed()
            .performClick()

        assertTrue(hideImeCalled)
        assertEquals(1, interactionCount)
    }

    @Test
    fun terminalKeyboardContent_arrowAndFunctionKeysInvokeKeyCallback() {
        val pressedKeys = mutableListOf<Int>()

        setKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.TRANSIENT,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onKeyPress = { pressedKeys += it },
            bumpyArrows = true,
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_up))
            .performTouchInput {
                down(center)
                up()
            }
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_f1))
            .performClick()

        assertEquals(listOf(VTermKey.UP, VTermKey.FUNCTION_1), pressedKeys)
    }

    @Test
    @Config(qualifiers = "+w320dp-h640dp")
    fun terminalKeyboardContent_reportsHorizontalScrollInteractions() {
        val scrollStates = mutableListOf<Boolean>()

        // One row on a phone-width screen: far more keys than fit, so the row scrolls
        setKeyboardContent(
            rows = 1,
            onScrollInProgressChange = { scrollStates += it },
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_ctrl))
            .performTouchInput { swipeLeft() }

        assertTrue(scrollStates.isNotEmpty())
    }

    @Test
    fun terminalKeyboardContent_threeRowsEndWithEnterOnStretchedKeys() {
        val pressedKeys = mutableListOf<Int>()

        setKeyboardContent(rows = 3, onKeyPress = { pressedKeys += it })

        val ctrl = composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_ctrl))
            .fetchSemanticsNode()
        val enter = composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_enter))
            .assertIsDisplayed()
        val enterNode = enter.fetchSemanticsNode()

        // Enter closes the bottom row, below the first row that holds Ctrl
        assertTrue(enterNode.positionInRoot.y > ctrl.positionInRoot.y)
        // Both rows fill the width, and the bottom one holds fewer keys, so its keys are wider
        assertTrue(enterNode.size.width > ctrl.size.width)

        enter.performClick()

        assertEquals(listOf(VTermKey.ENTER), pressedKeys)
    }

    @Test
    fun terminalKeyboardContent_threeRowsPutBackspaceLeftOfEnterOnTheLastRow() {
        var backspacePressed = false

        setKeyboardContent(rows = 3, onBackspacePress = { backspacePressed = true })

        val backspace = composeTestRule.onNodeWithText("⌫").assertIsDisplayed()
        val backspaceNode = backspace.fetchSemanticsNode()
        val enterNode = composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_enter))
            .fetchSemanticsNode()

        assertEquals(enterNode.positionInRoot.y, backspaceNode.positionInRoot.y)
        assertTrue(backspaceNode.positionInRoot.x < enterNode.positionInRoot.x)

        backspace.performClick()

        assertTrue(backspacePressed)
    }

    @Test
    fun terminalKeyboardContent_functionKeyCountTrimsTheFunctionRow() {
        setKeyboardContent(rows = 3, functionKeyCount = 2)

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_f2))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_f3))
            .assertDoesNotExist()
    }

    private fun setKeyboardContent(
        modifierState: ModifierState = ModifierState(
            ctrlState = ModifierLevel.OFF,
            altState = ModifierLevel.OFF,
            shiftState = ModifierLevel.OFF,
        ),
        onCtrlPress: () -> Unit = {},
        onShiftPress: () -> Unit = {},
        onEscPress: () -> Unit = {},
        onTabPress: () -> Unit = {},
        onBackspacePress: () -> Unit = {},
        onKeyPress: (Int) -> Unit = {},
        onInteraction: () -> Unit = {},
        onHideIme: () -> Unit = {},
        onShowIme: () -> Unit = {},
        onOpenTextInput: () -> Unit = {},
        onScrollInProgressChange: (Boolean) -> Unit = {},
        imeVisible: Boolean = false,
        bumpyArrows: Boolean = false,
        rows: Int = 2,
        functionKeyCount: Int = 12,
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                TerminalKeyboardContent(
                    modifierState = modifierState,
                    onCtrlPress = onCtrlPress,
                    onShiftPress = onShiftPress,
                    onEscPress = onEscPress,
                    onTabPress = onTabPress,
                    onBackspacePress = onBackspacePress,
                    onKeyPress = onKeyPress,
                    onInteraction = onInteraction,
                    onHideIme = onHideIme,
                    onShowIme = onShowIme,
                    onOpenTextInput = onOpenTextInput,
                    onScrollInProgressChange = onScrollInProgressChange,
                    imeVisible = imeVisible,
                    playAnimation = false,
                    bumpyArrows = bumpyArrows,
                    rows = rows,
                    functionKeyCount = functionKeyCount,
                )
            }
        }
    }
}
