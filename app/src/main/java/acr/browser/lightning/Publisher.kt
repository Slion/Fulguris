package acr.browser.lightning

/**
 * Define build config publishers.
 */
enum class Publisher(val value: String) {
    DOWNLOAD("download"),
    PLAYSTORE("playstore"),
    FDROID("fdroid");

    override fun toString(): String {
        return this.value
    }
}