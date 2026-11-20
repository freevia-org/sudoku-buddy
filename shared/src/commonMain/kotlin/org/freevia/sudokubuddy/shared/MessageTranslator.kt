package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.model.MessageCatalog

/** Presentation only: saved messages and technique identifiers remain in English. */
class MessageTranslator(sources: List<String>, translations: List<String>) {
    // Untranslated English is a better failure than an exception that ends the iOS process.
    private val catalog = runCatching { MessageCatalog(sources.zip(translations)) }.getOrNull()
    fun translate(text: String): String = catalog?.let { runCatching { it.translate(text) }.getOrNull() } ?: text
}
