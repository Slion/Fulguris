package fulguris.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Base64
import android.webkit.CookieManager
import fulguris.autofill.SiteCredentials
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Saves the passwords and the cookies of the browser to a single file and restores them from such a file,
 * so that a fresh install does not need everything to be entered again.
 *
 * Passwords: the logins of [SiteCredentials]. WebView itself does not keep passwords we could read.
 * Cookies: WebView has no API to list all cookies, so they are read from its cookie database.
 *
 * The file is JSON. Because it holds passwords and logged in sessions it can be encrypted with a
 * passphrase (PBKDF2 + AES-256-GCM); [isEncrypted] tells which kind of file we are given.
 */
object CredentialsBackup {

    private const val FORMAT = "fulguris-credentials-backup"
    private const val FORMAT_ENCRYPTED = "fulguris-credentials-backup-encrypted"

    class Result(val passwords: Int, val cookies: Int)

    class WrongPassphraseException : Exception()
    class NotABackupException : Exception()

    private class CookieRecord(
        val host: String, val name: String, val value: String, val path: String,
        /** Milliseconds since epoch, 0 for a session cookie */
        val expires: Long,
        val secure: Boolean, val httpOnly: Boolean,
        /** -1 unspecified, 0 none, 1 lax, 2 strict */
        val sameSite: Int
    )

    // ---------------------------------------------------------------- export

    /** Build the backup file content. Slow, call it off the main thread. */
    fun create(context: Context, passphrase: String?): ByteArray {
        val cookies = readCookies(context)
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("created", System.currentTimeMillis())
            .put("passwords", SiteCredentials.exportAll(context))
            .put("cookies", JSONArray().also { arr ->
                for (c in cookies) arr.put(JSONObject()
                    .put("host", c.host).put("name", c.name).put("value", c.value).put("path", c.path)
                    .put("expires", c.expires).put("secure", c.secure).put("httpOnly", c.httpOnly)
                    .put("sameSite", c.sameSite))
            })
        val plain = root.toString().toByteArray(Charsets.UTF_8)
        return if (passphrase.isNullOrEmpty()) plain else encrypt(plain, passphrase)
    }

    /** Number of passwords and cookies in [data], for the confirmation message. */
    fun summary(data: ByteArray, passphrase: String?): Result = parse(data, passphrase).first

    private fun cookieDatabase(context: Context): File? {
        // Regular browsing data directory of WebView, the incognito process has its own, see App.kt
        val base = File(context.applicationInfo.dataDir, "app_webview")
        // The location moved with some Chromium versions
        return listOf("Default/Cookies", "Default/Network/Cookies")
            .map { File(base, it) }
            .filter { it.isFile }
            .maxByOrNull { it.lastModified() }
    }

    private fun readCookies(context: Context): List<CookieRecord> {
        // Make sure what is in memory reaches the database
        CookieManager.getInstance().flush()
        val source = cookieDatabase(context) ?: return emptyList()
        // Work on a copy: the original is open in WebView, we don't want to lock it or leave it altered
        val copyDir = File(context.cacheDir, "cookies_backup").apply { mkdirs() }
        val copy = File(copyDir, "Cookies")
        val result = mutableListOf<CookieRecord>()
        try {
            for (suffix in listOf("", "-journal", "-wal", "-shm")) {
                val f = File(source.path + suffix)
                if (f.isFile) f.copyTo(File(copy.path + suffix), overwrite = true)
            }
            val db = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                db.rawQuery("SELECT * FROM cookies", null).use { c ->
                    val iHost = c.getColumnIndex("host_key")
                    val iName = c.getColumnIndex("name")
                    val iValue = c.getColumnIndex("value")
                    val iPath = c.getColumnIndex("path")
                    val iExpires = c.getColumnIndex("expires_utc")
                    val iHasExpires = c.getColumnIndex("has_expires")
                    val iSecure = c.getColumnIndex("is_secure")
                    val iHttpOnly = c.getColumnIndex("is_httponly")
                    val iSameSite = c.getColumnIndex("samesite")
                    if (iHost < 0 || iName < 0 || iValue < 0) return emptyList()
                    while (c.moveToNext()) {
                        val expiresUtc = if (iExpires >= 0) c.getLong(iExpires) else 0L
                        val hasExpires = if (iHasExpires >= 0) c.getInt(iHasExpires) != 0 else expiresUtc != 0L
                        result.add(CookieRecord(
                            host = c.getString(iHost) ?: continue,
                            name = c.getString(iName) ?: continue,
                            value = c.getString(iValue) ?: "",
                            path = if (iPath >= 0) c.getString(iPath) ?: "/" else "/",
                            // Chromium time is microseconds since 1601-01-01
                            expires = if (hasExpires && expiresUtc > 0) expiresUtc / 1000 - 11644473600000L else 0L,
                            secure = iSecure >= 0 && c.getInt(iSecure) != 0,
                            httpOnly = iHttpOnly >= 0 && c.getInt(iHttpOnly) != 0,
                            sameSite = if (iSameSite >= 0) c.getInt(iSameSite) else -1
                        ))
                    }
                }
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not read cookies database")
            throw e
        } finally {
            // Never leave a copy of the cookies lying around
            copyDir.listFiles()?.forEach { it.delete() }
            copyDir.delete()
        }
        return result
    }

