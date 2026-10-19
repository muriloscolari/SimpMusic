package com.maxrave.simpmusic

import com.maxrave.logger.Logger
import java.io.File

object DesktopAutostartManager {
    private const val TAG = "DesktopAutostartManager"
    private const val WIN_RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val WIN_VALUE_NAME = "SimpMusic"
    private const val LINUX_DESKTOP_ENTRY = "simpmusic.desktop"

    fun isSupported(): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        return os.contains("win") || os.contains("linux")
    }

    fun setAutostart(enabled: Boolean, startMinimized: Boolean) {
        val os = System.getProperty("os.name", "").lowercase()
        when {
            os.contains("win") -> setWindowsAutostart(enabled, startMinimized)
            os.contains("linux") -> setLinuxAutostart(enabled, startMinimized)
            else -> Logger.d(TAG, "Autostart is not supported on $os")
        }
    }

    private fun resolveExePath(): String? {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win")) {
            val javaHome = System.getProperty("java.home") ?: return null
            val javaHomeDir = File(javaHome)
            val appDir = if (javaHomeDir.name == "runtime") {
                javaHomeDir.parentFile
            } else {
                generateSequence(javaHomeDir) { it.parentFile }
                    .firstOrNull { it.name == "runtime" }
                    ?.parentFile
            }
            if (appDir != null) {
                val exeFile = File(appDir, "SimpMusic.exe")
                if (exeFile.exists()) return exeFile.absolutePath
            }
            return ProcessHandle.current().info().command().orElse(null)
        } else if (os.contains("linux")) {
            val appImage = System.getenv("APPIMAGE")
            if (!appImage.isNullOrBlank() && File(appImage).exists()) {
                return appImage
            }
            val cmd = ProcessHandle.current().info().command().orElse(null)
            if (!cmd.isNullOrBlank() && !cmd.endsWith("java")) {
                return cmd
            }
            val javaHome = System.getProperty("java.home")
            if (javaHome != null) {
                val javaHomeDir = File(javaHome)
                val appDir = generateSequence(javaHomeDir) { it.parentFile }
                    .firstOrNull { it.name == "runtime" || it.name == "lib" }
                    ?.parentFile
                if (appDir != null) {
                    val bin = File(appDir, "bin/SimpMusic")
                    if (bin.exists()) return bin.absolutePath
                    val binLower = File(appDir, "bin/simpmusic")
                    if (binLower.exists()) return binLower.absolutePath
                }
            }
            return cmd
        }
        return null
    }

    private fun setWindowsAutostart(enabled: Boolean, startMinimized: Boolean) {
        try {
            if (enabled) {
                val exePath = resolveExePath() ?: run {
                    Logger.e(TAG, "Cannot resolve Windows exe path for autostart")
                    return
                }
                val data = if (startMinimized) "\"$exePath\" --minimized" else "\"$exePath\""
                val escapedData = data.replace("\"", "\\\"")
                val cmdString = "reg add \"$WIN_RUN_KEY\" /f /v \"$WIN_VALUE_NAME\" /t REG_SZ /d \"$escapedData\""
                val process = ProcessBuilder("cmd.exe", "/c", cmdString)
                    .redirectErrorStream(true)
                    .start()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    val output = process.inputStream.bufferedReader().readText()
                    Logger.e(TAG, "Failed to enable autostart on Windows (exit=$exitCode): $output")
                } else {
                    Logger.d(TAG, "Autostart enabled on Windows (minimized=$startMinimized)")
                }
            } else {
                val cmdString = "reg delete \"$WIN_RUN_KEY\" /v \"$WIN_VALUE_NAME\" /f"
                val process = ProcessBuilder("cmd.exe", "/c", cmdString)
                    .redirectErrorStream(true)
                    .start()
                process.waitFor()
                Logger.d(TAG, "Autostart disabled on Windows")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error configuring Windows autostart: ${e.message}")
        }
    }

    private fun setLinuxAutostart(enabled: Boolean, startMinimized: Boolean) {
        try {
            val configHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/.config")
            val autostartDir = File(configHome, "autostart")
            val desktopFile = File(autostartDir, LINUX_DESKTOP_ENTRY)

            if (enabled) {
                val exePath = resolveExePath() ?: run {
                    Logger.e(TAG, "Cannot resolve Linux exe path for autostart")
                    return
                }
                if (!autostartDir.exists()) {
                    autostartDir.mkdirs()
                }
                val execLine = if (startMinimized) "\"$exePath\" --minimized %U" else "\"$exePath\" %U"
                val content = """
                    |[Desktop Entry]
                    |Type=Application
                    |Name=SimpMusic
                    |Comment=SimpMusic Desktop
                    |Exec=$execLine
                    |Icon=simpmusic
                    |Terminal=false
                    |Categories=AudioVideo;Audio;Player;Music;
                    |StartupNotify=false
                """.trimMargin()
                desktopFile.writeText(content)
                Logger.d(TAG, "Autostart enabled on Linux (minimized=$startMinimized)")
            } else {
                if (desktopFile.exists()) {
                    desktopFile.delete()
                }
                Logger.d(TAG, "Autostart disabled on Linux")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error configuring Linux autostart: ${e.message}")
        }
    }
}
