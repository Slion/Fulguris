package fulguris.browser

/**
 * Defines which menu a menu item should appear in by default
 * Note: Enum names are persisted in SharedPreferences, so renaming requires migration
 */
enum class MenuType {
    MainMenu,
    TabMenu,
    HiddenMenu,     // Items hidden by user customization
    FullMenu        // Full menu mode showing all non-optional items (not used for defaultMenu)
}