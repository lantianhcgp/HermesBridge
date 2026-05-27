# 🌉 Hermes Bridge — Termux Android 专属 AI Agent Skill

[![GitHub Release](https://img.shields.io/github/v/release/lantianhcgp/HermesBridge?style=flat-square)](https://github.com/lantianhcgp/HermesBridge/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/lantianhcgp/HermesBridge/build.yml?style=flat-square)](https://github.com/lantianhcgp/HermesBridge/actions)

> ⚠️ **本方案专为 Android + Termux 环境设计。**
> 其他平台（Linux Desktop / macOS / Windows / iOS）的 Termux 或终端环境有完全不同的权限模型和 API 机制，不能直接套用本方案。如果你的 Agent 跑在非 Android Termux 上，需要寻找对应平台的原生方案。

## 🎯 这是什么？

这是专为 **Android 手机上用 Termux 跑 AI Agent（如 Hermes）** 设计的桥接方案。

### 为什么需要它？

在 Android 上，Termux 是一个沙箱 App，**没有权限**直接操作手机的日历、短信、通讯录等系统功能。这个 App 作为桥梁，让 Termux 里的 Agent 通过 HTTP API 操作手机。

```
📱 Android 手机
├── Termux App（沙箱，权限受限）
│   └── Hermes Agent（运行在这里）
│       ↓ HTTP localhost:8889
├── Hermes Bridge App（有系统权限）
│   ├── 📅 日历
│   ├── 💬 短信
│   ├── 🔔 通知
│   ├── 📍 位置
│   └── 📱 设备信息
```

### ⚡ 快速体验

```
你：帮我明天下午3点创建开会日程
Agent：✅ 日程已创建（明天15:00-16:00 开会）
```

Agent 会自动拉起 App → 调用 API → 创建日程，**全程无感**。

## 📋 前置条件

| 条件 | 说明 |
|------|------|
| Android 手机 | 本方案仅适用于 Android |
| Termux App | 必须是 F-Droid 版本（不是 Google Play 版） |
| Hermes Agent | 在 Termux 中运行 |
| 网络 | 仅需本地通信，无需外网 |

## 🚀 安装

```bash
# 1. 从 GitHub Releases 下载最新 APK
# https://github.com/lantianhcgp/HermesBridge/releases

# 2. 安装到手机（手动点击安装）
# 3. 打开 App 一次，授予日历/短信/位置权限
# 4. 完成！App 会自动启动服务并退出
```

## 🔄 工作原理

```
1. 用户对 Agent 说："帮我创建日程"
2. Agent 执行: am start --user 0 -n com.hermes.bridge/.MainActivity
3. App 启动 → 自动启动 HTTP 服务 → 自动退出（用户无感）
4. Agent 调用: curl -s http://localhost:8889/api/calendar/create ...
5. App 执行系统 API，返回结果
6. Agent 告诉用户：日程已创建 ✅
```

## 📚 API 示例

```bash
# 启动服务（无感，用户看不到）
timeout 3 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 >/dev/null
sleep 5

# 检查服务状态
curl -s http://localhost:8889/api/health

# 创建日程
curl -s -X POST http://localhost:8889/api/calendar/create \
  -H "Content-Type: application/json" \
  -d '{
    "title": "开会",
    "start_ms": 1748450400000,
    "end_ms": 1748454000000
  }'

# 发送通知
curl -s -X POST http://localhost:8889/api/notify/send \
  -H "Content-Type: application/json" \
  -d '{"title": "提醒", "text": "该吃药了"}'

# 获取位置
curl -s http://localhost:8889/api/location
```

## ⚠️ 平台限制（仅限 Android Termux）

| 限制 | 原因 | 本方案的解决方式 |
|------|------|----------------|
| Termux 无法直接写日历 | Android 沙箱隔离 | App 有系统权限，通过 API 操作 |
| `am start` 在 Android 12+ 报错 | AppOpsManager bug | 用 `am start --user 0`（报错但实际能启动） |
| `am startservice` 不可用 | Termux uid ≠ App uid | App 自动启动服务，不需要外部 startservice |
| `pm install` 在 MIUI 失败 | XSpace 安全限制 | 用户手动安装 APK |
| `termux-open` 在 Android 13 不可靠 | 静默失败 | 用 `am start --user 0` 替代 |

> 📖 完整的故障排除请参考 [docs/SKILL.md](docs/SKILL.md)

## 📂 项目结构

```
app/src/main/java/com/hermes/bridge/
├── MainActivity.kt      # UI + 自动启动服务 + 自动退出
├── LaunchActivity.kt    # 透明启动器（MIUI 兼容）
├── HttpService.kt       # Ktor HTTP 服务器
├── CalendarTool.kt      # 日历操作
├── NotifyTool.kt        # 推送通知
├── SmsTool.kt           # 短信发送
├── DeviceTool.kt        # 设备信息
├── LocationTool.kt      # GPS 定位
└── BootReceiver.kt      # 开机自启动
```

## 🔧 开发

```bash
# 克隆仓库
git clone https://github.com/lantianhcgp/HermesBridge.git
cd HermesBridge

# 编译（需要 Android SDK + JDK）
./gradlew assembleRelease

# APK 输出
ls app/build/outputs/apk/release/
```

## 📄 License

MIT License
