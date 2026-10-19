package org.freevia.sudokubuddy.recognize
internal actual fun loadDigitModel(): ByteArray =
    checkNotNull(DigitClassifier::class.java.getResourceAsStream("/digits.bin")) {
        "digits.bin is missing from the classpath"
    }.use { it.readBytes() }
