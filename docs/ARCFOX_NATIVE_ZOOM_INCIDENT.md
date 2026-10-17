# 原车仪表地图缩放失效记录

> 2026-10-15 更新：下述自动启动/电话接管方案已撤回；当前实现见 [HUD-only 与原车蓝牙电话](ARCFOX_HUD_ONLY_NATIVE_PHONE_20261015.md)。历史证据保留，不作为当前安装方案。

记录日期：2026-10-14。车机地址为 `192.167.210.240:5555`，已安装导航服务版本为 `V01.01.05-HudArbiter1`。本次只记录已验证的原车缩放问题，CarPlay 重连缩放问题需另外定位。

## 原因与证据

方向盘 `Navi/guidecontrol` 的 up/down 指令正常到达车机通信服务。诊断时 `com.adayo.service.navi` 只运行 `local.arcfox.hud.HudArbiterService`，原有 `com.adayo.service.navi.NaviMidwareService` 没有运行，导航控制路径未初始化。

进程退出历史包含因 `installPackageLI` 和 `deletePackageX` 停止导航服务的记录。HUD 仲裁服务启动会创建同一应用进程，但其 onCreate 只调用 HudArbiter.attach，不会启动原导航中间服务。因此“导航服务进程存在”不能作为原导航功能已运行的判断。

仪表服务同时记录 `mNAVInstance: null`；该记录仅作辅助证据，恢复后仍可能存在，不作为判断原车缩放是否可用的唯一条件。

## 已完成的验证

经用户明确允许，仅启动已安装的原有服务：

```sh
adb -s 192.167.210.240:5555 shell am startservice -n com.adayo.service.navi/com.adayo.service.navi.NaviMidwareService
```

随后日志恢复 AmapNaviJsonListener 的 `80110` 请求，`externalEngineId=1002`，缩放级别切换为 16、17；用户确认原车仪表缩放恢复。没有安装 APK 或修改车机文件。

诊断材料位于本工程 build 目录：`arcfox-navi-services.txt`、`arcfox-navi-exit-info.txt`、`arcfox-native-zoom-verification.log`、`arcfox-navi-services-started.txt`。build 内容为本地诊断资料，不随 Git 提交。

## 修复实现与离线验证（2026-10-14）

改版 NaviService 的启动/更新恢复路径应确保原 NaviMidwareService 运行，保留原服务的导航控制初始化，避免仅拉起 HUD 仲裁。需验证覆盖安装后、车机启动后以及 DiPlay 绑定 HUD 服务后原车缩放均正常。原生服务已经运行时应避免重复初始化；本次临时启动不替代永久修复。

已加入 NativeNaviLifecycle：Application 初始化结束后、HUD 服务创建时检查并启动原 NaviMidwareService；已有 started 服务不重复启动。同一轮启动请求合并。RecoveryReceiver 接收 BOOT_COMPLETED/MY_PACKAGE_REPLACED，恢复覆盖安装后缺失的原服务。未加入任何电话职责。

NativeNaviLifecycleTest 三项通过：多入口启动合并、已有服务不重复启动、覆盖安装恢复与无关广播隔离。已本地构建并验证系统签名：`build/arcfox-release/NaviService-V01.01.05-HudArbiter2-NaviLifecycle-system.apk`。完整CI与其他修复一并运行，尚未重新上车验证。
