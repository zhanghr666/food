package com.example.foodmanager.core

import com.example.foodmanager.data.ManagedItem
import com.example.foodmanager.data.StorageLocation

/** 批量操作的目标快照；由仓库在同一事务内读取、校验和提交。 */
data class InventoryBatch(val ids: List<Long>, val action: String, val target: String) {
    companion object {
        fun prepare(ids: List<Long>, items: List<ManagedItem>, kind: String, archived: Boolean,
            action: String, location: String, places: List<StorageLocation>): InventoryBatch {
            require(ids.isNotEmpty() && ids.all { it > 0 } && ids.size == ids.distinct().size) { "请选择有效记录，不可重复" }
            require(kind == "food" || kind == "object")
            require(action in listOf("move", "archive", "restore", "delete")) { "不支持的批量操作" }
            require(items.size == ids.size && items.map { it.id }.toSet() == ids.toSet()) { "所选记录已删除，请退出多选后重试" }
            require(items.all { it.kind == kind && it.archived == archived }) { "所选记录状态已变化，请退出多选后重试" }
            require(action != "archive" || !archived) { "记录已经归档" }
            require(action != "restore" || archived) { "记录已经在库" }
            val target = if (action == "move") {
                require(location.trim().isNotEmpty() && location.trim().length <= 80) { "请填写不超过 80 字的位置" }
                HouseholdSearch.resolvePlace(location, places)
            } else ""
            return InventoryBatch(ids.toList(), action, target)
        }
    }
}
