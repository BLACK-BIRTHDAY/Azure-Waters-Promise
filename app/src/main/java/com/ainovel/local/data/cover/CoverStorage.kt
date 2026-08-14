package com.ainovel.local.data.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.graphics.scale
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoverStorage(private val context: Context) {
    private val directory: File
        get() = File(context.filesDir, "novel-covers").also { it.mkdirs() }

    suspend fun decodeForEditing(uri: Uri, maxDimension: Int = 3_072): Bitmap = withContext(Dispatchers.IO) {
        val orientation = openDescriptor(uri).use { descriptor ->
            runCatching {
                ExifInterface(descriptor.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openDescriptor(uri).use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式不受支持" }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension * 2 || bounds.outHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openDescriptor(uri).use {
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, options)
        } ?: error("无法解码所选图片")
        val oriented = applyOrientation(decoded, orientation)
        val largest = maxOf(oriented.width, oriented.height)
        if (largest <= maxDimension) oriented
        else {
            val scale = maxDimension.toFloat() / largest.toFloat()
            oriented.scale((oriented.width * scale).toInt(), (oriented.height * scale).toInt()).also {
                if (it !== oriented) oriented.recycle()
            }
        }
    }

    private fun openDescriptor(uri: Uri): ParcelFileDescriptor =
        context.contentResolver.openFileDescriptor(uri, "r") ?: error("无法读取所选图片")

    private fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    setRotate(180f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source) source.recycle()
        }
    }

    suspend fun save(novelId: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val fileName = "$novelId.webp"
        val destination = File(directory, fileName)
        val temporary = File(directory, "$fileName.tmp")
        try {
            FileOutputStream(temporary, false).use { output ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                check(bitmap.compress(format, 90, output)) { "封面保存失败" }
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
        fileName
    }

    fun file(fileName: String?): File? = fileName
        ?.takeIf { it.matches(Regex("[0-9a-fA-F-]{36}\\.webp")) }
        ?.let { File(directory, it) }
        ?.takeIf(File::isFile)
}
