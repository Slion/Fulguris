package fulguris.favicon

import fulguris.R
import fulguris.extensions.invert
import fulguris.extensions.pad
import fulguris.extensions.safeUse
import fulguris.utils.DrawableUtils
import fulguris.utils.FileUtils
import fulguris.utils.getFilteredColor
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import androidx.annotation.ColorInt
import androidx.annotation.WorkerThread
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.palette.graphics.Palette
import io.reactivex.Completable
import io.reactivex.Maybe
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive model that can fetch favicons
 * from URLs and also cache them.
 */
@Singleton
class FaviconModel @Inject constructor(
    private val application: Application
) {

    private val loaderOptions = BitmapFactory.Options()
    private val bookmarkIconSize = application.resources.getDimensionPixelSize(R.dimen.material_grid_small_icon)
    private val faviconCache = object : LruCache<String, Bitmap>(fulguris.utils.FileUtils.megabytesToBytes(1).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * Retrieves a favicon from the memory cache.Bitmap may not be present if no bitmap has been
     * added for the URL or if it has been evicted from the memory cache.
     *
     * @param url the URL to retrieve the bitmap for.
     * @return the bitmap associated with the URL, may be null.
     */
    private fun getFaviconFromMemCache(url: String): Bitmap? {
        synchronized(faviconCache) {
            return faviconCache.get(url)
        }
    }

    fun createDefaultBitmapForTitle(title: String?): Bitmap {
        val firstTitleCharacter = title?.takeIf(String::isNotBlank)?.let { it[0] } ?: '?'

        @ColorInt val defaultFaviconColor = fulguris.utils.DrawableUtils.characterToColorHash(firstTitleCharacter, application)

        return fulguris.utils.DrawableUtils.createRoundedLetterImage(
            firstTitleCharacter,
            bookmarkIconSize,
            bookmarkIconSize,
            defaultFaviconColor
        )
    }

    /**
     * Adds a bitmap to the memory cache for the given URL.
     *
     * @param url    the URL to map the bitmap to.
     * @param bitmap the bitmap to store.
     */
    private fun addFaviconToMemCache(url: String, bitmap: Bitmap) {
        synchronized(faviconCache) {
            faviconCache.put(url, bitmap)
        }
    }

    /**
     * Retrieves the favicon for a URL, may be from network or cache.
     *
     * @param url   The URL that we should retrieve the favicon for.
     * @param title The title for the web page.
     */
    fun faviconForUrl(url: String, title: String, aOnDark: Boolean): Maybe<Bitmap> = Maybe.create {
        val uri = url.toUri().toValidUri()
            ?: return@create it.onSuccess(createDefaultBitmapForTitle(title).pad())

        val cachedFavicon = getFaviconFromMemCache(url)

        if (cachedFavicon != null) {
            return@create it.onSuccess(cachedFavicon.pad())
        }

        // Try get the icon for the theme that was asked
        var faviconCacheFile = getFaviconCacheFile(application, uri, aOnDark)
        // If no icon and we ask for the dark variant
        if (!faviconCacheFile.exists() && aOnDark) {
            // Try get the light variant then
            faviconCacheFile = getFaviconCacheFile(application, uri, false)
        }

        if (faviconCacheFile.exists()) {
            val storedFavicon = BitmapFactory.decodeFile(faviconCacheFile.path, loaderOptions)

            if (storedFavicon != null) {
                addFaviconToMemCache(url, storedFavicon)
                return@create it.onSuccess(storedFavicon.pad())
            }
        }

        return@create it.onSuccess(createDefaultBitmapForTitle(title).pad())
    }

    /**
     * Caches a favicon for a particular URL.
     *
     * @param favicon the favicon to cache.
     * @param url     the URL to cache the favicon for.
     * @return an observable that notifies the consumer when it is complete.
     */
    fun cacheFaviconForUrl(favicon: Bitmap, url: String): Completable = Completable.create { emitter ->
        val uri = url.toUri().toValidUri() ?: return@create emitter.onComplete()

        Timber.d("Caching icon for ${uri.host}")

        /** TODO: This code was duplicated from [ImageView.setImageForTheme] fix it, somehow */
        // Check if that favicon is dark enough that it needs an inverted variant to be used on dark theme
        val palette = Palette.from(favicon).generate()
        val filteredColor = Color.BLACK or getFilteredColor(favicon) // OR with opaque black to remove transparency glitches
        val filteredLuminance = ColorUtils.calculateLuminance(filteredColor)
        //val color = Color.BLACK or (it.getVibrantColor(it.getLightVibrantColor(it.getDominantColor(Color.BLACK))))
        val color = palette.getDominantColor(Color.BLACK)
        val luminance = ColorUtils.calculateLuminance(color)
        // Lowered threshold from 0.025 to 0.02 for it to work with bbc.com/future
        // At 0.015 it does not kick in for GitHub
        val threshold = 0.02
        // Use white filter on darkest favicons
        // Filtered luminance  works well enough for theregister.co.uk and github.com while not impacting bbc.co.uk
        // Luminance from dominant color was added to prevent toytowngermany.com from being filtered
        if (luminance < threshold && filteredLuminance < threshold
            // Needed to exclude white favicon variant provided by GitHub dark web theme
            && palette.dominantSwatch != null)
        {
            // Yes, that favicon needs an inverted variant
            FileOutputStream(getFaviconCacheFile(application, uri, true)).safeUse {
                favicon.invert().compress(Bitmap.CompressFormat.PNG, 100, it)
                it.flush()
                emitter.onComplete()
            }
        } else {
            // Dark favicon cache not needed anymore then, just delete that file if any.
            // Notably the case after switching to app dark theme and using GitHub or other sites providing favicon for dark web theme.
            getFaviconCacheFile(application, uri, true).delete()
        }

        FileOutputStream(getFaviconCacheFile(application, uri, false)).safeUse {
            favicon.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.flush()
            emitter.onComplete()
        }
    }

    companion object {

        private const val TAG = "FaviconModel"

        /**
         * Creates the cache file for the favicon image. File name will be in the form of "hash of URI host".png
         *
         * @param app the context needed to retrieve the cache directory.
         * @param validUri the URI to use as a unique identifier.
         * @return a valid cache file.
         */
        @WorkerThread
        fun getFaviconCacheFile(app: Application, validUri: ValidUri, aOnDark: Boolean): File {
            val hash = validUri.host.hashCode().toString()

            return File(app.cacheDir, if (aOnDark) "ondark-" else {""} + "$hash.png")
        }
    }

}
