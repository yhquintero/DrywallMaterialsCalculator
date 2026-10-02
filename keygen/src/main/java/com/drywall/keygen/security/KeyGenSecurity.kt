package com.drywall.keygen.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.drywall.common.security.SecureStorageUtils
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec

object KeyGenSecurity {
    private const val TAG = "KeyGenSecurity"
    private const val PREFS_NAME = "secure_keygen_keys_v2" 
    private const val KEY_PUBLIC = "public_key"
    private const val KEY_SOFTWARE_PRIVATE = "software_private_key"
    private const val KEY_SOFTWARE_PUBLIC = "software_public_key"
    private const val KEY_KEYSTORE_ALIAS = "drywall_keygen_rsa_4096"

    private var softwarePrivateKey: PrivateKey? = null

    var publicKeyString: String = ""
        private set

    fun init(context: Context) {
        val prefs = getEncryptedPrefs(context, PREFS_NAME)
        
        if (tryLoadSoftware(prefs)) {
            Log.i(TAG, "Llave RSA 4096 de software cargada")
            return
        }

        if (tryInitFromKeyStore(prefs)) {
            Log.i(TAG, "Llave RSA 4096 de KeyStore cargada")
            return
        }

        Log.w(TAG, "Generando nueva llave RSA 4096 maestra")
        generateNewSoftwareKey(prefs)
    }

    private fun tryLoadSoftware(prefs: SharedPreferences): Boolean {
        return try {
            val savedPrivateB64 = prefs.getString(KEY_SOFTWARE_PRIVATE, null)
            val savedPublicB64 = prefs.getString(KEY_SOFTWARE_PUBLIC, null)

            if ((savedPrivateB64 != null) && (savedPublicB64 != null)) {
                val keyFactory = KeyFactory.getInstance("RSA")
                val privSpec = PKCS8EncodedKeySpec(Base64.decode(savedPrivateB64, Base64.DEFAULT))
                softwarePrivateKey = keyFactory.generatePrivate(privSpec)
                publicKeyString = savedPublicB64
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "Error loading software key: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun generateNewSoftwareKey(prefs: SharedPreferences) {
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(4096, SecureRandom())
            val keyPair = kpg.generateKeyPair()
            softwarePrivateKey = keyPair.private
            publicKeyString = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            
            prefs.edit()
                .putString(KEY_SOFTWARE_PRIVATE, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
                .putString(KEY_SOFTWARE_PUBLIC, publicKeyString)
                .putString(KEY_PUBLIC, publicKeyString)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating 4096 key", e)
        }
    }

    private fun tryInitFromKeyStore(prefs: SharedPreferences): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)

            if (!ks.containsAlias(KEY_KEYSTORE_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                kpg.initialize(createKeyGenParameterSpec())
                kpg.generateKeyPair()
            }

            val entry = ks.getEntry(KEY_KEYSTORE_ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                val pubEncoded = entry.certificate.publicKey.encoded
                publicKeyString = Base64.encodeToString(pubEncoded, Base64.NO_WRAP)
                prefs.edit().putString(KEY_PUBLIC, publicKeyString).apply()
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "KeyStore initialization failed: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun createKeyGenParameterSpec(): KeyGenParameterSpec {
        return KeyGenParameterSpec.Builder(KEY_KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS, KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setKeySize(4096)
            .build()
    }

    fun signData(data: String): String? {
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val algorithms = listOf("SHA256withRSAandMGF1", "SHA256withRSA", "SHA256withRSA/PSS")

        val privKey = softwarePrivateKey
        if (privKey != null) {
            for (algo in algorithms) {
                try {
                    val sig = Signature.getInstance(algo)
                    sig.initSign(privKey)
                    sig.update(dataBytes)
                    return Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
                } catch (_: Exception) { }
            }
        }

        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val entry = ks.getEntry(KEY_KEYSTORE_ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                for (algo in algorithms) {
                    try {
                        val sig = Signature.getInstance(algo)
                        sig.initSign(entry.privateKey)
                        sig.update(dataBytes)
                        return Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "signData failed in KeyStore fallback", e)
        }

        Log.e(TAG, "CRÍTICO: No se pudo firmar la data con ninguna de las opciones disponibles")
        return null
    }

    fun getEncryptedPrefs(context: Context, fileName: String = PREFS_NAME): SharedPreferences {
        return try {
            SecureStorageUtils.getEncryptedPrefs(context, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Error fatal accediendo a $fileName", e)
            throw SecurityException(
                "Almacenamiento seguro no disponible. La aplicación no puede generar licencias."
            )
        }
    }
}
