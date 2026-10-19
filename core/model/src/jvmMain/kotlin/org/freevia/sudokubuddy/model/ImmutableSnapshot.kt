package org.freevia.sudokubuddy.model

import java.util.Collections

internal actual fun <T> immutableSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
