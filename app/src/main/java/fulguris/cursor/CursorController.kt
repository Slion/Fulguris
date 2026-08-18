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
import android.view.ViewConfiguration
import kotlin.math.abs
import timber.log.Timber

/**
 * Owns the whole on-screen cursor: its position and velocity, the frame loop that moves it from
 * D-pad and analog-joystick input, and the construction/dispatch of synthetic pointer
 * [MotionEvent]s into the target view.
 *
 * Web pages get real `:hover` / `mouseover` / `mouseout` from continuous SOURCE_MOUSE
 * `ACTION_HOVER_MOVE` events as the cursor moves; the click is a precise SOURCE_TOUCHSCREEN tap at
 * the cursor coordinate (synthetic mouse *button* events can't be turned into a page click through
 * the public API on Android WebView — see [dispatchClick]). Reaching a WebView edge scrolls the
 * page instead of pushing the cursor off-screen.
 *
 * ## Boundary
 * The controller is deliberately decoupled from the browser activity. It only knows about:
 *  - [overlay]: the [CursorView] it renders into (and whose bounds it clamps to);
 *  - [targetProvider]: a way to fetch the view to dispatch into (the current `WebView`), re-queried
 *    on every dispatch so tab switches are transparent;
 *  - [settings]: [CursorSettings] for the hotkey toggle and speed;
 *  - [onModeChanged]: notified when the mode flips (the activity shows feedback and moves focus);
 *
 * The activity's only job is to forward [dispatchKeyEvent] and [onGenericMotionEvent] and to add the
 * overlay view. This is what keeps the component reusable / lib-extractable.
 *
 * ## Toggle hotkey
 * A short press of [KeyEvent.KEYCODE_MEDIA_FAST_FORWARD] is unused by the browser; a **long press**
 * (held for [HOTKEY_LONG_PRESS_MS]) toggles cursor mode. We detect the long press ourselves with a
 * posted runnable started on `ACTION_DOWN` and cancelled on `ACTION_UP`, because the framework does
 * not reliably deliver long-press key events for media keys. The activity forwards the key to us
 * *before* it can reach a page's `MediaSession`, so a playing `<video>` cannot steal the toggle.
 */
