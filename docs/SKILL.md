---
name: hermes-bridge
description: "HermesBridge Android app: control phone features (calendar, SMS, notifications, location, device) via HTTP API from terminal."
version: 2.4.0
author: Hermes Agent
license: MIT
platforms: [android]
metadata:
  hermes:
    tags: [android, phone, calendar, sms, notification, api, bridge]
    homepage: https://github.com/lantianhcgp/HermesBridge
---

# HermesBridge — Android Phone Control via HTTP API

HermesBridge is a lightweight Android app that runs an HTTP server (port 8889) on the phone, allowing Hermes to control phone features via REST API from the terminal.

**GitHub:** https://github.com/lantianhcgp/HermesBridge

## Prerequisites

1. **HermesBridge APK installed** on the phone (download from GitHub Releases)
2. **App opened at least once** (to grant permissions)
3. **Phone and Termux on the same device** (or network-accessible)

## ⚠️ User Interaction Style

When working with this user:
- **Follow instructions directly** — don't go in circles debugging
- **Minimize visible UI** — user expects 100% seamless (无感) experience
- **Never open browser or show App UI** unless explicitly asked
- **Test quietly** — report results, not process

## User Preference: Seamless (无感) Experience

**The user expects ZERO visible changes when Hermes starts the service.** No screen flash, no app switch, no notification pop, no browser opening. If the user sees anything happen on screen, the launch failed. This is non-negotiable — the user has corrected this multiple times.

When launching the service:
- NEVER open a browser or unrelated app (even for testing!)
- NEVER show the HermesBridge UI
- NEVER use `am start` / `termux-open` in a way that causes visible UI
- NEVER ask the user to "just open the app" or "click the start button" — find a way to do it automatically
- ALWAYS verify with `curl` health check before reporting success
- If the service doesn't start silently, debug silently — don't ask the user to do manual steps

## Platform Notes

### QQ (qqbot)
- **MEDIA: attachments are NOT supported on QQ.** The send_message MEDIA: prefix is silently ignored.
- To send files to the user: use `$PREFIX/bin/termux-open /path/to/file` to open the file handler, or provide a download URL.
- Text messages work normally.
- **`am start` from QQ bot context works** — the QQ bot runs in the same Termux environment.

## Quick Start

### 1. Start the Service (Seamless — User sees nothing)

```bash
# ⚠️ 实测发现：termux-open 在此设备上静默失败（exit 0 但未启动 Activity）
# 最可靠的方式是 am start --user 0（虽然报错但服务确实启动）

# RECOMMENDED: am start --user 0（最可靠）
timeout 3 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 >/dev/null
sleep 5
curl -s http://localhost:8889/api/health
# 返回 JSON = 服务已启动，App 已自动退出
```

**v2.4.0+ 行为：** MainActivity 打开后，如果权限已授予，会自动启动服务并通过轮询 `HttpService.isRunning` 检测成功后立即 `finish()`。用户看到 App 闪一下 → Toast "服务已启动 ✅" → 回到之前的界面。

**⚠️ `am start --user 0` vs `termux-open`：** `termux-open` 在某些设备上可能静默失败（exit code 0 但没启动 Activity）。如果 `termux-open` 不生效，用 `am start --user 0` 作为后备方案。`am start --user 0` 虽然报 AppOpsManager 错误，但 Activity 确实启动了。

### Full Auto-Start Workflow (Detect → Launch → Verify → Call)

```bash
# 完整工作流：检测 → 启动 → 验证 → 调用 API
HEALTH=$(curl -s --max-time 3 http://localhost:8889/api/health 2>/dev/null)
if [ $? -ne 0 ]; then
    # 启动服务（am start --user 0 最可靠）
    timeout 3 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 >/dev/null
    sleep 5
    HEALTH=$(curl -s --max-time 3 http://localhost:8889/api/health 2>/dev/null)
    
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to start service"
        exit 1
    fi
fi
echo "Service ready: $HEALTH"
# 现在可以调用任何 API...
```

### 2. Verify Service is Running

```bash
curl -s http://localhost:8889/api/health
# Expected: {"status":"ok","service":"HermesBridge","version":"2.1","port":8889}
```

