/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package fulguris.keyboard

import fulguris.R
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.view.KeyEvent
import android.view.KeyboardShortcutInfo
import androidx.annotation.RequiresApi
import java.util.ArrayList

/**
 * Define our keyboard shortcuts.
 * Allows us to publish our keyboard shortcuts enabling user quick references using Meta+/.?
 * TODO: Somehow make BrowserActivity use this to trigger actions then make the shortcuts customizable.
 */
@RequiresApi(Build.VERSION_CODES.N)
class Shortcuts(aContext: Context) {

    val iList: ArrayList<KeyboardShortcutInfo> = ArrayList()

    init {

        // NOTE: For some reason KeyboardShortcutInfo with Icon can't be called, no icons then.
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_cycle_tabs_forwards), KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_cycle_tabs_backwards), KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getText(R.string.action_tab_history_forward), KeyEvent.KEYCODE_FORWARD, 0))
        iList.add(KeyboardShortcutInfo(aContext.getText(R.string.action_reload), KeyEvent.KEYCODE_F5, 0))
        iList.add(KeyboardShortcutInfo(aContext.getText(R.string.action_reload), KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getText(R.string.action_focus_address_bar), KeyEvent.KEYCODE_F6, 0))
        iList.add(KeyboardShortcutInfo(aContext.getText(R.string.action_focus_address_bar), KeyEvent.KEYCODE_L, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getText(R.string.action_toggle_status_bar), KeyEvent.KEYCODE_F10, 0))
        iList.add(KeyboardShortcutInfo(aContext.getText(R.string.action_toggle_toolbar), KeyEvent.KEYCODE_F11, 0))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_text_size_decrement), KeyEvent.KEYCODE_MINUS, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_text_size_small_decrement), KeyEvent.KEYCODE_MINUS, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_text_size_increment), KeyEvent.KEYCODE_EQUALS, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_text_size_small_increment), KeyEvent.KEYCODE_EQUALS, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_add_bookmark), KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_open_bookmark_list), KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_open_tab_list), KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON ))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_open_tab_list), KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_new_tab), KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_duplicate_tab), KeyEvent.KEYCODE_D, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_open_session_list), KeyEvent.KEYCODE_S, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.exit), KeyEvent.KEYCODE_Q, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_find), KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_find_next), KeyEvent.KEYCODE_F3, 0))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_find_previous), KeyEvent.KEYCODE_F3, KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_find_selection), KeyEvent.KEYCODE_F3, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.close_tab), KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.close_tab), KeyEvent.KEYCODE_F4, KeyEvent.META_CTRL_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_switch_to_session), KeyEvent.KEYCODE_1, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_switch_to_last_session), KeyEvent.KEYCODE_0, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_switch_to_tab), KeyEvent.KEYCODE_1, KeyEvent.META_CTRL_ON ))
        iList.add(KeyboardShortcutInfo(aContext.getString(R.string.action_switch_to_last_tab), KeyEvent.KEYCODE_0, KeyEvent.META_CTRL_ON ))
    }

}