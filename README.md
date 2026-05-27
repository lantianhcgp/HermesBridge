# 🌉 Hermes Bridge

一个轻量级 Android 桥接 App，让 Hermes 能够通过 HTTP API 直接操作手机功能。

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
Hermes Bridge App（:8888）
    ↓ Android API
手机原生功能
```

## 🚀 使用方法

### 1. 安装 App
```bash
# 在 Termux 中编译
cd HermesBridge
./gradlew assembleDebug

# 安装到手机
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. 启动服务
打开 Hermes Bridge App，点击「启动服务」

### 3. 调用 API
```bash
# 创建日程
curl -X POST http://localhost:8888/api/calendar/create \
  -H "Content-Type: application/json" \
  -d '{
    "title": "开会",
    "start_ms": 1748450400000,
    "end_ms": 1748454000000
  }'

# 发送通知
curl -X POST http://localhost:8888/api/notify/send \
  -H "Content-Type: application/json" \
  -d '{
    "title": "提醒",
    "text": "该吃药了"
  }'

# 获取位置
curl http://localhost:8888/api/location

# 获取设备信息
curl http://localhost:8888/api/device/info

# 获取电量
curl http://localhost:8888/api/device/battery
```

## 📱 权限说明

- **日历**: 读写日历事件
- **短信**: 发送短信
- **位置**: 获取GPS定位
- **相机**: 拍照（未来功能）
- **录音**: 录音（未来功能）
- **通讯录**: 读取联系人（未来功能）

## 🔒 安全说明

- HTTP 服务器仅监听本地 (localhost)
- 外部无法直接访问
- 建议仅在可信网络环境使用

## 🛠️ 开发

### 项目结构
```
app/src/main/java/com/hermes/bridge/
├── MainActivity.kt      # 主界面
├── HttpService.kt       # HTTP 服务
├── CalendarTool.kt      # 日历工具
├── NotifyTool.kt        # 通知工具
├── SmsTool.kt           # 短信工具
├── LocationTool.kt      # 位置工具
└── DeviceTool.kt        # 设备工具
```

### 添加新功能

1. 在 `app/src/main/java/com/hermes/bridge/` 创建新的 `XxxTool.kt`
2. 在 `HttpService.kt` 中注册路由
3. 在 `AndroidManifest.xml` 中添加所需权限

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