### 3. Call APIs

```bash
# Create a calendar event
curl -s -X POST http://localhost:8889/api/calendar/create \
  -H "Content-Type: application/json" \
  -d '{"title":"Meeting","start_ms":1779948000000,"end_ms":1779951600000}'

# Send a notification
curl -s -X POST http://localhost:8889/api/notify/send \
  -H "Content-Type: application/json" \
  -d '{"title":"Alert","text":"Hello from Hermes!","priority":"high"}'
```

## API Reference

**Base URL:** `http://localhost:8889`

### Health Check

```
GET /api/health
```

Response:
```json
{"status":"ok","service":"HermesBridge","version":"2.1","port":8889}
```

### Device Info

```
GET /api/device/info
```

Returns: manufacturer, model, Android version, network status, storage info.

### Battery Status

```
GET /api/device/battery
```

Returns: percentage, status (charging/discharging), temperature, voltage.

### Calendar — List Events

```
GET /api/calendar/list
GET /api/calendar/list?from_ms=1779878400000&to_ms=1780483200000
```

- `from_ms`: Start timestamp in milliseconds (default: now)
- `to_ms`: End timestamp in milliseconds (default: now + 7 days)

Response:
```json
{
  "success": true,
  "count": 2,
  "events": [
    {
      "id": 12345,
      "calendar_id": 1,
      "title": "Meeting",
      "description": "...",
      "location": "...",
      "start_ms": 1779948000000,
      "end_ms": 1779951600000,
      "all_day": false
    }
  ]
}
```

### Calendar — Create Event

```
POST /api/calendar/create
Content-Type: application/json
```

Body:
```json
{
  "title": "Meeting with team",
  "start_ms": 1779948000000,
  "end_ms": 1779951600000,
  "description": "Optional description",
  "location": "Optional location",
  "all_day": false
}
```

**Required:** `title`, `start_ms`, `end_ms`
**Optional:** `description`, `location`, `all_day`

Response:
```json
{"success":true,"event_id":12345,"message":"Calendar event created successfully"}
```

### Calendar — Update Event

```
POST /api/calendar/update
Content-Type: application/json
```

Body:
```json
{
  "event_id": 12345,
  "title": "Updated title",
  "description": "Updated description",
  "location": "New location",
  "start_ms": 1779948000000,
  "end_ms": 1779951600000,
  "all_day": false
}
```

**Required:** `event_id`
**Optional:** any field to update

### Calendar — Delete Event

```
POST /api/calendar/delete
Content-Type: application/json
```

Body:
```json
{"event_id": 12345}
```

### Send Notification

```
POST /api/notify/send
Content-Type: application/json
```

Body:
```json
{
  "title": "Notification title",
  "text": "Notification content",
  "priority": "high"
}
```

**Priority values:** `high` (heads-up, sound, vibration), `default`, `low`

Response:
```json
{"success":true,"notification_id":100,"message":"Notification sent successfully"}
```

### Send SMS

```
POST /api/sms/send
Content-Type: application/json
```

Body:
```json
{
  "to": "13800138000",
  "message": "Hello from Hermes!"
}
```

### Get Location

```
GET /api/location
```

Returns GPS coordinates (requires location permission).

## Common Workflows

### Create Tomorrow's Meeting

```bash
# Calculate timestamps
TOMORROW_2PM=$(date -d "tomorrow 14:00" +%s)
TOMORROW_2PM_MS=$((TOMORROW_2PM * 1000))
TOMORROW_3PM_MS=$((TOMORROW_2PM_MS + 3600000))

# Create event
curl -s -X POST http://localhost:8889/api/calendar/create \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"Team Meeting\",
    \"start_ms\": $TOMORROW_2PM_MS,
    \"end_ms\": $TOMORROW_3PM_MS,
    \"location\": \"Conference Room A\"
  }"
```

### List This Week's Events

```bash
FROM_MS=$(($(date +%s) * 1000))
TO_MS=$(($FROM_MS + 7 * 24 * 60 * 60 * 1000))
curl -s "http://localhost:8889/api/calendar/list?from_ms=$FROM_MS&to_ms=$TO_MS" | python3 -m json.tool
```

