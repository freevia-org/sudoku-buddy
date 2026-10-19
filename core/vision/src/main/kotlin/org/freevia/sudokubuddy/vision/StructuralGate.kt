package org.freevia.sudokubuddy.vision


/** Why a photograph cannot be used, and what the user should do about it. */
sealed interface RejectionReason {
    val message: String

    data object NoGrid : RejectionReason {
        override val message = "Point the camera at a sudoku puzzle."
    }

    data object GridTooSmall : RejectionReason {
        override val message = "Move closer - the grid is too small to read."
    }

    data object GridCutOff : RejectionReason {
        override val message = "Fit the whole grid in view."
    }

    data object TooSkewed : RejectionReason {
        override val message = "Hold the phone flat above the puzzle."
    }

    data object NotUpright : RejectionReason {
        override val message = "Turn the phone so the puzzle is upright."
    }

    data object LinesNotFound : RejectionReason {
        override val message = "Could not make out the grid lines - try again."
    }
}

/** The structural early-out of spec section 4.1. */
sealed interface GateVerdict {

    data class Usable(
        val quad: Quad,
        val gridScore: Double,
        val rectified: GrayImage,
        val geometry: CellGeometry,
        val cells: List<GrayImage>,
        val quality: ImageQuality,
        /**
         * What was wrong with the framing, when something was, and nothing was done about
         * it. Worth repeating to the user beside a poor reading - "the grid was small" is
         * the likeliest explanation for a page full of wrong digits - and never a reason
         * to refuse the photograph.
         */
        val complaint: RejectionReason? = null,
        val curved: Boolean = false,
    ) : GateVerdict

    data class Rejected(val reason: RejectionReason) : GateVerdict
}

/**
 * Decides whether a captured photograph can be processed at all.
 *
 * It used to reject on any of half a dozen counts. It now rejects on one: no grid was
 * found. Everything else it can say - the grid is small, it is tilted, it runs off the
 * edge - is a remark about the framing and not a verdict on the photograph, and a remark
 * is no reason to throw away a puzzle that has been located and straightened. What comes
 * back is read as well as it can be, the complaint travels with it, and the person
 * looking at the screen decides whether the reading is good enough.
 *
 * That is a change of principle worth stating: the app cannot tell a photograph that will
 * read badly from one that will read fine - image metrics reject usable photographs and
 * pass unusable ones, which is why blur was never a rejection - so it should not pretend
 * to. It can tell whether it found a grid. That is the whole of what it now decides.
 *
 * The live advice in the viewfinder is untouched and still says all of these things while
 * the user is aiming, which is when they are worth saying. See [FramingAdvisor].
 */
object StructuralGate {

    internal const val MAX_OPPOSITE_SIDE_RATIO = CaptureLimits.MAX_OPPOSITE_SIDE_RATIO
    internal const val MAX_CORNER_ANGLE_DEVIATION = CaptureLimits.MAX_CORNER_ANGLE_DEVIATION
    internal const val MAX_ROTATION_DEGREES = CaptureLimits.MAX_ROTATION_DEGREES

    fun assess(image: GrayImage): GateVerdict {
        val located = GridLocator.locate(image)
        if (located !is GridLocation.Found) return GateVerdict.Rejected(RejectionReason.NoGrid)

        val quad = located.quad
        val geometry = GridLineFitter.fit(located.rectified)
            ?: CellGeometry.evenNinths(located.rectified.width)

        return GateVerdict.Usable(
            quad = quad,
            gridScore = located.gridScore,
            rectified = located.rectified,
            geometry = geometry,
            cells = CellExtractor.extract(located.rectified, geometry),
            quality = ImageQuality.of(located.rectified),
            complaint = complain(quad, image),
            curved = located.curved,
        )
    }

    /** What is wrong with how this was framed, if anything. Advice, not a verdict. */
    internal fun complain(quad: Quad, image: GrayImage): RejectionReason? = when (PhotoFraming.note(quad, image.width, image.height)) {
        RejectionReason.GridTooSmall.message -> RejectionReason.GridTooSmall
        RejectionReason.GridCutOff.message -> RejectionReason.GridCutOff
        RejectionReason.TooSkewed.message -> RejectionReason.TooSkewed
        RejectionReason.NotUpright.message -> RejectionReason.NotUpright
        else -> null
    }
}
