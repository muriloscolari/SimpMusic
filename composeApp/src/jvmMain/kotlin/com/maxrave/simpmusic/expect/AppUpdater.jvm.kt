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

    private val isWindows: Boolean
        get() = System.getProperty("os.name")?.lowercase()?.contains("windows") == true

    private val appImagePath: String?
        get() = System.getenv("APPIMAGE")?.takeIf { it.isNotBlank() }

    private val appDirPath: String?
        get() = System.getenv("APPDIR")?.takeIf { it.isNotBlank() }

    actual val isSupported: Boolean
        get() = isWindows || !appImagePath.isNullOrBlank()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    actual val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var targetExecutablePath: String? = null
    private var downloadedMsixPath: String? = null

    actual fun startUpdate(
        downloadUrl: String?,
        zsyncUrl: String?,
        msixUrl: String?,
        appInstallerUrl: String?,
    ) {
        if (isWindows) {
            startWindowsUpdate(msixUrl ?: downloadUrl, appInstallerUrl)
        } else {
            startLinuxAppImageUpdate(downloadUrl, zsyncUrl)
        }
    }

    private fun startWindowsUpdate(msixUrl: String?, appInstallerUrl: String?) {
        scope.launch {
            try {
                if (!msixUrl.isNullOrBlank()) {
                    _updateState.value = UpdateState.Downloading(0.0f, "Baixando atualização do Windows (.msix)...")
                    val tempDir = File(System.getProperty("java.io.tmpdir"))
                    val targetFile = File(tempDir, "simpmusic-update.msix")
                    downloadFileWithProgress(msixUrl, targetFile)
                    downloadedMsixPath = targetFile.absolutePath
                    _updateState.value = UpdateState.ReadyToRestart(targetFile.absolutePath)
                } else if (!appInstallerUrl.isNullOrBlank()) {
                    _updateState.value = UpdateState.Downloading(0.5f, "Disparando atualização via AppInstaller...")
                    val psScript = "Add-AppxPackage -AppInstallerFile '$appInstallerUrl' -ForceApplicationShutdown"
                    val process = ProcessBuilder("powershell", "-NoProfile", "-Command", psScript).start()
                    val exit = process.waitFor()
                    if (exit == 0) {
                        _updateState.value = UpdateState.ReadyToRestart("appinstaller")
                    } else {
                        _updateState.value = UpdateState.Error("Falha ao atualizar via AppInstaller (código $exit).")
                    }
                } else {
                    _updateState.value = UpdateState.Error("Nenhum pacote de instalação do Windows (.msix) foi encontrado nesta versão.")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Windows update failed: ${e.message}")
                _updateState.value = UpdateState.Error(e.message ?: "Erro ao atualizar no Windows.")
            }
        }
    }

    private fun startLinuxAppImageUpdate(downloadUrl: String?, zsyncUrl: String?) {
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

    private fun downloadFileWithProgress(urlStr: String, destinationFile: File) {
        val tempFile = File(destinationFile.parentFile, destinationFile.name + ".download")
        var currentUrl = urlStr
        var redirects = 0
        var connection: HttpURLConnection

        while (true) {
            val url = URI(currentUrl).toURL()
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "SimpMusic-Desktop-Updater")
            connection.connect()

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || ++redirects > 5) {
                    throw IllegalStateException("Redirecionamento HTTP inválido ($code)")
                }
                currentUrl = URI(currentUrl).resolve(location).toString()
                continue
            }
            break
        }

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Erro HTTP ${connection.responseCode}: ${connection.responseMessage}")
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
        try {
            java.nio.file.Files.move(
                tempFile.toPath(),
                destinationFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
        }
        destinationFile.setExecutable(true, false)
    }

    private fun runDirectDownload(downloadUrl: String, currentFile: File) {
        val parentDir = currentFile.parentFile ?: File(".")
        val targetFileName = downloadUrl.substringAfterLast('/').ifBlank { currentFile.name }
        val destinationFile = File(parentDir, targetFileName)

        downloadFileWithProgress(downloadUrl, destinationFile)
        destinationFile.setExecutable(true, false)

        if (destinationFile.absolutePath != currentFile.absolutePath && currentFile.exists()) {
            currentFile.delete()
        }

        targetExecutablePath = destinationFile.absolutePath
        _updateState.value = UpdateState.ReadyToRestart(destinationFile.absolutePath)
    }

    actual fun restartApp() {
        if (isWindows) {
            val msix = downloadedMsixPath
            try {
                if (msix != null && File(msix).exists()) {
                    val psScript = "Start-Sleep -Milliseconds 800; try { Add-AppxPackage -Path '$msix' -ForceApplicationShutdown -ForceUpdateFromAnyVersion -ErrorAction Stop } catch { Get-AppxPackage -Name '*Simpmusic*' -ErrorAction SilentlyContinue | Remove-AppxPackage -ErrorAction SilentlyContinue; Add-AppxPackage -Path '$msix' -ForceApplicationShutdown -ErrorAction SilentlyContinue }; start 'shell:appsFolder\\Simpmusic_ejp2bhxmz1qq6!Simpmusic'"
                    ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psScript).start()
                } else {
                    val psScript = "Start-Sleep -Milliseconds 800; start 'shell:appsFolder\\Simpmusic_ejp2bhxmz1qq6!Simpmusic'"
                    ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", psScript).start()
                }
                exitProcess(0)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to restart Windows app: ${e.message}")
            }
            return
        }

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
