/*
 * Copyright 2014 A.C.R. Development
 */
package fulguris.download;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import fulguris.BuildConfig;
import fulguris.activity.MainActivity;
import fulguris.R;
import fulguris.activity.WebBrowserActivity;
import fulguris.browser.WebBrowser;
import fulguris.database.downloads.DownloadEntry;
import fulguris.database.downloads.DownloadsRepository;
import fulguris.di.DatabaseScheduler;
import fulguris.di.MainScheduler;
import fulguris.di.NetworkScheduler;
import fulguris.dialog.BrowserDialog;
import fulguris.constant.Constants;
import fulguris.settings.preferences.UserPreferences;
import fulguris.utils.FileUtils;
import fulguris.utils.Utils;
import fulguris.view.WebPageTab;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import timber.log.Timber;

import static fulguris.utils.UrlUtils.guessFileName;

/**
 * Handle download requests
 */
@Singleton
public class DownloadHandler {
    private static final String COOKIE_REQUEST_HEADER = "Cookie";
    private static final String REFERER_REQUEST_HEADER = "Referer";
    private static final String USERAGENT_REQUEST_HEADER = "User-Agent";

    private final DownloadsRepository downloadsRepository;
    private final DownloadManager downloadManager;
    private final Scheduler databaseScheduler;
    private final Scheduler networkScheduler;
    private final Scheduler mainScheduler;

    long iDownloadId = 0;
    String iFilename="";


    @Inject
    public DownloadHandler(DownloadsRepository downloadsRepository,
                           DownloadManager downloadManager,
                           @DatabaseScheduler Scheduler databaseScheduler,
                           @NetworkScheduler Scheduler networkScheduler,
                           @MainScheduler Scheduler mainScheduler) {
        this.downloadsRepository = downloadsRepository;
        this.downloadManager = downloadManager;
        this.databaseScheduler = databaseScheduler;
        this.networkScheduler = networkScheduler;
        this.mainScheduler = mainScheduler;
    }

