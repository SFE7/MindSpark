package com.mindspark.compose

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidConfigTest {
    @Test
    fun manifestContainsRequiredPermissionsAndConfig() {
        val file = if (File("src/main/AndroidManifest.xml").exists()) {
            File("src/main/AndroidManifest.xml")
        } else {
            File("app/src/main/AndroidManifest.xml")
        }
        val manifest = file.readText()
        assertTrue("Expected INTERNET permission", manifest.contains("android.permission.INTERNET"))
        assertTrue("Expected usesCleartextTraffic=\"true\"", manifest.contains("android:usesCleartextTraffic=\"true\""))
    }

    @Test
    fun buildGradleContainsAppAuthRedirectSchemeAndApplicationId() {
        val file = if (File("build.gradle.kts").exists()) {
            File("build.gradle.kts")
        } else {
            File("app/build.gradle.kts")
        }
        val buildGradle = file.readText()
        assertTrue("Expected appAuthRedirectScheme", buildGradle.contains("appAuthRedirectScheme"))
        assertTrue("Expected com.mindspark.compose", buildGradle.contains("com.mindspark.compose"))
    }
}
