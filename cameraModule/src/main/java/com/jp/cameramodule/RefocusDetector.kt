package com.jp.cameramodule

import android.media.Image
import androidx.camera.core.ImageProxy
import kotlin.math.abs

class RefocusDetector {

    companion object {
        private const val MAX_PIXEL_DIFF_THRESHOLD = 10
        private const val MAX_CONSECUTIVE_DIFF_FRAMES = 3
    }

    private var lastImage: Image? = null
    private var pixelDiffCount: Int = 0
    private var consecutiveDiffFrames: Int = 0

    fun detectRefocus(image: ImageProxy): Boolean {
        // Skip first frame
        if (lastImage == null) {
            lastImage = image.image
            return false
        }

        // Calculate pixel difference between current and last frame
        val diffCount = calculatePixelDiff(image.image, lastImage)

        // Check if pixel difference exceeds threshold
        if (diffCount > MAX_PIXEL_DIFF_THRESHOLD) {
            pixelDiffCount += diffCount
            consecutiveDiffFrames++
        } else {
            pixelDiffCount = 0
            consecutiveDiffFrames = 0
        }

        // Check if there are enough consecutive frames with high pixel difference
        if (consecutiveDiffFrames >= MAX_CONSECUTIVE_DIFF_FRAMES) {
            reset()
            return true
        }

        lastImage = image.image

        return false
    }

    fun reset() {
        lastImage = null
        pixelDiffCount = 0
        consecutiveDiffFrames = 0
    }

    private fun calculatePixelDiff(currentImage: Image?, lastImage: Image?): Int {
        if (currentImage == null || lastImage == null) {
            return 0
        }

        val width = currentImage.width
        val height = currentImage.height

        val currentBuffer = currentImage.planes[0].buffer
        val lastBuffer = lastImage.planes[0].buffer

        val currentBytes = ByteArray(currentBuffer.remaining())
        currentBuffer.get(currentBytes)

        val lastBytes = ByteArray(lastBuffer.remaining())
        lastBuffer.get(lastBytes)

        var diffCount = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val currentPixel = getPixel(currentBytes, x, y, width)
                val lastPixel = getPixel(lastBytes, x, y, width)

                val diff = abs(currentPixel - lastPixel)
                if (diff > 0) {
                    diffCount++
                }
            }
        }

        return diffCount
    }

    private fun getPixel(bytes: ByteArray, x: Int, y: Int, width: Int): Int {
        val index = y * width + x
        return bytes[index].toInt() and 0xFF
    }
}