package com.bestrong.core.io

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException(cause: Throwable? = null) : Exception("Contraseña incorrecta", cause)

/** El archivo no tiene el formato de una copia cifrada (cortado, editado o de otra app). */
class DamagedBackupException : Exception("Archivo dañado")

/**
 * Copia de seguridad cifrada con contraseña: AES-256-GCM con clave derivada por PBKDF2
 * (SHA-256, 210 000 iteraciones). Formato de texto: «BSENC1:<sal>:<iv>:<datos>» en Base64.
 */
object EncryptedBackup {
    private const val PREFIX = "BSENC1"
    private const val ITERATIONS = 210_000

    fun isEncrypted(text: String): Boolean = text.startsWith("$PREFIX:")

    fun encrypt(plain: String, password: CharArray): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.encodeToByteArray())
        val b64 = Base64.getEncoder()
        return listOf(PREFIX, b64.encodeToString(salt), b64.encodeToString(iv), b64.encodeToString(data)).joinToString(":")
    }

    fun decrypt(text: String, password: CharArray): String {
        val parts = text.trim().split(':')
        val b64 = Base64.getDecoder()
        val (salt, iv, data) = parts.takeIf { it.size == 4 && it[0] == PREFIX }
            ?.let { runCatching { Triple(b64.decode(it[1]), b64.decode(it[2]), b64.decode(it[3])) }.getOrNull() }
            ?.takeIf { (salt, iv, data) -> salt.size == 16 && iv.size == 12 && data.size >= 16 }
            ?: throw DamagedBackupException()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.doFinal(data).decodeToString()
        } catch (e: javax.crypto.AEADBadTagException) {
            // La etiqueta GCM no cuadra: contraseña equivocada (o datos alterados)
            throw WrongPasswordException(e)
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
