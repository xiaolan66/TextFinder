# TextFinder - 实时文字查找神器

<div align="center">

**🎥 对准摄像头，输入关键词，瞬间定位目标文字**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-orange.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## 📖 项目简介

**TextFinder** 是一款基于 Android 的实时文字识别与定位应用。只需将摄像头对准任何包含文字的场景，输入你想查找的关键词，应用会**实时高亮显示**所有匹配的文字区域。

### 🎯 设计初衷

这个项目诞生于一个实际需求：**帮助集运仓库工作人员快速找到快递包裹**。

在繁忙的集运仓库中，工作人员经常需要在一堆包裹中找到特定客户的快递。传统方式是逐个翻看面单上的收件人信息，效率低下且容易遗漏。TextFinder 的出现彻底改变了这一工作流程：

> 🔲 对准包裹堆 → 🔍 输入客户姓名/单号 → ✅ 瞬间定位目标包裹

---

## ✨ 功能特性

| 特性 | 描述 |
|------|------|
| 🔴 **实时高亮** | 匹配的文字区域以红色边框实时标注，一目了然 |
| 🌐 **中英文支持** | 基于 Google ML Kit 中文识别引擎，完美支持中文、英文及混合文本 |
| ⚡ **极速响应** | 采用帧级处理优化，搜索结果即时呈现 |
| 📱 **简洁界面** | 极简设计，打开即用，零学习成本 |
| 🔒 **本地处理** | 所有文字识别在设备端完成，无需联网，保护隐私 |

### 使用场景

- 📦 **物流仓储**：快速定位特定客户的包裹
- 📚 **图书馆/书店**：在书架上快速找到目标书籍
- 📄 **文档整理**：在成堆文件中定位特定内容
- 🏪 **零售场景**：在货架上快速找到特定商品
- 🎓 **学习场景**：在教材/资料中快速定位知识点

---

## 🛠 技术架构

### 核心技术栈

```
┌─────────────────────────────────────────────────────────────┐
│                      TextFinder 架构                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  CameraX    │───▶│  ML Kit     │───▶│ OverlayView │     │
│  │  (预览流)    │    │  (OCR识别)   │    │  (结果渲染)  │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│         │                  │                   │            │
│         ▼                  ▼                   ▼            │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │ PreviewView │    │ TextRecognizer│   │ Canvas 绘制  │     │
│  │ (显示画面)   │    │ (中文模型)    │    │ (红色边框)   │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 技术选型

| 组件 | 技术方案 | 选择理由 |
|------|---------|---------|
| **相机框架** | [CameraX](https://developer.android.com/training/camerax) | Jetpack 官方相机库，生命周期感知，兼容性强 |
| **OCR 引擎** | [ML Kit Text Recognition (Chinese)](https://developers.google.com/ml-kit/vision/text-recognition/v2) | Google 端侧 OCR，中文识别精准，离线可用 |
| **UI 框架** | Kotlin + ViewBinding | 原生性能，类型安全，开发效率高 |
| **异步处理** | ExecutorService + Callback | 避免阻塞主线程，保证 UI 流畅 |

---

## 📐 实现原理

### 1. 实时文字识别流程

```
摄像头帧 → 图像预处理 → ML Kit OCR → 文字块解析 → 关键词匹配 → 坐标映射 → 界面渲染
    │           │            │            │            │            │           │
    ▼           ▼            ▼            ▼            ▼            ▼           ▼
 ImageProxy  旋转校正   TextRecognizer  TextBlock   contains()  坐标变换   drawRect()
```

### 2. 关键技术点

#### 帧级处理优化
```kotlin
// 使用 AtomicBoolean 避免重复处理，跳过积压帧
private val isProcessing = AtomicBoolean(false)

if (!isProcessing.compareAndSet(false, true)) {
    imageProxy.close()  // 跳过，正在处理中
    return
}
```

#### 坐标映射算法
PreviewView 使用 `fillCenter` (CENTER_CROP) 模式，需要精确映射 OCR 坐标到视图坐标：

```kotlin
// 复刻 CENTER_CROP 变换逻辑
val scale = maxOf(viewWidth / imageWidth, viewHeight / imageHeight)
val offsetX = (viewWidth - imageWidth * scale) / 2f
val offsetY = (viewHeight - imageHeight * scale) / 2f

// 映射坐标
mappedRect.set(
    box.left   * scale + offsetX,
    box.top    * scale + offsetY,
    box.right  * scale + offsetX,
    box.bottom * scale + offsetY
)
```

#### 多级匹配策略
应用在 Block → Line → Element 三个层级进行匹配，确保无论目标文字是整段、一行还是单个词，都能被准确捕获。

---

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- Android SDK 34
- Kotlin 1.9+
- 最低支持 Android 8.0 (API 26)

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/xiaolan66/TextFinder.git

# 打开 Android Studio，导入项目
# 等待 Gradle 同步完成

# 连接 Android 设备或启动模拟器
# 点击 Run 按钮即可
```

### 权限说明

应用仅需 **摄像头权限**，首次启动时会自动请求。所有数据处理均在本地完成，不收集任何用户信息。

---

## 📁 项目结构

```
app/src/main/java/com/example/textfinder/
├── MainActivity.kt      # 主界面逻辑：相机启动、OCR调用、搜索匹配
└── OverlayView.kt       # 自定义视图：坐标映射、边框绘制

app/src/main/res/layout/
└── activity_main.xml    # 界面布局：搜索栏、相机预览、叠加层
```

---

## 🎨 界面预览

```
┌─────────────────────────────────┐
│ 🔍 输入要查找的文字...      [×] │  ← 搜索栏
├─────────────────────────────────┤
│                                 │
│    ┌─────────────┐              │
│    │  客户: 张三  │              │  ← 匹配项
│    │  ┌───────┐  │              │    红色边框高亮
│    │  │李四   │  │              │
│    │  └───────┘  │              │
│    │  客户: 张三  │              │
│    └─────────────┘              │
│                                 │
│         ✅ 找到 2 处匹配         │  ← 状态提示
└─────────────────────────────────┘
```

---

## 🔧 扩展方向

- [ ] 支持连续多关键词搜索
- [ ] 添加历史搜索记录
- [ ] 支持语音输入关键词
- [ ] 增加夜间模式
- [ ] 支持导出识别结果

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 💼 商业合作

如果你对 TextFinder 感兴趣，欢迎在商业层面进行探索与合作：

**微信：URlake**

可能的合作方向：
- 企业定制开发
- 功能深度定制
- 技术咨询与培训
- SDK 授权集成

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给一个 Star 支持！**

Made with ❤️ by [xiaolan66](https://github.com/xiaolan66)

</div>
