package org.freevia.sudokubuddy.recognize

/** Role evidence from a group of strokes, independently of the CNN's digit confidence. */
internal object InkLayout {
    enum class Evidence { SINGLE_GLYPH, AMBIGUOUS_GROUP, NOTE_ROW, STACKED_GROUP }

    private data class Region(val left: Int, val top: Int, val width: Int, val height: Int, val area: Int) {
        val right get() = left + width
        val bottom get() = top + height
        val middleY get() = top + height / 2.0
    }

    fun evidence(ink: CellInk): Evidence {
        // The same-pen criterion used to gather broken glyphs. Faint erased notes beside
        // a fresh answer are not competing current glyphs.
        val regions = ink.pieces.filter { it.contrast >= ink.blob.contrast * 0.80 }
            .map { Region(it.left, it.top, it.width, it.height, it.area) }.toMutableList()
        // Overlapping pieces can be fragments of a candidate. Join their boxes before
        // counting; a broken5 must not disappear merely because each half is short.
        var merged = true
        while (merged) {
            merged = false
            loop@ for (a in regions.indices) for (b in a + 1 until regions.size) {
                val first = regions[a]
                val second = regions[b]
                val left = minOf(first.left, second.left)
                val top = minOf(first.top, second.top)
                val right = maxOf(first.right, second.right)
                val bottom = maxOf(first.bottom, second.bottom)
                if (overlap(first.left, first.right, second.left, second.right) * 2 >= minOf(first.width, second.width) &&
                    overlap(first.top, first.bottom, second.top, second.bottom) > 0 && right - left <= bottom - top) {
                    regions[a] = Region(left, top, right - left, bottom - top, first.area + second.area)
                    regions.removeAt(b)
                    merged = true
                    break@loop
                }
            }
        }
        val main = regions.maxByOrNull { it.area } ?: return Evidence.SINGLE_GLYPH
        // A candidate can fuse to the line beneath it, becoming more than twice as
        // tall as the remaining notes. Comparing everything to half that fused height
        // then loses the group entirely. Two substantial same-pen figures sharing a
        // row on one side of the tall component remain competing role evidence.
        // This can also be a full answer beside current notes: keep its value and ask.
        val other = regions.filter { it !== main }
        var alignedCompanions = false
        aligned@ for (a in other.indices) for (b in a + 1 until other.size) {
            val first = other[a]
            val second = other[b]
            if (maxOf(first.height, second.height) >= main.height) continue
            if (maxOf(first.height, second.height) > minOf(first.height, second.height) * 2) continue
            if (first.area + second.area < main.area / 2.0) continue
            if (overlap(first.top, first.bottom, second.top, second.bottom) * 2 < minOf(first.height, second.height)) continue
            if (overlap(first.left, first.right, second.left, second.right) * 2 > minOf(first.width, second.width)) continue
            val mainX = main.left + main.width / 2.0
            val firstX = first.left + first.width / 2.0
            val secondX = second.left + second.width / 2.0
            if ((firstX - mainX) * (secondX - mainX) <= 0) continue
            alignedCompanions = true
            break@aligned
        }
        val comparable = regions.filter { it.height * 2 >= main.height }
        if (comparable.size < 3) return if (alignedCompanions) Evidence.AMBIGUOUS_GROUP else Evidence.SINGLE_GLYPH

        // Three substantial same-pen regions sharing a baseline make a list. A broken
        // single digit is taller than this group's width and cannot satisfy both tests.
        val commonTop = comparable.maxOf { it.top }
        val commonBottom = comparable.minOf { it.bottom }
        val width = comparable.maxOf { it.right } - comparable.minOf { it.left }
        val height = comparable.maxOf { it.bottom } - comparable.minOf { it.top }
        if (commonBottom - commonTop >= comparable.minOf { it.height } / 2.0 && width > height) {
            return Evidence.NOTE_ROW
        }

        // Two vertically separate, aligned candidates beside a tall fused column are
        // a second explanation for that column. Keep the whole layout, not just its
        // tallest component;12/89 must not become one full-size8 when2 touches9.
        val companions = comparable.filter { it !== main }
        for (a in companions.indices) for (b in a + 1 until companions.size) {
            val upper = listOf(companions[a], companions[b]).minBy { it.top }
            val lower = listOf(companions[a], companions[b]).maxBy { it.top }
            if (upper.bottom > lower.top) continue
            if (overlap(upper.left, upper.right, lower.left, lower.right) * 2 < minOf(upper.width, lower.width)) continue
            if (main.top > upper.middleY || main.bottom < lower.middleY) continue
            if (main.width < maxOf(upper.width, lower.width)) continue
            if (upper.area + lower.area < main.area / 2.0) continue
            return Evidence.STACKED_GROUP
        }
        // Several comparable current components without a clear list layout may also
        // be a fragmented answer. Preserve its value, but ask about its role explicitly.
        return Evidence.AMBIGUOUS_GROUP
    }

    private fun overlap(a0: Int, a1: Int, b0: Int, b1: Int) = maxOf(0, minOf(a1, b1) - maxOf(a0, b0))
}
