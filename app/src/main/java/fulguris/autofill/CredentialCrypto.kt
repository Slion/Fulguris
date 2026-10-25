package fulguris.autofill

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the saved logins with an AES-256-GCM key that lives in the Android Keystore:
 * the key never leaves the secure hardware or system service, so the stored file is useless
 * when copied away from the device. The file is not part of Android auto backup, a new
 * install gets its logins back through the backup file instead (fulguris.backup).
 *
 * Below Android 6 there is no such key store, the text is then kept as is, in app private storage.
 */
object CredentialCrypto {

    private const val ALIAS = "fulguris_site_credentials"
    private const val PREFIX = "ENC1:"

    fun isEncrypted(stored: String) = stored.startsWith(PREFIX)

    fun encrypt(plain: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return plain
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv + data, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Losing the passwords would be worse than storing them without the extra layer
            Timber.e(e, "Keystore encryption failed, saving logins unencrypted")
            plain
        }
    }

    /** Also accepts text that was stored unencrypted. @throws Exception if the key cannot open it */
    fun decrypt(stored: String): String {
        if (!isEncrypted(stored)) return stored
        val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
