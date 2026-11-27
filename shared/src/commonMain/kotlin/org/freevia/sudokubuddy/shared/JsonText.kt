package org.freevia.sudokubuddy.shared

import org.freevia.sudokubuddy.model.Cell
import org.freevia.sudokubuddy.model.Grid
import org.freevia.sudokubuddy.solver.Chain

internal fun jsonString(value: String): String = buildString {
    append('"')
    for (ch in value) when (ch) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (ch.code < 32) append("\\u" + ch.code.toString(16).padStart(4, '0')) else append(ch)
    }
    append('"')
}

internal fun ints(values: Collection<Int>): String = values.sorted().joinToString(",", "[", "]")
internal fun array(values: List<String>): String = values.joinToString(",", "[", "]")
internal fun obj(vararg fields: Pair<String, String>): String = fields.joinToString(",", "{", "}") { jsonString(it.first) + ":" + it.second }

internal fun chainJson(chain: Chain?): String = chain?.let { c -> obj("links" to array(c.links.map { link ->
    obj("index" to link.index.toString(), "digit" to link.digit.toString(), "from" to (link.from?.toString() ?: "null"))
}), "deadEndFrom" to (c.deadEndFrom?.toString() ?: "null"), "deadEnd" to ints(c.deadEnd), "blocked" to ints(c.blocked), "missing" to (c.missing?.toString() ?: "null")) } ?: "null"

/** The printed clues with the user's answers written over the squares they left empty. */
internal fun gridOf(givens: String, progress: String): Grid {
    require(givens.length == 81 && progress.length == 81)
    var grid = Grid.fromGivens(givens)
    for (index in 0 until 81) {
        val digit = progress[index].digitToIntOrNull()?.takeIf { it != 0 } ?: continue
        if (!grid[index].isFilled) grid = grid.with(index, Cell.guess(digit))
    }
    return grid
}

internal fun cellSet(text: String): Set<Int> = text.split(',').mapNotNull { it.toIntOrNull() }.filter { it in 0..80 }.toSet()
