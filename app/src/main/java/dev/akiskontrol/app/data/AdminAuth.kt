package dev.akiskontrol.app.data

import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Local maintenance account only. Credentials never enter the portable backup format. */
internal class AdminAuth(directory: File) {
    private val file = AtomicFile(File(directory, "admin.json"))
    private val lock = Any()
    private val random = SecureRandom()

    fun hasAdmin(): Boolean = synchronized(lock) { file.baseFile.exists() || File(file.baseFile.path + ".bak").exists() }

    fun create(username: String, password: String) = synchronized(lock) {
        val name = username.trim()
        require(name.length in 3..64 && name.none { it.isISOControl() }) { "Kullanıcı adı 3–64 karakter olmalı" }
        require(password.length in 8..1024) { "Parola 8–1024 karakter olmalı" }
        check(!hasAdmin()) { "Yerel yönetici hesabı zaten oluşturulmuş" }
        val salt = ByteArray(32).also(random::nextBytes)
        val key = derive(password, salt, ITERATIONS)
        val record = JSONObject().put("version", 1).put("iterations", ITERATIONS)
            .put("usernameHash", encode(usernameHash(name))).put("salt", encode(salt)).put("key", encode(key))
            .put("failures", 0).put("retryAfter", 0L)
        try { write(record) } finally { key.fill(0) }
    }

    fun verify(username: String, password: String): Boolean = synchronized(lock) {
        if (!hasAdmin()) return@synchronized false
        val record = runCatching { read() }.getOrElse { return@synchronized false }
        val now = System.currentTimeMillis()
        if (record.getLong("retryAfter") > now) return@synchronized false
        val salt = decode(record.getString("salt"))
        val expected = decode(record.getString("key"))
        val actual = derive(password.take(1024), salt, record.getInt("iterations"))
        val passwordMatches = MessageDigest.isEqual(expected, actual)
        val userMatches = MessageDigest.isEqual(decode(record.getString("usernameHash")), usernameHash(username.trim()))
        actual.fill(0)
        expected.fill(0)
        val matches = passwordMatches and userMatches and (password.length <= 1024)
        val failures = if (matches) 0 else (record.getInt("failures") + 1).coerceAtMost(30)
        // Persist throttling so restarting the process does not reset the guessing budget.
        val waitMillis = if (failures < 3) 0L else (1_000L shl (failures - 3).coerceAtMost(6)).coerceAtMost(60_000L)
        record.put("failures", failures).put("retryAfter", if (matches) 0L else now + waitMillis)
        write(record)
        matches
    }

    private fun read(): JSONObject {
        val bytes = file.openRead().use { input ->
            val result = input.readBytes()
            require(result.size < 16_384) { "Yönetici kaydı geçersiz" }
            result
        }
        return JSONObject(bytes.toString(Charsets.UTF_8)).also {
            require(it.getInt("version") == 1 && it.getInt("iterations") in 100_000..1_000_000)
            require(decode(it.getString("salt")).size == 32 && decode(it.getString("key")).size == 32)
            require(decode(it.getString("usernameHash")).size == 32 && it.getInt("failures") in 0..30)
            require(it.getLong("retryAfter") >= 0)
        }
    }

    private fun write(record: JSONObject) {
        val stream = file.startWrite()
        try {
            stream.write(record.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword(); chars.fill('\u0000') }
    }
    private fun usernameHash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    companion object { private const val ITERATIONS = 210_000 }
}
