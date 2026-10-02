package com.drywall.common.security

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {
    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "Autenticación de Seguridad",
        subtitle: String = "Ingrese sus credenciales para continuar",
        onResult: (Boolean) -> Unit
    ) {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(activity)
                val authPrompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            when (errorCode) {
                                BiometricPrompt.ERROR_HW_NOT_PRESENT,
                                BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                                    android.util.Log.w("BiometricHelper",
                                        "Dispositivo sin hardware biométrico. Se requiere autenticación por credenciales de dispositivo.")
                                    onResult(false)
                                }
                                BiometricPrompt.ERROR_USER_CANCELED,
                                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                BiometricPrompt.ERROR_CANCELED -> {
                                    onResult(false)
                                }
                                else -> {
                                    android.util.Log.w("BiometricHelper", "Error de autenticación: $errorCode - $errString")
                                    onResult(false)
                                }
                            }
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onResult(true)
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(authenticators)
                    .build()

                try {
                    authPrompt.authenticate(promptInfo)
                } catch (e: Exception) {
                    android.util.Log.e("BiometricHelper", "Biometric prompt failed, denying access", e)
                    onResult(false)
                }
            }
            else -> {
                Toast.makeText(activity, "Debe configurar un bloqueo de pantalla seguro", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        }
    }
}
