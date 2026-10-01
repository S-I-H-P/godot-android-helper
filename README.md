# Godot 开发助手 (GodotDevAssistant)

一个安卓端的 Godot 引擎开发辅助工具，支持通过 SAF 管理项目、编辑 GDScript 脚本、查看离线文档。

## 功能

1. **SAF 文件管理** — 通过 Android Storage Access Framework 读写 Godot 项目目录，默认 `/storage/emulated/0/Documents/`，可在设置中调整
2. **项目自动识别** — 扫描子文件夹，发现 `project.godot` 即视为项目，以文件夹名为项目名
3. **GDScript 编辑器** — 语法高亮、代码补全、行号显示、自动缩进
4. **键盘工具栏** — 输入法上方显示 Tab、Shift、方向键、括号、引号等常用按键
5. **离线文档** — 内置 Godot 文档（HTML 格式），支持搜索浏览
6. **Material 风格** — 基于 Material Design 3

## 技术栈

- **语言**: Kotlin + C++17
- **构建**: CMake + Gradle (NDK)
- **UI**: Material Design 3, ViewBinding
- **文件**: SAF (Storage Access Framework)
- **文档**: Markwon (Markdown 渲染)

## 项目结构

```
GodotDevAssistant/
├── build.gradle                    # 项目级构建配置
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── README.md
└── app/
    ├── build.gradle                # 模块级构建配置
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   └── docs/               # 离线文档（HTML）
        │       ├── gdscript_basics.html
        │       ├── gdscript_operators.html
        │       ├── gdscript_signals.html
        │       ├── godot_nodes.html
        │       └── godot_input.html
        ├── jni/                    # C++ 原生代码
        │   ├── CMakeLists.txt
        │   ├── include/godot/
        │   │   ├── file_utils.h
        │   │   ├── project_scanner.h
        │   │   ├── tokenizer.h
        │   │   ├── completion_engine.h
        │   │   ├── syntax_highlighter.h
        │   │   ├── doc_indexer.h
        │   │   ├── settings_manager.h
        │   │   └── jni_interface.h
        │   └── src/
        │       ├── file_utils.cpp
        │       ├── project_scanner.cpp
        │       ├── tokenizer.cpp
        │       ├── completion_engine.cpp
        │       ├── syntax_highlighter.cpp
        │       ├── doc_indexer.cpp
        │       ├── settings_manager.cpp
        │       └── jni_interface.cpp
        ├── java/com/godot/devassistant/
        │   ├── GodotDevApp.kt      # Application 入口
        │   ├── native/
        │   │   └── NativeLib.kt   # JNI 桥接
        │   ├── model/
        │   │   └── Models.kt      # 数据模型
        │   ├── data/
        │   │   └── ProjectRepository.kt
        │   ├── util/
        │   │   └── SafFileManager.kt  # SAF 文件操作
        │   ├── editor/
        │   │   ├── GDScriptEditorView.kt  # 代码编辑器
        │   │   ├── EditorKeyboardBar.kt    # 键盘工具栏
        │   │   └── CompletionAdapter.kt   # 补全列表
        │   ├── adapter/
        │   │   ├── ProjectAdapter.kt
        │   │   └── DocAdapter.kt
        │   └── ui/
        │       ├── MainViewModel.kt
        │       ├── MainActivity.kt
        │       ├── EditorActivity.kt
        │       ├── DocBrowserActivity.kt
        │       └── SettingsActivity.kt
        └── res/
            ├── layout/
            │   ├── activity_main.xml
            │   ├── activity_editor.xml
            │   ├── activity_doc_browser.xml
            │   ├── activity_settings.xml
            │   ├── item_project.xml
            │   ├── item_doc.xml
            │   └── item_completion.xml
            ├── menu/
            │   ├── menu_main.xml
            │   └── menu_editor.xml
            ├── drawable/            # 图标资源
            ├── mipmap-anydpi-v26/   # 启动图标
            └── values/
                ├── strings.xml
                ├── colors.xml
                ├── themes.xml
                └── ids.xml
```

## 编译方法

### 前置要求

- Android Studio (Hedgehog 2023.1.1 或更高)
- Android SDK 34
- Android NDK (r25c 或更高)
- CMake 3.22.1
- JDK 17

### 使用 CMake 编译原生库

```bash
# 进入 jni 目录
cd app/src/main/jni

# 配置（arm64-v8a 为例）
cmake -B build \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_shared

# 编译
cmake --build build --config Release

# 生成的 .so 文件在 build/libgodot-dev-assistant.so
# 复制到 jniLibs 对应目录
cp build/libgodot-dev-assistant.so ../jniLibs/arm64-v8a/
```

### 使用 Gradle 编译整个项目

```bash
# 在项目根目录
./gradlew assembleDebug

# 或在 Android Studio 中打开项目，点击 Build > Make Project
```

## 使用说明

1. 打开应用，点击右上角文件夹图标选择项目根目录
2. 应用自动扫描子目录中的 `project.godot` 文件
3. 点击项目卡片进入编辑器
4. 选择或新建 `.gd` 脚本文件开始编辑
5. 编辑器支持语法高亮和代码补全
6. 键盘上方有常用符号快捷键
7. 点击菜单中的"文档"查看离线 Godot 文档

## 扩展离线文档

将 Godot 官方 HTML 文档放入 `app/src/main/assets/docs/` 目录即可自动索引。

下载地址：https://docs.godotengine.org/en/stable/ （页面底部有离线下载链接）
