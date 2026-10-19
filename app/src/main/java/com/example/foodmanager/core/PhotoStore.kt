package com.example.foodmanager.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 原图和缩略图仅存本机内部目录；附件从相册/相机复制，不依赖临时 URI 权限。 */
class PhotoStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "item_photos").apply { mkdirs() }
    fun file(key: String, thumb: Boolean = false): File {
        require(Regex("[a-f0-9-]{36}").matches(key)) { "无效的照片标识" }
        return File(directory, key + if (thumb) ".thumb.jpg" else ".image")
    }
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val key = UUID.randomUUID().toString()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file(key).outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 50L * 1024 * 1024) { "照片过大，请选择 50MB 以内的照片" }; output.write(buffer, 0, count) }
                }
            } ?: error("无法读取照片")
            val bitmap = decode(key, 360) ?: error("照片格式无法读取")
            try { file(key, true).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } } finally { bitmap.recycle() }
            key
        } catch (error: Exception) { file(key).delete(); file(key, true).delete(); throw error }
    }
    fun decode(key: String, edge: Int = 2048, thumb: Boolean = false): Bitmap? = runCatching {
        val file = file(key, thumb); if (!file.exists()) return null
        if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val ratio = (edge.toFloat() / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge) sample *= 2
            val image = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val transform = Matrix().apply { when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f); ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f); ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(90f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postScale(-1f, 1f); postRotate(270f) }
            } }
            if (transform.isIdentity) image else Bitmap.createBitmap(image, 0, 0, image.width, image.height, transform, true).also { image.recycle() }
        }
    }.getOrNull()
    /** 仅清理没有库存/草稿引用的附件；新导入暂存至少一天，防止进程切换或并发导入误删。 */
    fun cleanup(referenced: Set<String>, graceMillis: Long = 24L * 60 * 60 * 1000) {
        val cutoff = System.currentTimeMillis() - graceMillis
        directory.listFiles().orEmpty().filter { it.name.substringBefore('.') !in referenced && it.lastModified() < cutoff }.forEach { it.delete() }
    }
}
