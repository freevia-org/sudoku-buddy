package org.freevia.sudokubuddy.app

/** Platform wrapper preserves serialized cache access for Compose and background callers. */
internal class MessageCatalog(messages: List<Pair<String, String>>) {
    private val shared = org.freevia.sudokubuddy.model.MessageCatalog(messages)
    @Synchronized
    fun translate(text: String): String = shared.translate(text)
}
