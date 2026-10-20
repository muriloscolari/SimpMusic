package com.maxrave.simpmusic.expect

import com.maxrave.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlin.system.exitProcess

actual object PlatformAppUpdater {
    private const val TAG = "AppUpdater"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val appImagePath: String?
        get() = System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() }

    private val appDirPath: String?
        get() = System.getenv("APPDIR")?.takeIf { it.isNotBlank() }

    actual val isSupported: Boolean
        get() = !appImagePath.isNullOrBlank()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    actual val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var targetExecutablePath: String? = null

    actual fun startUpdate(downloadUrl: String?, zsyncUrl: String?) {
        val currentPath = appImagePath
        if (currentPath == null) {
            _updateState.value = UpdateState.Error("Não está sendo executado como AppImage.")
            return
        }

        val currentFile = File(currentPath)
        if (!currentFile.exists()) {
            _updateState.value = UpdateState.Error("Arquivo AppImage não encontrado: $currentPath")
            return
        }

        scope.launch {
            try {
                // Step 1: Try delta update via bundled or system appimageupdatetool
                val updaterBinary = findUpdaterBinary()
                if (updaterBinary != null) {
                    _updateState.value = UpdateState.Downloading(0.1f, "Atualizando delta via zsync...")
                    val success = runDeltaUpdate(updaterBinary, currentFile)
                    if (success) {
                        return@launch
                    }
                    Logger.w(TAG, "Delta update failed or not possible, falling back to direct download")
                }

                // Step 2: Fallback to direct HTTP download of the new AppImage
                if (!downloadUrl.isNullOrBlank()) {
                    _updateState.value = UpdateState.Downloading(0.0f, "Baixando nova versão...")
                    runDirectDownload(downloadUrl, currentFile)
                } else {
                    _updateState.value = UpdateState.Error("Não foi possível encontrar a URL para download da nova versão.")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Update failed: ${e.message}")
                _updateState.value = UpdateState.Error(e.message ?: "Erro desconhecido durante a atualização.")
            }
        }
    }

    private fun findUpdaterBinary(): File? {
        val appDir = appDirPath
        if (appDir != null) {
            val bundled = File(appDir, "bin/appimageupdatetool")
            if (bundled.exists() && bundled.canExecute()) {
                return bundled
            }
        }
        val systemBin = File("/usr/bin/appimageupdatetool")
        if (systemBin.exists() && systemBin.canExecute()) {
            return systemBin
        }
        return null
    }

    private fun runDeltaUpdate(updater: File, currentFile: File): Boolean {
        return try {
            val parentDir = currentFile.parentFile ?: return false
            val initialFiles = parentDir.listFiles()?.map { it.name }?.toSet().orEmpty()

            val process =
                ProcessBuilder(updater.absolutePath, "-r", currentFile.absolutePath)
                    .directory(parentDir)
                    .redirectErrorStream(true)
                    .start()

            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                Logger.d(TAG, "[appimageupdatetool] $line")
                line?.let { l ->
                    if (l.contains("%")) {
                        val match = Regex("""(\d+)%""").find(l)
                        val pct = match?.groupValues?.getOrNull(1)?.toFloatOrNull()
                        if (pct != null) {
                            _updateState.value =
                                UpdateState.Downloading(
                                    pct / 100f,
                                    "Baixando delta zsync: ${pct.toInt()}%",
                                )
                        }
                    }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                // Find newly created AppImage
                val currentFiles = parentDir.listFiles().orEmpty()
                val newFile =
                    currentFiles.firstOrNull { it.isFile && it.name.endsWith(".AppImage") && it.name !in initialFiles }
                        ?: currentFiles.filter { it.isFile && it.name.endsWith(".AppImage") }.maxByOrNull { it.lastModified() }

                if (newFile != null && newFile.exists()) {
                    newFile.setExecutable(true, false)
                    targetExecutablePath = newFile.absolutePath
                    _updateState.value = UpdateState.ReadyToRestart(newFile.absolutePath)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Logger.e(TAG, "runDeltaUpdate exception: ${e.message}")
            false
        }
    }

    private fun runDirectDownload(downloadUrl: String, currentFile: File) {
        val parentDir = currentFile.parentFile ?: File(".")
        val targetFileName = downloadUrl.substringAfterLast('/').ifBlank { currentFile.name }
        val tempFile = File(parentDir, "$targetFileName.download")

        val url = URI(downloadUrl).toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connect()

        if (connection.responseCode !in 200..299) {
            _updateState.value = UpdateState.Error("Erro HTTP ao baixar: ${connection.responseCode} ${connection.responseMessage}")
            return
        }

        val totalLength = connection.contentLengthLong
        var downloaded = 0L

        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    val progress = if (totalLength > 0) downloaded.toFloat() / totalLength.toFloat() else 0.5f
                    val mbDownloaded = downloaded / (1024 * 1024)
                    val mbTotal = totalLength / (1024 * 1024)
                    _updateState.value =
                        UpdateState.Downloading(
                            progress,
                            if (totalLength > 0) "Baixando: $mbDownloaded MB / $mbTotal MB (${(progress * 100).toInt()}%)" else "Baixando: $mbDownloaded MB",
                        )
                }
            }
        }

        tempFile.setExecutable(true, false)

        val destinationFile = File(parentDir, targetFileName)
        if (destinationFile.absolutePath != currentFile.absolutePath && currentFile.exists()) {
            currentFile.delete()
        }
        if (tempFile.absolutePath != destinationFile.absolutePath) {
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            tempFile.renameTo(destinationFile)
        }

        destinationFile.setExecutable(true, false)
        targetExecutablePath = destinationFile.absolutePath
        _updateState.value = UpdateState.ReadyToRestart(destinationFile.absolutePath)
    }

    actual fun restartApp() {
        val path = targetExecutablePath ?: appImagePath ?: return
        try {
            ProcessBuilder(path).start()
            exitProcess(0)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to launch restarted app: ${e.message}")
        }
    }

    actual fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
