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

    fun createImageFile(context: Context): File {
        val dir = File(context.cacheDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        return File(dir, "food_${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
