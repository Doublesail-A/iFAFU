# iFAFU · Material You

基于 [woolsen/iFAFU](https://github.com/woolsen/iFAFU) 公开的 1.4.10 源码重构。内部 1.5.7 只有 APK，未用于编译，不声称包含该版本的全部新增功能。

安装包见 [GitHub Release](https://github.com/Doublesail-A/iFAFU/releases/latest)。推荐安装带 _debug 后缀的 APK，可与原版共存。

## 界面和官方动态配色

- Material 3 顶栏、侧栏和主页 / 成绩 / 选修底栏。
- 主页显示接下来两门今日课程、上下课倒计时和下一场考试。
- 独立周课表、课程详情完整周次、成绩和选修查询。
- 移除旧版样式切换、反馈问题和信息平台入口。
- FAFU 应用图标使用白色背景，并为黄色徽章留出边距。
- 官方 CircularProgressIndicator、Material 对话框和 Snackbar 提示。

默认使用下一节尚未开始的课的主色，跨日、跨周寻找实际有课的日期。主题只保留“下一节课”“所选背景”“系统壁纸”三个来源，删除竹青、晴空、樱粉和对应的固定颜色资源。

系统壁纸主题直接采用 Android DynamicColors。手动背景使用 Material Components 内置的 **Celebi 量化 + Score 评分**；自定义主题使用官方 **SchemeTonalSpot** 和颜色资源覆盖管线。Android 12 以下回退到 Material 标准主题。

课程去除调课、补课等标记后使用持久的课程身份。Material 参考色只作为源色，Google 官方 Score 筛选彼此分离的颜色来源；容器色、文字色和强调色均由 Google Material Color Utilities 生成，不手调 HCT 色相、饱和度或明度。检测官方色阶量化后的颜色碰撞后，尝试其他官方角色或方案。课表浅色、深色分别使用对应 container / onContainer；15 分钟内上课的课程使用 errorContainer 提示。

课表始终有不透明的 colorSurface 底层，手选图片叠在底层上，再绘制网格和课程；不会显示手机桌面壁纸。

## 本地提醒

在“课表选项 → 日历导入与提醒”或“设置 → 日历导入与上课提醒”启用：

- 课程开始前 15 分钟，包含名称、时间和教室。
- 考试开始前 30 分钟，包含科目、时间、考场和座位。

使用 Android AlarmManager + NotificationCompat，无需服务器或推送账号。课程、考试、调课、开学日和课表设置更新后自动重排。磁盘缓存和重启接收器支持进程退出、重启、应用升级和时区变化。重复调度不会重复发送已送达的提醒。

Android 13+ 需要允许通知，Android 12+ 需要允许“闹钟和提醒”以准时触发；未允许精确闹钟时使用系统允许的非精确闹钟。界面显示权限状态，并提供 30 秒后测试通知。部分厂商需允许后台运行。强行停止应用会暂停 Android 闹钟，需要重新打开。提醒仅基于已经获取到本机的数据，请先刷新课程和考试。

## 彩色日历

“导入本机彩色日历”通过 Android CalendarContract 为每门课程建立独立的 LOCAL 日历并设置颜色，供 Google 日历 Android 和其他读取系统日历的客户端显示。每次课程作为实际事件导入，包含周次、调课、教师、教室及真实起止时间。重复导入只更新本学期由 iFAFU 创建的事件，不删除用户自行创建的事件。

这些日历存于手机，**不自动同步至 Google 云端**。应用不收集 Google 账号凭据。

还支持：
- 完整课表 ICS：标准 UTF-8、UTC 日期、字符转义、75 字节折行及稳定 UID。
- 分课程 ZIP：每门课一个 ICS，包含 X-IFAFU-COLOR / X-APPLE-CALENDAR-COLOR 元数据及导入说明。

Google 日历网页版忽略 ICS 颜色元数据。需要云端彩色课表时，使用 ZIP 文件，为每门课建独立日历并导入对应文件，再设置日历颜色。目标软件是否支持颜色元数据和文件重复导入去重，取决于该软件。

## 构建和验证

JDK 17、Android SDK 34、Build Tools 34.0.0、Gradle 8.2；AGP 8.2.2、Kotlin 1.9.22、Room 2.6.1、Hilt 2.48.1、Material Components 1.11.0。targetSdk 已提升至 33，以正确请求 Android 13 通知权限；此分支通过 GitHub 分发 APK，尚未满足当前 Google Play 目标 API 政策。

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest --tests 'cn.ifafu.ifafu.ui.timetable.CourseColorPaletteTest' --tests 'cn.ifafu.ifafu.schedule.ScheduleEventsTest'
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
```

Windows 使用 gradlew.bat，SDK 路径放在 local.properties 或 ANDROID_SDK_ROOT，不提交本机路径。设备集成测试验证实际 CalendarProvider 彩色导入与重复导入，以及系统闹钟的课程、考试通知。运行设备测试需要日历、通知和准时闹钟权限，使用独立测试设备。

测试版包名为 cn.ifafu.ifafu.debug，可与原版共存。缺少 key-release.properties 时，Release 使用开发签名，不能覆盖官方不同签名的安装包。正式发布应配置自己的签名。

保留原项目许可证和原作者署名。旧工程中的部分测试依赖原作者本机文件，CI 仅运行新增的独立回归测试。
