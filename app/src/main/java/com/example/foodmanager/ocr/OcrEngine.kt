package com.example.foodmanager.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** OCR 识别结果：原文 + 解析出的草稿 */
data class OcrResult(
    val rawText: String,
    val drafts: List<com.example.foodmanager.core.FoodDraft>,
    val lines: List<String> = emptyList(),
)

/**
 * 基于 Google ML Kit 的离线文字识别（中文 + 英文）。
 * 识别模型内置在 APK 中，完全离线运行，不上传任何图片。
 */
class OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /** 识别整张图片并解析成食物草稿 */
    suspend fun recognizeAndParse(
        context: Context,
        uri: Uri,
        defaultSource: String = "ocr",
    ): OcrResult {
        val text = recognizeText(context, uri)
        val drafts = OcrTextParser.parse(text, defaultSource)
        return OcrResult(
            rawText = text,
            drafts = drafts,
            lines = text.split('\n').filter { it.isNotBlank() },
        )
    }

    /** 只做文字识别 */
    suspend fun recognizeText(context: Context, uri: Uri): String =
        recognizeBitmap(loadBitmap(context, uri))

    suspend fun recognizeBitmap(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text -> if (cont.isActive) cont.resume(text.text) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
    }

    fun close() {
        recognizer.close()
    }

    companion object {

        /** 大图统一压缩，避免 OOM 并提升识别速度 */
        private const val MAX_DIMENSION = 2000

        fun loadBitmap(context: Context, uri: Uri): Bitmap {
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // ImageDecoder 会自动应用 EXIF 方向
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                @Suppress("DEPRECATION")
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                applyExifRotation(context, uri, bitmap)
            }
            return downscale(raw)
        }

        /** 从文件路径解码（拍照后保存的临时文件） */
        fun loadBitmapFromFile(path: String): Bitmap =
            downscale(BitmapFactory.decodeFile(path) ?: error("无法读取图片：$path"))

        private fun downscale(bitmap: Bitmap): Bitmap {
            val maxSide = maxOf(bitmap.width, bitmap.height)
            if (maxSide <= MAX_DIMENSION) return bitmap
            val scale = MAX_DIMENSION.toFloat() / maxSide
            val target = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (target != bitmap) bitmap.recycle()
            return target
        }

        @Suppress("DEPRECATION")
        private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
            val orientation = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            }.getOrNull() ?: return bitmap

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            return runCatching {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }.getOrDefault(bitmap)
        }
    }
}
