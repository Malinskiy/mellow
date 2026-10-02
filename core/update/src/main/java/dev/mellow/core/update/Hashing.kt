package dev.mellow.core.update

import java.io.File
import java.security.MessageDigest

internal const val IO_BUFFER_SIZE = 8 * 1024

private const val HEX_DIGITS = "0123456789abcdef"

internal fun newSha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

internal fun ByteArray.toLowerHex(): String {
    val chars = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        chars[index * 2] = HEX_DIGITS[value ushr 4]
        chars[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return String(chars)
}

/** Lowercase hex SHA-256 of the file's contents. Blocking IO: call from an IO dispatcher. */
internal fun File.sha256Hex(): String {
    val digest = newSha256()
    inputStream().use { input ->
        val buffer = ByteArray(IO_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toLowerHex()
}
