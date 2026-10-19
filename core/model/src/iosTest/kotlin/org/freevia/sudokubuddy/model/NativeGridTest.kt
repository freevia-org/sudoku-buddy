package org.freevia.sudokubuddy.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class NativeGridTest {
    @Test fun cellsCannotBeMutatedThroughAWritableView() {
        val grid = Grid.of(List(81) { Cell.Empty })
        assertFails { (grid.cells as MutableList<Cell>)[0] = Cell.given(5) }
        assertEquals(Cell.Empty, grid[0])
    }
}
