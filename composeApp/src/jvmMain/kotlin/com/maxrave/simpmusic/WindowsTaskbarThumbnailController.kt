package com.maxrave.simpmusic

import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.logger.Logger
import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Window
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

/**
 * Native Windows Taskbar Thumbnail Toolbar Controller (ITaskbarList3).
 *
 * Provides Previous, Play/Pause, and Next media buttons in the taskbar thumbnail preview
 * when hovering over the app window on Windows 7, 8, 10, and 11.
 */
object WindowsTaskbarThumbnailController {
    private const val TAG = "WindowsTaskbarThumbnail"

    private const val THB_BITMAP = 0x00000001
    private const val THB_ICON = 0x00000002
    private const val THB_TOOLTIP = 0x00000004
    private const val THB_FLAGS = 0x00000008

    private const val THBF_ENABLED = 0x00000000
    private const val THBF_DISABLED = 0x00000001
    private const val THBF_DISMISSONCLICK = 0x00000002
    private const val THBF_NOBACKGROUND = 0x00000004
    private const val THBF_HIDDEN = 0x00000008
    private const val THBF_NONINTERACTIVE = 0x00000010

    private const val THBN_CLICKED = 0x1800
    private const val WM_COMMAND = 0x0111
    private const val GWLP_WNDPROC = -4

    private const val ID_PREV = 101
    private const val ID_PLAY_PAUSE = 102
    private const val ID_NEXT = 103

    private val SUBCLASS_ID = BaseTSD.ULONG_PTR(9001L)

    // Strong reference to prevent Garbage Collection of JNA native callbacks
    @Volatile
    private var activeSubclassProc: SubclassProcCallback? = null

    @Volatile
    private var activeWndProc: WindowProcCallback? = null

    @Structure.FieldOrder("dwMask", "iId", "iBitmap", "hIcon", "szTip", "dwFlags")
    open class THUMBBUTTON : Structure {
        @JvmField var dwMask: Int = 0
        @JvmField var iId: Int = 0
        @JvmField var iBitmap: Int = 0
        @JvmField var hIcon: WinDef.HICON? = null
        @JvmField var szTip: CharArray = CharArray(260)
        @JvmField var dwFlags: Int = 0

        constructor() : super()
        constructor(p: Pointer) : super(p) { read() }

        fun setTooltip(tip: String) {
            val chars = tip.toCharArray()
            val len = minOf(chars.size, 259)
            System.arraycopy(chars, 0, szTip, 0, len)
            szTip[len] = '\u0000'
        }

        class ByReference : THUMBBUTTON(), Structure.ByReference
    }

    private interface CustomUser32 : Library {
        fun SetWindowLongPtr(hWnd: WinDef.HWND, nIndex: Int, wndProc: Callback): Pointer?
        fun SetWindowLongPtr(hWnd: WinDef.HWND, nIndex: Int, wndProc: Pointer): Pointer?
        fun CallWindowProc(
            lpPrevWndFunc: Pointer,
            hWnd: WinDef.HWND,
            uMsg: Int,
            wParam: WinDef.WPARAM,
            lParam: WinDef.LPARAM,
        ): WinDef.LRESULT
        fun RegisterWindowMessageW(lpString: WString): Int
        fun CreateIconIndirect(piconinfo: WinGDI.ICONINFO): WinDef.HICON?
        fun DestroyIcon(hIcon: WinDef.HICON): Boolean

        companion object {
            val INSTANCE: CustomUser32 by lazy {
                Native.load("user32", CustomUser32::class.java, W32APIOptions.UNICODE_OPTIONS)
            }
        }
    }

    private interface CustomComctl32 : Library {
        fun SetWindowSubclass(
            hWnd: WinDef.HWND,
            pfnSubclass: SubclassProcCallback,
            uIdSubclass: BaseTSD.ULONG_PTR,
            dwRefData: BaseTSD.DWORD_PTR,
        ): Boolean

        fun RemoveWindowSubclass(
            hWnd: WinDef.HWND,
            pfnSubclass: SubclassProcCallback,
            uIdSubclass: BaseTSD.ULONG_PTR,
        ): Boolean

        fun DefSubclassProc(
            hWnd: WinDef.HWND,
            uMsg: Int,
            wParam: WinDef.WPARAM,
            lParam: WinDef.LPARAM,
        ): WinDef.LRESULT

