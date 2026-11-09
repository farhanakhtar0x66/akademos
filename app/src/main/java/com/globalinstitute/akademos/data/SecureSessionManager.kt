package com.globalinstitute.akademos.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the login session encrypted with a hardware-backed Android Keystore key,
 * so the token cannot be copied off the phone and reused elsewhere.
 */
class SecureSessionManager(context: Context) : SessionManager {
    private val file = File(context.noBackupFilesDir, "session.bin")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val alias = "akademos_session_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(json.encodeToString(UserSession.serializer(), session).toByteArray())
        val out = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
        file.writeText(out)
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@withContext null
            val (iv, data) = file.readText().split(":").let { it[0] to it[1] }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val plain = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))
            json.decodeFromString(UserSession.serializer(), String(plain))
        }.getOrElse { file.delete(); null }
    }

    override suspend fun deleteSession() {
        withContext(Dispatchers.IO) { file.delete() }
    }
}
