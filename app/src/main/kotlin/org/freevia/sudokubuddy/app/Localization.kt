package org.freevia.sudokubuddy.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray

private data class MessageSource(val source: String, val id: Int)

/** English source messages are an interchange format, not the displayed app locale. */
internal object Localization {
    private var sources: List<MessageSource>? = null
    private val catalogs = object : LinkedHashMap<String, MessageCatalog>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MessageCatalog>?): Boolean = size > 3
    }

    @Synchronized
    fun catalog(context: Context): MessageCatalog {
        val language = context.resources.configuration.locales.toLanguageTags()
        return catalogs.getOrPut(language) {
            val definitions = sources ?: context.assets.open("messages.json").bufferedReader().use {
                val json = JSONArray(it.readText())
                List(json.length()) { index ->
                    val message = json.getJSONObject(index)
                    @Suppress("DiscouragedApi")
                    val id = context.resources.getIdentifier(message.getString("key"), "string", context.packageName)
                    check(id != 0) { "Missing bundled message resource" }
                    MessageSource(message.getString("source"), id)
                }
            }.also { sources = it }
            MessageCatalog(definitions.map { it.source to context.getString(it.id) })
        }
    }
}

@Composable
internal fun rememberMessageCatalog(): MessageCatalog {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration.locales.toLanguageTags()) { Localization.catalog(context) }
}

@Composable
internal fun localized(text: String): String = rememberMessageCatalog().translate(text)

internal fun Context.localized(text: String): String = Localization.catalog(this).translate(text)