        companion object {
            val INSTANCE: CustomComctl32 by lazy {
                Native.load("comctl32", CustomComctl32::class.java, W32APIOptions.UNICODE_OPTIONS)
            }
        }
    }

    private interface CustomGdi32 : Library {
        fun CreateBitmap(nWidth: Int, nHeight: Int, nPlanes: Int, nBitCount: Int, lpBits: IntArray): WinDef.HBITMAP?
        fun CreateBitmap(nWidth: Int, nHeight: Int, nPlanes: Int, nBitCount: Int, lpBits: ByteArray): WinDef.HBITMAP?
        fun DeleteObject(hObject: WinDef.HBITMAP): Boolean

        companion object {
            val INSTANCE: CustomGdi32 by lazy {
                Native.load("gdi32", CustomGdi32::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }
        }
    }

    private interface SubclassProcCallback : Callback {
        fun callback(
            hWnd: WinDef.HWND,
            uMsg: Int,
            wParam: WinDef.WPARAM,
            lParam: WinDef.LPARAM,
            uIdSubclass: BaseTSD.ULONG_PTR,
            dwRefData: BaseTSD.DWORD_PTR,
        ): WinDef.LRESULT
    }

    private interface WindowProcCallback : Callback {
        fun callback(
            hwnd: WinDef.HWND,
            uMsg: Int,
            wParam: WinDef.WPARAM,
            lParam: WinDef.LPARAM,
        ): WinDef.LRESULT
    }

    private class TaskbarList3(val pointer: Pointer) {
        private val vtable: Pointer = pointer.getPointer(0)

        private fun getFn(idx: Int): Function {
            return Function.getFunction(vtable.getPointer(idx.toLong() * Native.POINTER_SIZE))
        }

        fun hrInit(): Int = getFn(3).invokeInt(arrayOf(pointer))

        fun thumbBarAddButtons(hwnd: WinDef.HWND, count: Int, pButtons: Pointer): Int =
            getFn(15).invokeInt(arrayOf(pointer, hwnd.pointer, count, pButtons))

        fun thumbBarUpdateButtons(hwnd: WinDef.HWND, count: Int, pButtons: Pointer): Int =
            getFn(16).invokeInt(arrayOf(pointer, hwnd.pointer, count, pButtons))

        fun release(): Int = getFn(2).invokeInt(arrayOf(pointer))
    }

    private val CLSID_TaskbarList = Guid.CLSID(Guid.GUID("{56FDF344-FD6D-11d0-958A-006097C9A090}"))
    private val IID_ITaskbarList3 = Guid.IID(Guid.GUID("{ea1afb91-9e28-4b86-90e9-9e9f8a5eefaf}"))

