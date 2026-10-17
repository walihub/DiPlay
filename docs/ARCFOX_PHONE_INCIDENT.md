# 极狐电话同步诊断记录

诊断日期：2026-10-14，离线修复更新：2026-10-15。问题包为 DiPlay 0.2.19 极狐权限修复版。用户复现来电后方向盘电话灯不同步，以及任务管理器清理 DiPlay 后蓝牙电话未恢复。本记录描述诊断证据及离线实现，修复版尚未上车验证。

## 方向盘电话灯

实车日志显示 iAP2 `0x4155 CallStateUpdate` 到达 DiPlay，随后 `mfiservice.communication.call.state.update` 到达 AAOP_MeterInFoService，仪表服务将来电状态识别为 ringing、结束状态识别为 hangup。因此仪表电话卡片的数据接收链路正常。

参考 APK `ARCFOX_Link_V1.0.8_CarLifeCoverFix_v17.apk` 的 `com.carlinx.carlink.manage.BtPropertyManager.setBcmPhoneStatus()` 使用 CarPropertyManager 写入属性 `557845456`（EHU_STEERWHLPHNKEYBACKLI_SEND），areaId=0。调用者 IccoPhoneDataListener.onCallInfoReceived 的映射是 INCOMING=1、IDLE=0、DIALING/ALERTING/ACTIVE=2。DiPlay 当前 ArcfoxPhoneSync 只发送仪表广播，没有这条车辆属性写入路径，缺少电话灯状态同步。

SystemUI 也订阅相同广播，但 SystemUIApplication 中仅调用 WindowsUtils.setScreenStatus(true) 唤醒屏幕；不能把它误认为电话灯控制接口，扩大广播范围不能代替车辆属性适配。

### 原车蓝牙电话实测确认

用户在原车蓝牙电话执行响铃、接听、挂断后，日志确认 AdayoBluetoothPhone 的 BtPropertyManager 写属性 `557845456`，VehicleHalServer.onSetProperty 与 BCMFunc 均接收对应值。用户观察到的颜色映射如下：

| 阶段 | 属性值（areaId=0） | 方向盘电话键灯 |
| --- | --- | --- |
| 来电响铃 | 1 | 绿色 |
| 接通 | 2 | 红色 |
| 挂断 | 0 | 恢复正常 |

因此灯状态接口已由参考代码与实车日志、用户观察共同确认。日志为 `build/arcfox-native-phone-lamp-20261014.log`。灯同步现已由蓝牙进程实现，尚未上车复测。

待实施：CarPlay 负责电话时随来电/拨号/通话/结束同步上述车辆属性；原车 HFP 负责电话时让原车写入；连接退出与进程死亡时释放本来源，避免遗留电话灯或清除其他来源的状态。需实车验证属性授权、灯行为及竞争。

## 强制停止后的蓝牙电话恢复

复现日志记录 ActivityManager 执行 Force stopping com.shihab.diplay，并 Killing 该进程。ArcfoxHfpLease 的恢复 worker、定时任务和会话结束回调均在 DiPlay 进程中；强制停止不会保证执行这些清理，因此持久化 pending_address 只能供下一次 DiPlay 启动或重启恢复，无法保证被杀后立即恢复。

进一步核对当前车机的 nFore_BT_UI_service.apk：BtService 的 UiCommand.reqHfpDisconnect 在调用底层 HFP 断开前调用 AutoConnectUtils.stopAutoConnect。因此不能依赖蓝牙服务在 DiPlay 被杀后自行重连。原生 CarPlay 的恢复另由 BtLifeCycle.onCarPlayStatusChanged 接收 PhoneLinkProxy 的连接状态，在无线 CarPlay 断开时调度 N_AUTOCONNECT_CARPLAY；DiPlay 当前的 HFP 租约没有接入该原生投屏状态路径。

复现日志中强制停止后的间隔没有 DiPlay 发起的 reqHfpConnect；后续日志出现原车非自动连接请求，并确认 HFP 实际连接成功。这支持“恢复请求没有发起”的定位，不能泛化为所有连接请求都不会被拒绝：BtService.reqHfpConnect 仍可能因设备数量上限、补连进行中、已经连接或正在连接而返回 false。

