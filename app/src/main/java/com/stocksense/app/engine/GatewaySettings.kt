package com.stocksense.app.engine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.stocksense.app.BuildConfig
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.*

/** Connection credentials are device-local, encrypted, and excluded from Android backup. */
internal class GatewaySettings(context: Context) {
    data class Connection(val url: String, val token: String)
    private val file = File(context.noBackupFilesDir, "gateway-connection.json")
    private val alias = "quantsense-gateway-connection"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun read(): Connection {
        val defaults = Connection(BuildConfig.NEMOTRON_GATEWAY_URL, BuildConfig.NEMOTRON_GATEWAY_TOKEN)
        if (!file.exists()) return defaults
        return try {
            val data = Json.parseToJsonElement(file.readText()).jsonObject
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,
                Base64.decode(data.getValue("iv").jsonPrimitive.content, Base64.NO_WRAP)))
            val token = String(cipher.doFinal(Base64.decode(data.getValue("token").jsonPrimitive.content,
                Base64.NO_WRAP)), Charsets.UTF_8)
            Connection(data.getValue("url").jsonPrimitive.content, token)
        } catch (_: Exception) {
            // Invalidated device keys require the user to re-enter connection settings.
            Connection(defaults.url, "")
        }
    }

    @Synchronized fun save(url: String, token: String) {
        require(GatewayEndpoint.isAllowed(url, BuildConfig.DEBUG)) { "Use HTTPS, or a debug loopback URL." }
        require(token.length >= 32) { "Gateway access token must contain at least 32 characters." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val data = buildJsonObject {
            put("url", url.trimEnd('/'))
            put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            put("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(data.toString())
        check(temporary.renameTo(file)) { "Could not save gateway connection." }
    }
}
