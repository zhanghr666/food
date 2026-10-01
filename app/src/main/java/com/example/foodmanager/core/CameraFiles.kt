package com.example.foodmanager.core

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 拍照临时文件管理。使用 FileProvider 把文件暴露给系统相机，
 * 避免申请存储权限（Android 10+ 的分区存储要求）。
 */
object CameraFiles {

    private const val DIR_NAME = "images"

    /** 临时照片保留时长：超过这个时间的残留文件视为"上次没清理干净" */
    private const val STALE_AFTER_MILLIS = 24 * 60 * 60 * 1000L

    fun createImageFile(context: Context): File {
        val dir = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        return File(dir, "food_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * 清理拍照临时目录里的残留文件。
     *
     * 正常情况下每张照片识别完就会删掉（见 PhotoInputCard），
     * 这里再做一层兜底：处理"识别过程中被系统杀掉""用户取消拍照"等漏网情况，
     * 避免 cache 里越堆越多（单张约 4MB）。
     */
    fun cleanUpStale(context: Context, olderThanMillis: Long = STALE_AFTER_MILLIS): Int {
        val dir = File(context.cacheDir, DIR_NAME)
        if (!dir.isDirectory) return 0
        val deadline = System.currentTimeMillis() - olderThanMillis
        var removed = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < deadline && file.delete()) removed++
        }
        return removed
    }
}
