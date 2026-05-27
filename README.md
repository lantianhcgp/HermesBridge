# 🌉 Hermes Bridge

一个轻量级 Android 桥接 App，让 Hermes 能够通过 HTTP API 直接操作手机功能。

**GitHub:** https://github.com/lantianhcgp/HermesBridge

## ✨ 功能

| 功能 | API | 说明 |
|------|-----|------|
| 📅 日历 | `/api/calendar/*` | 创建/查询/修改/删除日程 |
| 💬 短信 | `/api/sms/send` | 发送短信 |
| 🔔 通知 | `/api/notify/send` | 发送手机通知 |
| 📍 位置 | `/api/location` | 获取GPS定位 |
| 📱 设备 | `/api/device/*` | 电量/网络/存储信息 |

## 🏗️ 架构

```
Hermes（你发消息）
    ↓ HTTP 请求
Hermes Bridge App（:8889）
    ↓ Android API
手机原生功能
```

## 🚀 使用方法

### 1. 安装 App

从 [GitHub Releases](https://github.com/lantianhcgp/HermesBridge/releases) 下载最新 APK 安装。

### 2. 启动服务

**无感启动（推荐）：** App 打开后会自动启动服务并自动退出，用户无需手动操作。

```bash
# 在 Termux 中启动服务（无感）
timeout 3 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 >/dev/null
sleep 5
curl -s http://localhost:8889/api/health
```

### 3. 调用 API

```bash
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
  -d '{
    "title": "提醒",
    "text": "该吃药了"
  }'

# 获取位置
curl -s http://localhost:8889/api/location

# 获取设备信息
curl -s http://localhost:8889/api/device/info

# 获取电量
curl -s http://localhost:8889/api/device/battery
```

## 📱 权限说明

| 权限 | 用途 |
|------|------|
| 日历 | 读写日历事件 |
| 短信 | 发送短信 |
| 位置 | 获取GPS定位 |
| 通知 | 发送推送通知 |

## 🔒 安全说明

- HTTP 服务器仅监听本地 (localhost:8889)
- 外部无法直接访问
- 建议仅在可信网络环境使用

## 🛠️ 开发

### 项目结构

```
app/src/main/java/com/hermes/bridge/
├── MainActivity.kt      # App UI + 自动启动服务 + 自动退出 (v2.4.0)
├── LaunchActivity.kt    # 透明启动器 (Activity base, MIUI fix)
├── HttpService.kt       # Ktor HTTP 服务器 + 通知通道
├── CalendarTool.kt      # 日历 CRUD 操作
├── NotifyTool.kt        # 推送通知 (heads-up)
├── SmsTool.kt           # 短信发送
├── DeviceTool.kt        # 设备信息 + 电量
├── LocationTool.kt      # GPS 定位
└── BootReceiver.kt      # 开机自启动
```

### CI/CD

项目使用 GitHub Actions 自动编译和发布：

- 推送到 `main` 分支触发编译
- 编译生成签名 APK
- 创建 GitHub Release

### 添加新功能

1. 在 `app/src/main/java/com/hermes/bridge/` 创建新的 `XxxTool.kt`
2. 在 `HttpService.kt` 中注册路由
3. 在 `AndroidManifest.xml` 中添加所需权限

## 📖 API 文档

详细的 API 文档请参考 [docs/SKILL.md](docs/SKILL.md)。

## 📝 TODO

- [ ] 蓝牙控制
- [ ] 音乐播放控制
- [ ] WiFi 控制
- [ ] 截屏功能
- [ ] 剪贴板读写
- [ ] App 启动/关闭
- [ ] 闹钟设置
- [ ] 文件操作

## 📄 License

MIT License
