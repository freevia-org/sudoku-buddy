package org.freevia.sudokubuddy.app

/** Applies a report update only while that report's photo is still the active one. */
internal fun <T : Any> updateIfSamePhoto(
    current: T?,
    expectedPhoto: Any,
    photoOf: (T) -> Any,
    update: (T) -> T,
): T? = current?.let { value ->
    if (photoOf(value) === expectedPhoto) update(value) else value
}
