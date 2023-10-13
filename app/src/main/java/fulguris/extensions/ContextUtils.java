package fulguris.extensions;


import static androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT;
import static androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY;
import static androidx.core.text.HtmlCompat.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL;

import android.content.Context;
import android.text.SpannedString;
import android.text.TextUtils;

import androidx.annotation.StringRes;
import androidx.core.text.HtmlCompat;

public class ContextUtils {


    /**
     * Allows us to have rich text and variable interpolation.
     * New line and white space handling is a mess though.
     * Kept it in Java cause we could not get varargs to work as we wanted in Kotlin.
     * Modded from: https://stackoverflow.com/a/23562910/3969362
     *
     * @param context
     * @param id
     * @param args
     * @return
     */
    public static CharSequence getText(Context context, @StringRes int id, Object... args) {
        for (int i = 0; i < args.length; ++i)
            args[i] = args[i] instanceof String ? HtmlCompat.toHtml(new SpannedString((String)args[i]),TO_HTML_PARAGRAPH_LINES_INDIVIDUAL) : args[i];
        return HtmlCompat.fromHtml(String.format(HtmlCompat.toHtml(new SpannedString(context.getText(id)),TO_HTML_PARAGRAPH_LINES_INDIVIDUAL), args),FROM_HTML_MODE_COMPACT);
    }

}