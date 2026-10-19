package org.freevia.sudokubuddy.model

internal expect fun <T> immutableSnapshot(values: List<T>): List<T>
