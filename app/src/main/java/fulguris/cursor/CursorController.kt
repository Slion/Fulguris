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

package fulguris.cursor

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import java.lang.reflect.Method
import kotlin.math.abs
import timber.log.Timber

/**
 * Owns the whole on-screen cursor: its position and velocity, the frame loop that moves it from
 * D-pad and analog-joystick input, and the construction/dispatch of synthetic pointer
 * [MotionEvent]s into the target view.
 *
 * Web pages get real `:hover` / `mouseover` / `mouseout` from a well-formed SOURCE_MOUSE hover
 * sequence — one `ACTION_HOVER_ENTER` per target, then `ACTION_HOVER_MOVE` events as the cursor
 * moves, and a matching `ACTION_HOVER_EXIT` when it leaves — because some WebView builds (Android
 * 13) drop a MOVE that arrives without a prior ENTER, while newer ones (Android 16) accept a bare
 * MOVE. The click is a precise touch DOWN→MOVE→UP at the cursor coordinate (synthetic mouse
 * *button* events can't be turned into a page click through the public API on Android WebView —
 * see [dispatchClick]). Reaching a WebView edge dispatches a
 * synthetic mouse **wheel** ([MotionEvent.ACTION_SCROLL]) at the cursor point so whichever DOM
 * element is under the cursor (including nested scrollable panels) scrolls, like a real mouse wheel.
 *
 * ## Two independent ways to drive the cursor
 *  - **Cursor on** ([enabled]): toggled with the hotkey / menu. While on, the **D-pad** moves the
 *    cursor and the select button clicks. This is the path for D-pad-only remotes and single-stick
 *    joysticks, where the D-pad would otherwise do focus navigation. One exception: a D-pad key
 *    that comes from a *two-stick* gamepad is always yielded back to focus navigation — on such a
 *    device the right stick (below) is the cursor's intended driver, so the D-pad keeps its normal
 *    role even with the cursor on.
 *  - **Right analog stick** ([onGenericMotionEvent]): on a two-stick gamepad the right stick moves
 *    the cursor at any time, *without* toggling the cursor on — the left stick still scrolls and
 *    the D-pad still does focus navigation (including while the cursor is on). The select button
 *    clicks whenever the cursor is [shown].
 *
 * The cursor fades out after [CursorSettings.fadeTimeoutSec] seconds of no movement and fades back in on any
 * movement.
 *
 * ## Movement is physical, not pixel-based
 * Speed and acceleration are expressed in cm/s and cm/s² and converted to pixels using the display's
 * DPI ([pxPerCm]), so a given setting feels comparable regardless of screen resolution / size.
 *
 * ## Boundary
 * The controller is deliberately decoupled from the browser activity. It only knows about:
 *  - [overlay]: the [CursorView] it renders into (and whose bounds it clamps to);
 *  - [targetProvider]: a way to fetch the view to dispatch into (the current WebView, or the
 *    fullscreen custom view while an HTML5 video is fullscreen), re-queried on every dispatch;
 *  - [uiRootProvider]: the root of the browser's own UI (toolbar, tab bar, drawers…). While the
 *    cursor hotspot lies over one of those views (rather than over the web content) a click or
 *    long press is delivered to that view — the toolbar buttons, the address field, the tab bar —
 *    instead of the web page; see [clickUiView] and [isUiPoint]. Wheel scrolling is always the
 *    web page's job ([dispatchScroll]);
 *  - [settings]: [CursorSettings] for the hotkey / speed / acceleration / fade timeout;
 *  - [onCursorToggled]: notified when the cursor is toggled on/off (the activity shows feedback and moves focus).
 *
 * The activity forwards [dispatchKeyEvent] / [onGenericMotionEvent], adds (and, for fullscreen,
 * re-parents) the overlay view, and provides the target — nothing else. This keeps the component
 * reusable / lib-extractable.
 *
 * ## Toggle hotkey
 * A **long press** of [KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE] (held for [HOTKEY_LONG_PRESS_MS]) toggles
 * the cursor on/off; we detect it ourselves (media keys don't reliably deliver system long-press). A
 * **short** press is yielded back to the activity (returns false) so it can play/pause the page's
 * video. The activity forwards the key to us before it can reach a page's `MediaSession`.
 *
 * ## Wheel scroll: media keys and gamepad shoulder buttons
 * While the cursor is on screen, [KeyEvent.KEYCODE_MEDIA_FAST_FORWARD] / [KeyEvent.KEYCODE_MEDIA_REWIND]
 * (remote) and [KeyEvent.KEYCODE_BUTTON_L1] / [KeyEvent.KEYCODE_BUTTON_R1] (gamepad LB / RB) dispatch a
 * synthetic mouse wheel scroll up / down at the cursor point (see [dispatchScroll]); when the cursor is
 * off, the media keys fall through to the activity's per-video seek (LB / RB are not used by the
 * browser and are simply ignored off-cursor). A standard gamepad has no media keys, so the shoulder
 * buttons are its wheel — they are discrete and repeatable, exactly like wheel notches, while the
 * left stick keeps doing its coarse native WebView scroll.
 *
 * ## Context menu via the action key
 * While the cursor is on screen, a **deliberate long press** of the action key
 * ([KeyEvent.KEYCODE_DPAD_CENTER] / [KeyEvent.KEYCODE_ENTER] / [KeyEvent.KEYCODE_BUTTON_A] —
 * see [isConfirmKey], held for [settings.actionHoldSec], user-configurable between 0.5 and
 * 1 s) performs a long press at the cursor and so
 * opens the WebView's built-in context menu for the element under it (the same menu a touch long
 * press would open). A **short** press is the normal click at the cursor: the click is deferred to
 * the key UP so a held press can still be reclassified as a long press while it is held.
 *
 * The threshold is deliberately ~1 s and *not* the system long-press timeout: a human "short
 * click" on a remote is routinely held 400-700 ms — past the ~400 ms at which the OS starts
 * flagging the key with [KeyEvent.FLAG_LONG_PRESS] — and the system flag must therefore be
 * ignored here, or hesitant clicks would open the context menu instead of clicking.
 *
 * With the cursor off the action key falls through to its normal meaning. The one case where it
 * is yielded to the focused control even though the cursor is on screen is the **passive
 * ghost** state: the cursor is visible only because the right stick moved it (`shown`, not
 * `enabled`) and the **web content is not focused** ([webContentFocusedProvider]) — e.g. focus
 * is on a toolbar widget or a menu — so the key goes to that widget instead of clicking the
 * page under the cursor. When cursor mode is explicitly ON (`enabled`), the cursor is the user's
 * active input and the action key ALWAYS acts at the cursor, even if Android focus is stranded
 * on a toolbar widget (which is exactly what happens after toggling the cursor off — the focus
 * moves to the menu button — and back on). In HTML5 fullscreen the provider reports the web
 * content as focused (the tab itself is INVISIBLE while the custom fullscreen view is shown), so
 * the cursor keeps its click there.
 */