待实施方案：使用仍运行的原车服务持有 HFP 会话租约，在断开原车 HFP 前保存目标地址，绑定 DiPlay 的存活 Binder；正常退出或 Binder 死亡后由外部服务调用原车 Nforetek reqHfpConnect，验证实际连接并重试。用户已明确排除 NaviService 承担电话职责。该恢复逻辑由电话/蓝牙侧常驻服务承接，与“bug修复”会话协同实现。单独增加 DiPlay 的 onDestroy、独立进程或定时器不能保证强制停止后的恢复。

该电话租约必须独立于 HUD 导航状态，以整个 CarPlay 连接为生命周期；结束导航不能结束电话租约。服务侧要持久化待恢复地址、在自身重启后恢复，并在确认 HFP 已连接后才删除记录。只恢复本租约断开的设备，不断开另一部手机。电话灯同样需要处理 Binder 死亡释放，并避免覆盖原车已接管的电话状态。

本次仅修改本地源码与构建候选 APK，没有安装或改动车机。原车缩放问题另见 [ARCFOX_NATIVE_ZOOM_INCIDENT.md](ARCFOX_NATIVE_ZOOM_INCIDENT.md)。原始日志为本工程 build/arcfox-repro-20261014.log；参考反编译文件位于 build/arcfox-analysis/，均为本地诊断材料。

## 离线修复协作进展（未完成实车验证）

NaviService 只修复原导航服务启动。Bluetooth.apk 封面/双手机策略及进程死亡后的 HFP 恢复与“bug修复”会话协作；DiPlay 不自行开启 dualDeviceEnable。双方接口已核对。

方向盘短按：当前 CarHardKeyInputService 的 InputModeKey.onRelease 优先处理原车蓝牙通话，否则通过 CarplayHardManager/HiCarHardManager 查询 PhoneLinkProxy.getPhoneLinkData(3).isPhoneCall；true 才发 N6 onKeyUp / InputKey=5，否则切换音源。长按 InputKey=6 没有相同条件。MCU167 不应直接当 Android CALL 键转发。

原生 CarPlaySDKAdapter.onAudioEvent 使用 reportPlayStatus(action, source=3, volumeType=5铃声/2通话)。蓝牙服务端 PhoneOutputs 通过此 Binder 合同报告并读回 isPhoneCall；不匹配则记录未确认并重试。PhoneLink 服务端尚无完整反编译证据，模拟 Binder 测试仅证明报文与重试，不能替代实车语义确认。

## 最终职责调整（2026-10-15）

以上待实施描述保留作为诊断历史，当前离线实现如下，尚未上车复测：

- 嘟嘟 fix51 新增电话服务中的 PhoneOutputs 统一写灯属性557845456（1/2/0）及 PhoneLink 通话状态。没有在 DiPlay 保留第二个输出写入者。2026-10-15 曾考虑 nFore 和独立伴随应用，最终按用户要求由嘟嘟承接；原车蓝牙 APK 不改。
- DiPlay 的 ArcfoxHfpLease/ArcfoxPhoneLeaseClient 只提交会话与阶段；保留旧版本 pending_address 的恢复迁移，不再直接断开 HFP。
- 嘟嘟电话服务用 lifetime Binder 死亡监听处理强制停止，按目标地址恢复，不断开另一部手机，也不覆盖其原生通话。它不依赖嘟嘟打开/关闭 DiPlay 的操作回调。
- 成功或结果未知的异步断开保留持久恢复记录，不在超时后放弃。明确拒绝且没有之前未决请求时，允许关闭并释放记录。HFP 广播补充轮询，捕获快速断开/重连。
- 有线空地址只在确认没有任何 HFP 手机时获得输出权限，不调用 HFP 断开/连接。
- NaviService 仅修复原导航中间服务启动，不承担电话职责。

接口见 [ARCFOX_PHONE_LEASE_PROTOCOL.md](ARCFOX_PHONE_LEASE_PROTOCOL.md)。离线测试直接编译蓝牙服务实际 Java 源码，以原车 Binder 合同模拟 vendor、PhoneLink、车辆属性和进程死亡。测试能确认状态机、报文及恢复路径，不能替代实车电话灯、PhoneLink 服务语义和 HFP 时序验证。本次没有安装或改动车机。
