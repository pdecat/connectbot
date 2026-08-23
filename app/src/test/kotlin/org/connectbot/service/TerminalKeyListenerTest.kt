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

package org.connectbot.service

import org.connectbot.terminal.VTermKey
import org.junit.Assert.assertEquals
import org.junit.Test

// Terminal modifier bitmasks (per VTerm spec), mirrored from TerminalKeyListener.
private const val VTERM_MOD_SHIFT = 1
private const val VTERM_MOD_CTRL = 4

class TerminalKeyListenerTest {

    private val noopDispatcher = object : KeyDispatcher {
        override fun dispatchKey(modifiers: Int, key: Int) = Unit

        override fun dispatchCharacter(modifiers: Int, character: Int) = Unit
    }

    private class RecordingDispatcher : KeyDispatcher {
        val dispatched = mutableListOf<Pair<Int, Int>>()
        val dispatchedCharacters = mutableListOf<Pair<Int, Int>>()

        override fun dispatchKey(modifiers: Int, key: Int) {
            dispatched += modifiers to key
        }

        override fun dispatchCharacter(modifiers: Int, character: Int) {
            dispatchedCharacters += modifiers to character
        }
    }

    // NONE: sticky is OFF for all modifiers. metaPress only works if forceSticky=true.

    @Test
    fun `NONE ctrl first press does nothing if not forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = false)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl first press goes to TRANSIENT if forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        assertEquals(ModifierLevel.TRANSIENT, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl second press goes to LOCKED if forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl third press goes to OFF if forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `NONE ctrl clearTransients removes TRANSIENT but not LOCKED`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.NONE)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.clearTransients()
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)

        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = true)
        listener.clearTransients()
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    // ALT: alt is sticky, others are not.

    @Test
    fun `ALT alt first press goes to TRANSIENT even if not forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALT)
        listener.metaPress(TerminalKeyListener.ALT_ON, forceSticky = false)
        assertEquals(ModifierLevel.TRANSIENT, listener.getModifierState().altState)
    }

    @Test
    fun `ALT ctrl first press does nothing if not forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALT)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = false)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    // ALL: all modifiers are sticky.

    @Test
    fun `ALL ctrl first press goes to TRANSIENT even if not forced`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.CTRL_ON, forceSticky = false)
        assertEquals(ModifierLevel.TRANSIENT, listener.getModifierState().ctrlState)
    }

    // sendPressedKey/sendTab/sendEscape clear TRANSIENT but preserve LOCKED

    @Test
    fun `sendPressedKey clears TRANSIENT ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.sendPressedKey(0)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `sendPressedKey preserves LOCKED ctrl`() {
        val listener = TerminalKeyListener(noopDispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.sendPressedKey(0)
        assertEquals(ModifierLevel.LOCKED, listener.getModifierState().ctrlState)
    }

    // sendTab carries the active modifiers, so Shift+Tab reaches the terminal as a back-tab.

    @Test
    fun `sendTab without modifiers dispatches a plain tab`() {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.ALL)
        listener.sendTab()
        assertEquals(listOf(0 to VTermKey.TAB), dispatcher.dispatched)
    }

    @Test
    fun `sendTab with shift dispatches a shifted tab`() {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.SHIFT_ON)
        listener.sendTab()
        assertEquals(listOf(VTERM_MOD_SHIFT to VTermKey.TAB), dispatcher.dispatched)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().shiftState)
    }

    // sendBackspace follows the host's DEL key setting, the same split the IME's backspace makes.

    @Test
    fun `sendBackspace dispatches the vterm backspace key by default`() {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.ALL)
        listener.sendBackspace(asCharacter = false)
        assertEquals(listOf(0 to VTermKey.BACKSPACE), dispatcher.dispatched)
        assertEquals(emptyList<Pair<Int, Int>>(), dispatcher.dispatchedCharacters)
    }

    @Test
    fun `sendBackspace dispatches the backspace character when the host asks for it`() {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.ALL)
        listener.sendBackspace(asCharacter = true)
        assertEquals(listOf(0 to 0x08), dispatcher.dispatchedCharacters)
        assertEquals(emptyList<Pair<Int, Int>>(), dispatcher.dispatched)
    }

    @Test
    fun `sendBackspace carries the active modifiers and clears them`() {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.sendBackspace(asCharacter = false)
        assertEquals(listOf(VTERM_MOD_CTRL to VTermKey.BACKSPACE), dispatcher.dispatched)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().ctrlState)
    }

    @Test
    fun `sendEscape without modifiers dispatches a plain escape`() {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.ALL)
        listener.sendEscape()
        assertEquals(listOf(0 to VTermKey.ESCAPE), dispatcher.dispatched)
    }

    @Test
    fun `sendEscape with shift dispatches a shifted escape`() {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.SHIFT_ON)
        listener.sendEscape()
        assertEquals(listOf(VTERM_MOD_SHIFT to VTermKey.ESCAPE), dispatcher.dispatched)
        assertEquals(ModifierLevel.OFF, listener.getModifierState().shiftState)
    }

    @Test
    fun `sendTab with LOCKED ctrl keeps dispatching a ctrl tab`() {
        val dispatcher = RecordingDispatcher()
        val listener = TerminalKeyListener(dispatcher, StickyModifierSetting.ALL)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.metaPress(TerminalKeyListener.CTRL_ON)
        listener.sendTab()
        listener.sendTab()
        assertEquals(
            listOf(VTERM_MOD_CTRL to VTermKey.TAB, VTERM_MOD_CTRL to VTermKey.TAB),
            dispatcher.dispatched,
        )
    }
}
