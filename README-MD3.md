# iFAFU

使用 Material 3 重构的校园课表应用。独立维护版本号，当前版本 **1.0.1**。

安装包见 [GitHub Release](https://github.com/Doublesail-A/iFAFU/releases/latest)。推荐安装带 -debug 后缀的 APK，可与原版共存。

## 界面和官方动态配色

- Material 3 顶栏、侧栏和主页 / 成绩 / 选修底栏。
- 主页显示接下来两门今日课程、上下课倒计时和下一场考试。
- 独立周课表、课程详情完整周次、成绩和选修查询。
- 移除旧版样式切换、反馈问题、信息平台和校园百事通入口。
- FAFU 应用图标保留白底，缩薄外围留边：自适应图标 inset 从 22dp 改为 14dp，旧系统外部留边从 8dp 改为 3dp。
- 官方 CircularProgressIndicator、Material 对话框和系统 Toast 提示。

默认使用下一节尚未开始的课的主色，跨日、跨周寻找实际有课的日期。主题只保留“下一节课”“所选背景”“系统壁纸”三个来源，删除竹青、晴空、樱粉和对应的固定颜色资源。

系统壁纸主题直接采用 Android DynamicColors。手动背景使用 Material Components 内置的 **Celebi 量化 + Score 评分**；自定义主题使用官方 **SchemeTonalSpot** 和颜色资源覆盖管线。Android 12 以下回退到 Material 标准主题。

课程去除调课、补课等标记后使用持久的课程身份。浅色课表恢复指定预览图的蓝色、薄荷绿、浅紫、蜜桃、粉色、橙色和珊瑚色色值；同一课程及调课保持同色。深色与扩展色阶仍由 Google Material Color Utilities 的 TonalPalette 生成，前 95 个持久槽位分别拥有不同颜色，文字对比度至少 4.5:1。全局主题继续使用官方 SchemeTonalSpot 动态配色，上课前 15 分钟仍使用 MD3 errorContainer 提示。

课表始终有不透明的 colorSurface 底层，手选图片叠在底层上，再绘制网格和课程；不会显示手机桌面壁纸。

## 启动、提示与更新

使用 AndroidX SplashScreen 的标准启动屏，不再叠加旧闪屏布局和侧滑切换。登录复选框使用 MaterialCheckBox 的主题状态色与完整点击区域，登录按钮与键盘提交均检查用户明确勾选的隐私同意状态。普通信息提示使用系统文本 Toast。

“关于 iFAFU”使用 Material 3 排版与组件，显示独立版本号、当前项目 GitHub、Release、隐私政策与开源致谢。侧边栏和关于页共用 GitHub Releases 更新检查，只从本项目最新正式 Release 获取更新；按当前安装渠道选择匹配 APK，缺少匹配包时打开发布页。旧预览版本标签不会被当作独立版本更新。移除旧官网更新接口和 Bugly 更新 SDK。

## 课表日期与调停课

保留教务系统课表及 DBGrid 调停课解析流程，统一假期仍使用原版的 Holiday 数据格式（from / days / changes）。原版公共假期接口返回空数据时，使用本项目按学期提供的日期数据；没有本校记录时保留原版接口与本地数据回退，不把福建农林大学校历套用到金山学院。

GitHub Actions 每天读取学校公开校历目录，自动发现学期和文档，生成 data/school-calendars.json。解析器支持学校已发布的 PDF、DOCX、XLSX、XLS；文档读取只在项目端执行，不在手机中打包解析库。学校明确沿用国务院安排的节日由 holiday-cn 补充已发布的停课日期；工作日不会被猜测为某个星期的课程。未来公告尚未发布时，沿用已有教务安排，发布后更新数据。未知的当前或未来学期文档会让同步任务报告失败，保留此前有效记录。

应用内置小份日期数据，首次安装离线也可读取；联网按 24 小时缓存更新，手动刷新强制获取。使用原有课程信息完成停课与补课转换，不修改数据库中的原始周次。主页、课表、主题、提醒、日历导出共用转换结果。支持一门课多次调课，保留学期、账号、教师和教室；教务系统已经调到补课日的课程不会被删除。手动添加的课程保留为明确安排。

没有新增假期说明横幅、修复说明或校历状态页，也不额外限制提醒与导出。切换学期取消旧请求，空课表正确替换旧数据；本地解析器正确处理停课范围的最后一周、单双周和相邻课程合并。

## 本地提醒

在侧边栏 **“提醒与日历导入”** 或课表选项中启用：

- 课程开始前 15 分钟，包含名称、时间和教室。
- 考试开始前 30 分钟，包含科目、时间、考场和座位。

使用 Android AlarmManager + NotificationCompat，无需服务器或推送账号。课程、考试、调课、开学日和课表设置更新后自动重排。磁盘缓存和重启接收器支持进程退出、重启、应用升级和时区变化。后台闹钟唤醒应用时，不初始化数据库与网络仓库；接收器同步读取队列、发送通知并登记下一次闹钟；不会启动可能覆盖队列的数据库刷新。数据库观察器只在界面打开后启动。重复调度不会重复发送已送达的提醒。

Android 13+ 需要允许通知，Android 12+ 需要允许“闹钟和提醒”以准时触发；未允许精确闹钟时使用系统允许的非精确闹钟。启用提醒时依次引导通知权限和准时闹钟权限，界面显示未授权造成的延迟，并提供 30 秒后测试通知与后台、电池设置入口。未实际提交成功的通知不会标记为已送达。部分厂商需允许自启动、后台运行或设置电池“不受限制”。[Android 系统闹钟](https://developer.android.com/develop/background-work/services/alarms) 可在进程不存在时触发；强行停止应用会暂停 Android 闹钟，需要重新打开。提醒仅基于已经获取到本机的数据，请先刷新课程和考试。

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
./gradlew :app:testDebugUnitTest --tests 'cn.ifafu.ifafu.ui.timetable.CourseColorPaletteTest' --tests 'cn.ifafu.ifafu.schedule.ScheduleEventsTest' --tests 'cn.ifafu.ifafu.update.GitHubReleasesTest' --tests 'cn.ifafu.ifafu.calendar.*' --tests 'cn.ifafu.ifafu.service.TimetableParserRegressionTest'
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
# Optional: integration tests against the minified release
./gradlew :app:assembleReleaseAndroidTest -PintegrationBuildType=release
```

Windows 使用 gradlew.bat，SDK 路径放在 local.properties 或 ANDROID_SDK_ROOT，不提交本机路径。设备集成测试验证实际 CalendarProvider 彩色导入与重复导入，以及系统闹钟的课程、考试通知。`prepareProcessDeathReminders` 接受 `coldStartDelayMs` 参数，在独立空白测试设备安排两次提醒；结束 instrumentation 并使用 `am kill` 确认进程不存在后，检查两条系统通知的名称、教室、考场和座位。普通测试运行会跳过此准备方法。参数 `coldStartTogether=true` 将两条提醒安排在同一闹钟批次，供深度休眠验证，避免连续短间隔闹钟受 Android 的休眠配额延迟；普通用户的提醒仍按各自时间安排。使用 `clearProcessDeathReminders` 和 `coldStartCleanup=true` 清理测试数据。运行设备测试需要日历、通知和准时闹钟权限，使用独立测试设备。

提醒已经通过独立单元与设备集成测试，并完成 Debug/Release 编译与 APK 签名验证。Android 15 独立测试设备验证了进程退出后连续两次提醒；最终混淆 Release 在确认进程不存在且 `deviceidle` 处于 `IDLE` 后，也实际收到了包含教室、考场和座位的课程、考试通知。

测试版包名为 cn.ifafu.ifafu.debug，可与原版共存。缺少 key-release.properties 时，Release 使用开发签名，不能覆盖官方不同签名的安装包。正式发布应配置自己的签名。

感谢 [woolsen/iFAFU](https://github.com/woolsen/iFAFU) 原作者及贡献者，保留原作者署名与各组件的开源许可。旧工程中的部分测试依赖原作者本机文件，CI 仅运行新增的独立回归测试。
