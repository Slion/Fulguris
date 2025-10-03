package fulguris

/**
 * Build configuration object providing convenient access to build information
 * and flavor detection methods.
 *
 * Usage: Build.isFdroid(), Build.isDownload(), etc.
 */
object Variant {
    /**
     * Check if the current build is the F-Droid variant
     */
    fun isFdroid(): Boolean = BuildConfig.FLAVOR_PUBLISHER == "fdroid"

    /**
     * Check if the current build is the Download variant
     */
    fun isDownload(): Boolean = BuildConfig.FLAVOR_PUBLISHER == "download"

    /**
     * Check if the current build is the Playstore variant
     */
    fun isPlayStore(): Boolean = BuildConfig.FLAVOR_PUBLISHER == "playstore"

    /**
     * Check if the current build is a debug build
     */
    fun isDebug(): Boolean = BuildConfig.DEBUG

    /**
     * Check if this is the full version
     */
    fun isFullVersion(): Boolean = BuildConfig.FULL_VERSION

    /**
     * Check if the brand is Slions
     */
    fun isSlions(): Boolean = BuildConfig.FLAVOR_BRAND == "slions"

    /**
     * Check if the brand is Styx
     */
    fun isStyx(): Boolean = BuildConfig.FLAVOR_BRAND == "styx"

}

