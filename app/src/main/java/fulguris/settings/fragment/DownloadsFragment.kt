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
import fulguris.extensions.toast
import fulguris.utils.Utils
import timber.log.Timber
import javax.inject.Inject
import androidx.core.net.toUri

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

    private lateinit var downloadsListCategory: PreferenceCategory
    private var cleanDownloadsPref: Preference? = null
    private var removeAllDownloadsPref: Preference? = null
    private var deleteAllDownloadsPref: Preference? = null

    // Track flags for action states (we only need to know if at least one exists)
    private var hasAnyDownloads = false
    private var hasFailedOrOrphaned = false
    private var hasRemovable = false

    // Flag to cancel ongoing population when a new one starts
    private var populationCancelled = false

    // Flag to track whether this is the initial load (for showing appropriate summary message)
    private var isInitialLoad = true

    // Track last known download count to detect new downloads
    private var lastKnownDownloadCount = 0
    private var processedCount = 0

    fun isUpdating() : Boolean =  lastKnownDownloadCount>processedCount

    companion object {
        // Delay increment for staggered download preference creation (in milliseconds)
        private const val DELAY_INCREMENT = 1L
        // Initial delay before starting download creation
        private const val INITIAL_DELAY = 100L
    }

    // ContentObserver to detect changes in DownloadManager database (new downloads, status changes)
    private val downloadObserver = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
            super.onChange(selfChange, uri, flags)

            // Log all parameters
            Timber.d("Download database changed: selfChange=$selfChange, uri=$uri, flags=$flags")

            // We a download is removed we get:
            // Download database changed: selfChange=false, uri=content://downloads/all_downloads, flags=1

            // Only process if fragment is visible/resumed
            if (!isResumed) {
                Timber.d("Fragment not resumed, skipping download check")
                return
            }

            // Try to extract download ID from URI
            val downloadId = uri?.lastPathSegment?.toLongOrNull()
            Timber.d("Extracted download ID: $downloadId")

            // Check if there are new downloads
            val query = DownloadManager.Query()
            val cursor = try {
                downloadManager.query(query)
            } catch (e: Exception) {
                Timber.e(e, "Failed to query downloads in observer")
                null
            }

            cursor?.use {
                val currentCount = it.count
                Timber.d("Download count check: lastKnown=$lastKnownDownloadCount, current=$currentCount")

                if (currentCount > lastKnownDownloadCount) {
                    Timber.i("New download(s) detected: $lastKnownDownloadCount -> $currentCount, reloading list")
                    // New download(s) started - reload the list
                    //loadDownloads()
                } else if (currentCount < lastKnownDownloadCount) {
                    Timber.i("Download(s) removed: $lastKnownDownloadCount -> $currentCount, reloading list")
                    // Download(s) removed - reload the list
                    // We should have already removed the download preference ourselves so no need to update the list
                    //loadDownloads()
                } else if (downloadId != null) {
                    // Count unchanged, but we have a specific download ID
                    // This is likely a progress update - update just this download
                    Timber.d("Progress update detected for download ID: $downloadId")
                    updateDownload(downloadId)
                } else {
                    // Count unchanged, no specific download to update
                    Timber.d("Download count unchanged, no specific ID to update")
                }
                lastKnownDownloadCount = currentCount
            }
        }
    }

    // BroadcastReceiver to listen for download events (start, complete, pause, etc.)
    private val downloadEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    Timber.d("Download completed: $downloadId")

                    // Update the specific download that completed
                    updateDownload(downloadId)
                }
                DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                    // User clicked on download notification - could open downloads list
                    Timber.d("Download notification clicked")
                }
                // Note: ACTION_VIEW_DOWNLOADS is also available but typically handled by system
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


        // Register content observer to detect download database changes (new downloads, status changes)
        // Use all_downloads to monitor all download changes
        try {
            val downloadUri = "content://downloads/all_downloads".toUri()
            Timber.d("Registering ContentObserver for URI: $downloadUri")
            requireContext().contentResolver.registerContentObserver(
                downloadUri,
                true, // notifyForDescendants - observe changes to any download
                downloadObserver
            )
            Timber.d("ContentObserver registered successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register download observer")
        }

        // Register receiver for download events
        val filter = IntentFilter().apply {
            addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // We need to export it otherwise we don't get download notifications from system
            requireContext().registerReceiver(downloadEventReceiver, filter, Context.RECEIVER_EXPORTED)
            Timber.d("BroadcastReceiver registered (RECEIVER_EXPORTED)")
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(downloadEventReceiver, filter)
            Timber.d("BroadcastReceiver registered")
        }
    }

    override fun onPause() {
        super.onPause()

        // Cancel any ongoing population
        populationCancelled = true


        // Unregister content observer
        try {
            requireContext().contentResolver.unregisterContentObserver(downloadObserver)
            Timber.d("ContentObserver unregistered successfully")
        } catch (e: Exception) {
            Timber.d(e, "Observer not registered or already unregistered")
        }

        // Unregister receiver to prevent memory leaks
        try {
            requireContext().unregisterReceiver(downloadEventReceiver)
            Timber.d("BroadcastReceiver unregistered successfully")
        } catch (e: Exception) {
            Timber.d(e, "Receiver not registered or already unregistered")
        }
    }


    /**
     * Load all downloads from DownloadManager asynchronously.
     * Updates existing preferences and adds/removes as needed.
     * Counting happens during population, action states update when done.
     */
    private fun loadDownloads() {
        // Cancel any ongoing population
        populationCancelled = true

        // Remove ALL pending callbacks from the handler to prevent old population tasks from running
        handler.removeCallbacksAndMessages(null)

        // Reset flags
        hasAnyDownloads = false
        hasFailedOrOrphaned = false
        hasRemovable = false

        // Start with actions disabled, will enable appropriately after population
        updateActionStates()

        val query = DownloadManager.Query()
        val cursor: Cursor? = try {
            downloadManager.query(query)
        } catch (e: Exception) {
            Timber.e(e, "Failed to query downloads")
            null
        }

        if (cursor == null) {
            downloadsListCategory.removeAll()
            showEmptyState()
            lastKnownDownloadCount = 0
            return
        }

        if (!cursor.moveToFirst()) {
            cursor.close()
            downloadsListCategory.removeAll()
            showEmptyState()
            lastKnownDownloadCount = 0
            return
        }

        // Move cursor back to beginning and start async population
        cursor.moveToPosition(-1) // Move before first
        val totalDownloads = cursor.count

        // Update last known count to track for new downloads
        lastKnownDownloadCount = totalDownloads

        // Reset cancellation flag for this new population
        populationCancelled = false
        populateDownloadsAsync(cursor, totalDownloads)
    }

    /**
     * Populate downloads list UI asynchronously by processing cursor items one at a time.
     * Updates existing preferences, adds new ones, and removes stale ones.
     * Fetches, counts, and displays each download with a staggered delay.
     * Updates action states when all downloads are processed.
     */
    private fun populateDownloadsAsync(cursor: Cursor, totalDownloads: Int) {
        // Clear empty state summary since we have downloads
        downloadsListCategory.summary = null

        var delay = INITIAL_DELAY
        processedCount = 0

        // Track which download IDs we've seen (to remove stale preferences later)
        val seenDownloadIds = mutableSetOf<Long>()

        // Process cursor items asynchronously
        fun processNextDownload() {
            // Check if this population was cancelled
            if (populationCancelled) {
                Timber.d("Population cancelled, stopping")
                cursor.close()
                return
            }

            if (cursor.moveToNext()) {
                handler.postDelayed({
                    // Double-check cancellation after delay
                    if (populationCancelled) {
                        Timber.d("Population cancelled during delay, stopping")
                        cursor.close()
                        return@postDelayed
                    }

                    try {
                        processedCount++

                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        seenDownloadIds.add(id)

                        // Use the shared updateDownload logic (doesn't update actions/count)
                        updateDownloadFromCursor(cursor)
                        //
                        updateDownloadCount()

                        // Schedule next download
                        processNextDownload()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to process download preference")
                        // Continue with next download even if this one failed
                        processNextDownload()
                    }
                }, delay)
                delay += DELAY_INCREMENT
            } else {
                // All downloads processed, clean up stale preferences
                handler.postDelayed({
                    // Check if this population was cancelled
                    if (populationCancelled) {
                        Timber.d("Population cancelled at completion, stopping")
                        cursor.close()
                        return@postDelayed
                    }

                    removeStalePreferences(seenDownloadIds)
                    cursor.close()

                    // Update category summary with final count
                    updateDownloadCount()

                    // Mark that initial load is complete
                    isInitialLoad = false
                }, delay)
            }
        }

        // Start processing
        processNextDownload()
    }

    /**
     * Remove preferences for downloads that no longer exist in the DownloadManager.
     */
    private fun removeStalePreferences(validDownloadIds: Set<Long>) {
        val prefsToRemove = mutableListOf<Preference>()

        for (i in 0 until downloadsListCategory.preferenceCount) {
            val pref = downloadsListCategory.getPreference(i)
            if (pref is DownloadPreference) {
                if (pref.downloadId !in validDownloadIds) {
                    prefsToRemove.add(pref)
                }
            }
        }

        prefsToRemove.forEach { pref ->
            downloadsListCategory.removePreference(pref)
        }
    }

    /**
     * Data class to hold download information extracted from cursor
     */
    private data class DownloadData(
        val id: Long,
        val title: String?,
        val status: Int,
        val localUri: String?,
        val uri: String?,
        val bytesDownloaded: Long,
        val totalSize: Long,
        val lastModified: Long,
        val mimeType: String?
    )

    /**
     * Create DownloadData from cursor position
     */
    private fun createDownloadData(cursor: Cursor): DownloadData {
        return DownloadData(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)),
            status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
            localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)),
            uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)),
            bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
            totalSize = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)),
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
        )
    }

    /**
     * Create DownloadData from download ID by querying DownloadManager
     * Returns null if the download doesn't exist
     */
    private fun createDownloadData(downloadId: Long): DownloadData? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        return cursor?.use {
            if (it.moveToFirst()) {
                createDownloadData(it)
            } else {
                null
            }
        }
    }


    /**
     * Update a single download by ID (e.g., when it completes or progresses).
     * Uses the same logic as list population - finds existing preference or creates new one,
     * then updates it with the latest download data.
     */
    private fun updateDownload(downloadId: Long) {
        Timber.d("updateDownload: downloadId=$downloadId")

        // Query the download manager for this download
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor != null && cursor.moveToFirst()) {
            try {
                updateDownloadFromCursor(cursor)
                // Update action states and download count after updating the preference
                updateDownloadCount()
            } finally {
                cursor.close()
            }
        } else {
            Timber.w("updateDownload: Download $downloadId not found in DownloadManager")
            cursor?.close()
        }
    }

    /**
     * Update a download from cursor data. This is the core implementation used by both
     * list population and individual updates. Does not call updateActionStates/updateDownloadCount
     * so caller can batch those calls.
     */
    private fun updateDownloadFromCursor(cursor: Cursor) {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))

        // Update flags based on this download's status
        hasAnyDownloads = true

        if (status == DownloadManager.STATUS_FAILED) {
            hasFailedOrOrphaned = true
            hasRemovable = true
        } else if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
            val uri = android.net.Uri.parse(localUri)
            val file = java.io.File(uri.path ?: "")
            if (!file.exists()) {
                hasFailedOrOrphaned = true
                hasRemovable = true
            } else {
                if (file.canRead() || file.canWrite()) {
                    hasRemovable = true
                }
            }
        } else if (status != DownloadManager.STATUS_SUCCESSFUL) {
            hasRemovable = true
        }

        // Check if preference already exists
        val prefKey = "download_$id"
        var downloadPref = downloadsListCategory.findPreference<DownloadPreference>(prefKey)

        if (downloadPref != null) {
            // Update existing preference
            val downloadData = createDownloadData(cursor)
            downloadPref.updateFromDownloadData(downloadData)
            // Update icon in case status or file type changed
            setDownloadIcon(downloadPref, downloadData.status, downloadData.mimeType, downloadData.localUri)
            Timber.d("updateDownloadFromCursor: Updated existing preference for download $id")
        } else {
            // Create new preference
            val downloadData = createDownloadData(cursor)
            downloadPref = createDownloadPreference(downloadData)
            downloadsListCategory.addPreference(downloadPref)
            Timber.d("updateDownloadFromCursor: Created new preference for download $id")
        }
    }

    /**
     * Update action preferences based on current flags.
     * Uses simple boolean flags to determine if actions should be enabled.
     */
    private fun updateActionStates() {
        // "Remove all" is enabled only if there are downloads that can be removed without deleting files
        removeAllDownloadsPref?.isEnabled = hasRemovable

        // "Delete all" is enabled if there are any downloads
        deleteAllDownloadsPref?.isEnabled = hasAnyDownloads

        // "Clean" is enabled only if there are failed or orphaned downloads
        cleanDownloadsPref?.isEnabled = hasFailedOrOrphaned
    }

    /**
     * Create a preference for a download item
     */
    private fun createDownloadPreference(downloadData: DownloadData): DownloadPreference {
        val id = downloadData.id

        // Try to get actual filename from local URI first, then fall back to title
        var displayTitle = downloadData.title ?: "Unknown"

        // Extract filename from local URI if available
        val localUri = downloadData.localUri
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
            val originalUri = downloadData.uri
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
        val status = downloadData.status
        val mimeType = downloadData.mimeType

        // Set icon based on status and file type
        setDownloadIcon(downloadPref, status, mimeType, localUri)

        downloadPref.setOnPreferenceClickListener {
            showDownloadOptionsDialog(id, displayTitle)
            true
        }

        // Initialize summary from downloadData
        downloadPref.initializeFromDownloadData(downloadData)

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
            pref.setIcon(R.drawable.ic_error_outline)
            return
        }

        // Check if download is orphaned (completed but file no longer exists)
        if (status == DownloadManager.STATUS_SUCCESSFUL && localUri != null) {
            try {
                val fileUri = android.net.Uri.parse(localUri)
                val file = java.io.File(fileUri.path ?: "")
                if (!file.exists()) {
                    pref.setIcon(R.drawable.ic_unknown_document_outline_error)
                    return
                }
            } catch (e: Exception) {
                Timber.d(e, "Failed to check if file exists")
            }
        }

        val packageManager = context.packageManager

        // Use static icon for APK files
        if (mimeType == "application/vnd.android.package-archive") {
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
                    resolveInfo.activityInfo.loadIcon(packageManager)?.let { icon ->
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
                    resolveInfo.activityInfo.loadIcon(packageManager)?.let { icon ->
                        pref.icon = resizeDrawableToIconSize(icon)
                        return
                    }
                } else {
                    // Last resort: try querying all apps if no default is set
                    val resolveInfos = packageManager.queryIntentActivities(intent, 0)

                    if (resolveInfos.isNotEmpty()) {
                        val selectedApp = resolveInfos[0]
                        selectedApp.activityInfo.loadIcon(packageManager)?.let { icon ->
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
        pref.setIcon(R.drawable.ic_download_outline)
    }

    /**
     * Show empty state when no downloads
     */
    private fun showEmptyState() {
        downloadsListCategory.summary = getString(R.string.pref_summary_no_downloads)
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

        // Check if we have write/read permission on the file
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

        val canReadFile = if (hasFile && localUri != null) {
            try {
                val fileUri = android.net.Uri.parse(localUri)
                val file = java.io.File(fileUri.path ?: "")
                file.canRead()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        // Log visibility flags for debugging
        Timber.d("Download options flags for ID $downloadId ($title):")
        Timber.d("  Status: $status, isSuccessful=$isSuccessful, hasFile=$hasFile")
        Timber.d("  isInProgress=$isInProgress, isPaused=$isPaused, isFailed=$isFailed, isOrphaned=$isOrphaned")
        Timber.d("  hasUrl=$hasUrl, canReadFile=$canReadFile, canWriteFile=$canWriteFile")
        Timber.d("  localUri=$localUri")

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

            if (canReadFile) {
                // 3. Remove and keep (requires read permission - uses rename or copy fallback)
                options.add(fulguris.dialog.DialogItem(title = R.string.remove_and_keep_file) {
                    confirmRemoveAndKeepFile(downloadId, title)
                })
            }

            if (canWriteFile) {
                // 4. Delete file only (requires write permission - uses file.delete())
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
                    })
                }
            }
        }

        // Options for in-progress or paused downloads
        if (isInProgress || isPaused) {
            // Cancel download
            options.add(fulguris.dialog.DialogItem(title = R.string.cancel_download) {
                // I believe we can only just remove it
                removeDownload(downloadId)
            })
        }

        // Options for orphaned or failed downloads
        if (isOrphaned || isFailed) {
            // Remove from list
            options.add(fulguris.dialog.DialogItem(title = R.string.remove_from_list) {
                removeDownload(downloadId)
            })
            // Re-download (if has URL)
            if (hasUrl) {
                options.add(fulguris.dialog.DialogItem(title = R.string.action_download) {

                    redownloadFile(downloadId,originalUri, title)
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
            })
        }

        // Show tabbed dialog with a single tab (hide tab, show icon in dialog)
        fulguris.dialog.BrowserDialog.show(
            requireContext(),
            R.drawable.ic_download_outline,
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
            val fileUri = localUriString.toUri()
            val originalFile = java.io.File(fileUri.path ?: return false)

            if (!originalFile.exists()) {
                // File doesn't exist, just remove the entry
                downloadManager.remove(downloadId)
                return true
            }

            // Create temporary file with unique name
            val tempFile = java.io.File(originalFile.parentFile, "tmp_${System.currentTimeMillis()}_${originalFile.name}")

            // Try to rename first (fast, works if we have write permission)
            if (originalFile.renameTo(tempFile)) {
                // Rename succeeded - remove from DownloadManager (won't find file to delete)
                downloadManager.remove(downloadId)

                // Rename back to original
                if (tempFile.renameTo(originalFile)) {
                    Timber.d("Successfully removed download via rename")
                    return true
                } else {
                    Timber.e("Failed to rename back! File is now: ${tempFile.absolutePath}")
                    return false
                }
            }

            // Rename failed (probably no write permission) - try copy approach
            Timber.d("Rename failed, trying copy approach")

            // Check if we at least have read permission
            if (!originalFile.canRead()) {
                Timber.w("Cannot read file, cannot preserve it")
                return false
            }

            // Copy the file to temp location
            originalFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Remove from DownloadManager (will fail to delete the file but will remove entry)
            downloadManager.remove(downloadId)

            // Verify the copy was successful
            if (tempFile.exists() && tempFile.length() == originalFile.length()) {
                Timber.d("Successfully removed download via copy (original file preserved)")
                // Note: tempFile remains as a backup copy - we can't delete the original due to permissions
                // Clean up temp file if it exists
                try {
                    tempFile.delete()
                } catch (e: Exception) {
                    Timber.d("Could not delete temp file: ${e.message}")
                }
                return true
            } else {
                // Copy failed, clean up and return false
                tempFile.delete()
                return false
            }

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
        removeDownloadPref(downloadId)
        updateDownloadCount()
    }

    /**
     * Remove a download preference by ID
     */
    private fun removeDownloadPref(downloadId: Long) {
        val prefKey = "download_$downloadId"
        downloadsListCategory.findPreference<DownloadPreference>(prefKey)?.let {
            downloadsListCategory.removePreference(it)
        }
    }

    /**
     * Show downloads count and progress in downloads category summary
     */
    private fun updateDownloadCount() {

        if (isUpdating()) {
            // Update category summary with progress
            val summaryResId = if (isInitialLoad) {
                R.string.pref_summary_loading_downloads
            } else {
                R.string.pref_summary_updating_downloads
            }
            downloadsListCategory.summary = getString(summaryResId, processedCount, lastKnownDownloadCount)
        } else {
            if (downloadsListCategory.preferenceCount == 1) {
                downloadsListCategory.summary = getString(R.string.pref_summary_downloads_count, downloadsListCategory.preferenceCount)
            } else {
                downloadsListCategory.summary = getString(R.string.pref_summary_downloads_count_plural, downloadsListCategory.preferenceCount)
            }
            // Notably needed for isUpdating to keep on working after redownload
            lastKnownDownloadCount = downloadsListCategory.preferenceCount
            processedCount = downloadsListCategory.preferenceCount
        }

        updateActionStates()
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
                    removeDownloadPref(downloadId)
                    updateDownloadCount()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Show confirmation dialog before removing download entry and deleting associated file
     */
    private fun confirmRemoveAndDeleteDownload(downloadId: Long, title: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_title_remove_and_delete)
            .setMessage(getString(R.string.dialog_message_remove_and_delete, title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                removeDownload(downloadId)
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
            val fileUri = localUriString.toUri()
            val file = java.io.File(fileUri.path ?: "")

            Timber.d("Attempting to delete file: ${file.absolutePath}")

            if (!file.exists()) {
                Timber.w("File does not exist: ${file.absolutePath}")
                requireContext().toast(R.string.file_does_not_exist)
                updateDownload(downloadId)
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

            if (file.delete()) {
                Timber.i("File deleted: ${file.absolutePath}")
                updateDownload(downloadId)
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
                activity?.toast(R.string.error_cant_open_file)
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
        }
    }


    /**
     * Re-download a file from its original URL.
     * Also removes all orphaned downloads with the same local URI.
     */
    private fun redownloadFile(downloadId: Long, originalUri: String?, title: String) {
        if (originalUri.isNullOrBlank()) {
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

            val newDownloadId = downloadManager.enqueue(request)
            if (newDownloadId!=-1L) {
                removeDownload(downloadId)
                // Create new preference
                val downloadData = createDownloadData(newDownloadId)
                if (downloadData!=null) {
                    val pref = createDownloadPreference(downloadData)
                    // Add it at the top
                    pref.order = downloadsListCategory.getPreference(0).order - 1
                    downloadsListCategory.addPreference(pref)
                    updateDownloadCount()
                }

            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to re-download file")
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
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete_sweep_outline)
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


        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_cleaning_services_outline)
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
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_delete_forever_outline)
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

        // Remove from DownloadManager while preserving files
        var successCount = 0
        var failCount = 0
        var skippedCount = 0

        idsToRemove.forEach { downloadId ->
            try {
                // Check if we can read the file before attempting removal
                val canRemove = checkCanRemoveDownload(downloadId)
                if (!canRemove) {
                    Timber.w("Skipping download $downloadId - no read/write permission to preserve file")
                    skippedCount++
                    return@forEach
                }

                if (removeFromListOnly(downloadId)) {
                    removeDownloadPref(downloadId)
                    successCount++
                } else {
                    failCount++
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove download: $downloadId")
                failCount++
            }
        }

        Timber.d("Remove all complete: $successCount succeeded, $failCount failed, $skippedCount skipped")

        // Show toast if some downloads were skipped
        if (skippedCount > 0) {
            requireContext().toast(R.string.downloads_could_not_remove)
        }

        loadDownloads()
    }

    /**
     * Check if we can safely remove a download while preserving its file.
     * Returns true if we have at least read permission (can copy) or write permission (can rename).
     */
    private fun checkCanRemoveDownload(downloadId: Long): Boolean {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (!cursor.moveToFirst()) {
            cursor.close()
            return false
        }

        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val localUriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
        cursor.close()

        // Only check files that are successfully downloaded
        if (status != DownloadManager.STATUS_SUCCESSFUL || localUriString.isNullOrEmpty()) {
            return true // Can safely remove non-file entries (failed, orphaned, etc.)
        }

        // Check if we have read or write permission
        try {
            val fileUri = android.net.Uri.parse(localUriString)
            val file = java.io.File(fileUri.path ?: return false)

            if (!file.exists()) {
                return true // File doesn't exist, safe to remove entry
            }

            // Need at least read permission to preserve file via copy
            return file.canRead() || file.canWrite()
        } catch (e: Exception) {
            Timber.e(e, "Error checking permissions for download $downloadId")
            return false // Don't remove if we can't check
        }
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

        // Remove preferences from UI immediately for instant feedback
        idsToRemove.forEach { downloadId ->
            removeDownloadPref(downloadId)
        }

        // Remove from download manager
        idsToRemove.forEach {
            try {
                downloadManager.remove(it)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove download: $it")
            }
        }

        loadDownloads()
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

        // Remove preferences from UI immediately for instant feedback
        idsToDelete.forEach { downloadId ->
            removeDownloadPref(downloadId)
        }

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
    }


    /**
     * Custom preference for download items that updates its progress.
     * Returns true if the download is actively in progress (running/pending/paused).
     */
    private class DownloadPreference(
        context: Context,
        val downloadId: Long,
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
            } else {
                Timber.w("updateProgress($downloadId): No cursor data found")
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

            summary = formatSummary(status, bytesDownloaded, bytesTotal, lastModified, localUri)
        }

        /**
         * Initialize the preference summary from DownloadData.
         * Called once during preference creation.
         */
        fun initializeFromDownloadData(data: DownloadData) {
            summary = formatSummary(data.status, data.bytesDownloaded, data.totalSize, data.lastModified, data.localUri)
        }

        /**
         * Update existing preference from DownloadData.
         * Called when refreshing the download list.
         */
        fun updateFromDownloadData(data: DownloadData) {
            // Update title if it has changed
            if (data.title != null && title != data.title) {
                title = data.title
            }

            // Update summary
            summary = formatSummary(data.status, data.bytesDownloaded, data.totalSize, data.lastModified, data.localUri)
        }

        /**
         * Format summary text from download status and data.
         */
        private fun formatSummary(
            status: Int,
            bytesDownloaded: Long,
            bytesTotal: Long,
            lastModified: Long,
            localUri: String?
        ): String {
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

            return when {
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
                    // Note: reason is not available in DownloadData, would need cursor for full error details
                    "${context.getString(R.string.download_status_failed, 0)}\n$dateTime"
                }
                status == DownloadManager.STATUS_PAUSED -> "${context.getString(R.string.download_status_paused)}\n$dateTime"
                status == DownloadManager.STATUS_PENDING -> "${context.getString(R.string.download_status_pending)}\n$dateTime"
                else -> "${context.getString(R.string.download_status_unknown)}\n$dateTime"
            }
        }
    }
}








