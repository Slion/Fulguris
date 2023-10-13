package fulguris.ssl

import fulguris.R
import android.content.Context
import android.graphics.drawable.Drawable
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