### Send Heads-Up Notification

```bash
curl -s -X POST http://localhost:8889/api/notify/send \
  -H "Content-Type: application/json" \
  -d '{"title":"⚡ Reminder","text":"Your meeting starts in 5 minutes!","priority":"high"}'
```

### Check Phone Battery Before Important Call

```bash
BATTERY=$(curl -s http://localhost:8889/api/device/battery | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"Battery: {d['battery']['percentage']}% ({d['battery']['status']})\")")
echo "$BATTERY"
```

## Auto-Start Behavior

- **Boot:** HermesBridge starts automatically on phone reboot (BootReceiver)
- **App Open:** Service starts automatically when the app is opened
- **Service Persistence:** Uses `START_STICKY` — Android restarts the service if killed

## Notification Channels

| Channel | Purpose | Behavior |
|---------|---------|----------|
| Hermes Bridge 服务 | Persistent service notification | Low priority, silent, no heads-up |
| Hermes 推送通知 | API-triggered notifications | High priority, heads-up, sound, vibration |

## Troubleshooting

### Service not responding

```bash
# 检查服务是否运行
curl -s http://localhost:8889/api/health

# 如果未运行，用 am start --user 0 启动（最可靠）
timeout 3 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 >/dev/null
sleep 5
curl -s http://localhost:8889/api/health

# 如果 am start 也失败，检查 App 是否安装
timeout 2 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 | grep -q "NullPointerException"
# 如果返回 0（有 NPE），说明 App 已安装但可能缺少权限
```

**实测发现：**
- `termux-open` 在 Android 13 上**静默失败**（exit 0 但未启动 Activity）
- `am start --user 0` 虽然报 AppOpsManager 错误，但**确实能启动 Activity**
- 启动后 MainActivity 会自动启动服务（权限已授予时）并自动退出

**⚠️ `input keyevent` is blocked on Android 13:** Termux does not have `INJECT_EVENTS` permission. Cannot simulate back press, home key, or any key event. `termux-keyevent` is also not installed by default. If you need the user to press back, just launch the Activity — it auto-finishes via `auto_finish` extra.

**⚠️ PATH 问题:** `which termux-open` 可能找不到，因为 `$PREFIX/bin` 不在默认 PATH 中。使用完整路径：
```bash
# ✅ 正确
$PREFIX/bin/termux-open --es auto_finish true "package:com.hermes.bridge/.MainActivity"

# ❌ 可能找不到
termux-open "package:com.hermes.bridge/.LaunchActivity"
```

**⚠️ Exported Service:** 从 v2.2.2 起 HttpService 设为 `exported="true"`，理论上可直接 `am startservice`，但 Android 12+ 的 AppOpsManager bug 导致报错。**不要用 `am startservice`，用 `am start --user 0` 启动 Activity（v2.4.0+ 的 MainActivity 会自动启动服务并退出）。**

**⚠️ 检测安装状态:** Termux 无法用 `pm list packages` 检测（user 999 权限不足）。间接检测方式：
```bash
# 通过 am start 检测（会报 AppOpsManager 错误但说明组件存在）
timeout 2 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 | grep -q "NullPointerException"
# 如果返回 0（有 NPE），说明 App 已安装
```

### App not installed

APK must be installed manually — Termux cannot install APKs on modern Android (especially MIUI).
1. Download APK from GitHub Releases
2. Open the APK file in a file manager
3. Allow "Unknown sources" if prompted
4. Install and open once to grant permissions

### Calendar events not showing

- Ensure calendar permissions are granted (Settings → Apps → Hermes Bridge → Permissions)
- Check that `from_ms` and `to_ms` are in milliseconds (not seconds)
- Events created with past timestamps may not appear in default query

### Notification not appearing (heads-up)

- Check notification channel settings: Settings → Apps → Hermes Bridge → Notifications
- Ensure "Hermes 推送通知" channel is enabled and set to "Allow"
- On some phones, "Do Not Disturb" may block heads-up notifications

### Port conflict

