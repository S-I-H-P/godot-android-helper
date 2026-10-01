# Godot 开发助手 - CMake 构建配置
# 构建原生库：godot-dev-assistant
# 作者: Agnes Assistant

cmake_minimum_required(VERSION 3.22.1)
project(godot_dev_assistant)

# 编译选项
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 警告开启
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Wpedantic")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra")

# 包含头文件目录
include_directories(${CMAKE_SOURCE_DIR}/include)

# =====================
# 源文件列表
# =====================
set(SOURCES
    # 核心工具类
    src/file_utils.cpp
    src/project_scanner.cpp

    src/tokenizer.cpp
    src/completion_engine.cpp
    src/syntax_highlighter.cpp
    src/doc_indexer.cpp
    src/settings_manager.cpp

    # JNI 入口
    src/jni_interface.cpp
)

# =====================
# 构建共享库
# =====================
add_library(godot-dev-assistant SHARED ${SOURCES})

# =====================
# 链接库
# =====================
# Android 系统库
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(godot-dev-assistant
    ${log-lib}
    ${android-lib}
    # 如需加密功能可链接 OpenSSL
    # OpenSSL::SSL
    # OpenSSL::Crypto
)

# =====================
# 安装规则（可选）
# =====================
install(TARGETS godot-dev-assistant
        LIBRARY DESTINATION ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
