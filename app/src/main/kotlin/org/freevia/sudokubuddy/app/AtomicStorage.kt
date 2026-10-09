package org.freevia.sudokubuddy.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Write beside the destination and replace only after a complete, flushed write. */
internal fun atomicWrite(file: File, write: (FileOutputStream) -> Unit) {
    val temporary = File.createTempFile(file.name, ".pending", file.parentFile)
    try {
        FileOutputStream(temporary).use { output ->
            write(output)
            output.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temporary.delete()
    }
}
