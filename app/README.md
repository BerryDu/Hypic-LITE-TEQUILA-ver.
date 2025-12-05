# 🎨 Hypic-LITE (SimpleEditor) - 安卓简易修图 App

> 字节跳动安卓训练营课程设计 | 基于 Kotlin + OpenGL ES 的高性能图片编辑工具

## 1. 项目简介
本项目是一个遵循 Android 现代开发规范（Modern Android Development）的轻量级修图应用。项目不依赖大型第三方编辑库，而是基于原生 **OpenGL ES** 手写渲染引擎，实现了从**媒体库管理**、**高性能渲染**、**交互式裁剪**到**无损导出**的完整闭环。

核心亮点包括：**音视频分流查看**、**实时滤镜**、**手势交互裁剪**以及**无限步撤销/重做 (Undo/Redo)**。

## 2. 核心功能预览

| 首页与分流 | 视频预览 | 交互式裁剪 | 滤镜与保存 |
|:---:|:---:|:---:|:---:|
| <img src="screenshots/1_home.png" width="220"/> | <img src="screenshots/2_video.png" width="220"/> | <img src="screenshots/3_crop.png" width="220"/> | <img src="screenshots/4_filter.png" width="220"/> |

## 3. 技术栈与架构
* **语言**: 100% Kotlin
* **架构**: MVVM (ViewModel + LiveData)
* [cite_start]**并发**: Kotlin Coroutines (全线接管 IO 操作，杜绝主线程阻塞) [cite: 30]
* **UI/交互**:
    * **Material Design**: ToggleGroup, CardView
    * [cite_start]**Custom View**: 自定义 `CropOverlayView` 实现遮罩交互 [cite: 10]
* **核心渲染**:
    * [cite_start]**OpenGL ES 2.0**: `GLSurfaceView` + 自定义 Shader 实现滤镜与变换 [cite: 17]
    * **Matrix**: 矩阵运算处理缩放与平移
* **图片加载**: Coil (支持 Bitmap 复用、降采样及视频帧解码)

## 4. 构建与运行说明
1.  **环境要求**: Android Studio Hedgehog 或更高版本，JDK 17+。
2.  **构建步骤**:
    * Clone 本仓库。
    * 等待 Gradle Sync 完成。
    * 连接真机（推荐 Android 10+），点击 Run。
3.  [cite_start]**权限说明**: 首次运行需授予存储权限（已适配 Android 13+ `READ_MEDIA_IMAGES/VIDEO` 细粒度权限） [cite: 15]。

## 5. 项目报告：难点攻克与解决思路

### 5.1 复杂手势与 OpenGL 坐标映射
* **问题**: 屏幕触控坐标（像素）与 OpenGL 纹理坐标（-1.0 ~ 1.0）不一致，导致手势缩放和平移时图片位置偏移，且裁剪框难以对齐。
* **解决**:
    1.  引入 **正交投影矩阵 (Projection Matrix)**，根据 View 与 Bitmap 的宽高比动态计算视口，实现 `FitCenter` 效果。
    2.  在 `CropOverlayView` 中复刻 OpenGL 的矩阵变换逻辑，将屏幕触控坐标逆向映射回 Bitmap 的原始像素坐标，实现精准裁剪。

### 5.2 裁剪操作的“破坏性”与撤销 (Undo/Redo)
* **问题**: 裁剪操作会改变图片的物理尺寸，而普通的 `Undo` 仅记录了缩放/位移参数，导致裁剪后无法正确撤销。
* [cite_start]**解决**: 设计了包含 `Bitmap?` 字段的 `EditorState` 状态机。对于缩放/滤镜等“参数操作”，仅记录数值以省内存；对于裁剪等“破坏性操作”，在操作前将 Bitmap 快照压入 Undo 栈，实现混合式状态回滚 [cite: 50]。

### 5.3 大图加载与 OOM 优化
* **问题**: 加载高分辨率照片（如 4000x3000）直接渲染会导致 OpenGL 黑屏或应用崩溃。
* **解决**:
    1.  使用 Coil 的 `.size()` 限制加载尺寸。
    2.  关键配置 `.allowHardware(false)` 关闭硬件位图加速，解决 OpenGL 纹理上传的兼容性问题。

---
*Created by [你的名字] | 2025*