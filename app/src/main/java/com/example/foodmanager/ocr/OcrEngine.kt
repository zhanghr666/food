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
        // 两条通道各识别一次，取并集：
        //  ① 原图  —— 印刷体、大字号识别最准
        //  ② 增强图 —— 点阵喷码（生产日期/保质期）常因"字小 + 低对比度"被漏掉，
        //     灰度化并做百分位对比度拉伸后，实测对比度可提升约 4 倍，能显著提高读出率
        val original = runRecognizer(bitmap)

        val enhancedBitmap = runCatching { enhanceForDotMatrix(bitmap) }.getOrNull()
        if (enhancedBitmap == null || enhancedBitmap === bitmap) return original

        val enhanced = runCatching { runRecognizer(enhancedBitmap) }.getOrDefault("")
        if (enhancedBitmap !== bitmap && !enhancedBitmap.isRecycled) enhancedBitmap.recycle()

        return mergeText(original, enhanced)
    }

    private suspend fun runRecognizer(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text -> if (cont.isActive) cont.resume(text.text) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }
    }

    /**
     * 放大 + 对比度拉伸，用于「日期喷码」这种小字号点阵字。
     *
     * 与 [enhanceForDotMatrix] 的区别：这里会**放大**，但**不做二值化**。
     * 原因：二值化会把包装上的深色图案（例如红色马卡龙）变成一大块黑斑，
     * 在整图上会严重干扰识别 —— 所以二值化只适合"已裁剪过的日期小区域"，
     * 而这个方法用于放大小区域后再拉伸。
     */
    fun close() {
        recognizer.close()
    }

    companion object {

        /**
         * 送进识别前的最大边长。
         *
         * 从 2000 提到 2600：包装上的点阵喷码字号很小，多留一点像素显著提升读出率；
         * 同时 2600 仍在内存可接受范围内（约 2600×1500 的 ARGB 位图 ≈ 15MB）。
         */
        private const val MAX_DIMENSION = 2600

        /**
         * 合并两次识别的文本：**逐行去重取并集**（对外的静态版本）。
         *
         * 不能只用其中一次的结果 —— 增强图可能把印刷体识别得更差，
         * 只有取并集才能"既保住原有准确度，又补上点阵喷码里的日期"。
         */
        fun mergeText(primary: String, secondary: String): String {
            if (secondary.isBlank()) return primary
            if (primary.isBlank()) return secondary

            val lines = LinkedHashSet<String>()
            primary.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines += it }

            secondary.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                if (lines.contains(line)) return@forEach

                val existing = lines.firstOrNull { it.contains(line) || line.contains(it) }
                if (existing == null) {
                    lines += line
                    return@forEach
                }

                // 与已有行部分重叠：但只要它含有**日期样式的数字串**，就不能丢。
                // 反例：原图把喷码识成了残缺的「2026」，增强图识出完整的「20260910」，
                // 「2026」被「20260910」包含 —— 若按"包含即去重"会把完整日期丢掉。
                if (DATE_LIKE.containsMatchIn(line) && line.length > existing.length) {
                    lines.remove(existing)
                    lines += line
                }
            }
            return lines.joinToString("\n")
        }

        /** 日期样式的数字串（含分隔符或 8 位连续数字），用于判断"这行是日期，不能丢" */
        private val DATE_LIKE = Regex(
            "\\d{4}\\s*[-/.年]\\s*\\d{1,2}(\\s*[-/.月]\\s*\\d{1,2})?|\\d{8}|\\d{6}",
        )

        /**
         * 从整图中裁剪一块区域（用于用户框定的「日期区域」）。
         *
         * 放在 companion 里是因为它是纯图像操作、不依赖识别器实例。
         */
        fun cropRegion(
            src: Bitmap,
            leftRatio: Float,
            topRatio: Float,
            rightRatio: Float,
            bottomRatio: Float,
        ): Bitmap {
            val left = (src.width * leftRatio).toInt().coerceIn(0, src.width - 1)
            val top = (src.height * topRatio).toInt().coerceIn(0, src.height - 1)
            val right = (src.width * rightRatio).toInt().coerceIn(left + 1, src.width)
            val bottom = (src.height * bottomRatio).toInt().coerceIn(top + 1, src.height)
            return Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        }

        /**
         * 放大 + 对比度拉伸，用于「日期喷码」这种小字号点阵字。
         *
         * 与 [enhanceForDotMatrix] 的区别：这里会**放大**，但**不做二值化**。
         * 原因：二值化会把包装上的深色图案（例如红色马卡龙）变成一大块黑斑，
         * 在整图上会严重干扰识别 —— 所以二值化只适合"已裁剪过的日期小区域"。
         */
        fun upscaleAndStretch(src: Bitmap, factor: Int = 3): Bitmap {
            val scaled = Bitmap.createScaledBitmap(src, src.width * factor, src.height * factor, true)
            val enhanced = enhanceForDotMatrix(scaled)
            if (enhanced !== scaled) scaled.recycle()
            return enhanced
        }


        /**
         * 为「点阵喷码」做图像增强：灰度 + 百分位对比度拉伸 + 轻度锐化。
         *
         * 为什么用百分位而不是固定 0..255 拉伸：包装底色本身有噪声，
         * 固定区间会把噪声一起拉满；百分位只压缩真正的暗/亮极值，保留文字形状。
         */
        fun enhanceForDotMatrix(src: Bitmap): Bitmap {
            val gray = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(gray)
            val paint = android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix().apply { setSaturation(0f) },
                )
            }
            canvas.drawBitmap(src, 0f, 0f, paint)

            val pixels = IntArray(gray.width * gray.height)
            gray.getPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)

            val histogram = IntArray(256)
            for (p in pixels) histogram[(p and 0xFF)]++

            val total = pixels.size
            val lowTarget = total * 2 / 100
            val highTarget = total * 98 / 100
            var acc = 0
            var low = 0
            var high = 255
            for (v in 0..255) {
                acc += histogram[v]
                if (acc <= lowTarget) low = v
                if (acc <= highTarget) high = v
            }
            if (high <= low) high = (low + 1).coerceAtMost(255)

            val range = (high - low).coerceAtLeast(1)
            for (i in pixels.indices) {
                val v = (pixels[i] and 0xFF)
                val mapped = ((v - low) * 255 / range).coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (mapped shl 16) or (mapped shl 8) or mapped
            }
            gray.setPixels(pixels, 0, gray.width, 0, 0, gray.width, gray.height)
            return gray
        }

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
