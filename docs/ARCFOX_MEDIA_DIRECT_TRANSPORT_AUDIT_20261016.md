# 极狐仪表音乐直连：静态证据及边界（2026-10-16）

> 2026-10-16 后续变更：按用户要求删除 ARCFOX Link 回退及路由选择代码。媒体仅通过原车 HIDL 消息 114 直连；不可用时保留状态并按现有节流重试，不发送 Link 广播。下文回退描述仅为此前方案历史。

## 采用的通路

DiPlay 在自己的媒体发布后台线程直接调用原车已有的 `IFgeComService.fgeSendMessage2ComService`，发送仪表音乐消息 114。JSON 与 `MIS_ArcfoxLinkMusic` 下游协议保持一致：`command.id=meidainfo`、`command.sub=id3info`、`content.mediatype=19`。无需 ARCFOX Link 转发，也无需伪造原生 CarPlay/蓝牙连接状态。

源代码位置：

- `common/src/main/java/com/shilapi/xcertplay/vehicle/arcfox/ArcfoxDirectMusicTransport.kt`：JSON、封面和后台线程限制。
- `common/src/main/java/com/shilapi/xcertplay/vehicle/arcfox/ArcfoxMusicHidl.kt`：反射调用已提取的 HIDL ABI。
- `common/src/test/java/com/shilapi/xcertplay/vehicle/arcfox/ArcfoxDirectMusicTransportTest.kt`：协议、保留/清除封面、失败回退及封面文件测试。

## ABI 证据

以下路径均相对仓库，来自实际仪表 APK 反编译目录 `build/arcfox-analysis/meter-vehicle-data/smali/`。

| 事实 | 证据 |
| --- | --- |
| ARCFOX Link 媒体类型为 19 | `com/adayo/aaop_meterinfoservice/instance/MIS_ArcfoxLinkMusic.smali:7` |
| JSON 字段及播放/暂停字串 | 同文件 `requireDataByMusicInfoReceived`，约 203–459 行 |
| 毫秒转换秒，余数严格大于 500 才进位 | 同文件 `parseTime`，约 179–201 行 |
| 消息编号 114、JSON command | 同文件 `convert2Json`，约 780–828 行 |
| 原车发送转发函数 | `com/adayo/aaop_meterinfoservice/ModuleBase.smali:99` |
| 最终调用 HIDL 方法 | `com/adayo/aaop_meterinfoservice/utils/ComServiceUtils.smali:845` |
| HIDL 服务描述符、transaction=1、flags=0、verifySuccess | `android/hardware/fgecomservice/V1_0/IFgeComService$Proxy.smali:332` |
| Message 长度 40，id@0/content@8/respond@24 | `android/hardware/fgecomservice/V1_0/Message.smali:492`、`:531` |
| mute 字段 1 为未静音、0 为静音 | `com/adayo/aaop_meterinfoservice/utils/MuteStateUtils$1.smali:104` 及 `MuteStateUtils.smali:83` |

发送函数没有 client ID 或回调参数。新实现不执行 register/unregister，不占用方向盘监听使用的 service ID 14；不存在“注册同 ID 替换原车监听”的行为。服务端/仪表固件未在本机执行，因此只能确认已证实的调用 ABI；事务正常返回表示本地 HAL 派发成功，不表示仪表提供了显示回执。

## 未采用的路径

1. `MIS_CarPlayMusic` 的 ADSBus 接口需要原生连接状态：`MIS_CarPlayMusic.smali:397` 开始读取 `isCarPlayConnected()`，并与 `PlaybackStatus==1` 共同判断。不能靠系统签名绕过这一业务条件。
2. `MIS_ArcfoxLinkMusic` 的上游入口是 Link 发布的 listener；`IReadPhoneData` 仅暴露 registerListener/unregisterListener，不是第三方写接口。反射调用 APK 类只会创建自己进程的对象，不能访问原 Link 进程 listener 列表。
3. 原车 MediaCenter 确实有 sendMediaInfo/sendMediaPlayProgress 等方法，但 `MediaType` 没有 CarPlay：QQ=0、BT=1、USB=2、HIMALAYA=3、RADIO=4/5/6、MEDIA_APP=7。`MIS_MediaCenter.himalayaMediaTypeToQnxMediaConvert()` 将 7 映射为 0；冒充 QQ/蓝牙会污染音源身份，不能用于替代本通路。

## 发布与回退约束