If port 8889 is in use, the service will fail to start. Change the port:
- In the app: modify `DEFAULT_PORT` in `HttpService.kt`
- Or restart the conflicting service

## Source Code

```
HermesBridge/
├── app/src/main/java/com/hermes/bridge/
│   ├── MainActivity.kt      # App UI + auto-start on open + auto-exit after service starts (v2.4.0)
│   ├── LaunchActivity.kt    # Transparent launcher (Activity base, NOT AppCompatActivity — MIUI fix)
│   ├── HttpService.kt       # Ktor HTTP server + notification channels
│   ├── CalendarTool.kt      # Calendar CRUD operations
│   ├── NotifyTool.kt        # Push notifications (heads-up)
│   ├── SmsTool.kt           # SMS sending
│   ├── DeviceTool.kt        # Device info + battery
│   ├── LocationTool.kt      # GPS location
│   └── BootReceiver.kt      # Auto-start on boot
├── .github/workflows/build.yml  # CI/CD (signed release APK)
└── app/build.gradle.kts     # Dependencies + signing config
```

## Pitfalls ( Lessons Learned )

### `termux-open` may silently fail to launch Activities

On some Android 13+ devices, `termux-open "package:..."` returns exit code 0 but doesn't actually launch the Activity. This is a silent failure — no error message, just nothing happens.

**Diagnostic:**
```bash
# Test if termux-open actually works
$PREFIX/bin/termux-open "package:com.hermes.bridge/.MainActivity"
sleep 5
curl -s http://localhost:8889/api/health
# If NOT_RUNNING, termux-open silently failed
```

**Fix:** Use `am start --user 0` as fallback:
```bash
timeout 3 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 >/dev/null
sleep 5
curl -s http://localhost:8889/api/health
```

`am start --user 0` always works for launching Activities (despite the AppOpsManager error message). It's the most reliable method when `termux-open` fails.

### Don't overcomplicate the launch — user wants simple

**User correction:** "你偏离方向了" — when the agent was adding `finishAndRemoveTask()`, `onWindowFocusChanged` safety nets, `FLAG_NOT_TOUCHABLE`, and multiple fallback layers to the LaunchActivity, the user said to stop and just do the simple thing: open Activity → service starts → Activity exits.

**The canonical pattern (v2.4.0+):**
```kotlin
// In MainActivity.onCreate — after auto-start logic
if (checkPermissions()) {
    startHttpService()
    autoFinishWhenReady()  // polls HttpService.isRunning, then finish()
} else {
    updateUI()
}
```

```bash
# Terminal launch — just this, nothing more
timeout 3 am start --user 0 -n com.hermes.bridge/.MainActivity 2>&1 >/dev/null
sleep 5
curl -s http://localhost:8889/api/health
```

**Lessons:**
- Simple `finish()` after service starts is enough — user sees a brief flash, that's fine
- Don't add invisible-activity complexity unless user explicitly asks
- `autoFinishWhenReady` (polling `isRunning`) is better than fixed `postDelayed` — it adapts to actual startup time
- The user's patience for debugging loops is low — if something works, move on

### Don't test termux-open with unrelated URLs

When debugging `termux-open`, do NOT test it with random URLs like `"https://www.baidu.com"`. It will open the browser and distract the user. Only test with the actual package/component you need:

```bash
# ❌ BAD — opens browser for no reason, user gets annoyed
$PREFIX/bin/termux-open "https://www.baidu.com"

# ✅ GOOD — test the actual component silently
$PREFIX/bin/termux-open "package:com.hermes.bridge/.LaunchActivity"
sleep 5
curl -s http://localhost:8889/api/health
```

**User correction:** "为什么你还打开了百度" — the user expects every terminal action to serve the task, not be a random test. Keep debugging focused and invisible.

**User correction:** "为什么你还打开了百度" — the user expects every terminal action to serve the task, not be a random test. Keep debugging focused and invisible.

### First launch needs ~5 seconds, not 2

The first time you launch the service via `termux-open`, Android needs to cold-start the app process, create the foreground service, and bind the Ktor server. `sleep 2` is not enough — the health check will fail. Use `sleep 5` for the first launch. Subsequent launches (when the process is warm) are faster.

