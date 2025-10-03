@file:JvmName("BuildConfigUtils")

package fulguris.extensions

import fulguris.BuildConfig

/**
 * Extension functions for BuildConfig to provide convenient flavor and build type checks
 */

/**
 * Check if the current build is the F-Droid variant
 */
fun isFdroid(): Boolean = BuildConfig.FLAVOR.contains("fdroid")

/**
 * Check if the current build is the Download variant
 */
fun isDownload(): Boolean = BuildConfig.FLAVOR.contains("download")

/**
 * Check if the current build is the Playstore variant
 */
fun isPlaystore(): Boolean = BuildConfig.FLAVOR.contains("playstore")

/**
 * Check if the current build is a debug build
 */
fun isDebug(): Boolean = BuildConfig.DEBUG
