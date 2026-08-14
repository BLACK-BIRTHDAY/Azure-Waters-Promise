package com.ainovel.local

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ainovel.local.ui.screens.cropCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverCropTest {
    @Test
    fun cropCover_producesTwoByThreeImage_andHonorsPan() {
        val source = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) setPixel(x, y, if (x < width / 2) Color.RED else Color.BLUE)
            }
        }

        val frame = IntSize(200, 300)
        val leftSide = cropCover(source, frame, zoom = 1f, pan = Offset(50f, 0f))
        val rightSide = cropCover(source, frame, zoom = 1f, pan = Offset(-50f, 0f))
        try {
            assertEquals(800, leftSide.width)
            assertEquals(1_200, leftSide.height)
            assertEquals(800, rightSide.width)
            assertEquals(1_200, rightSide.height)
            assertNotEquals(leftSide.getPixel(400, 600), rightSide.getPixel(400, 600))
        } finally {
            leftSide.recycle()
            rightSide.recycle()
            source.recycle()
        }
    }
}
