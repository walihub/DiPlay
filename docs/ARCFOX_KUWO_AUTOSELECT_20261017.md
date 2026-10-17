# 嘟嘟酷我自动适配 v5

本版替代需手选酷我插件的 v4。酷我 v1 APK 不变，只需更新嘟嘟。

## 规则

- 极狐上，当酷我 `cn.kuwo.kwmusiccar` 的 MediaSession 新进入 PLAYING，自动选择嘟嘟既有酷我车载插件（控制器 ID 6）。无需额外手选或新增开关。
- 观察器启动时已经播放的会话按 Android 排序处理；同时有多个新播放来源时沿用该排序，不猜测后台旧来源优先。
- 仅打开酷我、暂停会话、元数据／封面／进度更新、嘟嘟插件重建不触发选择。
- 原车／CarPlay 开始播放后，酷我维持旧的 PLAYING 快照也不会触发抢回；酷我下一次真正开始播放才可能接管。
- 自动操作在 UI 队列执行前检查原选择是否仍一致、酷我 token 是否仍存在且播放、是否出现更新的播放来源；用户手动切换／酷我暂停或退出／延迟过期则丢弃旧操作。
- 若既有 CarPlay 仍报告 PLAYING，最多等待 5 秒暂停反馈，再自动切插件，避免插件重建将旧 CarPlay 快照当成新播放。等待中用户改选、酷我停止、新来源播放或超时都取消，不修改原媒体仲裁。
- 只改变嘟嘟卡片所用的原有插件，不启动酷我、不发送播放命令、不修改音频焦点或原车音源。观察周期 1 秒，使用应用 Context；插件重建不重置历史。

## 原有极狐媒体适配保持

以已经验收的 v3 为构建基线。主 dex 只修改：

1. 酷我插件 n1 的 7 个 v4 兼容入口，保持 MediaSession 同源显示／控制。
2. ArcfoxMediaPlugin 原有 `MediaBridge.start` 后增加 `KuwoAutoSelect.watch(Context)`，使未选择酷我时也能观察它；原 MediaBridge 调用与 SDK 初始化保留。

没有修改 MediaBridge、SourcePolicy、原车 SDK、DuDuMediaCallback、控制转发、原车／CarPlay 交接规则。原有媒体 classes5.dex、车况／胎压 classes4.dex 及其余非主 dex 逐字节保留。没有修改 DiPlay 或导航 APK。

当原车接管时，仍由既有原车媒体适配显示／控制；本观察器对非酷我的播放边沿只记录状态，不主动选择控制器或转发任何命令。

## 产物与验收

`build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v5_KuwoAutoSelect_platform_signed.apk`

SHA256：`c7b0f40dc5a27ca0a8bea44bcf1d6548d8f0405cb29739a8bbef6eb012cbc912`。

使用原 platform 密钥签名。原酷我 APK 沿用 `Kuwo-7.1.8.21-Arcfox-v1-system.apk`。

- 21 项实际 Java／Robolectric 测试通过，记录 `build/kuwo-analysis/auto-tests-final.log`。覆盖原车与 CarPlay 接管后旧酷我不抢回、手动选择优先、排队期间新来源拒绝、插件重建不重放、标准卡片控制与封面／仪表协议。
- 旧极狐媒体回归全部通过，记录 `build/kuwo-analysis/existing-media-regression.log`：10000 次来源转换、真实桥接 Java 的卡片／命令／签名协调集成测试、原 SDK／manager 的 smali ABI。
- 静态脚本核对 ID 6 来自原 p 的 packed-switch，而非包名匹配表索引。没有猜测新的插件编号。
- 全部规定 CI 任务使用现有 JDK 21 测试运行时，记录 `build/kuwo-analysis/auto-ci-verified.log`。最终全部通过（5m10s）：2848 项测试零失败／错误，三个模块 lint 与 debug assemble 通过，home 无测试用例。

实车尚未验证。重点确认：不手选插件直接播放酷我会切卡片；切到原车音乐／CarPlay 后不会被酷我元数据抢回；手动切换卡片后保持选择，直至下一次明确开始酷我播放。
