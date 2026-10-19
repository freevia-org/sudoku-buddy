package org.freevia.sudokubuddy.model

/**
 * Resource-backed presentation adapter for the solver's English explanations.
 * Technique names remain stable identifiers inside the solver and saved history.
 * Interpolated values can move in translations; they are never sent to a service.
 */
class MessageCatalog(messages: List<Pair<String, String>>) {
    private companion object {
        val argumentPattern = Regex("\\{(\\d+)\\}")
        val formatPattern = Regex("%(\\d+)\\\$s")
        // Numeric labels must not swallow prose or an assembled history summary,
        // changing what is described as a page count, row or number of cells.
        val countArguments = mapOf(
            "{0} of {1}" to setOf(0, 1),
            "{0} empty" to setOf(0),
            "{0} squares" to setOf(0),
            "{0}, entry" to setOf(0),
            "row {0}" to setOf(0),
            "column {0}" to setOf(0),
            "box {0}" to setOf(0),
            "at least {0}" to setOf(0),
            "Step {0}: {1}" to setOf(0),
            "{0} at step {1}" to setOf(1),
            "A printed {0}." to setOf(0),
            "{0} printed" to setOf(0),
            "{0} written in" to setOf(0),
            "{0} printed, {1}" to setOf(0),
            "{0} printed, {1}, {2} wrong" to setOf(0, 2),
        )
        val evidenceArguments: Map<String, Map<Int, String>> = buildMap {
            // Outlook joins one shape sentence to an ending that may contain
            // several sentences. A whitespace boundary splits the shape at its
            // first word, preventing either complete message from translating.
            put("{0} steps make up this route, with {1} as the hardest supported technique. {2} {3}",
                mapOf(0 to "[0-9]+", 2 to "[^.]+\\.", 3 to ".+"))
            val digit = "[1-9]"
            // VisualHint expands compact coordinates before presenting its caption.
            val cell = "(?:r[1-9]c[1-9]|row [1-9], column [1-9])"
            // Legacy partial sentences must obey the same bounds. Otherwise a
            // leading wildcard can consume the introduction before a full match.
            put("{0} is excluded by {1}, which ", mapOf(0 to digit, 1 to cell))
            put("{0} cannot hold {1} because {2} ", mapOf(0 to cell, 1 to digit, 2 to cell))
            put(" {0} in the same {1}.",
                mapOf(0 to digit, 1 to "(?:row|column|box)(?: [1-9])?"))
            for (verb in listOf("contains", "can only hold")) {
                put("{0} $verb {1}.", mapOf(0 to cell, 1 to digit))
                for (unit in listOf("row", "column", "box")) {
                    put("{0} is excluded by {1}, which $verb {2} in the same $unit {3}.",
                        mapOf(0 to digit, 1 to cell, 2 to digit, 3 to digit))
                    put("{0} cannot hold {1} because {2} $verb {3} in the same $unit {4}.",
                        mapOf(0 to cell, 1 to digit, 2 to cell, 3 to digit, 4 to digit))
                }
            }
        }
    }

    private data class Entry(val source: String, val translated: String) {
        val arguments = argumentPattern.findAll(source).toList()
        val literalSize = source.length - arguments.sumOf { it.value.length }
        val pattern: Regex = run {
            var start = 0
            val parts = buildString {
                for (argument in arguments) {
                    append(Regex.escape(source.substring(start, argument.range.first)))
                    val index = argument.groupValues[1].toInt()
                    val expression = evidenceArguments[source]?.get(index)
                        ?: if (index in countArguments[source].orEmpty()) "[0-9]+" else ".*?"
                    append("($expression)")
                    start = argument.range.last + 1
                }
                append(Regex.escape(source.substring(start)))
            }
            Regex(parts, RegexOption.DOT_MATCHES_ALL)
        }

        fun render(match: MatchResult, translateArgument: (String) -> String): String {
            val values = match.groupValues.drop(1).map(translateArgument)
            return formatPattern.replace(translated) { placeholder ->
                values.getOrNull(placeholder.groupValues[1].toInt() - 1) ?: placeholder.value
            }
        }
    }

