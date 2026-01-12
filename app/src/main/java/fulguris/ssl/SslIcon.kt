package fulguris.ssl

import fulguris.R
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

/**
 * Creates the proper [Drawable] to represent the [SslState].
 */
fun Context.createSslDrawableForState(sslState: SslState): Drawable? = when (sslState) {
    is SslState.None -> null
    is SslState.Valid -> {
        ContextCompat.getDrawable(this, R.drawable.ic_secured)
    }
    is SslState.Invalid -> {
        ContextCompat.getDrawable(this, R.drawable.ic_unsecured)
    }
}

/**
 * Returns the proper icon resource ID to represent the [SslState].
 * Returns 0 if no icon should be shown.
 */
@DrawableRes
fun getSslIconRes(sslState: SslState): Int = when (sslState) {
    is SslState.None -> 0
    is SslState.Valid -> R.drawable.ic_secured
    is SslState.Invalid -> R.drawable.ic_unsecured
}