    fun attach(
        window: Window,
        mediaPlayerHandler: MediaPlayerHandler,
        coroutineScope: CoroutineScope,
    ): () -> Unit {
        if (!System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) {
            return {}
        }

        return try {
            val hwndPointer = Native.getWindowPointer(window) ?: return {}
            val hwnd = WinDef.HWND(hwndPointer)

            Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)

            val pTaskbar = PointerByReference()
            val hr = Ole32.INSTANCE.CoCreateInstance(
                CLSID_TaskbarList,
                null,
                1, // CLSCTX_INPROC_SERVER
                IID_ITaskbarList3,
                pTaskbar,
            )
            if (hr.toInt() != 0 || pTaskbar.value == null) {
                Logger.w(TAG, "CoCreateInstance(ITaskbarList3) failed: hr=$hr")
                return {}
            }

            val taskbar = TaskbarList3(pTaskbar.value)
            taskbar.hrInit()

            val prevIcon = createGlyphIcon { g ->
                g.fillRect(3, 4, 3, 12)
                g.fillPolygon(intArrayOf(16, 7, 16), intArrayOf(4, 10, 16), 3)
            }
            val playIcon = createGlyphIcon { g ->
                g.fillPolygon(intArrayOf(5, 16, 5), intArrayOf(4, 10, 16), 3)
            }
            val pauseIcon = createGlyphIcon { g ->
                g.fillRect(5, 4, 3, 12)
                g.fillRect(12, 4, 3, 12)
            }
            val nextIcon = createGlyphIcon { g ->
                g.fillPolygon(intArrayOf(4, 13, 4), intArrayOf(4, 10, 16), 3)
                g.fillRect(14, 4, 3, 12)
            }

            val template = THUMBBUTTON()
            @Suppress("UNCHECKED_CAST")
            val buttons = template.toArray(3) as Array<THUMBBUTTON>

            // Button 0: Prev
            buttons[0].dwMask = THB_ICON or THB_TOOLTIP or THB_FLAGS
            buttons[0].iId = ID_PREV
            buttons[0].hIcon = prevIcon
            buttons[0].setTooltip("Previous")
            buttons[0].dwFlags = THBF_ENABLED

            // Button 1: Play/Pause
            buttons[1].dwMask = THB_ICON or THB_TOOLTIP or THB_FLAGS
            buttons[1].iId = ID_PLAY_PAUSE
            buttons[1].hIcon = playIcon
            buttons[1].setTooltip("Play")
            buttons[1].dwFlags = THBF_ENABLED

            // Button 2: Next
            buttons[2].dwMask = THB_ICON or THB_TOOLTIP or THB_FLAGS
            buttons[2].iId = ID_NEXT
            buttons[2].hIcon = nextIcon
            buttons[2].setTooltip("Next")
            buttons[2].dwFlags = THBF_ENABLED

            var buttonsAdded = false

            fun addButtonsSafe() {
                if (buttonsAdded) return
                buttons[0].write()
                buttons[1].write()
                buttons[2].write()
                val res = taskbar.thumbBarAddButtons(hwnd, 3, buttons[0].pointer)
                Logger.d(TAG, "thumbBarAddButtons result: $res")
                if (res == 0) {
                    buttonsAdded = true
                }
            }

            fun updateButtonsSafe(isPlaying: Boolean, hasPrev: Boolean, hasNext: Boolean) {
                if (!buttonsAdded) {
                    addButtonsSafe()
                }
                buttons[0].dwFlags = if (hasPrev) THBF_ENABLED else THBF_DISABLED
                buttons[1].hIcon = if (isPlaying) pauseIcon else playIcon
                buttons[1].setTooltip(if (isPlaying) "Pause" else "Play")
                buttons[1].dwFlags = THBF_ENABLED
                buttons[2].dwFlags = if (hasNext) THBF_ENABLED else THBF_DISABLED

                buttons[0].write()
                buttons[1].write()
                buttons[2].write()
                taskbar.thumbBarUpdateButtons(hwnd, 3, buttons[0].pointer)
            }

            val taskbarCreatedMsg = CustomUser32.INSTANCE.RegisterWindowMessageW(WString("TaskbarButtonCreated"))

            fun handleCommandMessage(wParam: WinDef.WPARAM): Boolean {
                val hiWord = (wParam.toLong() shr 16) and 0xFFFF
                if (hiWord == THBN_CLICKED.toLong()) {
                    val id = (wParam.toLong() and 0xFFFF).toInt()
                    when (id) {
                        ID_PREV -> coroutineScope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Previous) }
                        ID_PLAY_PAUSE -> coroutineScope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.PlayPause) }
                        ID_NEXT -> coroutineScope.launch { mediaPlayerHandler.onPlayerEvent(PlayerEvent.Next) }
                    }
                    return true
                }
                return false
            }

            // Attempt safe window subclassing via comctl32 first
            val subclassProc = object : SubclassProcCallback {
                override fun callback(
                    hWnd: WinDef.HWND,
                    uMsg: Int,
                    wParam: WinDef.WPARAM,
                    lParam: WinDef.LPARAM,
                    uIdSubclass: BaseTSD.ULONG_PTR,
                    dwRefData: BaseTSD.DWORD_PTR,
                ): WinDef.LRESULT {
                    if (uMsg == taskbarCreatedMsg) {
                        SwingUtilities.invokeLater { addButtonsSafe() }
                        return CustomComctl32.INSTANCE.DefSubclassProc(hWnd, uMsg, wParam, lParam)
                    }
                    if (uMsg == WM_COMMAND && handleCommandMessage(wParam)) {
                        return WinDef.LRESULT(0)
                    }
                    return CustomComctl32.INSTANCE.DefSubclassProc(hWnd, uMsg, wParam, lParam)
                }
            }

            // Keep strong reference to prevent GC
            activeSubclassProc = subclassProc
            val usedSubclass = runCatching {
                CustomComctl32.INSTANCE.SetWindowSubclass(hwnd, subclassProc, SUBCLASS_ID, BaseTSD.DWORD_PTR(0L))
            }.getOrDefault(false)

            var oldWndProc: Pointer? = null
            var fallbackWndProc: WindowProcCallback? = null

            if (!usedSubclass) {
                Logger.w(TAG, "comctl32 SetWindowSubclass failed, falling back to SetWindowLongPtr")
                fallbackWndProc = object : WindowProcCallback {
                    override fun callback(
                        hwnd: WinDef.HWND,
                        uMsg: Int,
                        wParam: WinDef.WPARAM,
                        lParam: WinDef.LPARAM,
                    ): WinDef.LRESULT {
                        if (uMsg == taskbarCreatedMsg) {
                            SwingUtilities.invokeLater { addButtonsSafe() }
                        } else if (uMsg == WM_COMMAND && handleCommandMessage(wParam)) {
                            return WinDef.LRESULT(0)
                        }
                        val prev = oldWndProc
                        return if (prev != null) {
                            CustomUser32.INSTANCE.CallWindowProc(prev, hwnd, uMsg, wParam, lParam)
                        } else {
                            User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam)
                        }
                    }
                }
                activeWndProc = fallbackWndProc
                oldWndProc = CustomUser32.INSTANCE.SetWindowLongPtr(hwnd, GWLP_WNDPROC, fallbackWndProc)
            }

            // If the window is already showing when attach() is invoked, initialize buttons on EDT
            SwingUtilities.invokeLater {
                if (window.isShowing) {
                    addButtonsSafe()
                }
            }

            val stateJob = coroutineScope.launch {
                mediaPlayerHandler.controlState.collectLatest { state ->
                    SwingUtilities.invokeLater {
                        updateButtonsSafe(
                            isPlaying = state.isPlaying,
                            hasPrev = state.isPreviousAvailable,
                            hasNext = state.isNextAvailable,
                        )
                    }
                }
            }

            val cleanup: () -> Unit = {
                stateJob.cancel()
                if (usedSubclass) {
                    runCatching {
                        CustomComctl32.INSTANCE.RemoveWindowSubclass(hwnd, subclassProc, SUBCLASS_ID)
                    }
                    if (activeSubclassProc === subclassProc) {
                        activeSubclassProc = null
                    }
                } else {
                    val prev = oldWndProc
                    if (prev != null) {
                        CustomUser32.INSTANCE.SetWindowLongPtr(hwnd, GWLP_WNDPROC, prev)
                    }
                    if (activeWndProc === fallbackWndProc) {
                        activeWndProc = null
                    }
                }
                prevIcon?.let { CustomUser32.INSTANCE.DestroyIcon(it) }
                playIcon?.let { CustomUser32.INSTANCE.DestroyIcon(it) }
                pauseIcon?.let { CustomUser32.INSTANCE.DestroyIcon(it) }
                nextIcon?.let { CustomUser32.INSTANCE.DestroyIcon(it) }
                taskbar.release()
            }
            cleanup
        } catch (e: Throwable) {
            Logger.e(TAG, "Failed to initialize WindowsTaskbarThumbnailController: ${e.message}")
            val noop: () -> Unit = {}
            noop
        }
    }

    private fun createGlyphIcon(draw: (Graphics2D) -> Unit): WinDef.HICON? {
        val size = 20
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = Color.WHITE
            draw(g2)
        } finally {
            g2.dispose()
        }

        val pixels = IntArray(size * size)
        img.getRGB(0, 0, size, size, pixels, 0, size)

        val colorBmp = CustomGdi32.INSTANCE.CreateBitmap(size, size, 1, 32, pixels) ?: return null
        val maskBytes = ByteArray(((size + 15) / 16) * 2 * size) // empty monochrome mask
        val maskBmp = CustomGdi32.INSTANCE.CreateBitmap(size, size, 1, 1, maskBytes)
        if (maskBmp == null) {
            CustomGdi32.INSTANCE.DeleteObject(colorBmp)
            return null
        }

        val iconInfo = WinGDI.ICONINFO().apply {
            fIcon = true
            xHotspot = 0
            yHotspot = 0
            hbmMask = maskBmp
            hbmColor = colorBmp
        }

        val hIcon = CustomUser32.INSTANCE.CreateIconIndirect(iconInfo)
        CustomGdi32.INSTANCE.DeleteObject(colorBmp)
        CustomGdi32.INSTANCE.DeleteObject(maskBmp)
        return hIcon
    }
}
