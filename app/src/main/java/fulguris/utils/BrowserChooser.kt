package fulguris.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textview.MaterialTextView
import fulguris.R
import fulguris.extensions.px
import timber.log.Timber
import androidx.core.content.edit

/**
 * Utility class for showing browser chooser bottom sheet that works on all devices including Samsung.
 * Uses PackageManager.MATCH_ALL flag to bypass Samsung's default app override behavior.
 * Includes MRU (Most Recently Used) functionality to prioritize recently selected browsers.
 */
object BrowserChooser {

    private const val PREFS_NAME = "browser_chooser_mru"
    private const val MRU_LIST_KEY = "mru_browsers"
    private const val MAX_MRU_SIZE = 4 // Keep track of last N browsers

    /**
     * Opens a URL with a custom browser chooser bottom sheet.
     * Shows only browser apps with icons and friendly names.
     *
     * @param context The context to show the bottom sheet
     * @param url     The URL to open
     * @param excludeThisApp Whether to exclude the current app from the browser list (default: true)
     * @param extras Optional Bundle of extras to pass to the target intent
     * @param onDismiss Optional callback to be executed when the dialog is dismissed
     */
    fun open(context: Context, url: String, excludeThisApp: Boolean = true, extras: android.os.Bundle? = null, onDismiss: (() -> Unit)? = null) {
        Timber.d("DBG: BrowserChooser.open called with URL: $url, excludeThisApp: $excludeThisApp")
        showBrowserChooserBottomSheet(context, url, excludeThisApp, extras, onDismiss)
    }