    // ---------------------------------------------------------------- import

    fun isEncrypted(data: ByteArray): Boolean = try {
        JSONObject(String(data, Charsets.UTF_8)).optString("format") == FORMAT_ENCRYPTED
    } catch (e: Exception) {
        false
    }

    /**
     * Restore passwords and cookies from [data]. Slow, call it off the main thread.
     * @throws WrongPassphraseException if the file is encrypted and [passphrase] does not open it
     * @throws NotABackupException if the file is not one of ours
     */
    fun restore(context: Context, data: ByteArray, passphrase: String?): Result {
        val (counts, root) = parse(data, passphrase)
        val passwords = root.optJSONArray("passwords") ?: JSONArray()
        val cookies = root.optJSONArray("cookies") ?: JSONArray()

        SiteCredentials.importAll(context, passwords)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        val now = System.currentTimeMillis()
        var restored = 0
        for (i in 0 until cookies.length()) {
            val o = cookies.optJSONObject(i) ?: continue
            val host = o.optString("host")
            val name = o.optString("name")
            if (host.isEmpty() || name.isEmpty()) continue
            val expires = o.optLong("expires", 0L)
            if (expires != 0L && expires <= now) continue // already expired
            val secure = o.optBoolean("secure")
            val path = o.optString("path", "/").ifEmpty { "/" }
            val domainCookie = host.startsWith(".")
            val url = (if (secure) "https://" else "http://") + host.trimStart('.') + path

            val sb = StringBuilder()
            sb.append(name).append('=').append(o.optString("value"))
            if (domainCookie) sb.append("; Domain=").append(host)
            sb.append("; Path=").append(path)
            if (expires != 0L) sb.append("; Max-Age=").append((expires - now) / 1000)
            if (secure) sb.append("; Secure")
            if (o.optBoolean("httpOnly")) sb.append("; HttpOnly")
            when (o.optInt("sameSite", -1)) {
                0 -> sb.append("; SameSite=None")
                1 -> sb.append("; SameSite=Lax")
                2 -> sb.append("; SameSite=Strict")
            }
            cm.setCookie(url, sb.toString())
            restored++
        }
        cm.flush()
        return Result(counts.passwords, restored)
    }

    private fun parse(data: ByteArray, passphrase: String?): Pair<Result, JSONObject> {
        var root = try {
            JSONObject(String(data, Charsets.UTF_8))
        } catch (e: Exception) {
            throw NotABackupException()
        }
        if (root.optString("format") == FORMAT_ENCRYPTED) {
            if (passphrase.isNullOrEmpty()) throw WrongPassphraseException()
            root = try {
                JSONObject(String(decrypt(root, passphrase), Charsets.UTF_8))
            } catch (e: Exception) {
                throw WrongPassphraseException()
            }
        }
        if (root.optString("format") != FORMAT) throw NotABackupException()
        return Pair(Result(root.optJSONArray("passwords")?.length() ?: 0, root.optJSONArray("cookies")?.length() ?: 0), root)
    }

    // ---------------------------------------------------------------- encryption

    private fun kdfAlgorithm() =
        // SHA-256 variant only exists from Oreo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int, algorithm: String): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded, "AES")
    }

    private fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val algorithm = kdfAlgorithm()
        val iterations = 200_000
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, iterations, algorithm), GCMParameterSpec(128, iv))
        return JSONObject()
            .put("format", FORMAT_ENCRYPTED)
            .put("version", 1)
            .put("kdf", algorithm)
            .put("iterations", iterations)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)
    }

    private fun decrypt(envelope: JSONObject, passphrase: String): ByteArray {
        val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(passphrase, salt, envelope.getInt("iterations"), envelope.getString("kdf")),
            GCMParameterSpec(128, iv)
        )
        return cipher.doFinal(data)
    }
}
