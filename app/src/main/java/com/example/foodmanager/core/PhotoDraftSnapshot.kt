package com.example.foodmanager.core

import java.io.Serializable

/** 存储层只依赖草稿附件契约，不依赖具体录入界面。 */
interface PhotoDraftSnapshot : Serializable {
    val retainedPhotoKeys: List<String>
}
