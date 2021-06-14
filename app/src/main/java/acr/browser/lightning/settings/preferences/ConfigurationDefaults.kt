package acr.browser.lightning.settings.preferences

/**
 * Provide access to settings default values.
 * That was needed as our instantiated configuration settings can not rely on defaults defined in XML.
 * TODO: Add new methods to support new type.
 */
interface ConfigurationDefaults {

    /**
     * Return the default boolean value for the settings option matching [aKey].
     */
    fun getDefaultBoolean(aKey: String) : Boolean

    /**
     * Return the default integer value for the settings option matching [aKey].
     */
    fun getDefaultInteger(aKey: String) : Int

    /**
     * Provide a map of settings preference keys to their default values.
     */
    fun getDefaults() : Map<String,Any>

}