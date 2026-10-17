# 极狐蓝牙封面重复更新与 `system_server_wtf`：现场记录

采集时间：2026-10-14（车机本地时间，UTC+08）。本次 ADB 操作均为只读；没有清空日志、安装、停止服务或修改车机设置。原始材料保存在 `arcfox-capture/2026-10-14-reboot-risk/`，该目录含设备标识和媒体元数据，不应直接公开。

## 已确认的现象

- 断开蓝牙后的 20 秒现场日志中，没有封面回调或相应 `system_server_wtf`；重连原手机后，同一封面句柄持续产生回调和告警。
- 换另一部手机播放时也复现。`logcat-new-phone-playback.txt` 中，新手机同一封面句柄 `0000000`、大小 39,920 字节，22:45:03–22:46:47 共记录 126 次 `AvrcpControllerBipService.onBipCoverArtUpdated()`。这排除了单一手机导致问题的解释。
- 用户暂停播放后采集的 `media-session.txt` 显示蓝牙媒体会话 `PlaybackState state=2`（暂停），`bluetooth-state.txt` 显示活动 A2DP 设备已回到原手机、`Streaming: false`。在 22:50:29–22:51:45 的 76 秒内，`paused-followup-logcat.txt` 仍记录 80 次 `getAvrcpElementAttributes()`、80 次 `MESSAGE_FETCH_IMAGE`、80 次本地缓存文件命中以及 80 次 `CB_OBEX_IMAGE_FETCHED`。因此暂停后也重复请求；这段证据对应原手机，不能当作新手机暂停状态的证据。
- 每次重复封面广播都伴随 `ActivityManager: Sending non-protected broadcast com.nforetek.bluetooth.bip.action.COVER_ART_UPDATED from system ... com.android.bluetooth`，并生成 `am_wtf`。这是系统对非受保护广播的告警；不能仅凭 `wtf` 推断它就是重启原因。当前 `ro.boot.bootreason=reboot`，现有 pstore/crash 证据不足以归因重启。

## 静态调用链

从车机只读复制的 `Bluetooth.apk` 反编译结果：

1. `NfJniService` 暴露 `getAvrcpElementAttributes()`，委托 AVRCP JNI 向手机请求曲目属性。
2. `NfAvrcpJniByPass.onGetElementAttributes()` 对属性 ID 8（封面句柄）的每次成功响应调用 `AvrcpControllerBipService.fetchImg(handle)`，没有相同句柄去重。
3. `AvrcpControllerBipObexHandler.getImage()` 即使发现 `/data/misc/bluedroid/bip_temp/AVRCP_BIP_IMG_<handle>.JPEG` 已存在，仍读取文件并调用 `onBipCoverArtUpdated()`。
4. `NfJniService` 对每次回调构造包含整张图片字节的隐式 `COVER_ART_UPDATED` 广播，未去重或限定接收范围。

暂停状态日志在 22:51:43.511 记录一次 `getAvrcpElementAttributes()`，22:51:43.538 收到八项属性并发起 `MESSAGE_FETCH_IMAGE`，随后命中同一缓存文件并再次发送封面广播。约每秒重复一轮。蓝牙控制层进程涉及 `com.nforetek.bt`、`com.nforetek.bt.customer.service`；哪个上层调用者持续轮询属性还未被 Binder 调用记录直接确认。修复广播风暴不需要先改其他车机应用，可在蓝牙系统 APK 的封面路径去重。

## 离线修复约束与候选方案

最小范围优先修改 `Bluetooth.apk`：同一设备、同一曲目、同一封面句柄且已有有效封面时，不重复执行取图和广播；连接切换、曲目变化、无封面或读取失败时重置状态。不能只按句柄永久去重，因为另一部手机使用固定句柄 `0000000`，不同歌曲可能复用它。应把曲目 UID／标题等可靠身份、设备地址和缓存失效事件纳入判断，并保留首次更新。另需处理正常首次广播仍可能产生的非受保护广播告警；避免通过静默压制全部广播来掩盖功能回归。

`com.android.bluetooth` 是 `/system/app/Bluetooth` 的 `SYSTEM PERSISTENT` 包，UID 1002，版本 3.7.72；`com.nforetek.bt` 是 `/system/app/nFore_Hades` 的 `SYSTEM PERSISTENT` 包，UID 10024，版本 3.7.87；`com.nforetek.bt.customer.service` 是 `/system/app/nFore_BT_UI_service` 的 `SYSTEM PERSISTENT` 包，UID 1000，版本 02.40.220919。它们与车机桌面、蓝牙电话、媒体服务均使用同一签名证书 SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。Android 通常允许以相同包名、相同签名、合适的递增版本在 `/data/app` 安装系统应用更新，而不需 root；这不会覆盖 `/system/app` 原文件。仅靠静态信息不能保证车机厂商安装策略一定放行，最终需在后续安装测试中验证。更新持久化蓝牙系统进程会中断当前蓝牙连接，不能在现场采证期间尝试。

## 已保存的材料

- 原始日志：`logcat-all.txt`、`logcat-new-phone-playback.txt`、`paused-logcat.txt`、`paused-followup-logcat.txt`、断开／重连蓝牙前后的日志及 `dropbox.txt`。
- 当前状态：`bluetooth-state.txt`、`media-session.txt`、`audio-state.txt`、`processes.txt`、`activity-broadcasts.txt`、`boot-state.txt`、相关 `package-*.txt`。
- 原车 APK：`Bluetooth.apk`、`nFore_Hades.apk`、`nFore_BT_UI_service.apk`、`BtContactsProvider.apk`、`AdayoLauncher.apk`、`AdayoBluetoothPhone.apk`、`AAOP_MediaService.apk`。本地反编译结果在相应 `*-src/` 目录；反编译存在少量无法还原的方法，修复前须核对字节码。

后续在无车机连接时，先基于以上 APK 做离线补丁与验证；现场安装验证需确认更新后的包签名、版本号、蓝牙连接恢复、切歌换封面、固定句柄手机换歌、暂停/重播以及 `am_wtf` 数量。