- 只有 DiPlay 媒体确实拥有当前播放来源时发布。失去来源所有权时停止进度/保活，不能发空包覆盖新来源。
- 无 HAL、权限/SELinux 拒绝、死 Binder、事务异常属于发送失败。封面写入失败单独处理：目录不可写或图片无效时只去掉封面，仍发送完整歌曲/状态/进度，下次快照重试封面。HAL 派发失败返回不可用，由发布器按本会话已固定通路策略处理。失败不会触碰音频、电话、HUD 或服务生命周期。
- 服务不存在时 `getService(..., false)` 不等待服务启动；事务异常后清句柄，下次更新重新获取。同步 HIDL 调用和文件操作只允许在媒体串行后台线程，主线程调用拒绝直连。
- 每包都是完整字段快照，保留封面文件名；仅修改进度也不会擦掉封面。封面是 `/mnt/ota/media/diplay_music_<SHA256>.png`，先写临时文件、sync、设置全局可读、原子 rename。图片维度、像素数、输入大小受限。只清理超过 24 小时且完整匹配自己前缀/哈希命名的旧 PNG，绝不删除原车或 Link 文件。
- 正常派发无法阻止其他媒体服务后来写入同一物理显示；仍需来源仲裁及周期快照恢复。直连不宣称接管原车全局媒体栈。
- Cover 为空的 progress-only 广播在参考旧 Link Receiver 内会创建新 MusicInfo 并覆盖 albumpic；回退不能假设接收器缓存封面，必须采用完整的小封面快照或升级接收端的“省略即保留”协议。

## 仍需实车验收

验证 HIDL 访问权限、仪表实际接收 mediatype 19、封面共享目录读权限、原车与 CarPlay 来源切换、不占用方向盘监听、HAL 重启后的重新获取。离线测试只能覆盖编码、文件和调度/回退行为，不能证明车机端画面。

## 补充：事件 16、栈与电话/导航边界

`MIS_ArcfoxLinkMusic.requireDataByMusicInfoReceived` 在 playing 时先发内部事件 16。`MIS_Core.onEventReceivedLinkMusic`（约 413–460 行）对 16 的处理只有 setEventId 和 `MIS_StackCtrl.push`。push（约 130–219 行）替换同 level 的旧模块、调用 onRemoved/onLoad、写 `/mnt/sdcard/MeterInFoServiceImageDef.json`。本音乐类的 onLoad/onRemoved（约 906–919 行）均直接 return 0，**没有额外选源/页面/enable HIDL 消息**。随后内部事件 17 在 executeEngine（约 734–804 行）检查模块是否存在于栈，再生成并发送 114。因此 114 已包含最终输出字段，但直发会跳过仪表服务的栈登记/成员检查，无法更新原服务内部音乐所有权或让其替自己执行恢复。

`scanAliveElement`（MIS_StackCtrl 约 222–285 行）枚举全部非空 level 槽位，不是只返回最高优先级模块；executeEngine 以成员包含关系判定。因此不能把“电话入栈”理解为这层代码必然禁止所有音乐 114。

原车电话生成独立消息 149，`command.id=phoneinfo`（MIS_Phone.smali 约 1660–1705 行、MIS_ArcfoxLinkPhone.smali 约 611–659 行）；音乐是消息 114 的 `meidainfo/id3info`，导航通常是消息 93，地图卡片联动是 224。音乐编码本身不包含电话/导航数据或取消命令，不会从字段上清除电话/导航。然而仪表固件如何选前景页面、是否有同屏遮挡，不能从服务 APK 静态代码保证。

直连发送现在有具体保护：只有 AudioManager.mode 为 MODE_NORMAL 且 CarPlay 当前没有电话卡片时允许使用；响铃、电话、通信、未知模式或 CarPlay 来电直接返回不可用，交给 Link 原服务路径。该检查只读，不改变电话、蓝牙或音频模式。它覆盖已反映到 Android/CarPlay 状态的通话；原车若未及时更新 mode，或固件有自定义高优先级状态未反映到这些接口，仍需实车验收。此边界不能用伪造“已连接”或向原服务推送假电话解决。

## 补充：跨通路重复发送核对

原服务没有周期重放 Link 音乐快照的代码：`MIS_Core$1.run()` 的 60000 ms 循环只打印各 level 和 stack；`MIS_ArcfoxLinkMusic.requireData/onLoad/onRemoved` 都直接 return 0，也没有 dirty 标志或定时器。但并非完全无旧缓存重放风险：

- `MIS_ArcfoxLinkMusic$1.onConnectStateChanged` 可直接发送清空 114（约 286 行）或发内部事件 17。
- `MIS_Core.onStartCommand` 调用 readMirrorFiles；其读取保存的模块/eventId 并调用 executeEngine（约 1631 行）。
- Link 进程仍可在连接/音乐事件时向同一模块推送信息。

因此避免一会话中 native→Link→native 来回切换。发布器应在首次成功派发时固定通路：直连成功过后，模式保护或 HAL 暂时失败只挂起/重试直连；Link 成功过后，本会话只用 Link。首次直连尚未成功时可选择 Link。会话真正清理后再重置。这样至少不会由 DiPlay 自己同时建立两条持续写入链；已经运行的外部服务/其他应用仍可写仪表，不能宣称全局独占。

## 补充：固定直连后的封面故障降级

首次无图快照成功可能已经固定 native 通路。因此后续共享目录不可写、图片不可解码或写入失败不会阻断整条音乐快照；本包 albumpic 为空，歌名/状态/进度仍发送，后续包继续尝试保存保留的封面字节。歌曲/封面标识变化先清理内存中的旧文件名，禁止显示上一曲封面。封面文件名不符合受控前缀/无路径分隔符规则时仍拒绝整包，不能把不安全路径当作普通图片失败放行。
