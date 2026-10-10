# 离线食物管家新版 Implementation Plan

**Goal:** 按独立新应用重构离线智能录入、六字段编辑、库存和位置页面，不实现旧数据迁移。

**Architecture:** 本地识别适配器输出带来源的统一草稿；唯一录入状态负责补充合并、保护用户修改和保存。Compose 页面复用六字段控件；Room 保存日期类型、保质期及常用品模板，提醒区分到期和个人计划。

**Tech Stack:** Kotlin、Compose Material 3、Room、Vosk、ML Kit OCR、MediaPipe 本地目标检测。

## 任务与验证

1. 修改 app/build.gradle.kts、AppDatabase.kt、FoodItem.kt、FoodDao.kt、StorageLocation.kt：独立 applicationId，新数据库 v1，日期语义及模板字段；构建并验证新库读写。
2. 新建 core/EntryDraft.kt、recognition/SmartTextParser.kt：六字段证据、补充合并和四项统计；真实语句单测覆盖数量、位置、日期、分类、拒绝规格污染及用户保护。
3. 新建 recognition/FoodVisionEngine.kt，改造 OcrEngine.kt：内置量化目标检测模型，OCR 保留区域与日期候选；验证模型离线加载及图片推理。
4. 新建 ui/entry/EntryViewModel.kt、EntryScreen.kt、EntryFields.kt：统一新增、编辑、语音、图片和文本；测试保存后清理、失败保留、异步过时结果与恢复。
5. 重写 FoodsApp.kt，新增新版库存/位置/设置页面：四页面导航、库存首页、统一录入入口、日期组件、批次和撤销；真机截图及流程检查。
6. 更新 FoodNotifier 与本地提醒；运行 JVM、构建、lint 与新版设备测试，核对最终包网络权限、模型资源和真机日志。

识别效果：正确自动填写至少四项是验收目标，不等同于界面字段数。初期内置视觉模型只对其真实标签中的食品提供结果，模型不支持或信息不足时如实显示缺失，不虚构数据。

用户已授权在本会话实施全部任务；不创建并行代理。采用当前工作区保留现有未提交工作，不提交未经请求的 Git 提交。

## 重构前准备结果（2026-10-10）

- 现有版本构建成功，97 项 JVM 测试通过，失败、错误及跳过均为 0。
- 官方 EfficientDet Lite0 int8 模型已下载至 app/src/main/assets/vision/food-detector.tflite，大小 4,602,795 字节。SHA-256：0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb。
- 模型来源：https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite 。模型尚未接入业务流程；接入时核对分发许可与支持标签，并进行离线推理验证。
- 当前仅完成方案、实施计划及模型准备，尚未改动应用业务代码。上述测试验证现有基线，不代表新版识别准确率已达标。
