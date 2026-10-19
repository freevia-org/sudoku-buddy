package org.freevia.sudokubuddy.app

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageCatalogTest {
    @Test
    fun `result-screen refusal uses approved photo copy while English keeps specific advice`() {
        val source = "Photograph the original puzzle rather than an annotated result screen."
        val generic = "Could not find the printed digits in that photo."
        assertEquals(source, MessageCatalog(listOf(generic to generic)).translate(source))
        assertEquals("Photo illisible", MessageCatalog(listOf(generic to "Photo illisible")).translate(source))
        assertEquals("Original requis", MessageCatalog(listOf(generic to "Photo illisible", source to "Original requis")).translate(source))
    }

    @Test
    fun `route summaries translate full shape and ending sentences`() {
        val catalog = MessageCatalog(listOf(
            "{0} steps make up this route, with {1} as the hardest supported technique. {2} {3}" to
                "%1\$s Schritte mit %2\$s. %3\$s %4\$s",
            "Naked single" to "Offener Einer",
            "Every one of them fills a square in." to "Jeder Schritt füllt ein Feld.",
            "That is the rest of the puzzle, start to finish." to "Das löst den Rest des Rätsels.",
            "{0} of them fill a square in; the rest clear candidates out of the way first." to
                "%1\$s füllen ein Feld; der Rest entfernt Kandidaten.",
            "That is the rest of the puzzle. One square in it needs a solver-assisted answer because the app's supported techniques did not find a move. Its detailed search proof is unavailable." to
                "Der Rest ist gelöst. Ein Feld braucht Solver-Hilfe ohne detaillierten Beweis.",
        ))
        assertEquals("51 Schritte mit Offener Einer. Jeder Schritt füllt ein Feld. Das löst den Rest des Rätsels.",
            catalog.translate("51 steps make up this route, with Naked single as the hardest supported technique. " +
                "Every one of them fills a square in. That is the rest of the puzzle, start to finish."))
        assertEquals("52 Schritte mit Offener Einer. 50 füllen ein Feld; der Rest entfernt Kandidaten. " +
            "Der Rest ist gelöst. Ein Feld braucht Solver-Hilfe ohne detaillierten Beweis.",
            catalog.translate("52 steps make up this route, with Naked single as the hardest supported technique. " +
                "50 of them fill a square in; the rest clear candidates out of the way first. " +
                "That is the rest of the puzzle. One square in it needs a solver-assisted answer because the app's " +
                "supported techniques did not find a move. Its detailed search proof is unavailable."))
    }

    @Test
    fun `page count formatting cannot rewrite prose containing of`() {
        val catalog = MessageCatalog(listOf(
            "{0} of {1}" to "%1\$s/%2\$s",
            "{0} squares" to "nombre de cases : %1\$s",
        ))
        assertEquals("2/14", catalog.translate("2 of 14"))
        assertEquals("Earlier reasoning ruled digits out of this square.",
            catalog.translate("Earlier reasoning ruled digits out of this square."))
        assertEquals("nombre de cases : 2", catalog.translate("2 squares"))
        assertEquals("The highlights show supporting squares",
            catalog.translate("The highlights show supporting squares"))
    }

    @Test
    fun `whole numbered evidence sentences preserve paragraph boundaries and grammar`() {
        val catalog = MessageCatalog(listOf(
            "Only {0} can go here." to "Hier kann nur %1\$s stehen.",
            "row {0}, column {1}" to "Zeile %1\$s, Spalte %2\$s",
            "{0} is excluded by {1}, which " to "%1\$s wird durch %2\$s ausgeschlossen, was ",
            "contains" to "enthält",
            " {0} in the same {1}." to " %1\$s im selben %2\$s.",
            "{0} is excluded by {1}, which contains {2} in the same row {3}." to
                "%1\$s ist ausgeschlossen: %2\$s enthält bereits %3\$s in derselben Zeile %4\$s.",
            "{0} is excluded by {1}, which can only hold {2} in the same column {3}." to
                "%1\$s ist ausgeschlossen: %2\$s kann in derselben Spalte %4\$s nur %3\$s enthalten.",
        ))
        assertEquals("Hier kann nur 5 stehen.\n\n" +
            "1 ist ausgeschlossen: r5c9 enthält bereits 1 in derselben Zeile 5.\n" +
            "2 ist ausgeschlossen: r6c5 kann in derselben Spalte 5 nur 2 enthalten.",
            catalog.translate("Only 5 can go here.\n\n" +
                "1 is excluded by r5c9, which contains 1 in the same row 5.\n" +
                "2 is excluded by r6c5, which can only hold 2 in the same column 5."))
        assertEquals("Hier kann nur 5 stehen.\n\n" +
            "1 ist ausgeschlossen: Zeile 5, Spalte 9 enthält bereits 1 in derselben Zeile 5.",
            catalog.translate("Only 5 can go here.\n\n" +
                "1 is excluded by row 5, column 9, which contains 1 in the same row 5."))
    }

    @Test
    fun `assembled supporting evidence translates verbs and unit names`() {
        val catalog = MessageCatalog(listOf(
            "{0} is excluded by {1}, which " to "%1\$s wird durch %2\$s ausgeschlossen; es ",
            "contains" to "enthält",
            " {0} in the same {1}." to " %1\$s in derselben %2\$s.",
            "row" to "Zeile",
        ))
        assertEquals("6 wird durch r1c1 ausgeschlossen; es enthält 6 in derselben Zeile.",
            catalog.translate("6 is excluded by r1c1, which contains 6 in the same row."))
    }

    @Test
    fun `history summary counts keep their printed and written meanings`() {
        val catalog = MessageCatalog(listOf(
            "{0} printed" to "wydrukowane: %1\$s",
            "{0} printed, {1}" to "wydrukowane: %1\$s, %2\$s",
            "{0} printed, {1}, {2} wrong" to "wydrukowane: %1\$s, %2\$s, błędne: %3\$s",
            "{0} written in" to "wpisane: %1\$s",
        ))
        assertEquals("wydrukowane: 30, wpisane: 1", catalog.translate("30 printed, 1 written in"))
        assertEquals("wydrukowane: 30, wpisane: 1, błędne: 1", catalog.translate("30 printed, 1 written in, 1 wrong"))
    }

    @Test
    fun `assembled explanations translate dynamic fragments without losing coordinates`() {
        val catalog = MessageCatalog(listOf(
            "\nRemove {0} from " to "\nEntferne %1\$s aus ",
            " Earlier eliminations: {0}." to " Frühere Ausschlüsse: %1\$s.",
        ))
        assertEquals("\nEntferne 7 aus r1c1, r2c1. Frühere Ausschlüsse: 2, 4.",
            catalog.translate("\nRemove 7 from r1c1, r2c1. Earlier eliminations: 2, 4."))
    }

    @Test
    fun `optional empty arguments still match a translated template`() {
        val catalog = MessageCatalog(listOf("Saved puzzle{0}." to "Enregistré%1\$s."))
        assertEquals("Enregistré.", catalog.translate("Saved puzzle."))
    }

    @Test
    fun `default catalog preserves English exactly`() {
        val catalog = MessageCatalog(listOf("Place {0}." to "Place %1\$s.", "Read" to "Read"))
        assertEquals("Place {1, 2}.", catalog.translate("Place {1, 2}."))
    }

    @Test
    fun `translations can reorder values while preserving puzzle notation`() {
        val catalog = MessageCatalog(listOf(
            "Place {0} in row {1}, column {2}." to "Zeile %2\$s, Spalte %3\$s: %1\$s einsetzen.",
        ))
        assertEquals("Zeile 2, Spalte 4: 7 einsetzen.", catalog.translate("Place 7 in row 2, column 4."))
    }

    @Test
    fun `technique identifiers remain English and only their display is translated`() {
        val catalog = MessageCatalog(listOf(
            "Naked single" to "Одиночний кандидат",
            "Use {0} at {1}." to "Застосуйте %1\$s у %2\$s.",
        ))
        assertEquals("Застосуйте Одиночний кандидат у r2c4.", catalog.translate("Use Naked single at r2c4."))
        assertEquals("{1, 3, 7}", catalog.translate("{1, 3, 7}"))
    }

    @Test
    fun `fallback retains unknown text and does not replace inside other words`() {
        val catalog = MessageCatalog(listOf("Read" to "Lire", "Naked single" to "Candidat unique"))
        assertEquals("Reading something unknown", catalog.translate("Reading something unknown"))
        assertEquals("Candidat unique\nReadiness", catalog.translate("Naked single\nReadiness"))
    }
}