```bash
# ❌ Too short — health check fails
$PREFIX/bin/termux-open "package:com.hermes.bridge/.LaunchActivity"
sleep 2
curl -s http://localhost:8889/api/health  # likely fails

# ✅ Correct
$PREFIX/bin/termux-open "package:com.hermes.bridge/.LaunchActivity"
sleep 5
curl -s http://localhost:8889/api/health  # works
```

### Ktor `receive<Map>()` fails on Android

Ktor's `call.receive<Map<String, Any>>()` throws "Cannot transform this request's content to kotlin.collections.Map" on Android. **Always use Gson:**

```kotlin
// ❌ WRONG — crashes on Android
val body = call.receive<Map<String, Any>>()

// ✅ CORRECT — works everywhere
val bodyStr = call.receiveText()
@Suppress("UNCHECKED_CAST")
val body = gson.fromJson(bodyStr, Map::class.java) as Map<String, Any>
```

And the helper function **must be `suspend`** because `receiveText()` is a suspend function:
```kotlin
// ❌ WRONG — compile error
private fun parseBody(call: ApplicationCall): Map<String, Any>

// ✅ CORRECT
private suspend fun parseBody(call: ApplicationCall): Map<String, Any>
```

### Service state must persist via companion object

Activity-local `var isRunning = false` resets when the Activity is recreated (e.g., tapping a notification). Use `HttpService.companion` with `@Volatile`:

```kotlin
// In HttpService.kt
companion object {
    @Volatile var isRunning = false; private set
    @Volatile var currentPort = 8889; private set
}

// In MainActivity.kt — read from service, not local var
val running = HttpService.isRunning
```

### `am start` / `pm install` from Termux (Android 12+)

Termux's `am` command throws `NullPointerException` at `AppOpsManager.reportRuntimeAppOpAccessMessageAndGetConfig` on Android 12+. `pm install` fails on MIUI with `XSpaceManagerServiceImpl` SecurityException. **APK must be installed manually by the user.**

**⚠️ Exported service doesn't help:** Setting `android:exported="true"` on `HttpService` does NOT bypass the Android 12+ Termux limitation. `am startservice` still returns "Error: Requires permission not exported from uid 10740". The AppOpsManager bug affects all `am` commands regardless of export status.

What works from Termux:
- `$PREFIX/bin/termux-open "package:com.hermes.bridge/.MainActivity"` — launches an Activity (preferred)
- `am start --user 0 -n com.hermes.bridge/.MainActivity` — launches an Activity (reliable fallback, despite AppOpsManager error)
- `termux-open /path/to/file.apk` — opens file with default handler (may trigger installer)
- `curl localhost:8889/api/*` — calls the HTTP API

What does NOT work from Termux:
- `am startservice -n ...` / `am stopservice -n ...` — AppOpsManager bug or uid mismatch
- `am force-stop ...` — not supported by Termux's `am` (shows help text instead)
- `pm install -r ...` — fails on MIUI (XSpace)
- `pm list packages` — user 999 permission denied
- `input keyevent 4` — requires INJECT_EVENTS permission (blocked on Android 13)
- `cmd activity start ...` — SecurityException (uid mismatch)

To stop the service, there is no reliable Termux-side method. Options:
- Ask the user to swipe away the notification or force-stop via Settings
- Call a hypothetical `/api/service/stop` endpoint (not yet implemented)
- The service will stop itself if the app process is killed

### Transparent Activity for Seamless Start (MIUI-tested)

The `LaunchActivity` pattern starts the HTTP service without showing any UI.

**⚠️ CRITICAL: Must use `Activity` base class, NOT `AppCompatActivity`!**
MIUI overrides the transparent theme when using `AppCompatActivity`, causing the activity to flash on screen. Using `Activity` avoids this entirely.

**User's preferred pattern (simplest):** Start service → `finish()` immediately. The user is OK with a brief flash as long as it's fast and automatic — they don't want to be asked to do anything manually.

