package fulguris.settings

import android.content.Context
import fulguris.R
import fulguris.app
import fulguris.settings.preferences.DomainPreferences
import timber.log.Timber
import java.io.File

/**
 * Custom configuration model.
 * By default Fulguris provides only landscape and portrait configuration settings.
 * However users can define preferences specific for a custom configuration.
 * That notably enables specific preferences for foldables inner screen.
 *
 * This object notably provides:
 * - ID and full file name mapping
 * - User friendly name
 * - Method to delete XML shared preferences storage file
 */
class Config(idOrFile: String) {

    val id: String

    init {
        // Allow to create a config object from its id or from its file name
        // Just remove file prefix and suffix to obtain our config id
        id = idOrFile/*.replace(filePrefix, "")*/.replace(fileSuffix,"")
    }

    /**
     * User friendly localized named
     */
    fun name(aContext: Context): String {
        var name = id.replace(filePrefix,  "")
        name = name.replace("-",  " - ")
        name = name.replace(" - sw",  "° - sw")
        name = name.replace("landscape", aContext.getString(R.string.settings_title_landscape))
        name = name.replace("portrait", aContext.getString(R.string.settings_title_portrait))
        return "${name}dp"
    }

    /**
     *
     */
    val fileName: String get() = id

    /**
     *
     */
    val fullFileName: String get () = app.applicationInfo.dataDir + "/shared_prefs/" + fileName + fileSuffix

    /**
     * Delete the settings file belonging to this config.
     */
    fun delete() {
        val file = File(fullFileName)
        Timber.d("Delete ${file.absolutePath}: ${file.delete()}")
    }


    companion object {
        const val filePrefix = "[Config]"
        const val fileSuffix = ".xml"
    }

}