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
 * `ACTION_HOVER_MOVE` events as the cursor moves; the click is a precise touch DOWN→MOVE→UP at the
 * cursor coordinate (synthetic mouse *button* events can't be turned into a page click through the
 * public API on Android WebView — see [dispatchClick]). Reaching a WebView edge dispatches a
 * synthetic mouse **wheel** ([MotionEvent.ACTION_SCROLL]) at the cursor point so whichever DOM
 * element is under the cursor (including nested scrollable panels) scrolls, like a real mouse wheel.
 *
 * ## Two independent ways to drive the cursor
 *  - **Cursor mode** ([enabled]): toggled with the hotkey / menu. While on, the **D-pad** moves the
 *    cursor and the select button clicks. This is the path for D-pad-only remotes and single-stick
 *    joysticks, where the D-pad would otherwise do focus navigation.
 *  - **Right analog stick** ([onGenericMotionEvent]): on a two-stick gamepad the right stick moves
 *    the cursor at any time, *without* toggling cursor mode — the left stick still scrolls and the
 *    D-pad still does focus navigation. The select button clicks whenever the cursor is [shown].
 *
 * The cursor fades out after [CursorSettings.fadeTimeoutMs] of no movement and fades back in on any
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
 *  - [settings]: [CursorSettings] for the hotkey / speed / acceleration / fade timeout;
 *  - [onModeChanged]: notified when cursor mode flips (the activity shows feedback and moves focus).
 *
 * The activity forwards [dispatchKeyEvent] / [onGenericMotionEvent], adds (and, for fullscreen,
 * re-parents) the overlay view, and provides the target — nothing else. This keeps the component
 * reusable / lib-extractable.
 *
 * ## Toggle hotkey
 * A **long press** of [KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE] (held for [HOTKEY_LONG_PRESS_MS]) toggles
 * cursor mode; we detect it ourselves (media keys don't reliably deliver system long-press). A
 * **short** press is yielded back to the activity (returns false) so it can play/pause the page's
 * video. The activity forwards the key to us before it can reach a page's `MediaSession`.
 *
 * ## Media keys as a wheel
 * While the cursor is on screen, [KeyEvent.KEYCODE_MEDIA_FAST_FORWARD] / [KeyEvent.KEYCODE_MEDIA_REWIND]
 * dispatch a synthetic mouse wheel scroll down / up at the cursor point (see [dispatchScroll]); when
 * the cursor is off, they fall through to the activity's per-video seek.
 */
class CursorController(
    private val overlay: CursorView,
    private val targetProvider: () -> View?,
    private val settings: CursorSettings,
    private val onModeChanged: (enabled: Boolean) -> Unit,
) {

    // Cursor mode: D-pad drives the cursor and select clicks. Independent of [shown].
    var enabled: Boolean = false
        private set

    // Whether the cursor overlay is currently faded in (visible). Driven by cursor mode OR the
    // right stick, and cleared by the fade-out timeout.
    private var shown = false

    // Whether the cursor has ever been placed (so re-enabling doesn't recenter a right-stick cursor).
    private var positioned = false

    // Logical cursor position, in overlay-local pixels.
    private var posX = 0f
    private var posY = 0f

    // Active D-pad directions (each -1, 0 or +1). Only set while cursor mode is enabled.
    private var keyDx = 0
    private var keyDy = 0

    // Right-stick displacement after deadzone, -1..1. Drives the cursor regardless of cursor mode.
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

    // --- Fade state ---------------------------------------------------------

    private val fadeRunnable = Runnable { hideCursor() }

    // ------------------------------------------------------------------------

    /**
     * Forward the activity's key events here first. Returns true when consumed.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The toggle hotkey is a long press of play/pause, handled whether or not cursor mode is
        // currently on. A short press is yielded back (returns false) so the activity can play/pause
        // the page's video.
        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            return handleHotkey(event)
        }

        // While the cursor is on screen, fast-forward / rewind become a mouse wheel scroll at the
        // cursor (down / up respectively); off-cursor they fall through to the activity's video seek.
        if ((enabled || shown) &&
            (event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                wakeCursor()
                val notches = if (event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) -WHEEL_NOTCHES else WHEEL_NOTCHES
                dispatchScroll(notches, 0f)
            }
            return true
        }

        // The select button clicks whenever the cursor is on screen — whether it got there via
        // cursor mode or the right stick.
        if (isConfirmKey(event.keyCode) && (enabled || shown)) {
            if (event.action == KeyEvent.ACTION_UP) dispatchClick()
            return true
        }

        // The D-pad only drives the cursor while cursor mode is explicitly enabled; otherwise it
        // must fall through to normal focus navigation.
        if (!enabled) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
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
     * cursor at any time (no cursor-mode toggle needed) on two-stick gamepads. Always returns false
     * (non-consuming) so the left stick's scroll and D-pad focus navigation are left untouched — the
     * right stick's Z/RZ axes aren't used by either of those.
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
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

    /** Toggle cursor mode on/off. Safe to call from the menu or the hotkey. */
    fun toggle() {
        if (enabled) disable() else enable()
    }

    fun enable() {
        if (enabled) return
        enabled = true
        Timber.d("Cursor: enable")
        // Make the overlay visible now so it gets laid out before we read its size to center.
        overlay.visibility = View.VISIBLE
        overlay.post {
            // Center the cursor the first time it appears; keep its place if the right stick already
            // positioned it, so toggling cursor mode on doesn't make it jump.
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
        onModeChanged(true)
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        Timber.d("Cursor: disable")
        keyDx = 0; keyDy = 0
        // Keep the right stick able to move it; if nothing is driving it, let it hide.
        if (!hasMovementInput()) {
            hideCursor()
            stopLoop()
        }
        onModeChanged(false)
    }

    /** Detach lifecycle hooks; call from the activity's onDestroy. */
    fun release() {
        handler.removeCallbacks(hotkeyLongPress)
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
                val stepCm = STEP_MIN_CM + (settings.speed.coerceIn(1, 100) / 100f) * (STEP_MAX_CM - STEP_MIN_CM)
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

        // First movement (e.g. from the right stick before cursor mode was ever enabled) starts
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

        if (overflowX != 0f || overflowY != 0f) {
            // Wheel "notches": pushing down/right scrolls the content the same way a real wheel does.
            dispatchScroll(-overflowY / SCROLL_PX_PER_NOTCH, -overflowX / SCROLL_PX_PER_NOTCH)
        }
    }

    // --- Synthetic pointer events -------------------------------------------

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
        wakeCursor()
        val (x, y) = targetCoords(target)
        Timber.d("Cursor: click at target ($x, $y)")
        // Synthetic mouse *button* events don't produce a page click on Android WebView
        // (MotionEvent.obtain can't set actionButton, so Chromium never sees a button press). A
        // touch DOWN → MOVE → UP does: it yields pointerdown/up plus compat mousedown/mouseup/click.
        // The small MOVE makes drag-style targets (scrub bars) register, and is harmless for buttons.
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val move = MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, x + 2f, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 60, MotionEvent.ACTION_UP, x, y, 0)
        try {
            target.dispatchTouchEvent(down)
            target.dispatchTouchEvent(move)
            target.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }
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
        val timeout = settings.fadeTimeoutMs
        if (timeout > 0) handler.postDelayed(fadeRunnable, timeout.toLong())
    }

    private fun showCursor() {
        if (shown) return
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
        val s = settings.speed.coerceIn(1, 100) / 100f
        return SPEED_MIN_CM_S + s * (SPEED_MAX_CM_S - SPEED_MIN_CM_S)
    }

    private fun accelCmPerSec2(): Float =
        (settings.acceleration.coerceIn(0, 100) / 100f) * ACCEL_MAX_CM_S2

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
        // Pixels of edge overflow that map to one mouse-wheel notch.
        private const val SCROLL_PX_PER_NOTCH = 40f
        // Wheel notches per fast-forward / rewind press.
        private const val WHEEL_NOTCHES = 3f
    }
}