class CursorController(
    private val overlay: CursorView,
    private val targetProvider: () -> View?,
    private val settings: CursorSettings,
    private val onCursorToggled: (enabled: Boolean) -> Unit,
    // Root of the browser's own UI (the whole activity content: toolbar + web area + drawers).
    // Used to hit-test whether the cursor is over a UI control (toolbar button, address field,
    // tab bar, …) so clicks reach it instead of the web page (see [isUiPoint] / [clickUiView]).
    // Null (default) disables UI clicks — the cursor only ever acts on the web page.
    private val uiRootProvider: (() -> View?)? = null,
    // Whether the web content currently "holds focus": the current tab's WebView has input
    // focus, OR an HTML5 fullscreen custom view is up (the tab is INVISIBLE then, so the
    // WebView's focus is stripped — the fullscreen view counts as web content). Re-queried on
    // every key event. Only the passive ghost (shown, not [enabled]) is affected by it: the
    // confirm key is yielded to the focused control when the ghost hovers over web content that
    // does not hold focus (see [dispatchKeyEvent]). An enabled cursor always acts at the cursor.
    private val webContentFocusedProvider: () -> Boolean = { true },
    // The bounds of the WEB CONTENT region (the container that holds the page), used to decide
    // whether the cursor point is over the web content or over the browser's own UI (see
    // [isUiPoint]). This is NOT necessarily [targetProvider]: the WebView itself can extend
    // beyond its container (it reaches up behind the status bar on some devices), so testing
    // against the WebView's own bounds would misclassify the toolbar as web content. The
    // container (the browser's web area, below the toolbar) is the geometrically correct
    // boundary. Defaults to [targetProvider] so existing wiring is unaffected.
    private val contentBoundsProvider: () -> View? = targetProvider,
) {

    // Cursor on: D-pad drives the cursor and select clicks. Independent of [shown].
    var enabled: Boolean = false
        private set

    // Whether the cursor is temporarily suspended because the activity is showing an overlay that
    // must own the input (main menu, sessions menu, a dialog, a bottom sheet…). While suspended the
    // overlay is hidden and every input event is yielded (returned unconsumed) so it reaches the
    // overlay's own focus navigation. [suspendCursor] / [resumeCursor] are called by the activity
    // as those overlays open and close; a non-zero [suspendCount] means at least one such overlay
    // is up (they can nest, e.g. a menu over a sheet).
    private var suspended = false
    private var suspendCount = 0

    // Whether the cursor overlay is currently faded in (visible). Driven by the cursor being on OR
    // the right stick, and cleared by the fade-out timeout.
    private var shown = false

    // Whether the cursor has ever been placed (so re-enabling doesn't recenter a right-stick cursor).
    private var positioned = false

    // The browser-UI view that currently has the synthetic hover (a toolbar button, …). Tracked so a
    // HOVER_EXIT can be sent when the cursor leaves it (moves to another control, back to the page,
    // or the cursor is suspended / disabled) — otherwise its hover highlight would get stuck on.
    private var hoveredUiView: View? = null

    // The WEB target that currently has the synthetic hover (an ENTER has been delivered to it). A
    // well-formed mouse hover is ENTER -> MOVE* -> EXIT: newer WebView builds (Android 16) accept a
    // bare HOVER_MOVE (synthesizing the pointer-over themselves), but older ones (Android 13) drop a
    // MOVE that arrived without a prior ENTER, so the page never sees :hover / mouseover at all. The
    // ENTER is therefore sent once per target (re-sent after a target change or a return from a UI
    // control) and the matching EXIT when the cursor leaves the web content or goes away.
    private var hoveredWebTarget: View? = null

    // Logical cursor position, in overlay-local pixels.
    private var posX = 0f
    private var posY = 0f

    // Active D-pad directions (each -1, 0 or +1). Only set while the cursor is on.
    private var keyDx = 0
    private var keyDy = 0

    // Right-stick displacement after deadzone, -1..1. Drives the cursor regardless of whether the cursor is on.
    private var rsX = 0f
    private var rsY = 0f

    // When the current continuous-movement gesture started, for the acceleration ramp.
    private var moveStartMs = 0L

    // Last known overlay size. Cached so movement still works while the overlay is faded out (GONE),
    // when its measured width/height read back as 0.
    private var boundsX = 0f
    private var boundsY = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private var looping = false
    private var lastFrameNs = 0L

    // --- Toggle hotkey state ------------------------------------------------

    private var hotkeyDownHandled = false
    private val hotkeyLongPress = Runnable {
        hotkeyDownHandled = true
        toggle()
    }

    // --- Context-menu (action-key long press) state --------------------------
    // A short press of the action key is a click at the cursor (fired on the key UP); a deliberate
    // hold of [actionLongPressMs] (user setting, clamped to at least 500 ms) opens the context
    // menu. The DOWN arms the timer, the UP
    // resolves the press: if the timer already fired for this press, the UP is consumed. The OS's
    // own long-press flag (raised after the ~400 ms system timeout) is deliberately NOT honored
    // here — a human "short click" is routinely held that long, and acting on it would open the
    // menu instead of clicking.

    private var actionLongPressHandled = false
    // Deliberate-hold threshold for the context menu, from the user setting (seconds, clamped so
    // it stays well past the ~400 ms system long-press flag — see the class KDoc), in ms.
    private val actionLongPressMs: Long
        get() = (settings.actionHoldSec.coerceAtLeast(settings.minActionHoldSec) * 1000f).toLong()
    private val actionLongPress = Runnable {
        actionLongPressHandled = true
        dispatchLongPress()
    }

    // Diagnostic: accumulates the raw action-key event sequence of the current press
    // ("d23(r0)" = DOWN keycode 23 repeat 0, "u23" = UP ...) and logs it when the press
    // resolves on UP. This makes a misbehaving remote's event shape (a duplicated DOWN or
    // extra UP for one physical press, e.g. some Bluetooth remotes) visible in logcat.
    private val actionPressEvents = mutableListOf<String>()

    // --- Fade state ---------------------------------------------------------

    private val fadeRunnable = Runnable { hideCursor() }

    // ------------------------------------------------------------------------

    /**
     * Forward the activity's key events here first. Returns true when consumed.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // While a menu / dialog / bottom sheet is up the cursor is suspended and must not eat any
        // input: yield EVERYTHING (including the toggle hotkey) so it reaches the overlay's own
        // focus navigation / back handling. The hotkey must not toggle the cursor back on while a
        // menu is open.
        if (suspended) return false

        // The toggle hotkey is a long press of play/pause, handled whether or not the cursor is
        // currently on. A short press is yielded back (returns false) so the activity can play/pause
        // the page's video.
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            return handleHotkey(event)
        }

        // While the cursor is on screen, the wheel keys become a mouse wheel scroll at the cursor:
        // fast-forward / LB scroll up, rewind / RB scroll down. Off-cursor the media keys fall
        // through to the activity's video seek; LB / RB have no browser role and are ignored.
        val wheelNotches = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_BUTTON_L1 -> WHEEL_NOTCHES
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_BUTTON_R1 -> -WHEEL_NOTCHES
            else -> 0f
        }
        if ((enabled || shown) && wheelNotches != 0f) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                wakeCursor()
                Timber.d("Cursor: wheel ${event.keyCode} -> ${wheelNotches} notches (enabled=$enabled shown=$shown)")
                dispatchScroll(wheelNotches, 0f)
            }
            return true
        }

        // The action key (select / DPAD center / ENTER / BUTTON_A) drives the primary interaction
        // whenever the cursor is on screen — whether it got there via the hotkey toggle or the right
        // stick. A short press is a click at the cursor; a long press performs a long press there
        // and so opens the WebView's context menu for the element under the cursor. The click
        // fires on ACTION_UP, so a DOWN arms the long-press timer and the UP resolves the press.
        if (isConfirmKey(event.keyCode) && (enabled || shown)) {
            // The cursor's POSITION decides the confirm key's target:
            //  - Over the browser's own UI (toolbar button, address field, tab bar, …) the cursor
            //    is in charge — it activates whatever control is under it, REGARDLESS of which
            //    widget holds input focus. (Otherwise a stray focus on one toolbar button would
            //    steal the click from the button the cursor is actually pointing at.) This works
            //    whether or not the web content is focused.
            //  - Over the web content while that content is NOT focused AND the cursor is a
            //    passive ghost (shown only by the right stick, never explicitly turned on),
            //    the key is YIELDED to whatever widget holds focus (a toolbar button, a
            //    menu, …) instead of clicking the page under the cursor.
            //  - Over the web content while it IS focused, the key clicks the page at the
            //    cursor.
            // The yield is ONLY for the ghost case (shown, not enabled): when cursor mode is
            // explicitly ON, the cursor is the user's active input even if Android focus is
            // stranded on a toolbar widget (e.g. it moved to the menu button when the cursor
            // was last turned off) — yielding there would send the select press to that
            // widget (opening the browser menu) instead of clicking the page under the
            // cursor. Enabling the cursor never moves Android focus, so that stranded-focus
            // state is exactly the one users hit every time they toggle off -> on.
            // (HTML5 fullscreen counts as focused web content — see webContentFocusedProvider. A
            //  menu / dialog / sheet is suspended, so this branch is never reached with one up.)
            if (!enabled && !webContentFocusedProvider() && !isUiPoint()) {
                Timber.d("Cursor: yielding confirm key ${event.keyCode} (ghost over web, web content not focused)")
                return false
            }
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    actionPressEvents += "d${event.keyCode}(r${event.repeatCount})"
                    if (actionLongPressHandled) return true // already fired for this press
                    if (event.repeatCount == 0) {
                        handler.removeCallbacks(actionLongPress)
                        handler.postDelayed(actionLongPress, actionLongPressMs)
                    }
                    // Note: we intentionally do NOT react to event.isLongPress here — the OS
                    // raises it after the ~400 ms system timeout, and a human "short click" is
                    // routinely held that long (see the class KDoc). Only the deliberate
                    // actionLongPressMs hold counts.
                }
                KeyEvent.ACTION_UP -> {
                    actionPressEvents += "u${event.keyCode}"
                    handler.removeCallbacks(actionLongPress)
                    val longPressed = actionLongPressHandled
                    actionLongPressHandled = false
                    Timber.d("Cursor: action-key press resolved longPress=$longPressed events=[${actionPressEvents.joinToString("")}]")
                    actionPressEvents.clear()
                    if (!longPressed) dispatchClick()
                }
            }
            return true
        }

        // The D-pad only drives the cursor while the cursor is explicitly on; otherwise it
        // must fall through to normal focus navigation.
        if (!enabled) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // A directional key coming from a *two-stick* gamepad is yielded back even with
                // the cursor on: on such a device the right analog stick is the cursor's
                // intended driver (it works at any time, cursor on or off), so the D-pad keeps
                // its normal role (focus navigation) rather than also steering the cursor.
                // D-pad keys from a D-pad-only remote — or from adb's virtual keyboard, which
                // has no right stick — still drive the cursor.
                if (hasRightStick(event.device)) {
                    Timber.d("Cursor: yielding D-pad ${event.keyCode} from a two-stick gamepad")
                    return false
                }
                handleDirectionKey(event)
                true
            }
            else -> false
        }
    }

    private fun isConfirmKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A -> true
        else -> false
    }

    /**
     * Forward the activity's generic motion events here. The **right analog stick** drives the
     * cursor at any time (no cursor toggle needed) on two-stick gamepads. Always returns false
     * (non-consuming) so the left stick's scroll and D-pad focus navigation are left untouched — the
     * right stick's Z/RZ axes aren't used by either of those.
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // While a menu / dialog / bottom sheet is up the cursor is suspended; yield the stick too.
        if (suspended) return false
        if (event.source and InputDevice.SOURCE_CLASS_JOYSTICK == 0) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false
        // Only if the device actually has a centered right stick (exclude devices that report
        // triggers on Z/RZ, whose range is 0..1 rather than -1..1).
        if (!hasRightStick(event.device)) return false

        rsX = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Z))
        rsY = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_RZ))
        if (rsX != 0f || rsY != 0f) {
            if (!hasMovementInput(exceptRightStick = true)) moveStartMs = SystemClock.uptimeMillis()
            wakeCursor()
            startLoop()
        } else {
            maybeStopLoop()
        }
        return false
    }

    /** Toggle the cursor on/off. Safe to call from the menu or the hotkey. */
    fun toggle() {
        if (enabled) disable() else enable()
    }

    /**
     * Suspend the cursor for the lifetime of an input-owning overlay (a menu, dialog or bottom
     * sheet the activity is showing). The overlay is hidden and all input is yielded to the
     * overlay; call [resumeCursor] when it closes. Balanced calls are expected (the activity
     * suspends once per overlay-shown and resumes once per overlay-hidden). Unlike [disable] this
     * does NOT notify [onCursorToggled] (no toast / focus change) — the user's cursor setting is
     * preserved, it is merely paused while the overlay is up.
     */
    fun suspendCursor() {
        if (suspended) {
            // Already suspended (nested overlay); just track the extra level.
            suspendCount++
            return
        }
        suspendCount = 1
        suspended = true
        Timber.d("Cursor: suspend (overlay up, enabled=$enabled)")
        keyDx = 0; keyDy = 0
        rsX = 0f; rsY = 0f
        stopLoop()
        handler.removeCallbacks(fadeRunnable)
        // The cursor is leaving the screen (an overlay owns the input now); clear any hover it had
        // placed on a toolbar control so its highlight doesn't linger under the menu.
        clearUiHover()
        if (shown) {
            shown = false
            overlay.visibility = View.GONE
        }
    }

    /**
     * Counterpart to [suspendCursor]: re-show the cursor if it is on (restoring from the CURRENT
     * [enabled] state, so a toggle made from within the overlay is honored). Does not call
     * [enable]/[disable] itself — those fire [onCursorToggled] (toast / focus) which already ran
     * at the moment the user toggled.
     */
    fun resumeCursor() {
        if (!suspended) return
        if (--suspendCount > 0) return
        suspended = false
        Timber.d("Cursor: resume (overlay closed, enabled=$enabled)")
        if (enabled) {
            overlay.visibility = View.VISIBLE
            overlay.post {
                // Center it if it was never placed while hidden (enable's own centering is skipped
                // while the overlay is GONE, when its measured size reads 0).
                if (!positioned && overlay.maxX > 0f && overlay.maxY > 0f) {
                    posX = overlay.maxX / 2f
                    posY = overlay.maxY / 2f
                    positioned = true
                    overlay.setPosition(posX, posY)
                }
                wakeCursor()
                dispatchHover()
            }
        }
    }

    /** Whether the cursor is currently suspended by an open overlay (menus / dialogs / sheets). */
    fun isSuspended(): Boolean = suspended

    fun enable() {
        if (enabled) return
        enabled = true
        Timber.d("Cursor: enable")
        // Make the overlay visible now so it gets laid out before we read its size to center.
        // While suspended (a menu / dialog / sheet is up) it stays hidden until resumeCursor.
        if (!suspended) overlay.visibility = View.VISIBLE
        overlay.post {
            // Center the cursor the first time it appears; keep its place if the right stick already
            // positioned it, so toggling the cursor on doesn't make it jump.
            if (!positioned && overlay.maxX > 0f && overlay.maxY > 0f) {
                posX = overlay.maxX / 2f
                posY = overlay.maxY / 2f
                positioned = true
                overlay.setPosition(posX, posY)
                Timber.d("Cursor: centered at ($posX, $posY) in overlay ${overlay.maxX}x${overlay.maxY}")
            }
            wakeCursor()
            dispatchHover()
        }
        onCursorToggled(true)
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        Timber.d("Cursor: disable")
        keyDx = 0; keyDy = 0
        // The cursor is going away: clear any synthetic hover on a browser-UI control (a toolbar
        // button, …) so its :hover highlight doesn't get stuck on.
        clearUiHover()
        // Keep the right stick able to move it; if nothing is driving it, let it hide.
        if (!hasMovementInput()) {
            hideCursor()
            stopLoop()
        }
        onCursorToggled(false)
    }

    /** Detach lifecycle hooks; call from the activity's onDestroy. */
    fun release() {
        handler.removeCallbacks(hotkeyLongPress)
        handler.removeCallbacks(actionLongPress)
        handler.removeCallbacks(fadeRunnable)
        stopLoop()
    }

    // --- Hotkey -------------------------------------------------------------

    private fun handleHotkey(event: KeyEvent): Boolean {
        if (!settings.hotkeyEnabled) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (hotkeyDownHandled) return true // already toggled for this press
                if (event.repeatCount == 0) {
                    handler.removeCallbacks(hotkeyLongPress)
                    handler.postDelayed(hotkeyLongPress, HOTKEY_LONG_PRESS_MS)
                }
                // Some remotes (and `adb shell input keyevent --longpress`) also deliver a system
                // long-press event for media keys; honor it as a secondary trigger. Our own timer
                // above remains the primary, reliable path.
                if (event.isLongPress) {
                    handler.removeCallbacks(hotkeyLongPress)
                    hotkeyDownHandled = true
                    toggle()
                }
                // Consume DOWN so it never reaches a page MediaSession while we time the long press.
                return true
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(hotkeyLongPress)
                val toggled = hotkeyDownHandled
                hotkeyDownHandled = false
                // A completed long press (toggle) is consumed; a short press is yielded back to the
                // activity (returns false) so it can seek the page's video.
                return toggled
            }
        }
        return true
    }

    // --- Movement -----------------------------------------------------------

    private fun handleDirectionKey(event: KeyEvent) {
        val (ax, ay) = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
            else -> 0 to 0
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val wasIdle = !hasMovementInput()
            if (ax != 0) keyDx = ax
            if (ay != 0) keyDy = ay
            if (wasIdle) moveStartMs = SystemClock.uptimeMillis()
            // Move a fixed physical step on the initial press so a single tap (which releases almost
            // instantly, e.g. a discrete remote press) always nudges the cursor; the frame loop then
            // adds continuous, accelerating movement while the key stays held. Only on repeatCount 0
            // so auto-repeat DOWNs don't double up with the loop.
            if (event.repeatCount == 0) {
                val (pxCmX, pxCmY) = pxPerCm()
                val stepCm = STEP_MIN_CM + (settings.speed.coerceIn(1f, 100f) / 100f) * (STEP_MAX_CM - STEP_MIN_CM)
                moveBy(ax * stepCm * pxCmX, ay * stepCm * pxCmY)
            }
            startLoop()
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (ax != 0 && keyDx == ax) keyDx = 0
            if (ay != 0 && keyDy == ay) keyDy = 0
            if (!hasMovementInput()) maybeStopLoop()
        }
    }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNs -> onFrame(frameTimeNs) }

    private fun startLoop() {
        if (looping) return
        looping = true
        lastFrameNs = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        looping = false
        choreographer.removeFrameCallback(frameCallback)
    }

    private fun maybeStopLoop() {
        if (!hasMovementInput()) stopLoop()
    }

    private fun hasMovementInput(exceptRightStick: Boolean = false): Boolean {
        if (keyDx != 0 || keyDy != 0) return true
        if (!exceptRightStick && (rsX != 0f || rsY != 0f)) return true
        return false
    }

    private fun onFrame(frameTimeNs: Long) {
        if (!looping) return
        val dt = if (lastFrameNs == 0L) 0f else (frameTimeNs - lastFrameNs) / 1_000_000_000f
        lastFrameNs = frameTimeNs

        if (hasMovementInput()) {
            // Keep the cursor awake while it is actively moving (a held stick may not emit new
            // motion events, so we reset the fade timer here rather than only on input events).
            wakeCursor()

            val (pxCmX, pxCmY) = pxPerCm()
            val baseCmS = baseSpeedCmPerSec()
            val accelCmS2 = accelCmPerSec2()
            val held = (SystemClock.uptimeMillis() - moveStartMs) / 1000f
            val speedCmS = (baseCmS + accelCmS2 * held).coerceAtMost(baseCmS * MAX_SPEED_MULT)

            // D-pad contributes ±1 per axis; right stick contributes its analog displacement.
            val dirX = (keyDx + rsX).coerceIn(-1.5f, 1.5f)
            val dirY = (keyDy + rsY).coerceIn(-1.5f, 1.5f)
            val dxPx = dirX * speedCmS * pxCmX * dt
            val dyPx = dirY * speedCmS * pxCmY * dt
            if (dxPx != 0f || dyPx != 0f) moveBy(dxPx, dyPx)
        }

        if (looping) choreographer.postFrameCallback(frameCallback)
    }

    private fun moveBy(dx: Float, dy: Float) {
        // Keep the last real size so movement still works once the cursor has faded out (GONE),
        // whose measured size reads back as 0.
        if (overlay.maxX > 0f) boundsX = overlay.maxX
        if (overlay.maxY > 0f) boundsY = overlay.maxY
        val maxX = boundsX
        val maxY = boundsY
        if (maxX <= 0f || maxY <= 0f) return

        // First movement (e.g. from the right stick before the cursor was ever toggled on) starts
        // from the center rather than the top-left corner.
        if (!positioned) {
            posX = maxX / 2f
            posY = maxY / 2f
            positioned = true
        }

        var nx = posX + dx
        var ny = posY + dy

        // At an edge, keep pushing translates into a mouse-wheel scroll at the cursor point (so a
        // nested scrollable region under the cursor scrolls); the cursor itself stays clamped.
        var overflowX = 0f
        var overflowY = 0f
        if (nx < 0f) { overflowX = nx; nx = 0f }
        else if (nx > maxX) { overflowX = nx - maxX; nx = maxX }
        if (ny < 0f) { overflowY = ny; ny = 0f }
        else if (ny > maxY) { overflowY = ny - maxY; ny = maxY }

        posX = nx
        posY = ny
        positioned = true
        overlay.setPosition(posX, posY)
        // Any movement makes the cursor visible and restarts its fade-out countdown.
        wakeCursor()
        dispatchHover()

        // Wheel "notches": pushing past a screen edge scrolls the content the same way a real
        // wheel does. The overlay now spans the whole activity (toolbar included), so the cursor
        // reaches the screen's top edge while still over the toolbar — that is exactly where
        // scroll-up is triggered, so this must NOT be gated on the cursor being over the web
        // content (gating it would silently disable scrolling the page up).
        if (overflowX != 0f || overflowY != 0f) {
            dispatchScroll(-overflowY / SCROLL_PX_PER_NOTCH, -overflowX / SCROLL_PX_PER_NOTCH)
        }
    }

    // --- UI hit-testing (toolbar, tab bar, drawers) ---------------------------
    //
    // The overlay spans the whole activity (so the cursor is usable over the toolbar, not just the
    // web view). The web content is exactly [targetProvider]; everything else under the overlay —
    // the toolbar, the address field, the tab bar, the drawers, the find-in-page bar — is the
    // browser's own UI. So "is the cursor over the UI?" reduces to "is the cursor's point OUTSIDE
    // the web target's bounds?": outside, a click activates the UI control under it (and hover /
    // wheel are withheld from the page); inside, everything goes to the page as before.

    /**
     * True when the cursor's point lies over the browser's own UI (toolbar, tab bar, drawers, …)
     * rather than over the web content. When there is no web target at all (e.g. no tab) any
     * point is treated as UI.
     *
     * The boundary is the WEB CONTENT CONTAINER's bounds ([contentBoundsProvider]), not the raw
     * target's: the WebView's layout can extend beyond its container (it reaches up behind the
     * status bar on some devices), so testing against the WebView's own bounds would misclassify
     * the toolbar as web content. The container (the browser's web area, below the toolbar) is the
     * geometrically correct boundary.
     */
    private fun isUiPoint(): Boolean {
        val target = targetProvider() ?: return true
        val content = contentBoundsProvider() ?: target
        val (x, y) = targetCoords(content)
        return x < 0f || x > content.width.toFloat() || y < 0f || y > content.height.toFloat()
    }

    /**
     * Activate the browser UI control under the cursor with a synthetic tap.
     *
     * The overlay is the TOP-MOST child of the root, so a plain `root.dispatchTouchEvent(...)`
     * would hit-test straight into the (non-clickable) overlay and stop — the control *beneath*
     * it (a toolbar button, the address field, …) would never see the touch. Instead we find the
     * deepest *interactive* view under the cursor ourselves ([hitTestUi]) — walking the tree but
     * skipping the overlay — and dispatch the DOWN→MOVE→UP straight to THAT view, in its local
     * coordinates. Dispatching to the leaf (rather than the root) is what makes the framework run
     * its normal press state machine + `View.performClick()` on UP: a toolbar button fires its
     * click listener, the address field gains focus.
     */
    private fun clickUiView() {
        val root = uiRootProvider?.invoke() ?: return
        val (rx, ry) = targetCoords(root)
        val target = hitTestUi(root, rx, ry) ?: run {
            Timber.d("Cursor: no interactive UI view under ($rx, $ry)")
            return
        }
        val (x, y) = targetCoords(target)
        Timber.d("Cursor: tap UI ${target} at ($x, $y)")
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, x + 2f, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 60, MotionEvent.ACTION_UP, x, y, 0)
        try {
            target.dispatchTouchEvent(down)
            target.dispatchTouchEvent(move)
            target.dispatchTouchEvent(up)
        } finally {
            down.recycle(); move.recycle(); up.recycle()
        }
    }

    /**
     * Depth-first hit test for the deepest view that is visible, enabled, laid out and actually
     * interactive (clickable / long-clickable / focusable) at the point `(x, y)` in [root]'s
     * coordinate space. The cursor [overlay] is skipped so a tap can pass through it to the
     * control underneath. Returns null when nothing interactive is under the point.
     */
    private fun hitTestUi(v: View, x: Float, y: Float): View? {
        if (v === overlay) return null
        if (v.visibility != View.VISIBLE || !v.isEnabled) return null
        if (v.width == 0 || v.height == 0) return null
        if (x < 0f || x > v.width || y < 0f || y > v.height) return null
        if (v is ViewGroup) {
            // Children are drawn (and laid out) bottom-up, so walk top-down to reach the
            // topmost (last) child first.
            for (i in v.childCount - 1 downTo 0) {
                val c = v.getChildAt(i)
                val hit = hitTestUi(c, x - c.x, y - c.y)
                if (hit != null) return hit
            }
            return null
        }
        return if (v.isClickable || v.isLongClickable || v.isFocusable) v else null
    }

    // --- Synthetic pointer events -------------------------------------------

    /** Map the overlay-local cursor position into the target view's coordinate space. */
    private fun targetCoords(target: View): Pair<Float, Float> {
        val t = IntArray(2); target.getLocationOnScreen(t)
        val o = IntArray(2); overlay.getLocationOnScreen(o)
        return (posX + o[0] - t[0]) to (posY + o[1] - t[1])
    }

    private fun dispatchHover(xOffset: Float = 0f) {
        val target = targetProvider() ?: return
        val now = SystemClock.uptimeMillis()
        if (isUiPoint()) {
            // Over one of the browser's own UI controls (a toolbar button, the address field, the
            // tab bar, …): send the cursor as a mouse hover to the deepest interactive view under
            // it, so the control gets its hover highlight (the faint "halo" a mouse produces) and
            // tooltip, exactly as the web page does. When the cursor leaves that control (moves to
            // another one, back to the page, or the cursor is suspended / disabled) a matching
            // HOVER_EXIT is sent so its hover highlight is cleared (tracked in [hoveredUiView]).
            //
            // The hover is delivered through the control's own [View.onHoverEvent] — NOT
            // dispatchGenericMotionEvent. View.onHoverEvent is the framework's hover state
            // handler (the protected dispatchHoverEvent delegates hover actions straight to it):
            // on HOVER_ENTER/HOVER_MOVE it sets the view's hovered/pressed state, on HOVER_EXIT
            // it clears it (see AbsListView/DropDownListView: "let the super class handle hover
            // state management first" -> super.onHoverEvent). A well-formed hover is
            // ENTER -> MOVE* -> EXIT (InputEventConsistencyVerifier enforces a prior ENTER).
            // dispatchGenericMotionEvent instead routes to onGenericMotionEvent, which the base
            // View ignores for hover actions — so the original code (a bare HOVER_MOVE through
            // it) never set the hovered state and produced no halo. (The WebView is the
            // exception: Chromium overrides onGenericMotionEvent for CSS :hover, which is why
            // the web path below keeps using dispatchGenericMotionEvent.)
            val root = uiRootProvider?.invoke() ?: return
            val (rx, ry) = targetCoords(root)
            val view = hitTestUi(root, rx, ry)
            if (view !== hoveredUiView) {
                hoveredUiView?.let { exitUiHover(it, now) }
                hoveredUiView = view
                if (view != null) {
                    // A real mouse (and the framework's dispatchHoverEvent) always send a
                    // HOVER_ENTER the first time the pointer lands on a view, THEN the
                    // HOVER_MOVE. It is that ENTER that flips the view into its hovered
                    // state (the faint halo); a bare HOVER_MOVE alone never does. Mirror it.
                    uiHover(view, MotionEvent.ACTION_HOVER_ENTER, now)
                    uiHover(view, MotionEvent.ACTION_HOVER_MOVE, now)
                }
            }
            // The cursor is over a UI control now: end the web hover (a real mouse would have
            // left the page, and the matching EXIT keeps the sequence well-formed).
            hoveredWebTarget?.let { exitWebHover(it, now) }
            hoveredWebTarget = null
            return
        }
        // Over the web content: the cursor leaves any UI control it was over, so clear its hover.
        hoveredUiView?.let { exitUiHover(it, now) }
        hoveredUiView = null
        val (x, y) = targetCoords(target)
        if (target !== hoveredWebTarget) {
            // First hover on this target (or back from a UI control): the well-formed sequence
            // starts with an ENTER (see [hoveredWebTarget]). If a different target was hovered
            // (a tab switch), end its hover first so its :hover state doesn't linger.
            hoveredWebTarget?.let { exitWebHover(it, now) }
            val enter = obtainMouseEvent(now, now, MotionEvent.ACTION_HOVER_ENTER, x, y, 0)
            try {
                target.dispatchGenericMotionEvent(enter)
            } finally {
                enter.recycle()
            }
            hoveredWebTarget = target
        }
        val event = obtainMouseEvent(now, now, MotionEvent.ACTION_HOVER_MOVE, x + xOffset, y, 0)
        try {
            target.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** End the synthetic hover on a web [target] (its [hoveredWebTarget] counterpart). */
    private fun exitWebHover(target: View, now: Long) {
        val (x, y) = targetCoords(target)
        val event = obtainMouseEvent(now, now, MotionEvent.ACTION_HOVER_EXIT, x, y, 0)
        try {
            target.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
        if (target === hoveredWebTarget) hoveredWebTarget = null
    }

    /** Clear a synthetic hover on a browser-UI view (its [hoveredUiView] counterpart). */
    private fun exitUiHover(view: View, now: Long) {
        uiHover(view, MotionEvent.ACTION_HOVER_EXIT, now)
    }

    /**
     * Deliver one synthetic mouse hover [action] to a browser-UI [view], in the view's local
     * coordinates, through the view's own [View.onHoverEvent] — the framework's hover state
     * handler. This is what drives the view's hover/pressed state (the faint "halo"): it is set
     * on ENTER/MOVE and cleared on EXIT.
     */
    private fun uiHover(view: View, action: Int, now: Long) {
        val (x, y) = targetCoords(view)
        val event = obtainMouseEvent(now, now, action, x, y, 0)
        try {
            view.onHoverEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** Clear any pending hover (UI control and web content) and forget which views had it. */
    private fun clearUiHover() {
        val now = SystemClock.uptimeMillis()
        hoveredUiView?.let { exitUiHover(it, now) }
        hoveredUiView = null
        hoveredWebTarget?.let { exitWebHover(it, now) }
    }

    private fun dispatchClick() {
        wakeCursor()
        // Over one of the browser's own UI controls (toolbar button, address field, tab bar, …)
        // the click goes to that view — not to the web page (see [clickUiView]).
        if (isUiPoint()) { clickUiView(); return }
        val target = targetProvider() ?: return
        val (x, y) = targetCoords(target)
        Timber.d("Cursor: click at target ($x, $y)")
        dispatchHover()
        handler.postDelayed({
            val t = targetProvider() ?: return@postDelayed
            if (!dispatchMouseClick(t, x, y)) dispatchTouchClick(t, x, y)
        }, CLICK_DELAY_MS)
    }

    /**
     * Open the WebView's built-in context menu for the element under the cursor by performing a
     * long press. Like [dispatchClick] this is a synthetic **touch** (not a mouse button) — the
     * WebView's long-press detector (and thus its context menu) only reacts to the touch path.
     *
     * The hold is terminated with a **late ACTION_CANCEL**, dispatched *after* the long-press
     * dialog has appeared. Why, measured against the real input pipeline (DOM event log in
     * `scripts/tests/assets/longpress_log.html`):
     *
     *  - A real finger produces `ts … pc/ctx@~540ms tc@~564ms`. At ~560 ms the dialog window
     *    steals window focus (logcat: `onWindowFocusChanged` at ~561 ms after DOWN), and the
     *    input pipeline — which is tracking the in-progress touch — delivers the `tc`
     *    (touchcancel) to the WebView. That cancel cleanly releases the renderer's long-press
     *    input state; real long presses never wedge the page.
     *  - Our synthetic DOWN is dispatched straight to the view, bypassing the input pipeline,
     *    so the pipeline never sees it and never delivers that focus-loss cancel. The renderer
     *    keeps the touch's long-press state active. Terminating with an **UP** wedges that
     *    state: after ~2 long presses the page stops receiving *any* input — even real
     *    hardware taps — until a reload, and a late UP can be read as a stray click (the
     *    original "context menu opens and the page also navigates" symptom).
     *  - A **CANCEL** also has to land *after* the dialog has appeared: one sent early
     *    (~550 ms, before the focus change at ~561 ms) wedged the state just like an UP.
     *    Hence the delay: the cancel goes out at [longPressHoldMs] + [LONGPRESS_LATE_CANCEL_MS]
     *    (~850 ms), comfortably past the dialog's focus steal.
     *
     * The events carry the **same provenance as real touchscreen events** —
     * [InputDevice.SOURCE_TOUCHSCREEN] and [TOUCH_DEVICE_ID] — built with the full
     * [MotionEvent.obtain] constructor. The renderer matches in-progress touches by
     * device/pointer identity: a cancel built with the deprecated
     * `obtain(downTime, eventTime, action, x, y, metaState)` constructor (source
     * SOURCE_UNKNOWN, deviceId -1) is *not* matched to the tracked touch and is ignored,
     * which wedged the gesture state exactly like an UP. With matching provenance the
     * cancel is honored, the same as the pipeline's focus-loss cancel on a real touch.
     * A CANCEL (unlike an UP) can never produce a page click, and it is the exact event
     * the real pipeline sends in this situation — so repeated long presses leave the
     * renderer clean. Regression test:
     * `test_cursor_context_menu_repeated_long_press_touch_stays_clean`.
     */
    private fun dispatchLongPress() {
        // Over the browser's own UI a long press has no context-menu meaning: just activate the
        // control under the cursor (the reliable way to drive the toolbar, see [clickUiView]).
        if (isUiPoint()) { clickUiView(); return }
        val target = targetProvider() ?: return
        wakeCursor()
        val (x, y) = targetCoords(target)
        Timber.d("Cursor: long press (context menu) at target ($x, $y)")
        dispatchHover()
        val downTime = SystemClock.uptimeMillis()
        val down = touchEvent(downTime, downTime, MotionEvent.ACTION_DOWN, x, y)
        try {
            target.dispatchTouchEvent(down)
        } finally {
            down.recycle()
        }
        // Terminate the gesture with a CANCEL once the dialog is on screen (see the KDoc). The
        // posted delay starts counting from now, so the total hold is
        // longPressHoldMs() + LONGPRESS_LATE_CANCEL_MS.
        val cancelMs = longPressHoldMs() + LONGPRESS_LATE_CANCEL_MS
        handler.postDelayed({
            // Only terminate the gesture on the very view that received the DOWN. If the tab
            // changed in the window, an orphan cancel would land on a fresh WebView and could
            // corrupt its touch state; the old view is going away anyway.
            val t = targetProvider() ?: return@postDelayed
            if (t !== target) return@postDelayed
            val cancel = touchEvent(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y)
            try {
                t.dispatchTouchEvent(cancel)
            } finally {
                cancel.recycle()
            }
            Timber.d("Cursor: long press CANCEL dispatched (%d ms after DOWN)", cancelMs)
        }, cancelMs)
    }

    /**
     * A touch [MotionEvent] with the provenance of a real touchscreen event:
     * [InputDevice.SOURCE_TOUCHSCREEN] and a real device id (0 — the id InputDispatcher
     * uses for the primary touchscreen). The renderer's gesture tracker matches
     * in-progress touches by device/pointer identity, so a follow-up event with a
     * different source/deviceId (e.g. from the deprecated
     * `MotionEvent.obtain(downTime, eventTime, action, x, y, metaState)` constructor)
     * does not reach the tracked gesture.
     */
    private fun touchEvent(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(
            downTime, eventTime, action, 1,
            arrayOf(MotionEvent.PointerProperties().also { it.id = 0; it.toolType = MotionEvent.TOOL_TYPE_FINGER }),
            arrayOf(MotionEvent.PointerCoords().also { it.x = x; it.y = y; it.pressure = 1f; it.size = 1f }),
            0, 0, 1f, 1f, TOUCH_DEVICE_ID, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0
        )

    /**
     * How long to hold the long-press touch DOWN before the terminating CANCEL is scheduled
     * (ms). This is the base hold; [dispatchLongPress] adds [LONGPRESS_LATE_CANCEL_MS] so the
     * cancel lands *after* the long-press dialog has appeared and stolen window focus.
     */
    private fun longPressHoldMs(): Long =
        (ViewConfiguration.getLongPressTimeout() + LONGPRESS_MARGIN_MS).toLong()

    // setActionButton is @hide but needed for ACTION_BUTTON_PRESS/RELEASE (api=unsupported,test-api → allowed).
    private val setActionButtonMethod: Method? by lazy {
        try {
            MotionEvent::class.java.getDeclaredMethod("setActionButton", Int::class.java)
                .apply { isAccessible = true }
        } catch (_: Exception) { null }
    }

    private fun dispatchMouseClick(target: View, x: Float, y: Float): Boolean {
        val setter = setActionButtonMethod ?: return false
        val dt = SystemClock.uptimeMillis()
        val pp = arrayOf(MotionEvent.PointerProperties().also { it.id = 0; it.toolType = MotionEvent.TOOL_TYPE_MOUSE })
        fun at(px: Float) = arrayOf(MotionEvent.PointerCoords().also { it.x = px; it.y = y; it.pressure = 1f; it.size = 1f })
        fun ev(t: Long, action: Int, btns: Int, px: Float) =
            MotionEvent.obtain(dt, t, action, 1, pp, at(px), 0, btns, 1f, 1f, -1, 0, InputDevice.SOURCE_MOUSE, 0)
        // Exact event sequence from a real Bluetooth mouse (all via dispatchGenericMotionEvent):
        // ACTION_HOVER_EXIT → ACTION_DOWN → ACTION_BUTTON_PRESS → [MOVE] → ACTION_BUTTON_RELEASE → ACTION_UP
        // It is ACTION_BUTTON_PRESS with actionButton=BUTTON_PRIMARY that Chromium translates to mousedown(button=0).
        val hoverExit = ev(dt,    MotionEvent.ACTION_HOVER_EXIT,     MotionEvent.BUTTON_PRIMARY, x)
        val down      = ev(dt+10, MotionEvent.ACTION_DOWN,           MotionEvent.BUTTON_PRIMARY, x)
        val btnPress  = ev(dt+20, MotionEvent.ACTION_BUTTON_PRESS,   MotionEvent.BUTTON_PRIMARY, x)
        val move      = ev(dt+30, MotionEvent.ACTION_MOVE,           MotionEvent.BUTTON_PRIMARY, x + 2f)
        val btnRel    = ev(dt+50, MotionEvent.ACTION_BUTTON_RELEASE, 0,                          x)
        val up        = ev(dt+60, MotionEvent.ACTION_UP,             0,                          x)
        return try {
            setter.invoke(btnPress, MotionEvent.BUTTON_PRIMARY)
            setter.invoke(btnRel,   MotionEvent.BUTTON_PRIMARY)
            target.dispatchGenericMotionEvent(hoverExit)
            target.dispatchGenericMotionEvent(down)
            target.dispatchGenericMotionEvent(btnPress)
            target.dispatchGenericMotionEvent(move)
            target.dispatchGenericMotionEvent(btnRel)
            target.dispatchGenericMotionEvent(up)
            true
        } catch (_: Exception) { false }
        finally { listOf(hoverExit, down, btnPress, move, btnRel, up).forEach { it.recycle() } }
    }

    private fun dispatchTouchClick(target: View, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, x + 2f, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 60, MotionEvent.ACTION_UP, x, y, 0)
        try {
            target.dispatchTouchEvent(down)
            target.dispatchTouchEvent(move)
            target.dispatchTouchEvent(up)
        } finally { down.recycle(); move.recycle(); up.recycle() }
    }

    private fun dispatchScroll(vNotches: Float, hNotches: Float) {
        val target = targetProvider() ?: return
        val (x, y) = targetCoords(target)
        val now = SystemClock.uptimeMillis()
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            setAxisValue(MotionEvent.AXIS_VSCROLL, vNotches)
            setAxisValue(MotionEvent.AXIS_HSCROLL, hNotches)
        }
        val event = MotionEvent.obtain(
            now, now, MotionEvent.ACTION_SCROLL, 1,
            arrayOf(props), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_MOUSE, 0
        )
        try {
            target.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun obtainMouseEvent(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float, buttonState: Int): MotionEvent {
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        return MotionEvent.obtain(
            downTime, eventTime, action, 1,
            arrayOf(props), arrayOf(coords),
            0, buttonState, 1f, 1f, 0, 0,
            InputDevice.SOURCE_MOUSE, 0
        )
    }

    // --- Visibility / fade --------------------------------------------------

    /** Ensure the cursor is visible and (re)start its fade-out countdown. */
    private fun wakeCursor() {
        showCursor()
        handler.removeCallbacks(fadeRunnable)
        val timeoutMs = (settings.fadeTimeoutSec * 1000f).toLong()
        if (timeoutMs > 0) handler.postDelayed(fadeRunnable, timeoutMs)
    }

    private fun showCursor() {
        if (shown) return
        // Never reveal the cursor while a menu / dialog / sheet has it suspended.
        if (suspended) return
        shown = true
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(FADE_ANIM_MS).start()
    }

    private fun hideCursor() {
        if (!shown) return
        shown = false
        overlay.animate().alpha(0f).setDuration(FADE_ANIM_MS).withEndAction {
            if (!shown) overlay.visibility = View.GONE
        }.start()
    }

    // --- Helpers ------------------------------------------------------------

    private fun hasRightStick(device: InputDevice?): Boolean {
        val dev = device ?: return false
        return isCenteredAxis(dev, MotionEvent.AXIS_Z) && isCenteredAxis(dev, MotionEvent.AXIS_RZ)
    }

    /** A stick axis rests centered (range crosses 0); a trigger axis rests at one end (min >= 0). */
    private fun isCenteredAxis(device: InputDevice, axis: Int): Boolean {
        val range = device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) ?: return false
        return range.min < 0f
    }

    /** Pixels per physical centimetre on each axis, robust against TVs reporting bogus xdpi/ydpi. */
    private fun pxPerCm(): Pair<Float, Float> {
        val dm = overlay.resources.displayMetrics
        val dpiX = if (dm.xdpi in 40f..800f) dm.xdpi else dm.densityDpi.toFloat()
        val dpiY = if (dm.ydpi in 40f..800f) dm.ydpi else dm.densityDpi.toFloat()
        return (dpiX / CM_PER_INCH) to (dpiY / CM_PER_INCH)
    }

    private fun baseSpeedCmPerSec(): Float {
        val s = settings.speed.coerceIn(1f, 100f) / 100f
        return SPEED_MIN_CM_S + s * (SPEED_MAX_CM_S - SPEED_MIN_CM_S)
    }

    private fun accelCmPerSec2(): Float =
        (settings.acceleration.coerceIn(0f, 100f) / 100f) * ACCEL_MAX_CM_S2

    private fun applyDeadzone(v: Float): Float {
        if (abs(v) < JOYSTICK_DEADZONE) return 0f
        // Rescale so movement starts smoothly at the edge of the deadzone.
        val sign = if (v < 0) -1f else 1f
        return sign * ((abs(v) - JOYSTICK_DEADZONE) / (1f - JOYSTICK_DEADZONE))
    }

    /** Current cursor position (overlay-local). Exposed for tests / diagnostics. */
    val position: Pair<Float, Float> get() = posX to posY

    /**
     * Instantly place the cursor at [x], [y] (overlay-local) and dispatch a hover there.
     *
     * D-pad movement clamps the cursor at the overlay edges, and its per-press step is
     * DPI-dependent (and subject to dropped key events over network adb), so a key-press count
     * can't deterministically land the cursor over a specific control (e.g. a toolbar button that
     * sits *near* — not at — the top edge) across devices. This gives the automated tests a
     * device-independent way to put the cursor exactly over a control and then exercise the
     * confirm key / hover against it. It is a pure positioning aid — it does not alter any of the
     * real input paths (movement, click routing, etc.). The point is clamped to the overlay bounds
     * so a stale size (while the overlay is faded out / GONE) can never place it off-screen.
     */
    fun setPosition(x: Float, y: Float) {
        if (overlay.maxX > 0f) boundsX = overlay.maxX
        if (overlay.maxY > 0f) boundsY = overlay.maxY
        posX = x.coerceIn(0f, boundsX)
        posY = y.coerceIn(0f, boundsY)
        positioned = true
        overlay.setPosition(posX, posY)
        wakeCursor()
        dispatchHover()
        Timber.d("Cursor: teleported to ($posX, $posY)")
    }

    companion object {
        val HOTKEY_LONG_PRESS_MS: Long =
            ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(500L)
        private const val CM_PER_INCH = 2.54f
        // Physical travel speed / acceleration the 1..100 settings map onto.
        private const val SPEED_MIN_CM_S = 1.5f
        private const val SPEED_MAX_CM_S = 22f
        private const val ACCEL_MAX_CM_S2 = 45f
        private const val MAX_SPEED_MULT = 6f
        // Physical nudge applied on a single discrete press.
        private const val STEP_MIN_CM = 0.08f
        private const val STEP_MAX_CM = 0.45f
        private const val JOYSTICK_DEADZONE = 0.15f
        private const val FADE_ANIM_MS = 200L
        // Delay between the pre-click hover (to show controls) and the actual click events (ms).
        private const val CLICK_DELAY_MS = 80L
        // Extra time past the system long-press timeout to hold a touch DOWN (so it is a long
        // press even on a slow device), used by the action-key context-menu long press.
        private const val LONGPRESS_MARGIN_MS = 150L
        // Extra delay past [longPressHoldMs] before the terminating CANCEL is sent. The cancel
        // must land *after* the long-press dialog has appeared and stolen window focus (~560 ms
        // after the DOWN, see [dispatchLongPress]) — one sent before that wedges the renderer's
        // input state just like an UP. 300 ms keeps it ~300 ms past the focus change even on a
        // slow device.
        private const val LONGPRESS_LATE_CANCEL_MS = 300L
        // Device id for synthetic touchscreen events: the id InputDispatcher uses for the
        // primary touchscreen. Must match on DOWN and follow-up events or the renderer's
        // gesture tracker won't match them to the tracked touch (see [touchEvent]).
        private const val TOUCH_DEVICE_ID = 0
        // Pixels of edge overflow that map to one mouse-wheel notch.
        private const val SCROLL_PX_PER_NOTCH = 40f
        // Wheel notches per fast-forward / rewind press.
        private const val WHEEL_NOTCHES = 3f
    }
}