    /**
     * Notify the host application a download should be done, or that the data
     * should be streamed if a streaming viewer is available.
     *
     * @param context            The context in which the download was requested.
     * @param url                The full url to the content that should be downloaded
     * @param userAgent          User agent of the downloading application.
     * @param contentDisposition Content-disposition http header, if present.
     * @param mimeType           The mimeType of the content reported by the server
     * @param contentSize        The size of the content
     */
    public void onDownloadStart(@NonNull Activity context, @NonNull UserPreferences manager, @NonNull String url, String userAgent,
                                @Nullable String contentDisposition, String mimeType, @NonNull String contentSize) {

        Timber.d("DOWNLOAD: Trying to download from URL: %s", url);
        Timber.d("DOWNLOAD: Content disposition: %s", contentDisposition);
        Timber.d("DOWNLOAD: MimeType: %s", mimeType);
        Timber.d("DOWNLOAD: User agent: %s", userAgent);

        // if we're dealing wih A/V content that's not explicitly marked
        // for download, check if it's streamable.
        if (contentDisposition == null
            || !contentDisposition.regionMatches(true, 0, "attachment", 0, 10)) {
            // query the package manager to see if there's a registered handler
            // that matches.
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(url), mimeType);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            ResolveInfo info = context.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
            if (info != null) {
                // If we resolved to ourselves, we don't want to attempt to
                // load the url only to try and download it again.
                if (BuildConfig.APPLICATION_ID.equals(info.activityInfo.packageName)
                    || MainActivity.class.getName().equals(info.activityInfo.name)) {
                    // someone (other than us) knows how to handle this mime
                    // type with this scheme, don't download.
                    try {
                        context.startActivity(intent);
                        return;
                    } catch (ActivityNotFoundException ex) {
                        // Best behavior is to fall back to a download in this
                        // case
                    }
                }
            }
        }
        onDownloadStartNoStream(context, manager, url, userAgent, contentDisposition, mimeType, contentSize);
    }

    // This is to work around the fact that java.net.URI throws Exceptions
    // instead of just encoding URL's properly
    // Helper method for onDownloadStartNoStream
    @NonNull
    private static String encodePath(@NonNull String path) {
        char[] chars = path.toCharArray();

        boolean needed = false;
        for (char c : chars) {
            if (c == '[' || c == ']' || c == '|') {
                needed = true;
                break;
            }
        }
        if (!needed) {
            return path;
        }

        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            if (c == '[' || c == ']' || c == '|') {
                sb.append('%');
                sb.append(Integer.toHexString(c));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Notify the host application a download should be done, even if there is a
     * streaming viewer available for this type.
     *
     * @param context            The context in which the download is requested.
     * @param url                The full url to the content that should be downloaded
     * @param userAgent          User agent of the downloading application.
     * @param contentDisposition Content-disposition http header, if present.
     * @param mimetype           The mimetype of the content reported by the server
     * @param contentSize        The size of the content
     */
    /* package */
    private void onDownloadStartNoStream(@NonNull final Activity context, @NonNull UserPreferences preferences,
                                         @NonNull String url, String userAgent,
                                         String contentDisposition, @Nullable String mimetype, @NonNull String contentSize) {
        iFilename = guessFileName(url, contentDisposition, mimetype, null);

        WebBrowserActivity ba = (WebBrowserActivity)context;

        // Check to see if we have an SDCard
        String status = Environment.getExternalStorageState();
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            int title;
            String msg;

            // Check to see if the SDCard is busy, same as the music app
            if (status.equals(Environment.MEDIA_SHARED)) {
                msg = context.getString(R.string.download_sdcard_busy_dlg_msg);
                title = R.string.download_sdcard_busy_dlg_title;
            } else {
                msg = context.getString(R.string.download_no_sdcard_dlg_msg);
                title = R.string.download_no_sdcard_dlg_title;
            }

            Dialog dialog = new MaterialAlertDialogBuilder(context).setTitle(title)
                .setIcon(android.R.drawable.ic_dialog_alert).setMessage(msg)
                .setPositiveButton(R.string.action_ok, null).show();
            BrowserDialog.setDialogSize(context, dialog);
            return;
        }

        // java.net.URI is a lot stricter than KURL so we have to encode some
        // extra characters. Fix for b 2538060 and b 1634719
        WebAddress webAddress;
        try {
            webAddress = new WebAddress(url);
            webAddress.setPath(encodePath(webAddress.getPath()));
        } catch (Exception e) {
            // This only happens for very bad urls, we want to catch the
            // exception here
            Timber.e(e, "Exception while trying to parse url '" + url + '\'');
            ba.showSnackbar( R.string.problem_download);
            return;
        }

        String addressString = webAddress.toString();
        Uri uri = Uri.parse(addressString);
        final DownloadManager.Request request;
        try {
            request = new DownloadManager.Request(uri);
        } catch (IllegalArgumentException e) {
            ba.showSnackbar( R.string.cannot_download);
            return;
        }

        // set downloaded file destination to /sdcard/Download.
        // or, should it be set to one of several Environment.DIRECTORY* dirs
        // depending on mimetype?
        String location = preferences.getDownloadDirectory();
        //String location = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getPath();
        location = FileUtils.addNecessarySlashes(location);
        Uri downloadFolder = Uri.parse(location);

        if (!isWriteAccessAvailable(downloadFolder)) {
            ba.showSnackbar( R.string.problem_location_download);
            return;
        }
        String newMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Utils.guessFileExtension(iFilename));
        Timber.d("New mimetype: %s", newMimeType);
        request.setMimeType(newMimeType);
        request.setDestinationUri(Uri.parse(Constants.FILE + location + iFilename));
        // let this downloaded file be scanned by MediaScanner - so that it can
        // show up in Gallery app, for example.
        request.setVisibleInDownloadsUi(true);
        request.allowScanningByMediaScanner();
        request.setDescription(webAddress.getHost());
        // XXX: Have to use the old url since the cookies were stored using the
        // old percent-encoded url.
        String cookies = CookieManager.getInstance().getCookie(url);
        request.addRequestHeader(COOKIE_REQUEST_HEADER, cookies);
        request.addRequestHeader(REFERER_REQUEST_HEADER, url);
        request.addRequestHeader(USERAGENT_REQUEST_HEADER, userAgent);
        // We don't want to show the default download complete notification as it just opens our app when you click it
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

        //noinspection VariableNotUsedInsideIf
        if (mimetype == null) {
            Timber.d("Mimetype is null");
            if (TextUtils.isEmpty(addressString)) {
                return;
            }
            // We must have long pressed on a link or image to download it. We
            // are not sure of the mimetype in this case, so do a head request
            final Disposable disposable = new FetchUrlMimeType(downloadManager, request, addressString, cookies, userAgent)
                .create()
                .subscribeOn(networkScheduler)
                .observeOn(mainScheduler)
                .subscribe(result -> {
                    switch (result.iCode) {
                        case FAILURE_ENQUEUE:
                            ba.showSnackbar(R.string.cannot_download);
                            break;
                        case FAILURE_LOCATION:
                            ba.showSnackbar( R.string.problem_location_download);
                            break;
                        case SUCCESS:
                            iDownloadId = result.iDownloadId;
                            iFilename = result.iFilename;
                            ba.showSnackbar( context.getString(R.string.download_pending)  + '\n' + iFilename);
                            break;
                    }
                });
        } else {
            Timber.d("Valid mimetype, attempting to download");
            try {
                iDownloadId = downloadManager.enqueue(request);
            } catch (IllegalArgumentException e) {
                // Probably got a bad URL or something
                Timber.e(e,"Unable to enqueue request");
                ba.showSnackbar( R.string.cannot_download);
            } catch (SecurityException e) {
                // TODO write a download utility that downloads files rather than rely on the system
                // because the system can only handle Environment.getExternal... as a path
                ba.showSnackbar( R.string.problem_location_download);
            }
            ba.showSnackbar( context.getString(R.string.download_pending) + '\n' + iFilename);
        }

        // save download in database
        WebBrowser browserActivity = (WebBrowser) context;
        WebPageTab view = browserActivity.getTabModel().getCurrentTab();

        if (view != null && !view.isIncognito()) {
            downloadsRepository.addDownloadIfNotExists(new DownloadEntry(url, iFilename, contentSize))
                .subscribeOn(databaseScheduler)
                .subscribe(aBoolean -> {
                    if (!aBoolean) {
                        Timber.d("error saving download to database");
                    }
                });
        }
    }

    private static boolean isWriteAccessAvailable(@NonNull Uri fileUri) {
        if (fileUri.getPath() == null) {
            return false;
        }
        File file = new File(fileUri.getPath());

        if (!file.isDirectory() && !file.mkdirs()) {
            return false;
        }

        try {
            if (file.createNewFile()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
