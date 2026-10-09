package org.freevia.sudokubuddy.app

/** Bounds camera decoding before either an ARGB bitmap or recognition buffers exist. */
internal object CaptureSampling {
    // Preserve ordinary 12MP captures, including 4032 x 3024. Rotation or conversion
    // can hold eight bytes per decoded pixel: this caps that part of peak memory near
    // 96 MiB instead of the 384MB needed by a full 48MP capture. Native processing,
    // JPEG bytes and the UI need additional memory beyond this bound.
    const val MAX_PIXELS = 12L * 1024 * 1024

    /**
     * BitmapFactory honours power-of-two samples. Rounded-up dimensions keep the
     * bound conservative for odd image sizes and arithmetic stays safe for large JPEGs.
     *
     * A distant grid in a larger capture loses some detail. Ordinary 12MP images are
     * unchanged; recognition accuracy and peak memory on larger captures still need
     * physical-device validation, particularly for small grids and faint handwriting.
     */
    fun sampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "image dimensions must be positive" }
        var sample = 1
        while (roundedSize(width, sample) * roundedSize(height, sample) > MAX_PIXELS) {
            sample *= 2
        }
        return sample
    }

    private fun roundedSize(size: Int, sample: Int): Long =
        (size.toLong() + sample - 1) / sample
}