    private val identity = messages.all { (source, translated) ->
        source == formatPattern.replace(translated) {
            "{${it.groupValues[1].toInt() - 1}}"
        }
    }
    private val exact = buildMap {
        putAll(messages.filter { !it.first.contains(argumentPattern) }.toMap())
        // The photo guard uses the same already-reviewed error copy as iOS.
        // Identity/English catalogs retain the more specific original sentence.
        val overlay = "Photograph the original puzzle rather than an annotated result screen."
        val fallback = get("Could not find the printed digits in that photo.")
        if (!containsKey(overlay) && fallback != null) put(overlay, fallback)
    }
    private val templates by lazy {
        messages.filter { it.first.contains(argumentPattern) }
            .map { Entry(it.first, it.second) }.sortedByDescending { it.literalSize }
    }
    private val templateFragments by lazy {
        templates.filter { entry ->
            // A final literal bounds a partial match. A template ending in an
            // argument could otherwise consume an empty value and lose the data.
            entry.literalSize >= 12 && entry.arguments.last().range.last < entry.source.lastIndex
        }
    }
    private val argumentFragments by lazy {
        templateFragments.filter { it.arguments.first().range.first == 0 }
    }
    private val fragmentsByFirstCharacter by lazy {
        // Most templates cannot start at this character. Retain the original
        // specificity order while avoiding a full scan for every paragraph byte.
        templateFragments.map { it.source.first() }.toSet().associateWith { first ->
            templateFragments.filter { it.source.first() == first || it.arguments.first().range.first == 0 }
        }
    }
    private val fragments = messages.filter { !it.first.contains(argumentPattern) &&
        it.first.trim().length >= 3 && it.first != it.second }
        .sortedByDescending { it.first.length }
        .groupBy { it.first.first() }
    private val cache = linkedMapOf<String, String>()

    // Callers serialize access (Android's wrapper synchronizes; Swift uses its main actor).
    fun translate(text: String): String {
        if (identity) return text
        cache.remove(text)?.let { cache[text] = it; return it }
        val translated = translate(text, 0)
        if (cache.size >= 512) cache.remove(cache.keys.first())
        cache[text] = translated
        return translated
    }

    private fun translate(text: String, depth: Int): String {
        exact[text]?.let { return it }
        if (depth > 3 || text.none(Char::isLetter)) return text
        for (entry in templates) {
            val match = entry.pattern.matchEntire(text) ?: continue
            return entry.render(match) { translate(it, depth + 1) }
        }
        // Some teaching paragraphs concatenate independently defined explanations.
        // Translate those pieces, keeping punctuation and numerical notation intact.
        return buildString {
            var at = 0
            while (at < text.length) {
                val fragment = fragments[text[at]]?.firstOrNull { (source, _) ->
                    text.startsWith(source, at) &&
                        (at == 0 || !source.first().isLetter() || !text[at - 1].isLetter()) &&
                        (at + source.length == text.length || !source.last().isLetter() ||
                            !text[at + source.length].isLetter())
                }
                val dynamic = (fragmentsByFirstCharacter[text[at]] ?: argumentFragments).asSequence().filter { entry ->
                    entry.source.first() == text[at] ||
                        (entry.arguments.first().range.first == 0 &&
                            (at == 0 || text[at - 1].isWhitespace() || text[at - 1] in ",;"))
                }.mapNotNull { entry ->
                    entry.pattern.matchAt(text, at)?.takeIf { match ->
                        val end = match.range.last + 1
                        end == text.length || !text[end - 1].isLetter() || !text[end].isLetter()
                    }?.let { entry to it }
                }.firstOrNull()
                if (dynamic != null && dynamic.second.value.length > (fragment?.first?.length ?: 0)) {
                    append(dynamic.first.render(dynamic.second) { translate(it, depth + 1) })
                    at += dynamic.second.value.length
                } else if (fragment != null) {
                    append(fragment.second)
                    at += fragment.first.length
                } else {
                    append(text[at++])
                }
            }
        }
    }
}
