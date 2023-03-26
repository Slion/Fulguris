package acr.browser.lightning.database.history;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import acr.browser.lightning.database.History;
import acr.browser.lightning.database.WebPageKt;
import acr.browser.lightning.utils.Preconditions;
import acr.browser.lightning.utils.Utils;
import io.reactivex.Completable;

/**
 * The class responsible for importing and exporting
 * history in the JSON format.
 * <p>
 * Copied from BookmarkExporter by Cook I.T! on 18/3/23.
 */
public final class HistoryExporter {

    private static final String TAG = "HistoryExporter";

    private static final String KEY_URL = "url";
    private static final String KEY_TITLE = "title";
    private static final String KEY_LASTTIME = "lastTime";

    private HistoryExporter() {}

    /**
     * Exports the history to a file.
     *
     * @param historyList the history to export.
     * @param aStream the stream to export to.
     * @return an observable that emits a completion
     * event when the export is complete, or an error
     * event if there is a problem.
     */
    @NonNull
    public static Completable exportHistoryToFile(@NonNull final List<History.Entry> historyList,
                                                  @NonNull final OutputStream aStream) {
        return Completable.fromAction(() -> {
            Preconditions.checkNonNull(historyList);
            BufferedWriter historyWriter = null;
            try {
                //noinspection IOResourceOpenedButNotSafelyClosed
                historyWriter = new BufferedWriter(new OutputStreamWriter(aStream));

                JSONObject object = new JSONObject();
                for (History.Entry item : historyList) {
                    object.put(KEY_TITLE, item.getTitle());
                    object.put(KEY_URL, item.getUrl());
                    object.put(KEY_LASTTIME, item.getLastTimeVisited());
                    historyWriter.write(object.toString());
                    historyWriter.newLine();
                }
            } finally {
                Utils.close(historyWriter);
            }
        });
    }

    /**
     * Attempts to import history entries from the
     * given file. If the file is not in a
     * supported format, it will fail.
     *
     * @param inputStream The stream to import from.
     * @return A list of history entries, or throws an exception if the history cannot be imported.
     **/
    @NonNull
    public static List<History.Entry> importHistoryFromFileStream(@NonNull InputStream inputStream) throws Exception {
        BufferedReader historyReader = null;
        try {
            //noinspection IOResourceOpenedButNotSafelyClosed
            historyReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            List<History.Entry> history = new ArrayList<>();
            while ((line = historyReader.readLine()) != null) {
                JSONObject object = new JSONObject(line);
                final History.Entry entry = new History.Entry(
                    object.getString(KEY_URL),
                    object.getString(KEY_TITLE),
                    object.getInt(KEY_LASTTIME)
                );
                history.add(entry);
            }

            return history;
        } finally {
            Utils.close(historyReader);
        }
    }

}
