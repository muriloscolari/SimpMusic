package com.maxrave.simpmusic.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionManagerTest {
    @Test
    fun newerNumericVersionIsOffered() {
        assertTrue(isNewerRelease(current = "2.2.0", tag = "custom-v2.3.0-80807ec", commitHash = "f9e34abe", isWindows = false))
        assertTrue(isNewerRelease(current = "2.2.0", tag = "custom-v2.3.0-80807ec", commitHash = "f9e34abe", isWindows = true))
        assertTrue(isNewerRelease(current = "2.3.0", tag = "v2.3.1", commitHash = "", isWindows = false))
        assertTrue(isNewerRelease(current = "2.3.0", tag = "v2.3.1", commitHash = "", isWindows = true))
        assertTrue(isNewerRelease(current = "2.3.1", tag = "v2.3.2", commitHash = "", isWindows = false))
        assertTrue(isNewerRelease(current = "2.3.1", tag = "v2.3.2", commitHash = "", isWindows = true))
    }

    @Test
    fun olderNumericVersionIsNotOffered() {
        assertFalse(isNewerRelease(current = "2.4.0", tag = "custom-v2.3.0-80807ec", commitHash = "f9e34abe", isWindows = false))
    }

    @Test
    fun sameCommitHashIsNeverOffered() {
        assertFalse(isNewerRelease(current = "2.3.0", tag = "custom-v2.3.0-80807ec", commitHash = "80807ec", isWindows = false))
        assertFalse(isNewerRelease(current = "2.3.0", tag = "custom-v2.3.0-80807ec8", commitHash = "80807ec", isWindows = false))
    }

    @Test
    fun differentCommitHashOnSameVersionIsOfferedOnLinuxAndAndroid() {
        assertTrue(isNewerRelease(current = "2.3.0", tag = "custom-v2.3.0-80807ec", commitHash = "f9e34abe", isWindows = false))
    }

    @Test
    fun differentCommitHashOnSameVersionIsSkippedOnWindowsMsix() {
        assertFalse(isNewerRelease(current = "2.3.0", tag = "custom-v2.3.0-80807ec", commitHash = "f9e34abe", isWindows = true))
    }

    @Test
    fun hotfixTagIsOfferedWhenCurrentIsNotHotfix() {
        assertTrue(isNewerRelease(current = "2.3.0", tag = "v2.3.0-hf", commitHash = "", isWindows = false))
        assertFalse(isNewerRelease(current = "2.3.0-hf", tag = "v2.3.0-hf", commitHash = "", isWindows = false))
    }
}
