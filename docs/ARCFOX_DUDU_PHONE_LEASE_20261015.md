# 嘟嘟承接 DiPlay 电话会话

> 2026-10-15 更新：下述自动启动/电话接管方案已撤回；当前实现见 [HUD-only 与原车蓝牙电话](ARCFOX_HUD_ONLY_NATIVE_PHONE_20261015.md)。历史证据保留，不作为当前安装方案。

日期：2026-10-15。本轮由 DiPlay 会话独立修改。用户指定输入：

`/Users/h/Documents/arcfox/build/Dudu_PIP_fix51_高德关闭保活_其他应用退出后台_platform_signed.apk`

原包为 `com.dudu.autoui`，versionCode 1015031，targetSdk 28，系统 UID，平台签名，没有 persistent 标记。此前 Bluetooth/nFore 系统补丁和独立伴随应用均不是本轮交付方案。

## 行为与职责

DiPlay 在实际 CarPlay 电话会话建立时，启动并绑定嘟嘟的签名保护服务，提交目标手机地址与存活 Binder。服务持久化目标恢复记录后调用原厂 nFore UiCommand 断开该手机 HFP；确认电话归属后，接收响铃、拨号、接通、结束阶段，统一写电话灯及 PhoneLink 状态。

正常断开 CarPlay 时 DiPlay 释放租约；DiPlay 被清理或强停时，嘟嘟收到 Binder 死亡，恢复同一手机的原车 HFP。只关闭导航不释放电话租约。另一部手机原生通话时避让，不断开整机蓝牙。未知目标地址时不盲目断开任何手机。

**不使用嘟嘟的打开/关闭应用按钮、应用前后台或窗口显示判断电话归属。** 嘟嘟的原有画中画、关闭高德保活、其他应用退出后台逻辑均未改。

服务为 started + foreground，返回 START_STICKY。普通开机/解锁后及嘟嘟覆盖更新后读取持久恢复记录。Dudu 原包使用凭据保护存储，本轮不注册 LOCKED_BOOT_COMPLETED，不声称锁屏解锁前即可恢复。若用户把嘟嘟自身也强制停止，Android 不保证服务继续运行；恢复需等嘟嘟服务再次启动。

## 修改边界与构建

- 嘟嘟原有 classes.dex/classes2.dex/classes3.dex、资源和资产逐字节保留。
- 新增 classes4.dex，包含真实测试使用的 `tools/arcfox-bluetooth/java/local/arcfox/phone` 源码。
- 二进制 Manifest 只新增签名权限、车辆属性/蓝牙权限、可选 android.car 库、电话服务和恢复广播接收器。原声明保留，不重编译资源。
- DiPlay 改绑定 `com.dudu.autoui/local.arcfox.phone.PhoneBridgeService`，并声明签名权限；协议仍为 `arcfox.phone.lease.v1`。
- 构建脚本：`tools/arcfox-bluetooth/build_dudu.py`，Manifest 补丁：`dudu_manifest.py`。
- 不修改原车 Bluetooth、nFore、蓝牙电话 APK；NaviService 的原地图缩放修复保持独立。

按用户要求，双手机开启代码已删除：不调用 `setDualDeviceEnable`，不修改双手机设置或原厂静态缓存。没有 DiPlay 会话且没有待恢复记录时，不改变原车 HFP、电话灯或 PhoneLink 通话状态。本轮不包含原 Bluetooth 的封面重复广播修复。

协议见 [电话接口](ARCFOX_PHONE_LEASE_PROTOCOL.md)。实车仍需验证：电话键灯、短按接听/挂断、应用内断开和强停恢复、两部手机通话避让。没有安装或改动车机。

## 配套包与验证记录

目录：`build/arcfox-release/`。先更新嘟嘟，再更新配套 DiPlay：

- `Dudu_PIP_fix51_ArcfoxPhoneLease_v1_platform_signed.apk`
- `DiPlay-0.2.19-Arcfox-DuduPhoneLease-system.apk`

原生地图缩放另用既有 `NaviService-V01.01.05-HudArbiter2-NaviLifecycle-system.apk`，本轮不改。不要混用此前绑定 Bluetooth/nFore/独立伴随包的 DiPlay 候选版本。

电话测试覆盖21项服务租约、2项宿主生命周期以及5项客户端/协议测试。新增断言禁止调用双手机设置事务213；验证无 DiPlay 会话时不改变电话状态。宿主测试验证 foreground/started/START_STICKY 与恢复广播；客户端测试核对嘟嘟组件及启动在绑定之前。

嘟嘟构建、原文件一致性与系统签名验证已通过（`build/arcfox-dudu-phone-build.log`）。原包与修改包证书 SHA256 相同：`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。

完整 CI 第一轮停在原有 SettingsBackupServerTest.stopDoesNotFailWhileConnectionsAreEnding 的 native socket preClose，已保留 `build/arcfox-dudu-ci-thread-dump*.txt` 与 first-attempt 日志；没有修改或跳过该测试，随后原样重跑。

最终完整 CI 通过：`build/arcfox-dudu-phone-ci.log`，BUILD SUCCESSFUL，6m42s。shared 1470项（1项跳过）、common 1299项、home 6项；合计2774项通过、1项跳过、0失败。mobile/home/maphost 的 Debug lint 与构建通过。双手机开启模块已删除，新增嘟嘟 dex 中不包含该模块，测试禁止发送双手机设置事务213。

最终包 SHA256：

DiPlay 的 verifyStandaloneAuthentication、lintRelease、assembleRelease 全部通过（`build/arcfox-dudu-phone-release.log`，2m48s）；两个最终包均通过平台签名验证。认证流程和认证资源未修改，尚未进行车机安装或实测。

| 包 | SHA256 |
| --- | --- |
| DiPlay-0.2.19-Arcfox-DuduPhoneLease-system.apk | `b26aa78b9b5db9c59bbf5bd795886b255435c16b26cd7aa6f8a377a2d4bf8254` |
| Dudu_PIP_fix51_ArcfoxPhoneLease_v1_platform_signed.apk | `35f0b06d4d9a5ec955a596c67c08700eec89a9fa985465b3ac7adb063b3f081f` |
