package org.freevia.sudokubuddy.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Bundled legal notices remain available without opening a website. */
@Composable
internal fun LicenseScreen(onClose: () -> Unit) {
    val assets = LocalContext.current.assets
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val files = remember { assets.list("licenses").orEmpty().filter { it != "sources.json" }.sorted() }
    val text = remember(selected) {
        selected?.takeIf { it in files }?.let { name -> assets.open("licenses/$name").bufferedReader().use { it.readText() } }
    }
    val paragraphs = remember(text) { text?.split("\n\n") }
    val back: () -> Unit = { if (selected == null) onClose() else selected = null }
    // The system gesture steps back the same way the arrow does, rather than leaving About.
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize()) {
        AppBar(title = "Licenses", onBack = back)
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp)) {
            if (paragraphs == null) {
                items(files) { name ->
                    TextButton(onClick = { selected = name }) { Text(name) }
                }
            } else {
                // Short paragraphs avoid one extremely tall text layout at large font sizes.
                items(paragraphs) { paragraph ->
                    Text(paragraph, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
        }
    }
}
