/*
 * Copyright 2014 A.C.R. Development
 */
package acr.browser.lightning.download;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.webkit.DownloadListener;

import com.anthonycr.grant.PermissionsManager;
import com.anthonycr.grant.PermissionsResultAction;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import javax.inject.Inject;

import acr.browser.lightning.R;
import acr.browser.lightning.browser.activity.BrowserActivity;
import acr.browser.lightning.database.downloads.DownloadsRepository;
import acr.browser.lightning.di.Injector;
import acr.browser.lightning.dialog.BrowserDialog;
import acr.browser.lightning.extensions.ActivityExtensions;
import acr.browser.lightning.log.Logger;
import acr.browser.lightning.preference.UserPreferences;
import acr.browser.lightning.utils.Utils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Objects;

import static acr.browser.lightning.utils.UrlUtils.guessFileName;

public class LightningDownloadListener extends BroadcastReceiver implements DownloadListener {

    private static final String TAG = "LightningDownloader";

    private final Activity mActivity;

    @Inject UserPreferences userPreferences;
    @Inject DownloadHandler downloadHandler;
    @Inject DownloadManager downloadManager;
    @Inject DownloadsRepository downloadsRepository;
    @Inject Logger logger;

    public LightningDownloadListener(Activity context) {
        Injector.getInjector(context).inject(this);
        mActivity = context;
    }



    // From BroadcastReceiver
    // We use this to receive download complete notifications
    @Override
    public void onReceive(Context context, Intent intent) {

        if (Objects.equals(intent.getAction(), DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
            //Fetching the download id received with the broadcast
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            //Checking if the received broadcast is for our enqueued download by matching download id
            // TODO: what if we have multiple downloads going on? I doubt our architecture supports that properly for now.
            if (downloadHandler.iDownloadId == id) {

                // Our download is complete check if it was a success
                DownloadManager.Query q = new DownloadManager.Query();
                q.setFilterById(id);
                Cursor c = downloadManager.query(q);

                String contentTitle = "";
                String contentText = "";
                boolean success = false;

                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        success = true;
                        contentTitle = context.getString(R.string.download_complete);
                        String filePath = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        contentText = filePath.substring( filePath.lastIndexOf('/')+1, filePath.length());
                    }
                    // Assume failure
                    //if (status == DownloadManager.STATUS_FAILED) {
                    // That stupidly returns "placeholder" on F(x)tec Pro1
                    //filename = c.getString(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                }
                c.close();

                // Create system notification
                // Passing a null intent in case of failure means nothing happens when user taps our notification
                // User needs to dismiss it using swipe
                PendingIntent pendingIntent = null;
                Intent downloadsIntent = null;

                if (!success) {
                    contentTitle = context.getString(R.string.download_failed);
                    contentText =  downloadHandler.iFilename;
                }
                else
                {
                    // Create pending intent to open downloads folder when tapping notification
                    downloadsIntent = Utils.getIntentForDownloads(mActivity, userPreferences.getDownloadDirectory());
                    pendingIntent = PendingIntent.getActivity(mActivity, 0, downloadsIntent, 0);
                }

                NotificationCompat.Builder builder = new NotificationCompat.Builder(mActivity, ((BrowserActivity)mActivity).CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_file_download) // TODO: different icon for failure?
                        .setContentTitle(contentTitle) //
                        .setContentText(contentText) //
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        // Set the intent that will fire when the user taps the notification
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mActivity);
                // notificationId is a unique int for each notification that you must define
                notificationManager.notify(0, builder.build());

                //Show a snackbar with a link to open the downloaded file
                if (success) {
                    final Intent i = downloadsIntent;
                    ActivityExtensions.makeSnackbar(mActivity,contentTitle, ActivityExtensions.KDuration, userPreferences.getToolbarsBottom()?Gravity.TOP: Gravity.BOTTOM).setAction(R.string.show, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    context.startActivity(i);
                                }
                            }).show();
                }
                else {
                    ActivityExtensions.snackbar(mActivity,contentTitle,userPreferences.getToolbarsBottom()?Gravity.TOP: Gravity.BOTTOM);
                }
            }
        }
    }

    private String getFileName(long id)
    {
        DownloadManager.Query q = new DownloadManager.Query();
        q.setFilterById(id);
        Cursor c = downloadManager.query(q);
        String filename = "";

        if (c.moveToFirst()) {
            int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                String filePath = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                filename = filePath.substring( filePath.lastIndexOf('/')+1, filePath.length() );
            } else if (status == DownloadManager.STATUS_FAILED) {
                // That stupidly returns "placeholder" on F(x)tec Pro1
                //filename = c.getString(c.getColumnIndex(DownloadManager.COLUMN_REASON));
                filename =  "Failed";
            }
        }
        c.close();
        return filename;
    }


    @Override
    public void onDownloadStart(@NonNull final String url, final String userAgent,
                                final String contentDisposition, final String mimetype, final long contentLength) {
        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(mActivity,
            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
            new PermissionsResultAction() {
                @Override
                public void onGranted() {
                    doDownloadStart(url, userAgent, contentDisposition, mimetype, contentLength);
                }

                @Override
                public void onDenied(String permission) {
                    //TODO show message
                }
            });

        // Some download link spawn an empty tab, just close it then
        if (mActivity instanceof BrowserActivity) {
            ((BrowserActivity)mActivity).closeCurrentTabIfEmpty();
        }

    }

    private void doDownloadStart(@NonNull final String url, final String userAgent,
                                 final String contentDisposition, final String mimetype, final long contentLength)
    {
        final String fileName = guessFileName(url, contentDisposition, mimetype, null);
        final String downloadSize;

        if (contentLength > 0) {
            downloadSize = Formatter.formatFileSize(mActivity, contentLength);
        } else {
            downloadSize = mActivity.getString(R.string.unknown_size);
        }

        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    downloadHandler.onDownloadStart(mActivity, userPreferences, url, userAgent, contentDisposition, mimetype, downloadSize);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity); // dialog
        String message = mActivity.getString(R.string.dialog_download, downloadSize);
        Dialog dialog = builder.setTitle(fileName)
                .setMessage(message)
                .setPositiveButton(mActivity.getResources().getString(R.string.action_download),
                        dialogClickListener)
                .setNegativeButton(mActivity.getResources().getString(R.string.action_cancel),
                        dialogClickListener).show();
        BrowserDialog.setDialogSize(mActivity, dialog);
        logger.log(TAG, "Downloading: " + fileName);

    }

}
