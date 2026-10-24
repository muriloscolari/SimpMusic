package com.maxrave.simpmusic.utils

import com.maxrave.simpmusic.BuildKonfig
import com.maxrave.simpmusic.getPlatform

object VersionManager {
    private var versionName: String? = null

    fun initialize() {
        if (versionName == null) {
            versionName =
                try {
                    BuildKonfig.versionName
                } catch (_: Exception) {
                    String()
                }
        }
    }

    fun getVersionName(): String = removeDevSuffix(versionName ?: String())

    fun getGitCommitHash(): String =
        try {
            BuildKonfig.gitCommitHash
        } catch (_: Exception) {
            ""
        }

    /** True when [tag] names a release newer than this build. */
    fun isBehind(tag: String): Boolean =
        isNewerRelease(
            current = getVersionName(),
            tag = tag,
            isWindows = getPlatform().osName().lowercase().contains("windows"),
        )

    private fun removeDevSuffix(versionName: String): String {
        return if (versionName.endsWith("-dev")) {
            versionName.replace("-dev", "")
        } else {
            versionName
        }
    }
}

/**
 * True when release [tag] should be offered to a build running [current]. GitHub tags carry a leading
 * "v" ("v2.3.0") and F-Droid's do not ("2.3.0"). It is offered unless it is this very version or this
 * build is already ahead of it (dev and beta builds run ahead of the latest release). A hotfix
 * ("2.3.0-hf") has the same numbers as the release it fixes, so only its name tells it apart.
 */
internal fun isNewerRelease(
    current: String,
    tag: String,
    commitHash: String = VersionManager.getGitCommitHash(),
    isWindows: Boolean = false,
): Boolean {
    val cleanTag = tag.trim().removePrefix("custom-").removePrefix("v")
    val cleanCurrent = current.trim().removePrefix("custom-").removePrefix("v")

    if (commitHash.isNotEmpty() && tag.contains(commitHash)) {
        return false
    }

    val versionComparison = compareVersions(cleanCurrent, cleanTag)
    if (versionComparison < 0) return true
    if (versionComparison > 0) return false

    // When base numeric versions match (e.g. 2.3.0 vs custom-v2.3.0-<sha>):
    // On Linux AppImage and Android, custom CI builds can replace any commit build with the same
    // base version. On Windows MSIX, Windows Package Manager refuses in-place upgrades when the
    // 4-part numeric MSIX version hasn't increased, so skip same-version commit updates on Windows.
    if (!isWindows && tag.startsWith("custom-v") && commitHash.isNotEmpty() && !tag.endsWith(commitHash)) {
        return true
    }

    // When base numeric versions match (e.g. 1.0.1 vs 1.0.1-hf, or 0.1.5-beta vs 0.1.5):
    val tagIsHotfix = cleanTag.contains("-hf") || cleanTag.contains("-hotfix")
    val currentIsHotfix = cleanCurrent.contains("-hf") || cleanCurrent.contains("-hotfix")
    if (tagIsHotfix && !currentIsHotfix) return true

    val currentIsPrerelease = cleanCurrent.contains("-beta") || cleanCurrent.contains("-alpha") || cleanCurrent.contains("-dev")
    val tagIsPrerelease = cleanTag.contains("-beta") || cleanTag.contains("-alpha") || cleanTag.contains("-dev")
    if (currentIsPrerelease && !tagIsPrerelease && !tagIsHotfix) return true

    return false
}

/** Dotted versions compared number by number, so "2.10.0" is newer than "2.9.0". A suffix such as "-hf" is ignored. */
internal fun compareVersions(
    a: String,
    b: String,
): Int {
    val x = versionNumbers(a)
    val y = versionNumbers(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val diff = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}

private fun versionNumbers(version: String): List<Int> = version.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }
