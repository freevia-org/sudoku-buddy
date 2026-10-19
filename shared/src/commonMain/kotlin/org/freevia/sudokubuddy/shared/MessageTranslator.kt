package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.model.MessageCatalog

/** Presentation only: saved messages and technique identifiers remain in English. */
class MessageTranslator(sources: List<String>, translations: List<String>) {
    private val catalog = MessageCatalog(sources.zip(translations))
    fun translate(text: String): String = catalog.translate(text)
}
