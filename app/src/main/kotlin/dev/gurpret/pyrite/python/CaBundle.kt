package dev.gurpret.pyrite.python

import android.content.Context
import android.util.Log
import java.io.File

/**
 * A PEM bundle of the system's trusted CAs, for OpenSSL (SSL_CERT_FILE). Android keeps
 * them as one file per certificate, named by a hash OpenSSL doesn't use, so Python's
 * default SSL context would otherwise trust nothing.
 */
object CaBundle {
    private val SOURCES = listOf(
        "/apex/com.android.conscrypt/cacerts",  // updatable store, Android 14+
        "/system/etc/security/cacerts",
    )

    /** Builds (or reuses) filesDir/cacerts.pem; null if no CA store was found. */
    @Synchronized
    fun ensure(context: Context): File? {
        val certs = SOURCES.asSequence()
            .map { File(it).listFiles()?.filter { f -> f.isFile }.orEmpty() }
            .firstOrNull { it.isNotEmpty() } ?: return null
        val bundle = File(context.filesDir, "cacerts.pem")
        val stamp = File(context.filesDir, "cacerts.stamp")
        val signature = "${certs.size} ${certs.maxOf { it.lastModified() }}"
        if (bundle.length() > 0 && stamp.takeIf { it.exists() }?.readText() == signature) return bundle
        return try {
            val tmp = File(context.filesDir, "cacerts.pem.tmp")
            tmp.bufferedWriter().use { out ->
                for (cert in certs.sortedBy { it.name }) {
                    // Each file is "openssl x509 -text" output; keep just the PEM block.
                    val text = cert.readText()
                    val start = text.indexOf("-----BEGIN CERTIFICATE-----")
                    val end = text.indexOf("-----END CERTIFICATE-----")
                    if (start >= 0 && end > start) {
                        out.write(text, start, end - start + "-----END CERTIFICATE-----".length)
                        out.write("\n")
                    }
                }
            }
            tmp.renameTo(bundle)
            stamp.writeText(signature)
            bundle
        } catch (e: Exception) {
            Log.w("CaBundle", "couldn't build CA bundle", e)
            bundle.takeIf { it.length() > 0 }
        }
    }
}
