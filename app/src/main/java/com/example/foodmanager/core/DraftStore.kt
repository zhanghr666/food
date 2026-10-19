package com.example.foodmanager.core

import android.content.Context
import android.util.AtomicFile
import java.io.*

/** 内部原子快照：不包含 Bitmap，系统结束进程后仍可继续录入。 */
class DraftStore(context: Context, name: String) {
    private val file = AtomicFile(File(context.filesDir, "$name.draft"))
    @Synchronized fun read(): Serializable? = runCatching { ObjectInputStream(file.openRead()).use { it.readObject() as? Serializable } }.getOrNull()
    @Synchronized fun write(value: Serializable) {
        val stream = file.startWrite()
        try {
            // ObjectOutputStream.close 会提前关闭 AtomicFile 的流；先刷新再由 finishWrite 关闭。
            ObjectOutputStream(stream).apply { writeObject(value); flush() }
            file.finishWrite(stream)
        } catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
