# 极狐适配职责收敛：HUD 与原车蓝牙电话

日期：2026-10-15。本次范围仅 DiPlay 和 NaviService，替代之前的电话接管方案。嘟嘟、原车蓝牙电话、共存地图均未修改。

## DiPlay

- 删除 ArcfoxHfpLease、ArcfoxPhoneLeaseClient、ArcfoxPhoneLeaseProtocol、ArcfoxPhoneSync 及其启动、退出和通话回调。
- 不再连接嘟嘟电话租约服务，不再主动断开/恢复 HFP，不再发布 CarPlay 电话状态到仪表，也不再控制电话按键灯或 PhoneLink 电话状态。
- 删除 N6 电话键转发。极狐上的 CALL/ENDCALL 不被 DiPlay 消费，即使旧版保存了比亚迪电话按键设置。
- 撤销极狐专用铃声通道及电话扬声器路由覆盖。保留原版 CarPlay 协议；不新增电话音频协商、不强制更改 iPhone 的音频输出选择。
- 极狐不执行“连接 CarPlay 后暂停蓝牙”的比亚迪功能，避免切断原车电话连接。
- 保留导航 11、Siri 17、媒体 3/自动 0、音乐源仲裁、仪表地图和缩放适配。认证未修改。

原车蓝牙负责其 HFP 通话、电话界面及电话键状态。DiPlay 不再保证或强制 iPhone 选择某个通话输出；实际路由仍需在连接车机后核实。也不通过将全部 CarPlay 音频关闭的设置来实现电话交回，以免影响音乐和导航。

## NaviService

删除 NativeNaviLifecycle 和 RecoveryReceiver，不再因 HUD 初始化、开机或更新主动启动 NaviMidwareService。原车服务生命周期和缩放逻辑保持原 APK 的实现。

HUD 拦截仅处理属性 557853441、557853245、557853246、557853247，以及以 `[3,24,7]` 开头的 HUD 道路包；其他数据继续走原代码。电话灯属性 557845456 不在拦截范围。

构建从原始解包目录重新生成；强制检查仅 ServiceApplication、VehicleSendQueue9、BaseCarManager 三个原生类存在 HUD 接入改动，其余原生 smali（含 NaviMidwareService 和 ClusterComServiceUtils）逐字节相同。新增类只包含 HUD 仲裁。

构建命令：`python3 tools/arcfox-hud/build.py --native-only`。新版版本号为 `V01.01.05-HudArbiter3-HudOnly` / versionCode 5。

## 缩放的证据与限制

此前实车已确认：NaviMidwareService 未运行时原车缩放失效，手动启动后恢复。这条诊断记录保留，但自动启动修复因职责越界被撤回。

此次反馈“安装导航服务后原车恢复、DiPlay 失效”尚无新日志证明因果。参考固件的 HIDL 分析表明服务 ID 14 可对应多个回调，不能据此假定双方覆盖回调。DiPlay 保留现有独立被动订阅，不增加抢注册、原车服务启停或模拟缩放广播。

交付包需实车核实原车/DiPlay 各自缩放、断开重连后的缩放，以及蓝牙来电/接听/挂断和清理 DiPlay 后的电话可用性。离线通过不能代替这组验证。

## 交付与离线验证

- 已运行 AGENTS.md 完整 CI 命令，`BUILD SUCCESSFUL`；日志 `build/arcfox-hudonly-nativephone-ci.log`。
- 补充电话按键隔离及 HUD 协议测试共 5 项通过；认证资源验证、Release lint、Release 构建通过。日志 `build/arcfox-hudonly-nativephone-release.log`。
- 两个 APK 均通过平台签名校验，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
- 检查最终 DiPlay DEX：无 ArcfoxHfpLease、ArcfoxPhoneLeaseClient、ArcfoxPhoneLeaseProtocol、ArcfoxPhoneSync 或 PhoneBridgeService。检查 NaviService 新增 DEX：无电话扩展及 NativeNaviLifecycle/RecoveryReceiver。
- DiPlay：`build/arcfox-release/DiPlay-0.2.19-Arcfox-NativeBluetooth-HudOnly-system.apk`。
- NaviService：`build/arcfox-release/NaviService-V01.01.05-HudArbiter3-HudOnly-system.apk`。

本次没有连接车机或安装 APK；嘟嘟未重新构建。
