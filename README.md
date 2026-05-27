# 🌉 Hermes Bridge

一个轻量级 Android 桥接 App，让 AI 智能体能够通过 HTTP API 直接操控手机功能。

## 🤖 它是什么

Hermes Bridge 是一个 **AI Agent Skill**。安装后，智能体（如 Hermes、OpenClaw）可以通过对话自动帮你操作手机：

> **你：** "帮我明天下午3点创建一个开会的日程"
> **智能体：** 自动调用 Hermes Bridge API → 日程创建完成 ✅

你只需要：
1. 安装一次 App
2. 智能体会自动拉起服务、调用 API、完成操作

**全程无感，无需手动干预。**

## 📱 支持的功能

| 功能 | 说明 |
|------|------|
| 📅 日历 | 创建/查询/修改/删除日程 |
| 💬 短信 | 发送短信 |
| 🔔 通知 | 发送手机通知 |
| 📍 位置 | 获取 GPS 定位 |
| 📱 设备 | 电量/网络/存储信息 |

## 🚀 智能体如何使用

### 前置条件

1. 手机安装 Hermes Bridge APK（从 [Releases](https://github.com/lantianhcgp/HermesBridge/releases) 下载）
2. 智能体加载 `hermes-bridge` Skill

### 自动工作流

```
用户发送消息："帮我创建日程"
    ↓
智能体加载 Skill，获取 API 参考
    ↓
智能体自动执行：
  1. 拉起 App（无感，闪一下自动退出）
  2. 等待服务启动
  3. 调用 API 创建日程
    ↓
智能体回复："日程已创建 ✅"
```

### Skill 位置

```
~/.hermes/skills/android/hermes-bridge/SKILL.md
```

智能体通过 `skill_view(name='hermes-bridge')` 加载后，即可获得：
- 完整 API 参考（端点、参数、示例）
- 无感启动流程
- 故障排除指南

## 🏗️ 架构

```
用户 ←→ 智能体（Hermes/OpenClaw）
              ↓ 加载 Skill
              ↓ HTTP 调用
         Hermes Bridge App（:8889）
              ↓ Android API
         手机原生功能（日历/短信/通知...）
```

## 📖 详细文档

- **[docs/SKILL.md](docs/SKILL.md)** — 完整 API 参考、工作流程、故障排除

## 🛠️ 开发

### 项目结构

```
app/src/main/java/com/hermes/bridge/
├── MainActivity.kt      # App UI + 自动启动服务 + 自动退出
├── LaunchActivity.kt    # 透明启动器（无感拉起）
├── HttpService.kt       # Ktor HTTP 服务器
├── CalendarTool.kt      # 日历 CRUD
├── NotifyTool.kt        # 推送通知
├── SmsTool.kt           # 短信发送
├── DeviceTool.kt        # 设备信息 + 电量
├── LocationTool.kt      # GPS 定位
└── BootReceiver.kt      # 开机自启动
```

### CI/CD

推送到 `main` 分支自动编译，生成签名 APK 并发布到 GitHub Releases。

## 📄 License

MIT License
