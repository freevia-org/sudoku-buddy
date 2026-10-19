package org.freevia.sudokubuddy.vision

/** Align a candidate to its actual ten rules using identical pixels and line fitting. */
object RuleAlignment {
    fun align(image: GrayImage,quad: Quad): Quad {
        val extent=maxOf(quad.topEdge,quad.rightEdge,quad.bottomEdge,quad.leftEdge).coerceIn(288.0,1152.0)
        val geometry=GridLineFitter.fit(PerspectivePixels.gray(image,quad,extent.toInt(),extent)) ?: return quad
        val x0=geometry.verticalLines.first();val x1=geometry.verticalLines.last()
        val y0=geometry.horizontalLines.first();val y1=geometry.horizontalLines.last()
        return Quad.ordering(listOf(PerspectivePixels.point(quad,x0,y0,extent),PerspectivePixels.point(quad,x1,y0,extent),
            PerspectivePixels.point(quad,x1,y1,extent),PerspectivePixels.point(quad,x0,y1,extent)))
    }
}
