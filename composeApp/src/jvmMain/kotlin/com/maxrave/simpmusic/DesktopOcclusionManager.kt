package com.maxrave.simpmusic

import com.maxrave.logger.Logger
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import java.io.File
import javax.swing.SwingUtilities

interface DesktopOcclusionListener : DBusInterface {
    fun setOccluded(occluded: Boolean)
}

/**
 * Detects whether the SimpMusic desktop window is completely occluded / covered
 * by another opaque window (e.g. a fullscreen game or maximized window).
 *
 * If the user gives Alt-Tab to an unmaximized or partially overlapping window,
 * SimpMusic remains visible and continues rendering normally.
 *
 * Only when SimpMusic is completely covered by an opaque window, minimized,
 * or on an inactive virtual desktop does it signal occlusion so GPU rendering
 * can be suspended to drop GPU usage to 0.0%.
 */
object DesktopOcclusionManager : DesktopOcclusionListener {
    private const val TAG = "DesktopOcclusionManager"

    @Volatile
    var isOccluded: Boolean = false
        private set

    var onOcclusionChanged: ((Boolean) -> Unit)? = null

    private var scriptId: Int = -1
    private var tempScriptFile: File? = null
    private var dbusConnection: DBusConnection? = null

    override fun isRemote() = false
    override fun getObjectPath() = "/Occlusion"

    override fun setOccluded(occluded: Boolean) {
        if (isOccluded != occluded) {
            isOccluded = occluded
            Logger.i(TAG, "Window occlusion changed: occluded=$occluded")
            SwingUtilities.invokeLater {
                onOcclusionChanged?.invoke(occluded)
            }
        }
    }

    fun start() {
        if (!System.getProperty("os.name", "").contains("Linux", ignoreCase = true)) return

        runCatching {
            val pid = ProcessHandle.current().pid()
            val serviceName = "com.maxrave.simpmusic.Occlusion_$pid"

            val conn = DBusConnectionBuilder.forSessionBus().build()
            conn.requestBusName(serviceName)
            conn.exportObject(getObjectPath(), this)
            dbusConnection = conn

            val scriptContent = """
                var myPid = $pid;
                var dbusService = "$serviceName";
                var lastOccluded = null;

                function checkSimpMusicOcclusion() {
                    var order = workspace.stackingOrder;
                    var simpIdx = -1;
                    var simpWin = null;
                    for (var i = 0; i < order.length; i++) {
                        var w = order[i];
                        if (w.pid === myPid || (w.resourceClass && w.resourceClass === "SimpMusic")) {
                            simpWin = w;
                            simpIdx = i;
                            break;
                        }
                    }
                    if (!simpWin) return;
                    
                    if (simpWin.minimized) {
                        sendState(true);
                        return;
                    }
                    
                    if (simpWin.active) {
                        sendState(false);
                        return;
                    }
                    
                    if (workspace.currentDesktop && simpWin.desktops) {
                        var onCurr = false;
                        for (var d = 0; d < simpWin.desktops.length; d++) {
                            if (simpWin.desktops[d] === workspace.currentDesktop) {
                                onCurr = true;
                                break;
                            }
                        }
                        if (!onCurr && simpWin.desktops.length > 0) {
                            sendState(true);
                            return;
                        }
                    }
                    
                    var sx1 = simpWin.x;
                    var sy1 = simpWin.y;
                    var sx2 = simpWin.x + simpWin.width;
                    var sy2 = simpWin.y + simpWin.height;
                    
                    var isCovered = false;
                    for (var j = simpIdx + 1; j < order.length; j++) {
                        var top = order[j];
                        if (!top.normalWindow || top.minimized) continue;
                        if (top.opacity !== undefined && top.opacity < 0.95) continue;
                        
                        var tx1 = top.x;
                        var ty1 = top.y;
                        var tx2 = top.x + top.width;
                        var ty2 = top.y + top.height;
                        
                        if (tx1 <= sx1 && ty1 <= sy1 && tx2 >= sx2 && ty2 >= sy2) {
                            isCovered = true;
                            break;
                        }
                    }
                    sendState(isCovered);
                }

                function sendState(occ) {
                    if (lastOccluded !== occ) {
                        lastOccluded = occ;
                        callDBus(dbusService, "/Occlusion", "com.maxrave.simpmusic.DesktopOcclusionListener", "setOccluded", occ);
                    }
                }

                workspace.windowActivated.connect(checkSimpMusicOcclusion);
                workspace.currentDesktopChanged.connect(checkSimpMusicOcclusion);
                checkSimpMusicOcclusion();
            """.trimIndent()

            val tempFile = File.createTempFile("simpmusic_kwin_occ_", ".js")
            tempFile.writeText(scriptContent)
            tempScriptFile = tempFile

            val loadProcess = ProcessBuilder(
                "busctl", "--user", "call", "org.kde.KWin", "/Scripting",
                "org.kde.kwin.Scripting", "loadScript", "s", tempFile.absolutePath
            ).start()
            val loadOut = loadProcess.inputStream.bufferedReader().readText().trim()
            loadProcess.waitFor()
            val id = loadOut.substringAfterLast(" ").toIntOrNull()
            if (id != null) {
                scriptId = id
                ProcessBuilder(
                    "busctl", "--user", "call", "org.kde.KWin",
                    "/Scripting/Script$id", "org.kde.kwin.Script", "run"
                ).start().waitFor()
                Logger.i(TAG, "Registered KWin occlusion script with id $id")
            }
        }.onFailure {
            Logger.w(TAG, "Failed to initialize KWin occlusion manager: ${it.message}")
        }
    }

    fun stop() {
        runCatching {
            if (scriptId != -1) {
                ProcessBuilder(
                    "busctl", "--user", "call", "org.kde.KWin",
                    "/Scripting/Script$scriptId", "org.kde.kwin.Script", "stop"
                ).start().waitFor()
                tempScriptFile?.let {
                    ProcessBuilder(
                        "busctl", "--user", "call", "org.kde.KWin",
                        "/Scripting", "org.kde.kwin.Scripting", "unloadScript", "s", it.absolutePath
                    ).start().waitFor()
                }
                scriptId = -1
            }
            tempScriptFile?.delete()
            tempScriptFile = null
            dbusConnection?.disconnect()
            dbusConnection = null
        }
    }
}
