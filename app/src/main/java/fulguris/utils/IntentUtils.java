package fulguris.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.MailTo;
import androidx.core.content.FileProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import android.util.Log;
import android.webkit.WebView;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fulguris.R;
import fulguris.BuildConfig;
import fulguris.constant.Constants;

public class IntentUtils {

    @NonNull private final Activity mActivity;

    private static final Pattern ACCEPTED_URI_SCHEMA = Pattern.compile("(?i)"
        + // switch on case insensitive matching
        '('
        + // begin group for schema
        "(?:http|https|file)://" + "|(?:inline|data|about|javascript):" + "|(?:.*:.*@)"
        + ')' + "(.*)");

    public IntentUtils(@NonNull Activity activity) {
        mActivity = activity;
    }

    /**
     *
     * @param tab
     * @param url
     * @return
     */
    public Intent intentForUrl(@Nullable WebView tab, @NonNull String url) {
        Intent intent;
        try {
            intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException ex) {
            Log.w("Browser", "Bad URI " + url + ": " + ex.getMessage());
            return null;
        }

        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(null);
        intent.setSelector(null);

        if (tab != null) {
            intent.putExtra(Constants.INTENT_ORIGIN, tab.hashCode());
        }

        if (ACCEPTED_URI_SCHEMA.matcher(url).matches() && !isSpecializedHandlerAvailable(intent)) {
            return null;
        }

        return intent;
    }

    private Intent handleUnresolvableIntent(Intent intent) {
        String packageName = intent.getPackage();
        if (packageName != null) {
            Uri marketUri = Uri.parse("market://search?q=pname:" + packageName);
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
            marketIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            return marketIntent;
        }
        return null;
    }

    public boolean startActivityForIntent(Intent aIntent) {
        if (mActivity.getPackageManager().resolveActivity(aIntent, 0) == null) {
                aIntent = handleUnresolvableIntent(aIntent);
        }

        try {
            if (mActivity.startActivityIfNeeded(aIntent, -1)) {
                return true;
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            // TODO: 6/5/17 fix case where this could throw a FileUriExposedException due to file:// urls
        }
        return false;
    }

    public boolean startActivityForUrl(@Nullable WebView tab, @NonNull String url) {
        Intent intent = intentForUrl(tab,url);
        return startActivityForIntent(intent);
    }

    public boolean startActivityWithFallback(@Nullable WebView tab, @NonNull Intent intent, boolean onlyFallback) {
        if (!onlyFallback && startActivityForIntent(intent)) {
            Log.d("IntentUtils", "Intent successfully started.");
            // Intent was successfully handled
            return true;
        } else {
            Log.d("IntentUtils", "Failed to start intent, checking for fallback URL.");
            // Check for a fallback URL if the intent could not be started
            String fallbackUrl = intent.getStringExtra("browser_fallback_url");
            if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                Log.d("IntentUtils", "Fallback URL found: " + fallbackUrl);
                if (tab != null) {
                    tab.loadUrl(fallbackUrl);
                }
                return true;
            }
            Log.d("IntentUtils", "No fallback URL found.");
        }
        return false;
    }

    /**
     * Search for intent handlers that are specific to this URL aka, specialized
     * apps like google maps or youtube
     */
    private boolean isSpecializedHandlerAvailable(@NonNull Intent intent) {
        PackageManager pm = mActivity.getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(intent,
            PackageManager.GET_RESOLVED_FILTER);
        if (handlers == null || handlers.isEmpty()) {
            return false;
        }
        for (ResolveInfo resolveInfo : handlers) {
            IntentFilter filter = resolveInfo.filter;
            if (filter == null) {
                // No intent filter matches this intent?
                // Error on the side of staying in the browser, ignore
                continue;
            }
            // NOTICE: Use of && instead of || will cause the browser
            // to launch a new intent for every URL, using OR only
            // launches a new one if there is a non-browser app that
            // can handle it.
            // Previously we checked the number of data paths, but it is unnecessary
            // filter.countDataAuthorities() == 0 || filter.countDataPaths() == 0
            if (filter.countDataAuthorities() == 0) {
                // Generic handler, skip
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Handles URLs with special schemes such as mailto, tel, and intent by creating
     * and returning an appropriate Intent based on the scheme. This method also handles
     * file URLs by creating an Intent to open the file. If the URL does not match any
     * special scheme or if an error occurs (e.g., URISyntaxException), null is returned.
     *
     * @param activity The Activity context used to access package manager and resources.
     * @param url The URL to be handled. It can be a special scheme URL or a file URL.
     * @param view The WebView that may be used for additional context or actions.
     * @return An Intent that corresponds to the action required by the URL's scheme,
     *         or null if the URL does not match a special scheme or an error occurs.
     */
    public Intent handleSpecialSchemes(Activity activity, String url, WebView view) {
        Uri uri = Uri.parse(url);
        Log.d("IntentUtils", "Handling special schemes for URL: " + url);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        Log.d("IntentUtils", "Detected scheme: " + scheme);
        switch (scheme) {
            case "mailto":
                Log.d("IntentUtils", "Detected mailto scheme.");
                MailTo mailTo = MailTo.parse(url);
                return fulguris.utils.Utils.newEmailIntent(mailTo.getTo(), mailTo.getSubject(), mailTo.getBody(), mailTo.getCc());
            case "tel":
                Log.d("IntentUtils", "Detected tel scheme.");
                Intent intentTel = new Intent(Intent.ACTION_DIAL);
                intentTel.setData(Uri.parse(url));
                return intentTel;
            case "intent":
                try {
                    Log.d("IntentUtils", "Detected intent scheme.");
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setComponent(null);
                    intent.setSelector(null);
                    return intent;
                } catch (URISyntaxException e) {
                    Log.e("IntentUtils", "URISyntaxException for URL: " + url, e);
                    return null;
                }
            default:
                if (URLUtil.isFileUrl(url) && !UrlUtils.isSpecialUrl(url)) {
                    Log.d("IntentUtils", "Detected file URL.");
                    File file = new File(url.replace("file://", ""));
                    if (file.exists()) {
                        String newMimeType = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(fulguris.utils.Utils.guessFileExtension(file.toString()));
                        Intent intentFile = new Intent(Intent.ACTION_VIEW);
                        intentFile.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Uri contentUri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", file);
                        intentFile.setDataAndType(contentUri, newMimeType);
                        return intentFile;
                    } else {
                        Log.d("IntentUtils", "File not found for URL: " + url);
                        // Handle file not found scenario
                        return null;
                    }
                }
                Log.d("IntentUtils", "No special handling required for URL: " + url);
                return null;
        }
    }

    /**
     * Shares a URL to the system.
     *
     * @param url   the URL to share. If the URL is null
     *              or a special URL, no sharing will occur.
     * @param title the title of the URL to share. This
     *              is optional.
     */
    public void shareUrl(@Nullable String url, @Nullable String title, @StringRes int aTitleId) {
        if (url != null && !UrlUtils.isSpecialUrl(url)) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            if (title != null) {
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, url);
            mActivity.startActivity(Intent.createChooser(shareIntent, mActivity.getString(aTitleId)));
        }
    }

    /**
     * Same as above with default parameter.
     * @param url
     * @param title
     */
    public void shareUrl(@Nullable String url, @Nullable String title) {
        shareUrl(url,title,R.string.dialog_title_share);
    }

}
