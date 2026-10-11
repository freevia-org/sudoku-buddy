package org.freevia.sudokubuddy.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.text.TextUtils
import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.util.Locale

internal data class LanguageChoice(val tag: String, val select: (String) -> Unit)
internal val LocalLanguageChoice = staticCompositionLocalOf { LanguageChoice("") {} }

internal object AppLanguages {
    // Autonyms help users recover even after selecting an unfamiliar language.
    val supported: List<Pair<String, String>> = listOf(
        "en" to "English",
        "uk" to "Українська",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "pt" to "Português",
        "pt-BR" to "Português (Brasil)",
        "pl" to "Polski",
        "it" to "Italiano",
        "nl" to "Nederlands",
        "cs" to "Čeština",
        "sk" to "Slovenčina",
        "hu" to "Magyar",
        "ro" to "Română",
        "bg" to "Български",
        "el" to "Ελληνικά",
        "sv" to "Svenska",
        "da" to "Dansk",
        "nb" to "Norsk bokmål",
        "nn" to "Norsk nynorsk",
        "fi" to "Suomi",
        "is" to "Íslenska",
        "et" to "Eesti",
        "lv" to "Latviešu",
        "lt" to "Lietuvių",
        "sl" to "Slovenščina",
        "hr" to "Hrvatski",
        "sr" to "Српски",
        "bs" to "Bosanski",
        "sq" to "Shqip",
        "mk" to "Македонски",
        "ru" to "Русский",
        "be" to "Беларуская",
        "ca" to "Català",
        "eu" to "Euskara",
        "gl" to "Galego",
        "mt" to "Malti",
        "ga" to "Gaeilge",
        "cy" to "Cymraeg",
        "lb" to "Lëtzebuergesch",
        "gd" to "Gàidhlig",
        "sco" to "Scots",
        "kw" to "Kernewek",
        "gv" to "Gaelg",
        "hsb" to "Hornjoserbšćina",
        "br" to "Brezhoneg",
        "co" to "Corsu",
        "fy" to "Frysk",
        "oc" to "Occitan",
        "fo" to "Føroyskt",
        "rm" to "Rumantsch",
        "se" to "Davvisámegiella",
        "hy" to "Հայերեն",
        "ka" to "ქართული",
        "az" to "Azərbaycan dili",
        "tr" to "Türkçe",
        "ar" to "العربية",
        "he" to "עברית",
        "fa" to "فارسی",
        "hi" to "हिन्दी",
        "bn" to "বাংলা",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu",
        "vi" to "Tiếng Việt",
        "th" to "ไทย",
        "sw" to "Kiswahili",
        "eo" to "Esperanto",
    )

    fun matchingTag(tags: String): String? {
        for (tag in tags.split(',').filter(String::isNotEmpty)) {
            supported.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.let { return it.first }
            val locale = Locale.forLanguageTag(tag)
            val preferred = when {
                locale.language == "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
                locale.language == "pt" && locale.country == "BR" -> "pt-BR"
                locale.language == "no" -> "nb"
                else -> locale.language
            }
            supported.firstOrNull { it.first == preferred }?.let { return it.first }
        }
        return null
    }

    /** Keep a choice made on Android 8–12 when the phone upgrades to Android 13+. */
    fun migrate(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = context.getSharedPreferences("app-language", Context.MODE_PRIVATE)
        if (!prefs.contains("tag")) return
        val previous = prefs.getString("tag", "").orEmpty()
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager.applicationLocales.isEmpty && matchingTag(previous) != null) {
            manager.applicationLocales = LocaleList.forLanguageTags(previous)
        }
        prefs.edit().remove("tag").apply()
    }

    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else context.getSharedPreferences("app-language", Context.MODE_PRIVATE).getString("tag", "").orEmpty()

    fun select(context: Context, tag: String) {
        require(tag.isEmpty() || supported.any { it.first == tag })
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences("app-language", Context.MODE_PRIVATE).edit().putString("tag", tag).apply()
        }
    }
}

/** Locale changes update this composition without discarding the active photographed puzzle. */
@Composable
internal fun LanguageContent(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    var tag by remember { mutableStateOf(AppLanguages.selected(base)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tag = AppLanguages.selected(base) }
    LaunchedEffect(systemConfiguration.locales.toLanguageTags()) { tag = AppLanguages.selected(base) }
    val configuration = remember(systemConfiguration, tag) {
        Configuration(systemConfiguration).apply {
            if (tag.isNotEmpty()) setLocales(LocaleList.forLanguageTags(tag))
        }
    }
    // Retain the Activity in the ContextWrapper chain: Compose discovers permission
    // and photo-picker ActivityResultRegistry owners through that chain.
    val localizedContext = remember(base, configuration) {
        ContextThemeWrapper(base, 0).apply {
            applyOverrideConfiguration(configuration)
            theme.setTo(base.theme)
        }
    }
    val direction = if (TextUtils.getLayoutDirectionFromLocale(configuration.locales[0]) == 1) {
        LayoutDirection.Rtl
    } else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides direction,
        LocalLanguageChoice provides LanguageChoice(tag) { selected ->
            AppLanguages.select(base, selected)
            tag = selected
        },
        content = content,
    )
}

@Composable
internal fun LanguageSetting() {
    val choice = LocalLanguageChoice.current
    var open by remember { mutableStateOf(false) }
    val selectedTag = AppLanguages.matchingTag(choice.tag)
    val current = AppLanguages.supported.firstOrNull { it.first == selectedTag }?.second
        ?: if (choice.tag.isEmpty()) localized("Use device language") else {
            val locale = Locale.forLanguageTag(choice.tag.substringBefore(','))
            locale.getDisplayName(locale)
        }
    TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(localized("Language"))
            Text(current)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(localized("Choose language")) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    for ((tag, label) in listOf("" to localized("Use device language")) + AppLanguages.supported) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                open = false
                                choice.select(tag)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = if (tag.isEmpty()) choice.tag.isEmpty() else selectedTag == tag, onClick = {
                                open = false
                                choice.select(tag)
                            })
                            Text(label, Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(localized("Cancel")) } },
        )
    }
}