    /**
     * Show a browser chooser bottom sheet.
     */
    private fun showBrowserChooserBottomSheet(context: Context, aUrl: String, excludeThisApp: Boolean, extras: android.os.Bundle?, onDismiss: (() -> Unit)?) {
        try {
            Timber.d("DBG: showBrowserChooserBottomSheet starting for URL: $aUrl")
            val intent = Intent(Intent.ACTION_VIEW, aUrl.toUri())
            val packageManager = context.packageManager

            // Use MATCH_ALL flag to find all browsers, including those hidden by Samsung
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.content.pm.PackageManager.MATCH_ALL
            } else {
                0
            }
            val allResolveInfos = packageManager.queryIntentActivities(intent, flags)
            Timber.d("DBG: Found ${allResolveInfos.size} total apps that can handle URLs")

            allResolveInfos.forEachIndexed { index, resolveInfo ->
                Timber.d("DBG: App $index: ${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name} - ${resolveInfo.loadLabel(packageManager)}")
            }

            // Filter based on excludeThisApp setting
            val resolveInfos = allResolveInfos.filter { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val activityName = resolveInfo.activityInfo.name
                val isOurApp = packageName == context.packageName

                // Always exclude SelectBrowserActivity
                if (isOurApp && activityName == "fulguris.activity.SelectBrowserActivity") {
                    return@filter false
                }

                // If excludeThisApp is true, filter out all our activities
                // If excludeThisApp is false, include MainActivity and IncognitoActivity
                !isOurApp || !excludeThisApp
            }.toMutableList()

            // If excludeThisApp is false, force add MainActivity and IncognitoActivity if not already present
            if (!excludeThisApp) {
                val mainActivityName = "fulguris.activity.MainActivity"
                val incognitoActivityName = "fulguris.activity.IncognitoActivity"

                // Check if MainActivity is already in the list
                val hasMainActivity = resolveInfos.any {
                    it.activityInfo.packageName == context.packageName &&
                    it.activityInfo.name == mainActivityName
                }

                // Add MainActivity if not present
                if (!hasMainActivity) {
                    try {
                        val mainIntent = Intent(Intent.ACTION_VIEW, aUrl.toUri()).apply {
                            setClassName(context.packageName, mainActivityName)
                        }
                        val mainResolveInfo = packageManager.resolveActivity(mainIntent, 0)
                        if (mainResolveInfo != null) {
                            resolveInfos.add(mainResolveInfo)
                            Timber.d("DBG: Manually added MainActivity to browser list")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "DBG: Failed to add MainActivity to browser list")
                    }
                }

                // Check if IncognitoActivity is already in the list
                val hasIncognitoActivity = resolveInfos.any {
                    it.activityInfo.packageName == context.packageName &&
                    it.activityInfo.name == incognitoActivityName
                }

                // Add IncognitoActivity if not present
                if (!hasIncognitoActivity) {
                    try {
                        val incognitoIntent = Intent(Intent.ACTION_VIEW, aUrl.toUri()).apply {
                            setClassName(context.packageName, incognitoActivityName)
                        }
                        val incognitoResolveInfo = packageManager.resolveActivity(incognitoIntent, 0)
                        if (incognitoResolveInfo != null) {
                            resolveInfos.add(incognitoResolveInfo)
                            Timber.d("DBG: Manually added IncognitoActivity to browser list")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "DBG: Failed to add IncognitoActivity to browser list")
                    }
                }
            }

            Timber.d("DBG: After filtering: ${resolveInfos.size} activities found")

            when (resolveInfos.size) {
                0 -> {
                    Timber.w("DBG: No browser apps found for URL: $aUrl")
                    android.widget.Toast.makeText(context, "No browser apps found", android.widget.Toast.LENGTH_SHORT).show()
                }

                1 -> {
                    // Only one browser available, open directly
                    val resolveInfo = resolveInfos[0]
                    Timber.d("DBG: Only one browser found: ${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name}, opening directly")
                    val targetIntent = Intent(Intent.ACTION_VIEW, aUrl.toUri()).apply {
                        // Use setComponent instead of setPackage to specify exact activity
                        component = android.content.ComponentName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // Add extras if provided
                        if (extras != null) {
                            putExtras(extras)
                        }
                    }
                    context.startActivity(targetIntent)
                }

                else -> {
                    // Multiple browsers - show bottom sheet with icons
                    Timber.d("DBG: Multiple browsers found (${resolveInfos.size}), showing bottom sheet")
                    createBrowserBottomSheet(context, aUrl, resolveInfos, extras, onDismiss)
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "DBG: Failed to show browser chooser for URL: $aUrl")
            android.widget.Toast.makeText(context, "Failed to open URL", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Get MRU (Most Recently Used) browsers from SharedPreferences
     */
    private fun getMruBrowsers(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mruString = prefs.getString(MRU_LIST_KEY, "") ?: ""
        return if (mruString.isEmpty()) {
            emptyList()
        } else {
            mruString.split(",").filter { it.isNotEmpty() }
        }
    }

    /**
     * Save a browser as most recently used
     * Uses full component name (package/activity) for uniqueness
     */
    private fun saveMruBrowser(context: Context, packageName: String, activityName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentMru = getMruBrowsers(context).toMutableList()

        // Use full component name for uniqueness
        val componentName = "$packageName/$activityName"

        // Remove if already exists (to move it to front)
        currentMru.remove(componentName)

        // Add to front
        currentMru.add(0, componentName)

        // Keep only MAX_MRU_SIZE items
        while (currentMru.size > MAX_MRU_SIZE) {
            currentMru.removeAt(currentMru.size - 1)
        }

        // Save back to preferences
        val mruString = currentMru.joinToString(",")
        prefs.edit { putString(MRU_LIST_KEY, mruString) }

        Timber.d("DBG: Saved MRU browser: $componentName, MRU list: $mruString")
    }

    /**
     *
     */
    private fun createBrowserBottomSheet(context: Context, url: String, resolveInfos: List<android.content.pm.ResolveInfo>, extras: android.os.Bundle?, onDismiss: (() -> Unit)?) {
        Timber.d("DBG: createBrowserBottomSheet called with ${resolveInfos.size} browsers")
        val packageManager = context.packageManager

        // Create browser items with icons, friendly names, and activity names
        val allBrowserItems = resolveInfos.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val activityName = resolveInfo.activityInfo.name
            val appName = resolveInfo.loadLabel(packageManager).toString()
            val icon = resolveInfo.loadIcon(packageManager)

            // Process app name: split PascalCase and clean up
            val displayName = splitPascalCase(appName)

            // Store as Quadruple: (displayName, packageName, activityName, icon)
            BrowserInfo(displayName, packageName, activityName, icon)
        }

        // Get MRU browsers and sort the list
        val mruBrowsers = getMruBrowsers(context)
        Timber.d("DBG: MRU browsers: $mruBrowsers")

        // Separate MRU and non-MRU browsers
        val mruItems = mutableListOf<BrowserInfo>()
        val otherItems = mutableListOf<BrowserInfo>()

        // First, add MRU browsers in order (maintaining MRU order)
        for (mruComponentName in mruBrowsers) {
            // MRU now stores full component names (package/activity)
            val mruItem = allBrowserItems.find {
                "${it.packageName}/${it.activityName}" == mruComponentName
            }
            if (mruItem != null) {
                mruItems.add(mruItem)
            }
        }

        // Then add remaining browsers alphabetically
        for (item in allBrowserItems) {
            val componentName = "${item.packageName}/${item.activityName}"
            if (!mruBrowsers.contains(componentName)) {
                otherItems.add(item)
            }
        }
        otherItems.sortBy { it.displayName } // Sort alphabetically

        // Combine: MRU first, then others
        val browserItems = mruItems + otherItems

        Timber.d("DBG: Final browser order - MRU: ${mruItems.map { it.displayName }}, Others: ${otherItems.map { it.displayName }}")

        // Create bottom sheet dialog
        val bottomSheetDialog = BottomSheetDialog(context)

        // Create RecyclerView for the browser list
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 4) // 4 columns
            adapter = BrowserBottomSheetAdapter(context, browserItems) { selectedBrowser ->
                try {
                    // Save as MRU before launching
                    saveMruBrowser(context, selectedBrowser.packageName, selectedBrowser.activityName)

                    val targetIntent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                        // Use setComponent instead of setPackage to specify exact activity
                        component = android.content.ComponentName(
                            selectedBrowser.packageName,
                            selectedBrowser.activityName
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        // Add extras if provided
                        if (extras != null) {
                            putExtras(extras)
                        }
                    }
                    Timber.d("DBG: Launching browser: ${selectedBrowser.packageName}/${selectedBrowser.activityName}")
                    context.startActivity(targetIntent)
                    bottomSheetDialog.dismiss()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to launch browser: ${selectedBrowser.displayName}")
                    android.widget.Toast.makeText(context, "Failed to open browser", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            // Add padding for better visual appearance
            val padding = 0.px
            setPadding(padding, 0, padding, 0)

            // Enable fade edge effects at top and bottom of RecyclerView
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(32.px) // 32dp fade length

            // Enable edge effect (overscroll glow) for better UX
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        // Create container with title
        val containerView = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = 16.px
            setPadding(padding, padding, padding, 0)
        }

        // Add title
        val titleView = MaterialTextView(context).apply {
            setText(R.string.open_with)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val padding = 8.px
            setPadding(padding, 0, padding, padding)
        }

        containerView.addView(titleView)
        containerView.addView(recyclerView)

        bottomSheetDialog.setContentView(containerView)

        // Execute the provided callback when the dialog is dismissed, or finish activity if no callback provided
        bottomSheetDialog.setOnDismissListener {
            if (onDismiss != null) {
                Timber.d("DBG: Bottom sheet dismissed, executing callback")
                onDismiss()
            }
        }

        // Ensure the bottom sheet respects the max width by setting it on the parent as well
        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)

                // Set proper behavior for landscape mode
                val screenHeight = context.resources.displayMetrics.heightPixels
                val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                if (isLandscape) {
                    // In landscape, set a higher peek height and expand the sheet
                    val peekHeight = (screenHeight * 0.6f).toInt() // 60% of screen height
                    behavior.peekHeight = peekHeight
                }

                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED

                // Set width constraints
                val maxWidthDp = 400
                val maxWidthPx = maxWidthDp.px
                val screenWidth = context.resources.displayMetrics.widthPixels
                val actualWidth = minOf(maxWidthPx, screenWidth)

                sheet.layoutParams = sheet.layoutParams.apply {
                    width = actualWidth
                }

                // Keep draggable behavior
                behavior.isDraggable = true
            }
        }

        bottomSheetDialog.show()
    }

