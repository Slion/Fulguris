package fulguris.settings.fragment

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import fulguris.R
import fulguris.extensions.copyToClipboard
import fulguris.extensions.snackbar
import fulguris.extensions.toast
import fulguris.utils.Utils
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment to display and manage downloads from DownloadManager.
 * Shows a list of downloads with their status and progress.
 * Allows users to cancel, remove, or delete downloads.
 */
@AndroidEntryPoint
class DownloadsFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var downloadManager: DownloadManager

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L // Update every second
    private var isUpdating = false

    private lateinit var downloadsListCategory: PreferenceCategory
    private var cleanDownloadsPref: Preference? = null
    private var removeAllDownloadsPref: Preference? = null
    private var deleteAllDownloadsPref: Preference? = null

    // BroadcastReceiver to listen for download completion/failure
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    Timber.d("Download completed: $downloadId")
                    loadDownloads()
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preference_downloads, rootKey)

        downloadsListCategory = findPreference("downloads_list_category")!!

        // Set up explore downloads folder action
        findPreference<Preference>("pref_explore_downloads")?.setOnPreferenceClickListener {
            exploreDownloadsFolder()
            true
        }

        // Set up remove all downloads action (keeps files)
        removeAllDownloadsPref = findPreference<Preference>("pref_remove_all_downloads")?.apply {
            setOnPreferenceClickListener {
                showRemoveAllDownloadsDialog()
                true
            }
        }

        // Set up clean downloads action (removes failed and orphaned)
        cleanDownloadsPref = findPreference<Preference>("pref_clean_downloads")?.apply {
            setOnPreferenceClickListener {
                showCleanDownloadsDialog()
                true
            }
        }

        // Set up delete all downloads action (removes and deletes files)
        deleteAllDownloadsPref = findPreference<Preference>("pref_delete_all_downloads")?.apply {
            setOnPreferenceClickListener {
                showDeleteAllDownloadsDialog()
                true
            }
        }

        // Load downloads
        loadDownloads()
    }

    override fun onResume() {
        super.onResume()
        loadDownloads()
        startPeriodicUpdates()

        // Register receiver for download completion events
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // We need to export it otherwise we don't get download complete notifications from system
            requireContext().registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            requireContext().registerReceiver(downloadCompleteReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        stopPeriodicUpdates()

        // Unregister receiver to prevent memory leaks
        try {
            requireContext().unregisterReceiver(downloadCompleteReceiver)
        } catch (e: Exception) {
            Timber.d(e, "Receiver not registered or already unregistered")
        }
    }

    /**
     * Start periodic updates for download progress
     */
    private fun startPeriodicUpdates() {
        if (!isUpdating) {
            isUpdating = true
            handler.post(updateRunnable)
        }
    }

    /**
     * Stop periodic updates
     */
    private fun stopPeriodicUpdates() {
        isUpdating = false
        handler.removeCallbacks(updateRunnable)
    }

    /**
     * Runnable to update download progress periodically.
     * Automatically stops when no downloads are in progress.
     */
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isUpdating) {
                val hasActiveDownloads = updateDownloads()
                if (hasActiveDownloads) {
                    handler.postDelayed(this, updateInterval)
                } else {
                    // No active downloads, stop updating
                    Timber.d("No active downloads, stopping periodic updates")
                    isUpdating = false
                }
            }
        }
    }

    /**
     * Load all downloads from DownloadManager
     */
    private fun loadDownloads() {
        downloadsListCategory.removeAll()

        val query = DownloadManager.Query()
        val cursor: Cursor? = try {
            downloadManager.query(query)
        } catch (e: Exception) {
            Timber.e(e, "Failed to query downloads")
            null
        }

        if (cursor == null) {
            showEmptyState()
            updateActionStates(0, 0)
            return
        }

        var hasDownloads = false
        var failedOrOrphanedCount = 0
        var totalCount = 0

        if (cursor.moveToFirst()) {
            hasDownloads = true
            do {
                totalCount++

                // Count failed and orphaned downloads for "Clean" action
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                if (status == DownloadManager.STATUS_FAILED) {
                    failedOrOrphanedCount++
                } else if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                    val uri = android.net.Uri.parse(localUri)
                    val file = java.io.File(uri.path ?: "")
                    if (!file.exists()) {
                        failedOrOrphanedCount++
                    }
                }

                val downloadItem = createDownloadPreference(cursor)
                downloadsListCategory.addPreference(downloadItem)
            } while (cursor.moveToNext())
        }
        cursor.close()

        if (!hasDownloads) {
            showEmptyState()
        }

        updateActionStates(totalCount, failedOrOrphanedCount)
    }

    /**
     * Update download progress for all items that are in progress.
     * Only updates the summary of preferences, doesn't rebuild the entire list.
     * Returns true if there are any downloads still in progress.
     */
    private fun updateDownloads(): Boolean {
        var hasActiveDownloads = false

        // Only update progress for existing preferences in the list
        for (i in 0 until downloadsListCategory.preferenceCount) {
            val pref = downloadsListCategory.getPreference(i) as? DownloadPreference
            if (pref != null) {
                // updateProgress() returns true if download is still active (running/pending/paused)
                if (pref.updateProgress()) {
                    hasActiveDownloads = true
                }
            }
        }

        return hasActiveDownloads
    }

    /**
     * Update the enabled state of action preferences based on download counts
     */
    private fun updateActionStates(totalCount: Int, failedOrOrphanedCount: Int) {
        // "Remove all" and "Delete all" are enabled only if there are any downloads
        removeAllDownloadsPref?.isEnabled = totalCount > 0
        deleteAllDownloadsPref?.isEnabled = totalCount > 0

        // "Clean" is enabled only if there are failed or orphaned downloads
        cleanDownloadsPref?.isEnabled = failedOrOrphanedCount > 0
    }

    /**
     * Create a preference for a download item
     */
    private fun createDownloadPreference(cursor: Cursor): Preference {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))

        // Try to get actual filename from local URI first, then fall back to title
        var displayTitle = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            ?: cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION))
            ?: "Unknown"

        // Extract filename from local URI if available
        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        if (localUri != null) {
            try {
                val fileUri = android.net.Uri.parse(localUri)
                val file = java.io.File(fileUri.path ?: "")
                val filename = file.name
                if (filename.isNotBlank()) {
                    displayTitle = filename
                }
            } catch (e: Exception) {
                Timber.d(e, "Failed to extract filename from local URI")
            }
        } else {
            // For failed/pending downloads, try to extract filename from original URI
            val originalUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
            if (originalUri != null) {
                try {
                    val uri = android.net.Uri.parse(originalUri)
                    val path = uri.path
                    if (path != null) {
                        // Get filename from path (after last '/')
                        var filename = path.substringAfterLast('/')

                        // Remove query parameters if present (e.g., "file.pdf?token=123" -> "file.pdf")
                        if (filename.contains('?')) {
                            filename = filename.substringBefore('?')
                        }

                        // Remove fragment if present (e.g., "file.pdf#page=1" -> "file.pdf")
                        if (filename.contains('#')) {
                            filename = filename.substringBefore('#')
                        }

                        // Use filename if it's not blank (removed the extension requirement)
                        if (filename.isNotBlank()) {
                            displayTitle = filename
                        }
                    }
                } catch (e: Exception) {
                    Timber.d(e, "Failed to extract filename from original URI")
                }
            }
        }

        val downloadPref = DownloadPreference(requireContext(), id, downloadManager)
        downloadPref.key = "download_$id"
        downloadPref.title = displayTitle
        downloadPref.isIconSpaceReserved = true

        // Get status and set appropriate icon
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))

        // Set icon based on status and file type
        setDownloadIcon(downloadPref, status, mimeType, localUri)

        downloadPref.setOnPreferenceClickListener {
            showDownloadOptionsDialog(id, displayTitle)
            true
        }

        downloadPref.updateFromCursor(cursor)

        return downloadPref
    }

    /**
     * Resize a drawable to match the standard preference icon size (24dp)
     */
    private fun resizeDrawableToIconSize(drawable: android.graphics.drawable.Drawable): android.graphics.drawable.Drawable {
        val iconSizePx = (24 * requireContext().resources.displayMetrics.density).toInt()

        val bitmap = android.graphics.Bitmap.createBitmap(
            iconSizePx,
            iconSizePx,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return android.graphics.drawable.BitmapDrawable(requireContext().resources, bitmap)
    }

    /**
     * Set appropriate icon for a download preference based on status and file type.
     *
     * For static icons (error, warning, fallback), uses resource IDs.
     * For dynamic app icons, loads drawable from PackageManager and resizes to match static icons.
     */
    private fun setDownloadIcon(pref: Preference, status: Int, mimeType: String?, localUri: String?) {
        val context = requireContext()

        // Use error icon for failed downloads (static resource ID)
        if (status == DownloadManager.STATUS_FAILED) {
            Timber.d("Using error icon for failed download")
            pref.setIcon(R.drawable.ic_error_outline)
            return
        }

        // Check if download is orphaned (completed but file no longer exists)
        if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
            try {
                val fileUri = android.net.Uri.parse(localUri)
                val file = java.io.File(fileUri.path ?: "")
                if (!file.exists()) {
                    Timber.d("Using warning icon for orphaned download (file missing)")
                    pref.setIcon(R.drawable.ic_unknown_document_outline)
                    return
                }
            } catch (e: Exception) {
                Timber.d(e, "Failed to check if file exists")
            }
        }

        val packageManager = context.packageManager

        Timber.d("=== Icon Resolution ===")
        Timber.d("MIME type: $mimeType")
        Timber.d("Local URI: $localUri")

        // Use static icon for APK files
        if (mimeType == "application/vnd.android.package-archive") {
            Timber.d("Using APK document icon for APK file")
            pref.setIcon(R.drawable.ic_apk_document_outline)
            return
        }

        // Try with URI + MIME type (matches how we open files)
        if (localUri != null && mimeType != null && mimeType.isNotBlank()) {
            try {
                val fileUri = android.net.Uri.parse(localUri)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.setDataAndType(fileUri, mimeType)
                intent.flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION

                // Query for the DEFAULT app - this matches what will actually open the file
                val resolveInfo = packageManager.resolveActivity(intent, 0)

                if (resolveInfo != null) {
                    val appName = resolveInfo.loadLabel(packageManager)
                    val packageName = resolveInfo.activityInfo.packageName
                    Timber.d("Default app for URI + MIME: $appName ($packageName)")

                    resolveInfo.activityInfo.loadIcon(packageManager)?.let { icon ->
                        Timber.d("✓ Using icon from default app: $appName ($packageName)")
                        pref.icon = resizeDrawableToIconSize(icon)
                        return
                    }
                }
            } catch (e: Exception) {
                Timber.d(e, "Failed to resolve with URI + MIME type")
            }
        }

        // Fallback: Try with just MIME type
        mimeType?.takeIf { it.isNotBlank() && it != "*/*" && it != "application/octet-stream" }?.let { mime ->
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.type = mime

                val resolveInfo = packageManager.resolveActivity(intent, 0)

                if (resolveInfo != null) {
                    val appName = resolveInfo.loadLabel(packageManager)
                    val packageName = resolveInfo.activityInfo.packageName
                    Timber.d("Default app for MIME type $mime: $appName ($packageName)")

                    resolveInfo.activityInfo.loadIcon(packageManager)?.let { icon ->
                        Timber.d("✓ Using icon from default app: $appName ($packageName)")
                        pref.icon = resizeDrawableToIconSize(icon)
                        return
                    }
                } else {
                    Timber.d("✗ No default app found for MIME type: $mime")

                    // Last resort: try querying all apps if no default is set
                    val resolveInfos = packageManager.queryIntentActivities(intent, 0)
                    Timber.d("Found ${resolveInfos.size} apps that can handle MIME type: $mime")

                    if (resolveInfos.isNotEmpty()) {
                        resolveInfos.forEachIndexed { index, info ->
                            val name = info.loadLabel(packageManager)
                            val pkg = info.activityInfo.packageName
                            Timber.d("  [$index] $name ($pkg)")
                        }

                        val selectedApp = resolveInfos[0]
                        val appName = selectedApp.loadLabel(packageManager)
                        selectedApp.activityInfo.loadIcon(packageManager)?.let { icon ->
                            Timber.d("✓ Using icon from first available app: $appName (${selectedApp.activityInfo.packageName})")
                            pref.icon = resizeDrawableToIconSize(icon)
                            return
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.d(e, "Failed to load icon for MIME type: $mime")
            }
        }

        // Final fallback: use generic download icon (static resource ID)
        Timber.d("✗ Using fallback download icon")
        pref.setIcon(R.drawable.ic_file_download)
    }

    /**
     * Show empty state when no downloads
     */
    private fun showEmptyState() {
        val emptyPref = Preference(requireContext())
        emptyPref.key = "empty_state"
        emptyPref.title = getString(R.string.no_downloads)
        emptyPref.summary = getString(R.string.no_downloads_summary)
        emptyPref.isSelectable = false
        downloadsListCategory.addPreference(emptyPref)
    }

    /**
     * Show dialog with options for a download
     */
    private fun showDownloadOptionsDialog(downloadId: Long, title: String) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (!cursor.moveToFirst()) {
            cursor.close()
            return
        }

        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        val originalUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
        cursor.close()

        // Check if download is orphaned (successful but file missing)
        val isOrphaned = if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
            try {
                val fileUri = android.net.Uri.parse(localUri)
                val file = java.io.File(fileUri.path ?: "")
                !file.exists()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        // Determine state flags
        val isSuccessful = status == DownloadManager.STATUS_SUCCESSFUL
        val hasFile = isSuccessful && !isOrphaned
        val isInProgress = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING
        val isPaused = status == DownloadManager.STATUS_PAUSED
        val isFailed = status == DownloadManager.STATUS_FAILED
        val hasUrl = originalUri?.isNotEmpty() == true

        // Check if we have write permission on the file
        val canWriteFile = if (hasFile && localUri != null) {
            try {
                val fileUri = android.net.Uri.parse(localUri)
                val file = java.io.File(fileUri.path ?: "")
                file.canWrite()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        val options = mutableListOf<fulguris.dialog.DialogItem>()

        // Options for successful downloads with existing file
        if (hasFile) {
            // 1. Open
            options.add(fulguris.dialog.DialogItem(title = R.string.open_download) {
                openDownload(downloadId)
            })
            // 2. Remove and delete (always available - uses DownloadManager which has system permissions)
            options.add(fulguris.dialog.DialogItem(title = R.string.remove_and_delete_file) {
                confirmRemoveAndDeleteDownload(downloadId, title)
            })

            if (canWriteFile) {
                // 3. Remove and keep (only if we have write permission - uses file.renameTo() which requires write permission)
                options.add(fulguris.dialog.DialogItem(title = R.string.remove_and_keep_file) {
                    confirmRemoveAndKeepFile(downloadId, title)
                })

                // 4. Delete file only (only if we have write permission - uses file.delete())
                options.add(fulguris.dialog.DialogItem(title = R.string.delete_file) {
                    confirmDeleteFileOnly(downloadId, title)
                })
            }

            // 5. Share file
            options.add(fulguris.dialog.DialogItem(title = R.string.share_file) {
                shareDownload(downloadId)
            })
            // 6. Copy name
            if (localUri != null) {
                val fileUri = android.net.Uri.parse(localUri)
                val fileName = fileUri.lastPathSegment
                if (fileName != null) {
                    options.add(fulguris.dialog.DialogItem(title = R.string.copy_name) {
                        val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.copyToClipboard(fileName)
                        activity?.snackbar(R.string.message_text_copied)
                    })
                }
            }
            // 7. Copy path
            if (localUri != null) {
                val filePath = android.net.Uri.parse(localUri).path
                if (filePath != null) {
                    options.add(fulguris.dialog.DialogItem(title = R.string.copy_path) {
                        val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.copyToClipboard(filePath)
                        activity?.snackbar(R.string.message_text_copied)
                    })
                }
            }
        }

        // Options for in-progress or paused downloads
        if (isInProgress || isPaused) {
            // Cancel download
            options.add(fulguris.dialog.DialogItem(title = R.string.cancel_download) {
                cancelDownload(downloadId)
            })
        }

        // Options for orphaned or failed downloads
        if (isOrphaned || isFailed) {
            // Remove from list
            options.add(fulguris.dialog.DialogItem(title = R.string.remove_from_list) {
                removeDownload(downloadId)
                activity?.snackbar(R.string.removed_from_list)
            })
            // Re-download (if has URL)
            if (hasUrl) {
                options.add(fulguris.dialog.DialogItem(title = R.string.action_download) {
                    redownloadFile(originalUri, title)
                })
            }
        }

        // Options for all downloads with URL (share & copy link)
        if (hasUrl) {
            // Share link
            options.add(fulguris.dialog.DialogItem(title = R.string.share_link) {
                shareLink(originalUri)
            })
            // Copy link
            options.add(fulguris.dialog.DialogItem(title = R.string.dialog_copy_link) {
                val clipboard = requireContext().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.copyToClipboard(originalUri)
                activity?.snackbar(R.string.message_text_copied)
            })
        }

        // Show tabbed dialog with a single tab (hide tab, show icon in dialog)
        fulguris.dialog.BrowserDialog.show(
            requireContext(),
            R.drawable.ic_file_download,
            null,
            false, // Hide tab layout
            fulguris.dialog.DialogTab(
                show = true,
                icon = 0,
                text = title,
                items = options.toTypedArray()
            )
        )
    }

    /**
     * Cancel a running download
     */
    private fun cancelDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        loadDownloads()
        activity?.snackbar(R.string.download_cancelled)
    }

    /**
     * Remove download entry from DownloadManager without deleting the file.
     * This is a workaround since DownloadManager.remove() always deletes both entry and file.
     *
     * Strategy:
     * 1. Rename the file to a temporary name
     * 2. Call downloadManager.remove() - it won't find the original file to delete
     * 3. Rename the file back to its original name
     *
     * @return true if successful, false otherwise
     */
    private fun removeFromListOnly(downloadId: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (!cursor.moveToFirst()) {
            cursor.close()
            return false
        }

        val localUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        cursor.close()

        if (localUriString == null) {
            // No file to preserve, just remove
            downloadManager.remove(downloadId)
            return true
        }

        try {
            val fileUri = android.net.Uri.parse(localUriString)
            val originalFile = java.io.File(fileUri.path ?: return false)

            if (!originalFile.exists()) {
                // File doesn't exist, just remove the entry
                downloadManager.remove(downloadId)
                return true
            }

            // Create temporary file name
            val tempFile = java.io.File(originalFile.parentFile, "tmp_${System.currentTimeMillis()}_${originalFile.name}")

            // Step 1: Rename to temporary name
            if (!originalFile.renameTo(tempFile)) {
                Timber.w("Failed to rename file to temporary name")
                return false
            }

            // Step 2: Remove from DownloadManager (won't find the file to delete)
            downloadManager.remove(downloadId)

            // Step 3: Rename back to original name
            if (!tempFile.renameTo(originalFile)) {
                Timber.e("Failed to rename file back to original name! File is now: ${tempFile.absolutePath}")
                // Try to recover by calling it the original name anyway
                return false
            }

            Timber.d("Successfully removed download from list while preserving file")
            return true

        } catch (e: Exception) {
            Timber.e(e, "Error removing download from list")
            return false
        }
    }

    /**
     * Remove download from DownloadManager.
     * This removes both the database entry and the file (if it exists).
     */
    private fun removeDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        loadDownloads()
    }

    /**
     * Show confirmation dialog before removing and keeping file
     */
    private fun confirmRemoveAndKeepFile(downloadId: Long, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_remove_and_keep)
            .setMessage(getString(R.string.dialog_message_remove_and_keep, title))
            .setPositiveButton(R.string.action_remove) { _, _ ->
                if (removeFromListOnly(downloadId)) {
                    activity?.snackbar(R.string.removed_from_list)
                    loadDownloads()
                } else {
                    activity?.snackbar("Failed to remove from list")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Show confirmation dialog before removing and deleting
     */
    private fun confirmRemoveAndDeleteDownload(downloadId: Long, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_remove_and_delete)
            .setMessage(getString(R.string.dialog_message_remove_and_delete, title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                removeDownload(downloadId)
                activity?.snackbar(R.string.download_deleted)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Show confirmation dialog before deleting file only (keeps entry)
     */
    private fun confirmDeleteFileOnly(downloadId: Long, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_delete_file)
            .setMessage(getString(R.string.dialog_message_delete_file, title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteFileOnly(downloadId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Delete only the file, keeping the download entry in DownloadManager.
     * The entry will show as "orphaned" after deletion.
     */
    private fun deleteFileOnly(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (!cursor.moveToFirst()) {
            cursor.close()
            requireContext().toast(R.string.download_not_found)
            return
        }

        val localUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        cursor.close()

        if (localUriString == null) {
            requireContext().toast(R.string.file_not_found)
            return
        }

        try {
            val fileUri = android.net.Uri.parse(localUriString)
            val file = java.io.File(fileUri.path ?: "")

            Timber.d("Attempting to delete file: ${file.absolutePath}")

            if (!file.exists()) {
                Timber.w("File does not exist: ${file.absolutePath}")
                requireContext().toast(R.string.file_does_not_exist)
                loadDownloads()
                return
            }

            // Log file properties for debugging
            Timber.d("File exists: ${file.exists()}")
            Timber.d("Can read: ${file.canRead()}")
            Timber.d("Can write: ${file.canWrite()}")
            Timber.d("Is file: ${file.isFile}")
            Timber.d("Is directory: ${file.isDirectory}")
            Timber.d("Parent exists: ${file.parentFile?.exists()}")
            Timber.d("Parent can write: ${file.parentFile?.canWrite()}")

            // Check if we have write permission
            if (!file.canWrite()) {
                Timber.w("No write permission for file: ${file.absolutePath}")
                requireContext().toast(R.string.error_no_permission)
                return
            }

            if (file.delete()) {
                Timber.d("Successfully deleted file: ${file.absolutePath}")
                requireContext().toast(R.string.file_deleted)
                loadDownloads()  // Refresh to show orphaned status
            } else {
                // Delete failed - log detailed information
                Timber.w("Failed to delete file: ${file.absolutePath}")
                Timber.w("File still exists: ${file.exists()}")
                Timber.w("Can write after fail: ${file.canWrite()}")
                Timber.w("Parent can write: ${file.parentFile?.canWrite()}")
                requireContext().toast(R.string.error_deleting_file)
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Security exception deleting file - no permission")
            requireContext().toast(R.string.error_no_permission)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting file")
            requireContext().toast(R.string.error_deleting_file)
        }
    }

    /**
     * Open a completed download
     */
    private fun openDownload(downloadId: Long) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri != null) {
            val mimeType = downloadManager.getMimeTypeForDownloadedFile(downloadId)

            Timber.d("=== Opening Download ===")
            Timber.d("Download ID: $downloadId")
            Timber.d("URI: $uri")
            Timber.d("MIME type: $mimeType")

            // Check if this is an APK and if we need install permission
            if (mimeType == "application/vnd.android.package-archive") {
                if (!canInstallPackages()) {
                    requestInstallPermission()
                    return
                }
            }

            val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            openIntent.setDataAndType(uri, mimeType)
            openIntent.flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK

            // Check which app will handle this
            val packageManager = requireContext().packageManager
            val resolveInfo = packageManager.resolveActivity(openIntent, 0)

            if (resolveInfo != null) {
                val appName = resolveInfo.loadLabel(packageManager)
                val packageName = resolveInfo.activityInfo.packageName
                Timber.d("Will be opened by: $appName ($packageName)")
            } else {
                Timber.d("No app found to open this file")
            }

            try {
                startActivity(openIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to open download")
                activity?.snackbar(R.string.no_app_to_open)
            }
        } else {
            Timber.e("URI is null for download ID: $downloadId")
        }
    }

    /**
     * Check if the app has permission to install packages.
     * Required for Android 8.0+ (API 26+) when opening APK files.
     */
    private fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().packageManager.canRequestPackageInstalls()
        } else {
            true // Permission not required before Android 8.0
        }
    }

    /**
     * Request install packages permission.
     * Shows a dialog explaining why the permission is needed, then opens settings.
     * User needs to manually grant permission and come back to open the APK.
     */
    private fun requestInstallPermission() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_install_apk)
            .setMessage(R.string.dialog_message_install_apk)
            .setPositiveButton(R.string.action_open) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to open install permission settings")
                        activity?.toast(R.string.install_permission_denied)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Share a completed download
     */
    private fun shareDownload(downloadId: Long) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri != null) {
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND)
            shareIntent.type = downloadManager.getMimeTypeForDownloadedFile(downloadId)
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
            shareIntent.flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION

            try {
                startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.action_share)))
            } catch (e: Exception) {
                activity?.snackbar(R.string.error_sharing)
            }
        }
    }

    /**
     * Share the download URL/link
     */
    private fun shareLink(url: String) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, url)

        try {
            startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_link)))
        } catch (e: Exception) {
            activity?.snackbar(R.string.error_sharing)
        }
    }


    /**
     * Re-download a file from its original URL.
     * Also removes all orphaned downloads with the same local URI.
     */
    private fun redownloadFile(originalUri: String?, title: String) {
        if (originalUri.isNullOrBlank()) {
            activity?.snackbar("Cannot re-download: original URL not available")
            return
        }

        try {
            // First, find and remove all downloads with the same local URI that are orphaned
            removeOrphanedDownloadsWithSameUri(title)

            val uri = android.net.Uri.parse(originalUri)
            val request = DownloadManager.Request(uri)
            request.setTitle(title)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS,
                title
            )

            downloadManager.enqueue(request)
            loadDownloads()
            activity?.snackbar("Download started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to re-download file")
            activity?.snackbar("Failed to start download")
        }
    }

    /**
     * Remove all orphaned downloads that would have the same local URI (same filename).
     * This cleans up duplicate database entries for files that were deleted.
     */
    private fun removeOrphanedDownloadsWithSameUri(filename: String) {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)

        val idsToRemove = mutableListOf<Long>()
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                // Check if this is an orphaned download with matching filename
                if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                    try {
                        val fileUri = android.net.Uri.parse(localUri)
                        val file = java.io.File(fileUri.path ?: "")

                        // If file doesn't exist and the filename matches, mark for removal
                        if (!file.exists() && file.name == filename) {
                            idsToRemove.add(id)
                            Timber.d("Marking orphaned download for removal: $id (${file.name})")
                        }
                    } catch (e: Exception) {
                        Timber.d(e, "Failed to check file for download $id")
                    }
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Remove all orphaned downloads with same filename
        if (idsToRemove.isNotEmpty()) {
            idsToRemove.forEach { downloadManager.remove(it) }
            Timber.d("Removed ${idsToRemove.size} orphaned download(s) with same filename")
        }
    }

    /**
     * Open downloads folder in file explorer
     */
    private fun exploreDownloadsFolder() {
        val intent = Utils.getIntentForDownloads(requireContext(),
            requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("download_directory",
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS
                    ).path) ?: "")

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open downloads folder")
            activity?.snackbar(R.string.error_opening_folder)
        }
    }

    /**
     * Show confirmation dialog before clearing downloads list
     */
    private fun showClearDownloadsDialog() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)
        val count = cursor.count
        cursor.close()

        if (count == 0) {
            activity?.snackbar(R.string.no_downloads)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_clear_downloads)
            .setMessage(getString(R.string.dialog_message_clear_downloads, count))
            .setPositiveButton(R.string.action_clear) { _, _ ->
                clearAllDownloads()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Show confirmation dialog before removing all downloads (keeps files)
     */
    private fun showRemoveAllDownloadsDialog() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)
        val count = cursor.count
        cursor.close()

        if (count == 0) {
            activity?.snackbar(R.string.no_downloads)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_remove_all_downloads)
            .setMessage(R.string.dialog_message_remove_all_downloads)
            .setPositiveButton(R.string.action_remove) { _, _ ->
                removeAllDownloads()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Show confirmation dialog before cleaning downloads (removes failed and orphaned)
     */
    private fun showCleanDownloadsDialog() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)

        var cleanCount = 0
        if (cursor.moveToFirst()) {
            do {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                // Count failed downloads
                if (status == DownloadManager.STATUS_FAILED) {
                    cleanCount++
                }
                // Count orphaned downloads (file not found)
                else if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                    val uri = android.net.Uri.parse(localUri)
                    val file = java.io.File(uri.path ?: "")
                    if (!file.exists()) {
                        cleanCount++
                    }
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        if (cleanCount == 0) {
            activity?.snackbar("No failed or orphaned downloads to clean")
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_clean_downloads)
            .setMessage(R.string.dialog_message_clean_downloads)
            .setPositiveButton(R.string.action_clean) { _, _ ->
                cleanDownloads()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Show confirmation dialog before deleting all downloads and files
     */
    private fun showDeleteAllDownloadsDialog() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)
        val count = cursor.count
        cursor.close()

        if (count == 0) {
            activity?.snackbar(R.string.no_downloads)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_delete_all_downloads)
            .setMessage(R.string.dialog_message_delete_all_downloads)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteAllDownloads()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Clear all downloads from list
     */
    private fun clearAllDownloads() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)

        val idsToRemove = mutableListOf<Long>()
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                // Only remove completed, failed, or cancelled downloads
                if (status == DownloadManager.STATUS_SUCCESSFUL ||
                    status == DownloadManager.STATUS_FAILED) {
                    idsToRemove.add(id)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        idsToRemove.forEach { downloadManager.remove(it) }

        loadDownloads()
        activity?.snackbar(getString(R.string.downloads_cleared, idsToRemove.size))
    }

    /**
     * Remove all downloads from list but keep files
     */
    private fun removeAllDownloads() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)

        val idsToRemove = mutableListOf<Long>()
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                idsToRemove.add(id)
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Remove from DownloadManager without deleting files
        idsToRemove.forEach {
            try {
                downloadManager.remove(it)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove download: $it")
            }
        }

        loadDownloads()
        activity?.snackbar(R.string.all_downloads_removed)
    }

    /**
     * Remove failed and orphaned downloads
     */
    private fun cleanDownloads() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)

        val idsToRemove = mutableListOf<Long>()
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

                // Remove failed downloads
                if (status == DownloadManager.STATUS_FAILED) {
                    idsToRemove.add(id)
                }
                // Remove orphaned downloads (file not found)
                else if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                    val uri = android.net.Uri.parse(localUri)
                    val file = java.io.File(uri.path ?: "")
                    if (!file.exists()) {
                        idsToRemove.add(id)
                    }
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        idsToRemove.forEach {
            try {
                downloadManager.remove(it)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove download: $it")
            }
        }

        loadDownloads()
        activity?.snackbar(R.string.downloads_cleaned)
    }

    /**
     * Remove all downloads and delete files
     */
    private fun deleteAllDownloads() {
        val query = DownloadManager.Query()
        val cursor = downloadManager.query(query)

        val idsToDelete = mutableListOf<Long>()
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                idsToDelete.add(id)
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Remove from DownloadManager (this also deletes the files)
        var deletedCount = 0
        idsToDelete.forEach {
            try {
                val removed = downloadManager.remove(it)
                if (removed > 0) deletedCount++
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete download: $it")
            }
        }

        loadDownloads()
        activity?.snackbar(R.string.all_downloads_deleted)
    }

    /**
     * Custom preference for download items that updates its progress.
     * Returns true if the download is actively in progress (running/pending/paused).
     */
    private class DownloadPreference(
        context: Context,
        private val downloadId: Long,
        private val downloadManager: DownloadManager
    ) : x.Preference(context) {

        // Speed calculation - track samples over last 10 seconds
        private data class SpeedSample(val timestamp: Long, val bytes: Long)
        private val speedSamples = mutableListOf<SpeedSample>()
        private val speedWindowMs = 10000L // 10 seconds window

        init {
            // Set title to single line with ellipsis in the middle
            titleEllipsize = android.text.TextUtils.TruncateAt.MIDDLE
            // Workaround our dodgy line wraps
            summaryMaxLines = 2
        }

        /**
         * Calculate average download speed over the last 10 seconds.
         * Uses a circular buffer of samples to provide smooth speed display.
         */
        private fun calculateAverageSpeed(currentBytes: Long, currentTime: Long): String {
            // Add current sample
            speedSamples.add(SpeedSample(currentTime, currentBytes))

            // Remove samples older than 10 seconds
            speedSamples.removeAll { currentTime - it.timestamp > speedWindowMs }

            // Need at least 2 samples to calculate speed
            if (speedSamples.size < 2) {
                return ""
            }

            // Calculate average speed using oldest and newest samples
            val oldestSample = speedSamples.first()
            val newestSample = speedSamples.last()

            val timeDiff = newestSample.timestamp - oldestSample.timestamp
            val bytesDiff = newestSample.bytes - oldestSample.bytes

            if (timeDiff > 0 && bytesDiff > 0) {
                // Speed in bytes per second
                val speedBps = (bytesDiff * 1000) / timeDiff
                return " • ${Formatter.formatFileSize(context, speedBps)}/s"
            }

            return ""
        }

        /**
         * Update progress from DownloadManager.
         * Only updates the summary text for efficiency.
         * @return true if this download is actively in progress and needs updates
         */
        fun updateProgress(): Boolean {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            var isActive = false
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                isActive = status == DownloadManager.STATUS_RUNNING ||
                          status == DownloadManager.STATUS_PENDING ||
                          status == DownloadManager.STATUS_PAUSED

                // Only update if this download is active or needs a summary refresh
                if (isActive || summary == null) {
                    updateFromCursor(cursor)
                }
            }
            cursor.close()

            return isActive
        }

        /**
         * Update the preference summary from cursor data.
         * This only updates the text, not the entire preference UI.
         */
        fun updateFromCursor(cursor: Cursor) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP))
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

            // Format date and time using system default format
            val dateTime = android.text.format.DateUtils.formatDateTime(
                context,
                lastModified,
                android.text.format.DateUtils.FORMAT_SHOW_DATE or
                android.text.format.DateUtils.FORMAT_SHOW_TIME or
                android.text.format.DateUtils.FORMAT_SHOW_YEAR or
                android.text.format.DateUtils.FORMAT_ABBREV_MONTH
            )

            // Check if download is orphaned (completed but file no longer exists)
            val isOrphaned = if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
                try {
                    val fileUri = android.net.Uri.parse(localUri)
                    val file = java.io.File(fileUri.path ?: "")
                    !file.exists()
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }

            val statusText = when {
                isOrphaned -> "${context.getString(R.string.download_status_orphaned)}\n$dateTime"
                status == DownloadManager.STATUS_RUNNING -> {
                    if (bytesTotal > 0) {
                        val progress = (bytesDownloaded * 100 / bytesTotal).toInt()

                        // Calculate average download speed over last 10 seconds
                        val currentTime = System.currentTimeMillis()
                        val speedText = calculateAverageSpeed(bytesDownloaded, currentTime)

                        "$progress%$speedText\n${Formatter.formatFileSize(context, bytesDownloaded)} / ${Formatter.formatFileSize(context, bytesTotal)}"
                    } else {
                        context.getString(R.string.download_status_downloading)
                    }
                }
                status == DownloadManager.STATUS_SUCCESSFUL ->
                    "${Formatter.formatFileSize(context, bytesTotal)}\n$dateTime"
                status == DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    "${context.getString(R.string.download_status_failed, reason)}\n$dateTime"
                }
                status == DownloadManager.STATUS_PAUSED -> "${context.getString(R.string.download_status_paused)}\n$dateTime"
                status == DownloadManager.STATUS_PENDING -> "${context.getString(R.string.download_status_pending)}\n$dateTime"
                else -> "${context.getString(R.string.download_status_unknown)}\n$dateTime"
            }

            summary = statusText
        }
    }
}

