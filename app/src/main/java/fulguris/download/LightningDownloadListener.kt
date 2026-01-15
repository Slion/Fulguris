/*
 * Copyright 2014 A.C.R. Development
 */
package fulguris.download

import fulguris.R
import fulguris.activity.WebBrowserActivity
import fulguris.database.downloads.DownloadsRepository
import fulguris.di.HiltEntryPoint
import fulguris.di.configPrefs
import fulguris.extensions.KDuration
import fulguris.extensions.makeSnackbar
import fulguris.extensions.snackbar
import fulguris.settings.preferences.UserPreferences
import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import android.view.Gravity
import android.webkit.DownloadListener
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.text.parseAsHtml
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.EntryPointAccessors
import fulguris.app
import fulguris.extensions.launch
import fulguris.permissions.PermissionsManager
import fulguris.permissions.PermissionsResultAction
import timber.log.Timber

//@AndroidEntryPoint
class LightningDownloadListener     //Injector.getInjector(context).inject(this);
    (private val mActivity: Activity) : BroadcastReceiver(),
    DownloadListener {

    // Could not get injection working in broadcast receiver
    private val hiltEntryPoint = EntryPointAccessors.fromApplication(app, HiltEntryPoint::class.java)

    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val downloadHandler: fulguris.download.DownloadHandler = hiltEntryPoint.downloadHandler
    val downloadManager: DownloadManager = hiltEntryPoint.downloadManager
    val downloadsRepository: DownloadsRepository = hiltEntryPoint.downloadsRepository

    // From BroadcastReceiver
    // We use this to receive download complete notifications
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            //Fetching the download id received with the broadcast
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            //Checking if the received broadcast is for our enqueued download by matching download id
            // TODO: what if we have multiple downloads going on? I doubt our architecture supports that properly for now.
            if (downloadHandler.iDownloadId == id) {

                // Our download is complete check if it was a success
                val q = DownloadManager.Query()
                q.setFilterById(id)
                val c = downloadManager.query(q)
                var contentTitle = ""
                var contentText: String? = ""
                var success = false
                if (c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        success = true
                        contentTitle = context.getString(R.string.download_complete)
                        val filePath =
                            c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                        contentText =
                            filePath.substring(filePath.lastIndexOf('/') + 1, filePath.length)
                    }
                    // Assume failure
                    //if (status == DownloadManager.STATUS_FAILED) {
                    // That stupidly returns "placeholder" on F(x)tec Pro1
                    //filename = c.getString(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                }
                c.close()

                // Create system notification
                // Passing a null intent in case of failure means nothing happens when user taps our notification
                // User needs to dismiss it using swipe
                var pendingIntent: PendingIntent? = null
                var downloadsIntent: Intent? = null
                if (!success) {
                    contentTitle = context.getString(R.string.download_failed)
                    contentText = downloadHandler.iFilename
                } else {
                    // Create pending intent to open downloads folder when tapping notification
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    downloadsIntent =
                        fulguris.utils.Utils.getIntentForDownloads(mActivity, userPreferences.downloadDirectory)
                    pendingIntent = PendingIntent.getActivity(mActivity, 0, downloadsIntent, flags)
                }
                val builder =
                    NotificationCompat.Builder(mActivity, (mActivity as WebBrowserActivity).CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_download_outline) // TODO: different icon for failure?
                        .setContentTitle(contentTitle) //
                        .setContentText(contentText) //
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Set the intent that will fire when the user taps the notification
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                val notificationManager = NotificationManagerCompat.from(mActivity)
                // notificationId is a unique int for each notification that you must define
                notificationManager.notify(0, builder.build())

                //Show a snackbar with a link to open the downloads list
                if (success) {
                    mActivity.makeSnackbar(
                        contentTitle,
                        KDuration,
                        if (mActivity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM
                    ).setAction(
                        R.string.show
                    ) {
                        @Suppress("USELESS_CAST")
                        (mActivity as WebBrowserActivity).openDownloads()
                    }.show()
                } else {
                    mActivity.snackbar(
                        contentTitle,
                        if (mActivity.configPrefs.toolbarsBottom) Gravity.TOP else Gravity.BOTTOM
                    )
                }
            }
        }
    }

    private fun getFileName(id: Long): String {
        val q = DownloadManager.Query()
        q.setFilterById(id)
        val c = downloadManager.query(q)
        var filename = ""
        if (c.moveToFirst()) {
            val status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val filePath = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                filename = filePath.substring(filePath.lastIndexOf('/') + 1, filePath.length)
            } else if (status == DownloadManager.STATUS_FAILED) {
                // That stupidly returns "placeholder" on F(x)tec Pro1
                //filename = c.getString(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                filename = "Failed"
            }
        }
        c.close()
        return filename
    }

    override fun onDownloadStart(
        url: String, userAgent: String,
        contentDisposition: String, mimetype: String, contentLength: Long
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // No permissions needed anymore from Android 13
            doDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength)
        } else {
            PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(mActivity, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
                object : PermissionsResultAction() {
                    override fun onGranted() {
                        doDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength)
                    }

                    override fun onDenied(permission: String) {
                        //TODO show message
                    }
                })
        }


        // Some download link spawn an empty tab, just close it then
        if (mActivity is WebBrowserActivity) {
            mActivity.closeCurrentTabIfEmpty()
        }
    }

    private fun doDownloadStart(
        url: String, userAgent: String,
        contentDisposition: String, mimetype: String, contentLength: Long
    ) {
        // Get original filename WITHOUT extension changes to check for mismatches
        val originalFileName = fulguris.utils.guessFileNameWithoutExtensionChange(url, contentDisposition, mimetype, null)

        val downloadSize: String = if (contentLength > 0) {
            Formatter.formatFileSize(mActivity, contentLength)
        } else {
            mActivity.getString(R.string.unknown_file_size)
        }

        val builder = MaterialAlertDialogBuilder(mActivity)

        // Build descriptive message following MD3 guidelines
        // If server sends generic octet-stream, infer MIME type from file extension
        // Notably the case for Fulguris APK download from slions.net
        val mimeTypeDetectedFromExtension = mimetype == "application/octet-stream" || mimetype.isBlank()
        val fileType = when {
            mimeTypeDetectedFromExtension -> {
                val extension = originalFileName.substringAfterLast('.', "").lowercase()
                if (extension.isNotEmpty()) {
                    val detectedMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    Timber.d("Server sent octet-stream for .$extension file, detected MIME type: $detectedMimeType")
                    detectedMimeType ?: mimetype.ifEmpty { mActivity.getString(R.string.unknown_file_type) }
                } else {
                    mimetype.ifEmpty { mActivity.getString(R.string.unknown_file_type) }
                }
            }
            mimetype.isNotEmpty() -> mimetype
            else -> mActivity.getString(R.string.unknown_file_type)
        }

        Timber.d("Final MIME type for display: $fileType (original server MIME: $mimetype)")

        // Only check for extension mismatch if server provided a real MIME type (not octet-stream)
        // If we detected type from extension, don't offer "Download as" button since we already fixed it
        val (hasMismatch, correctedFilename) = if (!mimeTypeDetectedFromExtension) {
            fulguris.utils.hasExtensionMismatch(originalFileName, mimetype)
        } else {
            Pair(false, null)
        }

        // Parameters: filename, type, size (size is last)
        val message = mActivity.getString(R.string.dialog_download_message, originalFileName, fileType, downloadSize)

        // Use question as title per MD3 guidelines
        val dialog: Dialog = builder.setIcon(R.drawable.ic_download_outline)
            .setTitle(R.string.dialog_download_title)
            .setMessage(message.parseAsHtml())
            .setPositiveButton(
                mActivity.resources.getString(R.string.action_download)
            ) { _, _ ->
                downloadHandler.onDownloadStart(
                    mActivity,
                    userPreferences,
                    url,
                    userAgent,
                    contentDisposition,
                    mimetype,
                    downloadSize
                )
            }
            .apply {
                // Add neutral button if there's an extension mismatch
                if (hasMismatch && correctedFilename != null) {
                    // Extract just the extension for the button label
                    val correctedExt = correctedFilename.substringAfterLast('.').uppercase()
                    setNeutralButton(
                        mActivity.getString(R.string.download_as_format, correctedExt)
                    ) { _, _ ->
                        // Download with corrected filename
                        downloadHandler.onDownloadStartWithFilename(
                            mActivity,
                            userPreferences,
                            url,
                            userAgent,
                            contentDisposition,
                            mimetype,
                            downloadSize,
                            correctedFilename
                        )
                    }
                }
            }
            .setNegativeButton(
                mActivity.resources.getString(R.string.action_cancel)
            ) { _, _ -> }
            .launch()
        Timber.d("Downloading: $originalFileName (mimetype: $mimetype, hasMismatch: $hasMismatch, correctedFilename: $correctedFilename)")
    }

}