```kotlin
// ✅ User's preferred pattern — simple and fast
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        val port = intent?.getIntExtra("port", 8889) ?: 8889
        if (!HttpService.isRunning) {
            HttpService.start(this, port)
        }
        finish()  // Simple — user sees a brief flash, then back to what they were doing
    }
}
```

```kotlin
// ❌ WRONG — MIUI shows the activity briefly
class LaunchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HttpService.start(this, 8889)
        finish()
    }
}
```

Requires a transparent theme in `themes.xml`:
```xml
<style name="Theme.Transparent" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowContentOverlay">@null</item>
    <item name="android:backgroundDimEnabled">false</item>
    <item name="android:windowAnimationStyle">@null</item>
</style>
```

And in `AndroidManifest.xml`:
```xml
<activity android:name=".LaunchActivity"
    android:exported="true"
    android:theme="@style/Theme.Transparent"
    android:taskAffinity=""
    android:excludeFromRecents="true"
    android:noHistory="true" />
```

Key elements for true invisibility:
- `finish()` — simple, fast exit. User sees brief flash then returns to previous app
- `FLAG_NOT_TOUCHABLE` — prevents window from receiving input
- `Activity` base class — avoids MIUI theme override
- For even less visibility: add `finishAndRemoveTask()` + `overridePendingTransition(0, 0)` + `onWindowFocusChanged` safety net (see references/launch-activity-miui.md)

**v2.4.0+ auto-exit pattern (in MainActivity):** Instead of a separate LaunchActivity, MainActivity now auto-exits after service starts:
```kotlin
if (checkPermissions()) {
    startHttpService()
    autoFinishWhenReady()  // polls isRunning, then finish()
} else {
    updateUI()
}
```
This means opening the app (via any method) will auto-start the service and auto-exit. No need for a separate LaunchActivity.

> 📖 See [references/launch-activity-miui.md](references/launch-activity-miui.md) for full MIUI analysis, code, and verification steps.

### Git push timeout on slow connections

Termux's git push over HTTPS can hang indefinitely on slow/unstable connections. Fix:

```bash
git config http.lowSpeedLimit 0
git config http.lowSpeedTime 999999
git push origin main
```

**实测：** 需要设置 `http.postBuffer` 为较大值才能推送成功：
```bash
git config http.postBuffer 524288000
git push origin main
```

### GitHub Actions artifact download fallback

`gh run download` can timeout on slow connections. Use the API directly:

```bash
# Get artifact URL
ARTIFACT_URL=$(gh api repos/OWNER/REPO/actions/runs/RUN_ID/artifacts --jq '.artifacts[0].archive_download_url')

# Download with curl (more reliable than gh run download)
curl -L -o artifact.zip "$ARTIFACT_URL" -H "Authorization: token $(gh auth token)"
unzip -o artifact.zip -d ./apk-output
```

### GitHub Actions release creation fails with 403

The default `GITHUB_TOKEN` in GitHub Actions may not have permission to create releases. Two options:
1. Create the release manually with `gh release create` from Termux
2. Add `permissions: contents: write` to the workflow YAML

### APK Signing for Consistent Upgrades

Store keystore as base64 in GitHub Secrets, decode in CI:
```bash
# In GitHub Actions workflow
echo "$KEYSTORE_BASE64" | base64 -d > hermesbridge.keystore
```

In `build.gradle.kts`:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../hermesbridge.keystore")
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "hermes2024"
        keyAlias = System.getenv("KEY_ALIAS") ?: "hermesbridge"
        keyPassword = System.getenv("KEY_PASSWORD") ?: "hermes2024"
    }
}
```

## Tips

- **Always check health first** before calling other APIs
- **Use `am start --user 0`** for reliable service start — `termux-open` silently fails on Android 13
- **Use `python3 -m json.tool`** to pretty-print JSON responses
- **Timestamps are in milliseconds** — multiply Unix seconds by 1000
- **The service runs in the foreground** — visible as a persistent notification
- **Boot auto-start** requires the app to have been opened at least once
- **First launch needs 5s wait** — subsequent launches are faster
- **`am start --user 0`** is the most reliable method — always works despite AppOpsManager error messages
- **App auto-exits after service starts** — user sees a brief flash, then returns to previous app