    // Data class to hold browser information including activity name
    private data class BrowserInfo(
        val displayName: String,
        val packageName: String,
        val activityName: String,
        val icon: android.graphics.drawable.Drawable
    )

    // Custom adapter for RecyclerView to show browser icons with names
    private class BrowserBottomSheetAdapter(
        private val context: Context,
        private val browsers: List<BrowserInfo>,
        private val onBrowserClick: (BrowserInfo) -> Unit
    ) : RecyclerView.Adapter<BrowserBottomSheetAdapter.BrowserViewHolder>() {

        inner class BrowserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconView: android.widget.ImageView = view.findViewById(android.R.id.icon)
            val textView: android.widget.TextView = view.findViewById(android.R.id.text1)

            init {
                view.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onBrowserClick(browsers[position])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrowserViewHolder {
            // Create custom grid item layout programmatically
            val containerView = android.widget.LinearLayout(parent.context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                // Possibly determine the spacing between rows
                val padding = 4.px
                setPadding(padding, padding, padding, padding)
                // Set minimum height for consistent grid appearance - reduced for tighter spacing
                minimumHeight = 48.px
            }

            // Create ImageView for the icon
            val iconView = android.widget.ImageView(parent.context).apply {
                id = android.R.id.icon
                val iconSize = 48.px
                layoutParams = android.widget.LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    // Specify margin between icon and text
                    bottomMargin = 4.px
                }
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }

            // Create TextView for the label with fixed height to ensure icon alignment
            val textView = android.widget.TextView(parent.context).apply {
                id = android.R.id.text1
                val textHeight = 28.px
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    textHeight
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP // Align to top
                textSize = 11f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            containerView.addView(iconView)
            containerView.addView(textView)

            // Add ripple effect for better touch feedback
            val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            val backgroundDrawable = typedArray.getDrawable(0)
            typedArray.recycle()
            containerView.background = backgroundDrawable

            return BrowserViewHolder(containerView)
        }

        override fun onBindViewHolder(holder: BrowserViewHolder, position: Int) {
            val browser = browsers[position]

            // Set the browser icon
            holder.iconView.setImageDrawable(browser.icon)

            // Set the browser name
            holder.textView.text = browser.displayName
        }

        override fun getItemCount(): Int = browsers.size
    }

    // Helper function to split PascalCase strings and join with spaces
    private fun splitPascalCase(input: String): String {
        if (input.isEmpty()) return input

        // Use a more sophisticated regex that handles acronyms properly
        // This regex will split on:
        // 1. Lowercase followed by uppercase (camelCase)
        // 2. Multiple uppercase letters followed by lowercase (acronyms like FOSSBrowser -> FOSS Browser)
        val regex = "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])".toRegex()
        val words = input.split(regex)

        // Join the words with spaces, keeping original capitalization for acronyms
        return words.joinToString(" ") { it.trim() }.trim()
    }
}
