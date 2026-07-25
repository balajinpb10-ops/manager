package com.vaultra.app.crypto

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Handles master-password verification and derives the passphrase used to
 * open the SQLCipher-encrypted vault database. The verifier + salt are
 * stored in EncryptedSharedPreferences, itself protected by a key inside
 * the Android Keystore (hardware-backed on most devices).
 */
class CryptoManager(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "vaultra_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isSetupComplete(): Boolean = prefs.contains("salt") && prefs.contains("verifier")

    /** Creates a brand-new vault: generates a salt, derives a key, stores a verifier hash. */
    fun setupNewVault(masterPassword: CharArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(masterPassword, salt)
        val verifier = sha256(derived)
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("verifier", Base64.encodeToString(verifier, Base64.NO_WRAP))
            .apply()
        return derived
    }

    /** Returns the derived key (usable as SQLCipher passphrase) if the password is correct, else null. */
    fun unlock(masterPassword: CharArray): ByteArray? {
        val saltB64 = prefs.getString("salt", null) ?: return null
        val verifierB64 = prefs.getString("verifier", null) ?: return null
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expectedVerifier = Base64.decode(verifierB64, Base64.NO_WRAP)
        val derived = deriveKey(masterPassword, salt)
        val actualVerifier = sha256(derived)
        return if (actualVerifier.contentEquals(expectedVerifier)) derived else null
    }

    /** Re-encrypts vault metadata under a new master password. Caller must re-save the DB with the new key. */
    fun changeMasterPassword(newPassword: CharArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(newPassword, salt)
        val verifier = sha256(derived)
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("verifier", Base64.encodeToString(verifier, Base64.NO_WRAP))
            .apply()
        return derived
    }

    /** Returns this device's currently stored (salt, verifier) pair, if a vault has been set up.
     *  Embedded into a full backup so a restore can reinstate the same master-password
     *  verification material on a fresh install. */
    fun getStoredSaltAndIv(): Pair<ByteArray, ByteArray>? {
        val saltB64 = prefs.getString("salt", null) ?: return null
        val verifierB64 = prefs.getString("verifier", null) ?: return null
        return Base64.decode(saltB64, Base64.NO_WRAP) to Base64.decode(verifierB64, Base64.NO_WRAP)
    }

    /** Reinstates the master-password verifier on this device using salt/verifier material recovered
     *  from a restored backup, then returns the freshly derived key so the vault DB can be
     *  re-encrypted under it. */
    fun reKeyFromRestore(password: CharArray, salt: ByteArray, verifier: ByteArray): ByteArray {
        val derived = deriveKey(password, salt)
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("verifier", Base64.encodeToString(verifier, Base64.NO_WRAP))
            .apply()
        return derived
    }

    fun getAutoLockMinutes(): Int = prefs.getInt("auto_lock_mins", 2)
    fun setAutoLockMinutes(mins: Int) { prefs.edit().putInt("auto_lock_mins", mins).apply() }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean("biometric_enabled", false)
    fun setBiometricEnabled(enabled: Boolean) { prefs.edit().putBoolean("biometric_enabled", enabled).apply() }

    /** Stores the derived key so biometric unlock can retrieve it without re-typing the password.
     *  This value lives only inside EncryptedSharedPreferences (Keystore-backed). */
    fun storeKeyForBiometric(key: ByteArray) {
        prefs.edit().putString("biometric_key", Base64.encodeToString(key, Base64.NO_WRAP)).apply()
    }
    fun getKeyForBiometric(): ByteArray? =
        prefs.getString("biometric_key", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
    fun clearBiometricKey() { prefs.edit().remove("biometric_key").apply() }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = 150_000): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun sha256(data: ByteArray): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256").digest(data)
}
