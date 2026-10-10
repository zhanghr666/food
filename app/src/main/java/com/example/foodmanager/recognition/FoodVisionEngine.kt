package com.example.foodmanager.recognition

import android.content.Context
import android.net.Uri
import com.example.foodmanager.core.*
import com.example.foodmanager.data.CommonFood
import com.example.foodmanager.ocr.OcrEngine
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** 整图识物 + 包装文字。每次推理使用独立资源，结果不依赖服务端。 */
class FoodVisionEngine(private val context: Context) {
    suspend fun recognize(uri: Uri, foods: List<CommonFood>): List<EntryDraft> = withContext(Dispatchers.IO) {
        val bitmap = OcrEngine.loadBitmap(context, uri)
        val ocr = OcrEngine()
        try {
            val engineWarnings = mutableListOf<String>()
            val text = try {
            val lines = ocr.recognizeLines(bitmap)
            // 同行相邻文本连接；无空间依据时保留独立行。
            val spatial = lines.sortedWith(compareBy({ it.top }, { it.left })).joinToString("\n") { line ->
                val right = lines.filter { next -> next.left >= line.right && kotlin.math.abs(next.top - line.top) < maxOf(12, (line.bottom - line.top) / 2) }
                    .minByOrNull { it.left }
                if (right != null && right.left - line.right < maxOf(50, (line.bottom - line.top) * 4)) "${line.text} ${right.text}" else line.text
            }
                OcrEngine.mergeText(spatial, ocr.recognizeBitmap(bitmap))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { engineWarnings += "包装文字未能读取，日期可直接填写"; "" }
            val packageDraft = SmartTextParser.photoText(text, foods)
            var detector: ObjectDetector? = null
            val detections = try {
                detector = ObjectDetector.createFromOptions(context, ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("vision/food-detector.tflite").build())
                    .setRunningMode(RunningMode.IMAGE).setScoreThreshold(0.55f).setMaxResults(30)
                    .setCategoryAllowlist(labels.keys.toList()).build())
                val image = BitmapImageBuilder(bitmap).build()
                try { detector.detect(image).detections().mapNotNull { it.categories().maxByOrNull { c -> c.score() }?.categoryName() } }
                finally { image.close() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { engineWarnings += "识物模型暂不可用，已保留包装文字结果"; emptyList<String>() }
            catch (e: LinkageError) { engineWarnings += "设备暂不支持识物模型，已保留包装文字结果"; emptyList<String>() }
            finally { detector?.close() }
            listOf(singleDraft(packageDraft, detections, engineWarnings))
        } finally { ocr.close(); if (!bitmap.isRecycled) bitmap.recycle() }
    }
    companion object {
        internal fun singleDraft(packageDraft: EntryDraft, detections: List<String>, engineWarnings: List<String> = emptyList()): EntryDraft {
            if (packageDraft.name.isNotBlank()) return packageDraft.copy(warnings = packageDraft.warnings + engineWarnings)
            val counts = detections.filter { it in labels }.groupingBy { it }.eachCount()
            if (counts.isEmpty()) return packageDraft.copy(warnings = packageDraft.warnings + engineWarnings + "没有确定的食物名称，请直接填写；日期和位置不会根据外观猜测")
            val (label, count) = counts.entries.first()
            val (name, category) = labels.getValue(label)
            val multiple = counts.size > 1
            val options = counts.keys.drop(1).map { labels.getValue(it).first }
            val draft = EntryDraft(name = name, category = category, quantity = if (multiple) "" else count.toString(), unit = "个",
                origins = buildMap {
                    put(EntryField.NAME, FieldOrigin.IMAGE); put(EntryField.CATEGORY, FieldOrigin.DERIVED)
                    put(EntryField.UNIT, FieldOrigin.DERIVED)
                    if (!multiple) put(EntryField.QUANTITY, FieldOrigin.DERIVED)
                }, fieldCandidates = if (multiple) mapOf(EntryField.NAME to options) else emptyMap(),
                warnings = engineWarnings,
                fieldWarnings = if (multiple) mapOf(EntryField.NAME to listOf("照片里有多种食物，本次只录入一种，请确认名称和数量；包装日期不会自动分配"))
                    else mapOf(EntryField.QUANTITY to listOf("照片可见 $count 个$name；遮挡和成串物品可能影响数量，请核对")), rawText = packageDraft.rawText)
            return if (multiple) draft else draft.merge(packageDraft)
        }

        val labels = mapOf("apple" to ("苹果" to "水果"), "banana" to ("香蕉" to "水果"), "orange" to ("橙子" to "水果"),
            "broccoli" to ("西兰花" to "蔬菜"), "carrot" to ("胡萝卜" to "蔬菜"), "sandwich" to ("三明治" to "主食"),
            "hot dog" to ("热狗" to "主食"), "pizza" to ("披萨" to "主食"), "donut" to ("甜甜圈" to "零食"), "cake" to ("蛋糕" to "零食"))
    }
}
