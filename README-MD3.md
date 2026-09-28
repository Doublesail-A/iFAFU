# iFAFU · Material You

基于 [woolsen/iFAFU](https://github.com/woolsen/iFAFU) 公开的 1.4.10 源码重构。内部 1.5.7 仅提供 APK，未用于编译，也不声称包含该版本的全部新增功能。

安装包和更新说明见 [GitHub Release](https://github.com/Doublesail-A/iFAFU/releases/latest)。推荐安装带 `_debug` 后缀的 APK，可与原版共存。

## 界面

- Material 3 顶栏、侧栏和主页 / 成绩 / 选修底栏。
- 主页显示接下来两门今日课程、上下课倒计时，以及下一场考试。
- 独立周课表、课程详情完整周次、成绩和选修查询。
- 移除旧版样式切换、反馈问题和信息平台入口。
- 使用提供的黄色 FAFU 图标。

## 配色与壁纸

默认随课程变化。使用下一堂尚未开始的课程主色；跨日和跨周都会寻找实际有课的日期。正在上课的倒计时独立显示到下课时间。

课程名称先去除 `[调课]`、`【调课】`、补课等排课标记，再分配持久且独立的色彩身份。浅色与深色课表使用 Google Material Color Utilities 的 HCT 统一明度，课程颜色不会被全局主题混成灰色。

主题可选择随课程变化、壁纸取色、跟随系统，以及竹青 / 晴空 / 樱粉。更换课表背景会保存图片并自动选择壁纸取色；提取使用 Material Components 内置的 **Celebi 量化 + Score 评分**，全局配色使用 Google 官方 **SchemeTonalSpot** 和颜色资源覆盖管线，浅色与深色分别生成整套 MD3 颜色角色。移除壁纸后恢复随课程变化。自定义动态配色需要 Android 12+；较早系统回退到竹青。系统动态色遵循设备支持情况。

## 构建

需要 JDK 17、Android SDK 34、Build Tools 34.0.0；Gradle 8.2 使用项目 wrapper。Material Components 1.11 修复了 Android 14 / 15 正式版本的内容动态色资源覆盖问题。

编译依赖已同步到 AGP 8.2.2、Kotlin 1.9.22、Room 2.6.1、Hilt 2.48.1 和 Navigation 2.7.5。AndroidX Activity / Fragment / Lifecycle 同步使用支持 CreationExtras 的版本。

此分支通过 GitHub 分发 APK，保留原工程 targetSdk 31 的系统行为；尚未做 Google Play 上架所需的目标 API 迁移。Release 检查仅排除 Google Play 目标 API 政策项，保留其他致命检查。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests 'cn.ifafu.ifafu.ui.timetable.CourseColorPaletteTest'
./gradlew :app:assembleRelease
```

Windows 使用 `gradlew.bat`。SDK 路径放在本地 `local.properties` 或 `ANDROID_SDK_ROOT`，无需提交本机路径。

测试版包名为 `cn.ifafu.ifafu.debug`，可与原版共存。缺少 `key-release.properties` 时，Release 使用开发签名，不能覆盖官方不同签名的安装包。正式发布应配置自己的发布签名。

旧工程中停止发布的 ImmersionBar / PickerView / ViewHelper 依赖由仓库内的兼容实现替代。保留原仓库许可证和原作者署名。
