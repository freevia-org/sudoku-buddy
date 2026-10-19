package org.freevia.sudokubuddy.model

internal actual fun <T> immutableSnapshot(values: List<T>): List<T> {
    val snapshot = values.toList()
    return object : AbstractList<T>() {
        override val size: Int get() = snapshot.size
        override fun get(index: Int): T = snapshot[index]
    }
}
