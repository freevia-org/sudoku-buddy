@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.freevia.sudokubuddy.recognize
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.memcpy
internal actual fun loadDigitModel(): ByteArray {
    val path = checkNotNull(NSBundle.mainBundle.pathForResource("digits", ofType = "bin")) {
        "digits.bin is missing from the app bundle"
    }
    val data = checkNotNull(NSData.dataWithContentsOfFile(path))
    return ByteArray(data.length.toInt()).also { bytes ->
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    }
}
