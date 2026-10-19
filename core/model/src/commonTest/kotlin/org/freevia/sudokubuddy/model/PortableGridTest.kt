package org.freevia.sudokubuddy.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PortableGridTest {
    @Test fun inputChangesCannotAlterASnapshot() {
        val input = MutableList(81) { Cell.Empty }
        val grid = Grid.of(input)
        val hash = grid.hashCode()
        input[0] = Cell.given(1)
        input.clear()
        assertEquals(81, grid.cells.size)
        assertEquals(Cell.Empty, grid[0])
        assertEquals(hash, grid.hashCode())
        val updated = grid.with(0, Cell.guess(2))
        assertEquals(Cell.Empty, grid[0])
        assertEquals(Cell.guess(2), updated[0])
    }
}