class CursorController(
    private val overlay: CursorView,
    private val targetProvider: () -> View?,
    private val settings: CursorSettings,
    private val onModeChanged: (enabled: Boolean) -> Unit,
) {

    var enabled: Boolean = false
        private set

    // Logical cursor position, in overlay-local pixels.
    private var posX = 0f
    private var posY = 0f

    // Active key directions (each -1, 0 or +1) and how long each axis has been held, for acceleration.
    private var keyDx = 0
    private var keyDy = 0
    private var keyHeldSinceMs = 0L

    // Joystick displacement after deadzone, -1..1.
    private var joyX = 0f
    private var joyY = 0f

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

    // ------------------------------------------------------------------------

    /**
     * Forward the activity's key events here first. Returns true when consumed.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The toggle hotkey is handled whether or not cursor mode is currently on.
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            return handleHotkey(event)
        }

        if (!enabled) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                handleDirectionKey(event)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    dispatchClick()
                }
                true
            }
            else -> false
        }
    }

    /**
     * Forward the activity's generic motion events here. Handles the analog joystick while cursor
     * mode is on; returns false otherwise so normal focus navigation / DPAD synthesis is unaffected.
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!enabled) return false
        if (event.source and InputDevice.SOURCE_CLASS_JOYSTICK == 0) return false
        if (event.action != MotionEvent.ACTION_MOVE) return false

        val rawX = readAxis(event, MotionEvent.AXIS_X, MotionEvent.AXIS_HAT_X)
        val rawY = readAxis(event, MotionEvent.AXIS_Y, MotionEvent.AXIS_HAT_Y)
        joyX = applyDeadzone(rawX)
        joyY = applyDeadzone(rawY)
        if (joyX != 0f || joyY != 0f) startLoop() else maybeStopLoop()
        return true
    }

    /** Toggle cursor mode on/off. Safe to call from the menu or the hotkey. */
    fun toggle() {
        if (enabled) disable() else enable()
    }

    fun enable() {
        if (enabled) return
        enabled = true
        Timber.d("Cursor: enable")
        // Start centered over the overlay.
        overlay.visibility = View.VISIBLE
        overlay.post {
            posX = overlay.maxX / 2f
            posY = overlay.maxY / 2f
            overlay.setPosition(posX, posY)
            Timber.d("Cursor: centered at ($posX, $posY) in overlay ${overlay.maxX}x${overlay.maxY}")
            dispatchHover()
        }
        overlay.animate().alpha(1f).setDuration(150).start()
        onModeChanged(true)
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        Timber.d("Cursor: disable")
        keyDx = 0; keyDy = 0; joyX = 0f; joyY = 0f
        stopLoop()
        overlay.animate().alpha(0f).setDuration(150).withEndAction {
            if (!enabled) overlay.visibility = View.GONE
        }.start()
        onModeChanged(false)
    }

    /** Detach lifecycle hooks; call from the activity's onDestroy. */
    fun release() {
        handler.removeCallbacks(hotkeyLongPress)
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
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(hotkeyLongPress)
                hotkeyDownHandled = false
                // Short press: no-op in the browser today (we simply swallow it).
            }
        }
        // Consume so the key never reaches a page MediaSession or anything else.
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
            val wasIdle = keyDx == 0 && keyDy == 0
            if (ax != 0) keyDx = ax
            if (ay != 0) keyDy = ay
            if (wasIdle) keyHeldSinceMs = SystemClock.uptimeMillis()
            // Move a fixed step on the initial press so a single tap (which releases almost
            // instantly, e.g. a discrete remote press) always nudges the cursor; the frame loop
            // then adds continuous, accelerating movement for as long as the key stays held.
            // Only on repeatCount 0 so auto-repeat DOWNs don't double up with the loop.
            if (event.repeatCount == 0) {
                val step = STEP_PX * (0.5f + settings.speed.coerceIn(1, 100) / 100f)
                moveBy(ax * step, ay * step)
            }
            startLoop()
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (ax != 0 && keyDx == ax) keyDx = 0
            if (ay != 0 && keyDy == ay) keyDy = 0
            if (keyDx == 0 && keyDy == 0) maybeStopLoop()
        }
    }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNs -> onFrame(frameTimeNs) }

    private fun startLoop() {
        if (looping || !enabled) return
        looping = true
        lastFrameNs = 0L
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        looping = false
        choreographer.removeFrameCallback(frameCallback)
    }

    private fun maybeStopLoop() {
        if (keyDx == 0 && keyDy == 0 && joyX == 0f && joyY == 0f) stopLoop()
    }

    private fun onFrame(frameTimeNs: Long) {
        if (!looping || !enabled) return
        val dt = if (lastFrameNs == 0L) 0f else (frameTimeNs - lastFrameNs) / 1_000_000_000f
        lastFrameNs = frameTimeNs

        // px/second base speed, scaled by the user's 1..100 speed setting.
        val base = BASE_SPEED_PX_PER_SEC * (0.4f + 1.6f * (settings.speed.coerceIn(1, 100) / 100f))

        var vx = 0f
        var vy = 0f

        // Keyboard: constant direction with an acceleration ramp the longer it is held.
        if (keyDx != 0 || keyDy != 0) {
            val held = (SystemClock.uptimeMillis() - keyHeldSinceMs) / 1000f
            val accel = (1f + held * KEY_ACCEL_PER_SEC).coerceAtMost(KEY_ACCEL_MAX)
            vx += keyDx * base * accel
            vy += keyDy * base * accel
        }

        // Joystick: continuous, scaled by displacement magnitude.
        if (joyX != 0f || joyY != 0f) {
            vx += joyX * base * JOYSTICK_SPEED_FACTOR
            vy += joyY * base * JOYSTICK_SPEED_FACTOR
        }

        if (vx != 0f || vy != 0f) {
            moveBy(vx * dt, vy * dt)
        }

        if (looping) choreographer.postFrameCallback(frameCallback)
    }

    private fun moveBy(dx: Float, dy: Float) {
        val maxX = overlay.maxX
        val maxY = overlay.maxY
        if (maxX <= 0f || maxY <= 0f) return

        var nx = posX + dx
        var ny = posY + dy

        // At an edge, keep pushing translates into page scrolling; the cursor stays clamped.
        var scrollX = 0
        var scrollY = 0
        if (nx < 0f) { scrollX += (nx).toInt(); nx = 0f }
        else if (nx > maxX) { scrollX += (nx - maxX).toInt(); nx = maxX }
        if (ny < 0f) { scrollY += (ny).toInt(); ny = 0f }
        else if (ny > maxY) { scrollY += (ny - maxY).toInt(); ny = maxY }

        posX = nx
        posY = ny
        overlay.setPosition(posX, posY)
        dispatchHover()

        if (scrollX != 0 || scrollY != 0) {
            targetProvider()?.scrollBy(scrollX, scrollY)
        }
    }

    // --- Synthetic mouse events --------------------------------------------

    /** Map the overlay-local cursor position into the target view's coordinate space. */
    private fun targetCoords(target: View): Pair<Float, Float> {
        val t = IntArray(2); target.getLocationOnScreen(t)
        val o = IntArray(2); overlay.getLocationOnScreen(o)
        return (posX + o[0] - t[0]) to (posY + o[1] - t[1])
    }

    private fun dispatchHover() {
        val target = targetProvider() ?: return
        val (x, y) = targetCoords(target)
        val now = SystemClock.uptimeMillis()
        val event = obtainMouseEvent(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0)
        try {
            target.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun dispatchClick() {
        val target = targetProvider() ?: return
        val (x, y) = targetCoords(target)
        Timber.d("Cursor: click at target ($x, $y)")
        // Hover/:hover is driven by SOURCE_MOUSE ACTION_HOVER_MOVE events (see dispatchHover), but
        // the click itself is a plain touch tap. Synthetic mouse *button* events don't turn into a
        // page click on Android WebView (MotionEvent.obtain can't set actionButton, so Chromium
        // never sees a primary-button press), and an explicit SOURCE_TOUCHSCREEN event with
        // deviceId 0 is rejected by some WebView builds. The bare obtain(...x, y...) form — tool
        // FINGER, source unspecified — is the portable pattern that activates the element under
        // the cursor across Android versions. DOWN and UP share one downTime to form a tap gesture.
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)
        try {
            target.dispatchTouchEvent(down)
            target.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
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

    // --- Helpers ------------------------------------------------------------

    private fun readAxis(event: MotionEvent, primary: Int, hat: Int): Float {
        val v = event.getAxisValue(primary)
        return if (abs(v) > 0.01f) v else event.getAxisValue(hat)
    }

    private fun applyDeadzone(v: Float): Float {
        if (abs(v) < JOYSTICK_DEADZONE) return 0f
        // Rescale so movement starts smoothly at the edge of the deadzone.
        val sign = if (v < 0) -1f else 1f
        return sign * ((abs(v) - JOYSTICK_DEADZONE) / (1f - JOYSTICK_DEADZONE))
    }

    /** Current cursor position (overlay-local). Exposed for tests / diagnostics. */
    val position: Pair<Float, Float> get() = posX to posY

    companion object {
        val HOTKEY_LONG_PRESS_MS: Long =
            ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(500L)
        private const val BASE_SPEED_PX_PER_SEC = 900f
        private const val STEP_PX = 40f
        private const val KEY_ACCEL_PER_SEC = 1.2f
        private const val KEY_ACCEL_MAX = 3.5f
        private const val JOYSTICK_SPEED_FACTOR = 1.3f
        private const val JOYSTICK_DEADZONE = 0.15f
    }
}
