package org.freevia.sudokubuddy.shared

internal fun jsonString(value: String): String = buildString {
    append('"')
    for (ch in value) when (ch) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (ch.code < 32) append("\\u" + ch.code.toString(16).padStart(4, '0')) else append(ch)
    }
    append('"')
}
