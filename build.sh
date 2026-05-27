#!/bin/bash

# Hermes Bridge Build Script
# 用于在 Termux 中编译 APK

set -e

echo "🔨 开始编译 Hermes Bridge..."

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "❌ Java 未安装，请先安装: pkg install openjdk-17"
    exit 1
fi

# 检查 JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
    echo "⚠️ JAVA_HOME 未设置，尝试自动检测..."
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
    echo "JAVA_HOME=$JAVA_HOME"
fi

# 进入项目目录
cd "$(dirname "$0")"

# 下载 Gradle Wrapper（如果不存在）
if [ ! -f "gradlew" ]; then
    echo "📥 下载 Gradle Wrapper..."
    # 使用 gradle init 或手动创建
    echo "请先运行: gradle init --dsl kotlin"
fi

# 给 gradlew 执行权限
chmod +x gradlew 2>/dev/null || true

# 编译 Debug APK
echo "🔨 编译 Debug APK..."
./gradlew assembleDebug

# 检查编译结果
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "✅ 编译成功！"
    echo "📦 APK 位置: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "📱 安装到手机:"
    echo "   adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "   或者:"
    echo "   cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/"
    echo "   然后在手机文件管理器中点击安装"
else
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi
