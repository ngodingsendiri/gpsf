# gpsf 📍

基于 **Jetpack Compose** 与 **Material Design 3** 的 Android **模拟定位 / 假 GPS** 应用。在 OpenStreetMap 地图上点选坐标后，应用会将该位置模拟为设备 GPS 位置（并在 50 米半径内随机抖动）。

---

## 功能

- Material 3 界面（Android 12+ 支持动态取色）
- OpenStreetMap（osmdroid）交互地图 — 点击选点
- 前台服务模拟 GPS + Network 定位源
- 50 米半径随机抖动，避免位置“卡死”
- 快捷打开 **开发者选项**（选择模拟定位应用）
- 模拟运行时通知
- 跟随系统深色 / 浅色模式

---

## 下载 APK

推送到 `main`/`master` 或打 `v*` 标签时，GitHub Actions 会自动构建 APK：

👉 **[Releases](../../releases/latest)**

也可在 **Actions** 标签页下载构建产物。

---

## 系统要求

| 项目 | 说明 |
|------|------|
| Android | **8.0（API 26）** 及以上 |
| 网络 | 加载地图瓦片需要联网 |
| 权限 | 定位（精确/粗略）、通知（Android 13+） |
| 设置 | 须在开发者选项中将本应用设为 **模拟位置信息应用** |

---

## 使用方法

1. 从 Releases 安装 APK。
2. 开启 **开发者选项**（在“关于手机”中连续点击版本号 7 次）。
3. **设置 → 开发者选项 → 选择模拟位置信息应用** → 选择 **gpsf**。
4. 打开 **gpsf**，在地图上点选位置，按 **Play** 开始。
5. 按 **Stop**（X 图标）停止。

---

## 本地编译

```bash
# 需要 JDK 17+ 与 Android SDK（platform 34）
./gradlew assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Windows：

```bat
gradlew.bat assembleDebug
```

---

## 项目结构

| 文件 | 作用 |
|------|------|
| `MainActivity.kt` | Compose UI、OSM 地图、启停模拟 |
| `MockLocationService.kt` | 前台服务 + 测试定位 Provider |
| `GpsfConstants.kt` | 共享常量（半径、默认坐标等） |

---

## 说明

- 仅当在开发者选项中将 **gpsf** 选为模拟定位应用时才会生效。
- 请仅用于测试 / 开发，并负责任地使用。

## 许可证

MIT
