# 极狐 HUD 与仪表盘接口记录

更新日期：2026-10-09。实现基线：DiPlay 0.2.15，上游 `ef4e3fe5`；保留极狐适配和 HUD 诊断修复。

本文记录本项目从车机 APK、只读采集资料中分析出的接口，以及 DiPlay 当前使用方式。它不是极狐厂家发布的协议规范，也不是完整 CAN 总线协议。属性编号、字段和行为适用于已分析的车机样本；其他车型、固件需要重新核验。

## 1. 适用范围与证据

| 项目 | 已采集信息或分析来源 |
| --- | --- |
| 平台 | HS7013A，Android 11，系统指纹前缀 `AAOP_COCKPIT/` |
| 仪表显示屏 | Android Display 2，`local:3`，名称 `HDMI 屏幕`，1920×720 |
| HUD 导航接口 | `NaviService.apk`：`ClusterHudManger`、`VehicleSendQueue9`、`BaseCarManager` |
| 仪表音乐和电话接口 | `AAOP_MeterInFoService.apk`：`MIS_CarPlayMusic`、`MIS_CarPlayPhone`、`MIS_ArcfoxLinkMusic` |
| 音乐转发参考 | CarLife v4 修改版、ARCFOX Link v17 修改版的 `CarLifeMusicReceiver` |
| 仪表地图参考 | 用户提供的高德增强版 APK，以及显示屏、窗口采集记录 |
| 系统仪表组件 | `DirectRenderingCluster.apk`；不能仅凭包名认定它负责所有原车仪表基础画面 |

本地采集目录：`arcfox-capture/2026-10-07/`。该目录包含 APK 和只读采集记录，不属于已提交的公开源码；日志可能包含个人信息，不应直接发布。

本地反编译分析重点类：

- `com.adayo.service.navi.clusterhud.ClusterHudManger`
- `com.adayo.service.navi.can.BaseCarManager`
- `com.adayo.aaop_meterinfoservice.instance.MIS_CarPlayMusic`
- `com.adayo.aaop_meterinfoservice.instance.MIS_CarPlayPhone`
- `com.adayo.aaop_meterinfoservice.instance.MIS_ArcfoxLinkMusic`
- `com.carlinx.arcfox.carlink.CarLifeMusicReceiver`

当前适配通过离线测试、lint 和构建检查；尚未完成适配版 APK 的实车验收。原车高德在仪表及 HUD 上正常显示的观察，不能替代 DiPlay 适配版的实车验证。

## 2. 输出通道

| 功能 | 当前数据流 | 输出接口 |
| --- | --- | --- |
| 仪表地图 | iPhone → CarPlay 仪表地图视频流 → DiPlay 解码 → 仪表屏 | Android `Presentation` 与 `Surface` |
| 导航 HUD | iPhone → iAP2 导航消息 → DiPlay → Android Car 服务 | 厂商车辆属性，area ID 为 0 |
| 仪表音乐 | iPhone → Now Playing/封面 → DiPlay → ARCFOX Link → 仪表服务 | 显式广播、原车 Binder 转发及 HIDL |
| 仪表电话 | iPhone → iAP2 通话状态 → DiPlay → 仪表服务 | 带 JSON 字符串的广播 |
| 语音长按 | 原车长按广播 → DiPlay → 当前 CarPlay 会话 | 广播监听、原版 Siri 请求接口 |

地图视频和 HUD 导航是两套独立输出。HUD 不依赖把仪表地图画面截图、解析或镜像到 HUD。

## 3. 仪表地图显示

### 3.1 显示屏选择

当前实现必须同时满足：

1. `Build.FINGERPRINT` 以 `AAOP_COCKPIT/` 开头。
2. 显示屏不是默认显示屏，`displayId != 0`。
3. 显示名称为 `HDMI 屏幕`。
4. 尺寸为 1920×720。

采集时的 Android Display ID 为 2，但实现不固定使用数字 2。厂商地图窗口 ID `1002` 与 Android Display ID 不同，不能互换。

`AAOP_COCKPIT/` 是当前平台匹配条件，不应视为所有极狐车型的唯一识别标志。HUD、音乐、电话和长按适配目前只检查此指纹前缀；地图额外检查显示屏。

### 3.2 视频配置和生命周期

| 项目 | 当前实现 |
| --- | --- |
| CarPlay 仪表视频流 | stream 111，即项目中的 alternate/cluster stream |
| 请求视频尺寸 | 1920×720，scale 100% |
| 默认显示/安全区域 | 用户指定 1200×600，原点 (360,120)，底部贴齐；默认车标保留 (960,540)，默认安全区为 (360,360) 的 1200×360 |
| 内容类型 | 沿用原版仪表内容设置 |
| 显示方式 | `Presentation` 内的 `TextureView`；透明窗口，不接收触摸、不获取焦点 |
| 默认启用 | 未保存设置且匹配仪表屏时启用；用户已保存的关闭值仍有效 |
| 开始显示 | 仪表视频流开始后显示，不以“开始导航”为前提 |
| 停止显示 | 视频流停止后地图层透明；没有极狐等待占位画面 |

连接 CarPlay 后会准备显示窗口，但是否立即有地图取决于 iPhone 地图 App 是否发送仪表视频流。此通道请求的是 CarPlay 仪表内容，不是主屏完整画面的镜像。

原车仪表的驾驶、地图、歌词模式如何仲裁，以及显示区域是否会遮挡车辆信息，目前没有已确认的切换接口；1920×720 满屏输出仍需实车核验。

实现：[ArcfoxClusterDisplay.kt](../common/src/main/java/com/shilapi/xcertplay/ArcfoxClusterDisplay.kt)、[ClusterMapPresentation.kt](../common/src/main/java/com/shilapi/xcertplay/ClusterMapPresentation.kt)、[CarPlayHostActivity.kt](../common/src/main/java/com/shilapi/xcertplay/CarPlayHostActivity.kt)。

## 4. HUD 导航属性

### 4.1 Android Car 接口

从 `NaviService` 确认的调用链为：

```text
ClusterHudManger → VehicleSendQueue9 → BaseCarManager → CarPropertyManager
```

DiPlay 使用 `android.car.Car` 获取 `property` manager，并直接调用对应属性。需要 `android.car.permission.CAR_VENDOR_EXTENSION`；Manifest 声明可选库 `android.car`，`required=false`。

匹配系统签名是当前授权方案，但最终权限是否授予、属性是否可写仍取决于车机服务和固件。本文不定义底层 CAN 帧 ID、波特率、校验或总线映射。

| 十进制属性 ID | 类型 | area ID | 含义和取值 |
| --- | --- | --- | --- |
| 557853441 | `int` | 0 | 导航状态：**0 开始/有效，1 停止** |
| 557853245 | `int` | 0 | 转向图标编号，见下表；停止时写 0 |
| 557853246 | `int` | 0 | 距离，原始值以米计；停止时写 0 |
| 557853247 | `int` | 0 | 道路进度条百分比；DiPlay 当前写 0 |
| 560997119 | `byte[]` | 0 | `BCM_TRANS_INFO_SEND`，用于发送道路文本包 |

整数接口：`setIntProperty(propertyId, 0, value)`。
文本接口：`setProperty(byte[].class, 560997119, 0, payload)`。

DiPlay 当前有效导航写入顺序：图标 → 距离 → 进度条 → 非空有效道路文本 → 导航状态 0。距离限幅为 0～2,000,000 米，进度条限幅为 0～100；这些是 DiPlay 的实现边界，不是已确认的硬件上限。

### 4.2 图标转换

DiPlay 先将 Apple 导航动作转换为项目已有的 AMap 图标表示，再映射为 HUD 数值。

| AMap 图标 ID | HUD 值 | 含义 |
| --- | --- | --- |
| 2 | 7 | 左转 |
| 3 | 3 | 右转 |
| 4 | 8 | 左前方/轻微左转 |
| 5 | 2 | 右前方/轻微右转 |
| 6 | 6 | 左后方/急左转 |
| 7 | 4 | 右后方/急右转 |
| 8 | 5 | 左侧掉头 |
| 9 | 1 | 直行 |
| 11 | 87～96 | 进入环岛，出口 1～10：`87 + exit - 1` |
| 12 | 97～106 | 离开环岛，出口 1～10：`97 + exit - 1` |
| 15 | 24 | 目的地 |
| 17 | 80 | 保留 APK 映射；图形语义待确认 |
| 18 | 81 | 保留 APK 映射；图形语义待确认 |
| 19 | 82 | 保留 APK 映射；图形语义待确认 |
| 其他 | 0 | 当前实现不输出该动作，清理自己此前显示的 HUD 状态 |

环岛出口在当前实现中限幅为 1～10。此表是已实现子集，不代表 HUD 的全部图标取值。

### 4.3 道路文本包

```text
03 18 07 LL [GBK 文本字节]
```

| 偏移 | 长度 | 内容 |
| --- | --- | --- |
| 0 | 1 | 固定 `0x03` |
| 1 | 1 | 固定 `0x18`，十进制 24 |
| 2 | 1 | 固定 `0x07` |
| 3 | 1 | 后续文本的字节长度，按无符号字节理解 |
| 4 | LL | GBK 道路文本，无额外结束符 |

发送前去除空白字符，长度按编码后字节数计算，不按字符数计算。例如道路 `A路` 编码为 `41 C2 B7`，完整包为：

```text
03 18 07 03 41 C2 B7
```

当前 DiPlay 只发送 1～251 字节的文本。空文本或超过 251 字节返回空结果，跳过文本写入，不截断。这一限制是当前代码选择；跳过写入可能保留此前道路文字，清空文本的可靠方式待实车验证。

APK 采用逐字符编码，DiPlay 使用整串 GBK 编码；常用中英文路径已有离线测试，特殊字符兼容性仍需核验。

### 4.4 导航消息和清理

- 输入消息：iAP2 `0x5201` 路线状态、`0x5202` 动作信息。
- 沿用项目 `BydHudRouteState` 解码，属于复用解析器，不表示输出到比亚迪协议。
- 导航状态过期阈值为解析器的 30 秒；每秒检查一次，并尝试更新当前有效状态。
- 成功发送的相同内容不重复写入；写入失败会失效整个成功缓存，可在下一次检查重试（包括回到先前成功值的情形）。
- 会话结束时，仅在记录为自身可能已输出（包括部分写入失败）的情况下写停止状态，并清零图标、距离和进度条，断开 Car 服务连接。
- `ownsHud` 只是 DiPlay 内部标志，不是与高德、NaviService 或原生 CarPlay 协商的全局所有权锁。
- 当前没有多导航源仲裁；强制结束进程也不能保证执行清理。

实现：[ArcfoxHudSync.kt](../shared/src/main/java/com/shilapi/xcertplay/hud/ArcfoxHudSync.kt)。

## 5. 仪表音乐协议

### 5.1 当前使用的 ARCFOX Link 路径

这是用户提供的参考修改版接口，不能假定所有出厂版 ARCFOX Link 都具备相同接收器。

```text
Action:    com.carlinx.arcfox.carlink.action.CARPLAY_MUSIC
Component: com.carlinx.arcfox.carlink/.CarLifeMusicReceiver
```

| Intent extra | Android 类型 | 含义 |
| --- | --- | --- |
| `title` | String | 歌曲名 |
| `artist` | String | 歌手 |
| `album` | String | 专辑 |
| `duration` | Long | 总时长，毫秒 |
| `progress` | Int | 当前播放位置，毫秒 |
| `playing` | Boolean | 播放状态 |
| `cover` | byte[]，可选 | 与当前封面 transfer ID 匹配的图片原始字节，最大 512 KiB |

参考接收器将图片写入 `/mnt/ota/media/`，生成文件名，再经 `HMILinkManager.onMusicInfoReceived("carplay", MusicInfo)` 和监听器送到 `MIS_ArcfoxLinkMusic`。

仪表服务下游通过 HIDL `MSG_MEDIA_ID3INFO=114` 输出 JSON，命令为 `{"id":"meidainfo","sub":"id3info"}`。ARCFOX Link 来源的 `mediatype=19`；内容包括 `songname`、`singername`、`totalplaytime`、`curplaytime`、`status`、`mute`、`albumpic`、`singlelyric`、`lyric`。下游时长转为秒，封面采用文件名。

DiPlay 当前不主动发送歌词；封面超过上限或 transfer ID 不匹配时跳过。仅播放位置改变时不重复发送广播，因此 `progress` 随歌曲、状态或封面更新发送，不是持续的进度刷新通道。会话释放时发送空歌曲及停止状态。

### 5.2 仪表服务原生 CarPlay 音乐入口

`MIS_CarPlayMusic` 还接收 SOA/ADSBus Bundle。已分析入口为服务 `AAOP_MeterInfoService`、子服务 `SN_METER_INFO_CARPLAY_MUSIC`、消息 `MSG_SET_METER_INFO_CARPLAY_MUSIC`。

字符串字段包括 `deviceID`、`MediaItemTitle`、`MediaItemArtist`、`MediaItemAlbumTitle`、`MediaItemGenre`、`MediaItemComposer`、`PlaybackAppName`、`albumpic`。整数字段包括 `PlaybackStatus`、`MediaItemPlaybackDurationInMilliseconds`、`PlaybackElapsedTimeInMilliseconds`、`PlaybackRepeatMode`、`PlaybackShuffleMode`。

该入口下游也是消息 114，`mediatype=7`，但是否进入显示栈受原生 CarPlay 连接状态及 `PlaybackStatus==1` 等条件影响，文本映射也存在播放 App 的特殊分支。因此，获得系统权限并不等于 DiPlay 可以直接使用该入口。当前 DiPlay 没有实现直接 ADSBus 音乐发送，也没有伪造原生 CarPlay 连接状态。

实现：[ArcfoxMusicSync.kt](../common/src/main/java/com/shilapi/xcertplay/ArcfoxMusicSync.kt)、[CarPlayMediaKeys.kt](../common/src/main/java/com/shilapi/xcertplay/CarPlayMediaKeys.kt)。

## 6. 仪表电话广播

```text
Action:  mfiservice.communication.call.state.update
Package: com.adayo.aaop_meterinfoservice
Extra:   CallState，类型 String，内容为 JSON 对象
```

接收方为 `MIS_CarPlayPhone` 动态注册的广播接收器。当前 DiPlay JSON 字段：

| 字段 | 类型 | 当前发送内容 |
| --- | --- | --- |
| `CallUUID` | String | `diplay-current-call` |
| `DisplayName` | String | 原版通话解析器给出的姓名或号码 |
| `Status` | Number | 0 结束；1 拨号；2 来电；4 通话中 |
| `Direction` | Number | 来电 1；拨号和 ACTIVE 2；清理 0 |
| `StartTimestamp` | Number | 接通时间的 Unix 秒值，没有则 0 |
| `AddressBookID`、`ConferenceGroup`、`Label`、`RemoteID` | String | 空字符串 |
| `DisconnectReason`、`IsConferenced`、`Service`、`deviceID` | Number | 0 |

该表描述当前适配行为，不是完整原车枚举。原车解析器还具有保持等状态分支；DiPlay 当前复用原版简化通话卡片，只转发代表性通话。多通电话不保留各自 UUID；接通后的 `Direction` 当前统一为 2，没有保留来电方向，这些属于待完善限制。

此广播承担显示同步，不承担通话音频、蓝牙电话栈或接听/挂断控制。电话实际出现于仪表还是同时出现于 HUD，仍取决于原车服务，不能仅根据广播发送成功确认。

实现：[ArcfoxPhoneSync.kt](../shared/src/main/java/com/shilapi/xcertplay/hud/ArcfoxPhoneSync.kt)、[BydCarPlayCall.kt](../shared/src/main/java/com/shilapi/xcertplay/hud/BydCarPlayCall.kt)。

## 7. 原生导航转发与语音按键

原生 CarPlay 导航广播包括 `mfiservice.route.guidance.state.update` 和 `mfiservice.maneuver.information.state.update`。已分析的 NaviService 路径存在原生 PhoneLink 连接和导航状态判断，并输出仪表 HIDL 消息 93；不能据此认定它会自动驱动 HUD。当前 DiPlay HUD 使用第 4 节的车辆属性通道。

极狐语音长按广播：

```text
Action: adayo.keyEvent.onKeyLongPress.N6
Extra:  InputKey，Int，231 或 219
```

当前只在匹配平台的投影 Activity 启动时注册，停止时注销。触发 Siri 还要求投影页面拥有窗口焦点、没有当前通话、DiPlay 有可用会话。接收器不终止广播，原车接收器仍可能同时响应；这不是独占按键或完整原生 CarPlay 仲裁协议。

原版可选无障碍方向盘按键服务是另一条路径，仍按原版设置运行。

实现：[ArcfoxHardKeys.kt](../shared/src/main/java/com/shilapi/xcertplay/orchestration/ArcfoxHardKeys.kt)。

## 8. 认证与权限边界

Android 系统签名用于车机权限，与 MFi 认证相互独立。本次适配没有修改 MFi、配对、认证初始化、原版协议解析或认证资源构建配置。

源码包含认证实现，默认不包含运行认证文件。独立本地认证构建仍按 [BUILD.md](BUILD.md) 使用外部 `DIPLAY_AUTH_ASSETS_DIR`，提供 `offline-mfi/identity.pk8`、`offline-mfi/certificate.p7b`。协议文档和源码提交不保存认证私钥或系统签名密钥。

原车自带 CarPlay 不代表可导出认证私钥，也不代表 DiPlay 已能使用其认证芯片或厂商服务；目前没有确认原车认证复用接口。

## 9. 验证记录与后续实车检查

离线自动检查覆盖：

- `ArcfoxClusterDisplayTest`：平台、显示名称、尺寸与视频配置。
- `ArcfoxClusterAutoSelectionTest`：默认开启、显式关闭和不匹配屏幕。
- `ArcfoxHudSyncTest`：动作映射、距离及 GBK 文本包。
- `ArcfoxMusicSyncTest`：广播字段、清理、封面上限和非目标平台。
- `ArcfoxPhoneSyncTest`：广播 JSON、状态转换、清理和非目标平台。
- `ArcfoxHardKeysTest`：长按匹配、注销和不阻断其他广播接收器。

这些测试没有覆盖实际 VHAL 写入、原车服务的显示栈仲裁、仪表实体画面或 HUD 投影效果。

后续实车检查应包括：仪表地图连接/导航/断开周期，原车地图和歌词模式切换；HUD 左右转、环岛、距离、道路中文与结束清理；与高德和原生 CarPlay 共存；音乐接收器可用性与封面；来电、拨号、通话保持、多通电话及清理；语音前后台及原车助手是否重复响应；进程异常退出后是否残留。

需要新增协议结论时，应同时记录来源 APK 的版本或 SHA-256、具体类/方法、采集证据和验证状态。不要把“接口发送成功”写成“实车显示验证通过”。

### 2026-10-09：HUD 故障定位与车标位置

车标默认目标改为 (960, 540)，即 1920×720 屏幕的 (50%, 75%)；原版水平和垂直位置设置已传入极狐流配置。此参数通过安全区提示 iPhone 绘制位置，具体地图 App 的遵循情况仍需实车核验；修改后重新连接生效。

已用回归测试复现并修复两种写入缓存问题：先成功 A、随后部分写入 B 失败，再请求 A 时被旧缓存错误跳过；首次有效写入部分失败后，清理因缺少所有权标志被跳过。修复只涉及极狐 HUD 输出状态，不修改原版导航解析行为。

诊断报告新增 `Arcfox HUD diagnostics`：记录路线/动作消息计数与最后接收时间、隐藏原因、最近 64 条状态转换/输出事件、失败属性编号或连接阶段、异常类型。不记录道路文本或电话信息。`route_stale_30s`、`empty_list_3s`、`missing_maneuver_details`、`unsupported_apple_type` 可用于区分输入状态导致的隐藏；`write_failed` 可用于定位车辆属性接口故障。

这些回归证明的是代码缺陷，不证明它们就是用户本次实车消失的原因。实际根因需与同次故障诊断报告和时间对应。已增加每秒一次的只读属性回读，比较状态、图标和距离，记录 `readback=match/mismatch/unavailable`；回读不自动重新抢占输出。`mismatch` 表明读到的值与请求不一致，仍不能单独确定是其他来源覆盖、异步生效还是车机属性缓存问题；没有原车来源仲裁记录。`write_ok` 和回读一致也不直接证明 HUD 实体画面显示。

## 小窗口导航卡适配

极狐复用上游的“小窗口”备用布局：手动选择关闭或开启，选择“地图＋自定义导航卡”时联动原车固定窗口，透明度和日夜主题可独立设置。卡片仍绘制在 DiPlay 的仪表 Presentation 内，坐标按整个 1920×720 屏幕计算；预览也按同样坐标显示。它调用原车 menuwidget 显示/取消接口，不改变 DirectRenderingCluster。地图仍使用 CarPlay stream 111。

没有采集到极狐原车全屏/小窗状态的可靠接口，因此极狐隐藏比亚迪的自动检测选项，已有 AUTO 值按关闭处理。非极狐原有 OFF/ON/AUTO 行为保持。车标位置改用上游 1% 精度；极狐默认 (50%,75%)，旧偏移以该位置为基准迁移，显式保存的新百分比优先。小窗未设置车标时继承全屏位置，避免直接套用比亚迪右侧车标默认值。车标设置需重新连接，卡片设置沿用上游即时更新。

本地测试可验证坐标、设置迁移、渲染与构建；原车遮挡区域、导航数据到卡片及 HUD 的实际显示仍需车机验证。

### 高德 7.5 卡片位置参考

参考 APK SHA-256：`734986014d6717a4a11678da053e4128e102554ebea81b91e7820bb7f7339f50`。

参考用户指定的 `高德地图7.5_极狐适配_补必要包_关闭仪表3D车道线_DPI140_覆盖物1.0_车标0.85_platform_signed.apk`，读取 `assets/res/HmiRes/MultipleScreen.json`：大仪表 WindowId=1002，Surface=1920×720；巡航/导航 RenderConfig 均为 UiLeft=85、UiTop=130、UiWidth=720、UiHeight=-1、Dpi=140、MapOverlayScale=1.0。这些是 UI 容器字段，不能直接认定为内部 TBT 卡片矩形。Widget WindowId=1003，SurfaceWidth/Height=0、IsSurfaceVisiable=0、CreateSurfaceWhenInit=0，地图和 UI 均禁用。

DiPlay 小窗卡片改为窗口 1002 的 CrossViewType=3 矩形：CrossViewStartX=99、CrossViewStartY=206、CrossViewWidth=490、CrossViewHeight=382；巡航和导航配置相同。之前按 UI 容器原点 (85,130) 和横向卡片比例推算位置不准确，已废弃。内部基准为 X=18%、Y=55%、大小=40%，极狐专用坐标换算精确得到 (99,206,490,382)，不因百分比取整偏移。由于联动消息没有几何参数，渲染和预览均使用固定矩形，位置/大小控件在极狐隐藏。卡片内部包含转向、距离、道路和到达信息，所有绘制裁剪在同一矩形内，不在底部另画超出区域的信息条。已有显式保存的卡片设置保留，不覆盖用户值。地图车标仍为 (50%,75%)。

原车 NaviService 的 ComServiceBase 在地图运行状态 202（非精品 3D 放大图）时调用 notifyMenuwidget(true)，203/停止导航时调用 false；ClusterHelpertools.getComService_navi012 构造 Message.id=224，JSON 为 command.id=Navi、command.sub=menuwidget、content.status=1（显示）/2（取消）。已采集 naviconf.conf 的 ClusterType=TYPE_COMSERVICE，因此此车路径为 ClusterComServiceUtils → IFgeComService.fgeSendMessage2ComService → 原车下游。消息不含矩形字段；不能仅凭它证明透明区域与 CrossView 矩形一致。现有 APK 没有原车下游透明区域实现，DiPlay 已通过最小 HIDL 客户端接入此信号。卡片矩形适配和仪表端透明区域联动是两个独立事项。

### 原车 menuwidget 联动接入

ArcfoxClusterLink 在真实极狐仪表 Presentation 显示、Surface 有效、stream 111 活跃且“地图＋自定义导航卡”有有效导航指引时发送显示状态 1；卡片取消、流结束、地图隐藏、关闭仪表输出、退出或断线时发送状态 2。只释放自己曾请求的槽位，旧 Host 不得释放新 Host 的请求。失败保留待重试状态；包括显示部分失败后仍需要取消。成功显示每 5 秒重申一次以应对原车服务重启，失败/取消每秒重试，操作放在单线程后台，不在 UI 线程调用 HIDL。

传输协议严格按已提取 SDK 的 wire format：descriptor=android.hardware.fgecomservice@1.0::IFgeComService、instance=default、getService retry=false；同步 transact code=1、flags=0；HwBlob 长度 40，id 位于 offset 0（Int32=224），content 字符串位于 offset 8，respond 位于 offset 24（空字符串）。verifySuccess 确认 Binder 事务完成，随后释放 parcel 资源。没有注册回调、没有启动原车服务、没有替换原车 APK。日志和诊断报告记录事务成功/失败及重试次数，事务成功不能视为仪表像素确认。

由于原车消息没有矩形参数，极狐自定义卡片固定按 (99,206,490,382) 绘制，忽略旧保存的移动/缩放参数并隐藏相应控件（原偏好不删除）。全屏和小窗自定义卡片均使用此槽位；日夜和透明度仍可设置。其他车机保持原有布局与行为。现场透明区域是否恰好与 CrossView 一致、SELinux/隐藏 API 对平台签名应用的实际访问，需实车验证；本地已验证消息序列、失败恢复、wire format 和绘制裁剪。

## 极狐导航通道 11 的音频焦点适配（2026-10-09）

仅在 `Build.FINGERPRINT` 以 `AAOP_COCKPIT/` 开头、音频映射为导航且成功创建传统流 11 时启用。默认自动路由、其他车型、音乐/电话/Siri 的原有输出接口不变；流 11 创建失败并回退到自动路由时不启用。无需增加设置，原有全局焦点开关仍管理原有音乐/电话/Siri，极狐导航流 11 的配套焦点独立生效。

播放继续使用 `AudioTrack(11, ...)`。第一段非空 PCM 写入前申请 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，使用 AudioTrack 的实际属性（车机 Android 11 可读取），不把 stream 11 当作 usage 11，也不强制替换为 usage 18。记录实际 usage 与申请返回值供实车核对。焦点申请失败不阻断既有播放，连续 PCM 最多每 500 ms 重试一次。

焦点持续至音频队列和 AudioTrack 待播放缓冲均排空且最近 PCM 已过去 700 ms，随后释放；下一段播报重新申请。断开/关闭直接释放。焦点被抢占后不在同一播报期间反复抢回；检测到电话/Siri 播放提示时释放并抑制重新申请，静默结束后复位。释放会使旧焦点回调失效，避免旧回调终止下一段播报的焦点。

参考 CarLife 的旧版播放最终也调用 `AudioTrack(streamType, ...)`，导航焦点开关默认开启，导航请求 gain=3，结束后释放。该 APK 的播放 API 选择与焦点 API 选择独立，且其焦点优先级到属性的映射不能直接作为导航 stream/usage 映射照搬。高德本地配置及历史实车 dumpsys 另有旧 API、流 11 和导航焦点申请/释放的佐证。

本地测试覆盖车型/流门控、连续 PCM、缓冲尾部、下一段重新申请、请求失败限速、焦点丢失、电话/Siri 优先及重复关闭。尚未实车确认流 11 的实际属性、可听声音和混音效果；本修改补齐已确认的焦点流程缺口，不将故障根因视为已实车证实。不会调整车机流音量、静音或 DSP 策略。

## 2026-10-10：用户指定的仪表地图区域（早期方案，已被下文替代）

按用户指定使用 1200×600、水平居中、底部贴齐，物理坐标为 `(360,120)～(1560,720)`。这是一项用户选定的适配参数，不是从 APK 验证出的原车透明区域。

保留 1920×720 视频流和解码画布，向 iPhone 声明上述 viewArea，默认 safeArea 保留车标靠下位置，位于 `(360,360)～(1560,720)`，并关闭安全区域外的 UI 绘制。TextureView 在同一物理矩形裁剪，避免 iPhone 和显示端使用不同坐标；没有把整幅 1920×720 视频简单缩放到 1200×600。

按用户后续明确选择，默认车标仍为 `(960,540)`，即整个仪表的 (50%,75%)。用户明确保存的车标百分比继续保留并限定在显示区域内；safeArea 围绕车标缩小，默认高为 360，宽为 1200。背景地图显示区域仍高 600。原版百分比位置设置仍可使用，重新连接后生效。

导航卡片及原车 menuwidget 联动仍使用固定 `(99,206,490,382)`，不跟随地图显示区域移动。卡片与新地图区有重叠，本次没有额外缩小用户指定的地图区域避让卡片。是否足以完整显示路线仍需实车核验，地图应用也可能有自身布局限制。

## 2026-10-10：HUD 多导航来源仲裁方案（待实现）

状态：仅记录检查结果与修改方案，尚未实现、构建或安装本方案。用户本阶段要求只读检查车机；另按用户明确要求清空 logcat 后重新采集日志。此前安装的 DiPlay 不包含本方案。

### 实车证据

本轮完整日志：`arcfox-capture/2026-10-10/hud-source-switch-fresh.log`；筛选证据：`arcfox-capture/2026-10-10/hud-source-switch-evidence.log`。用户反馈三个来源均可显示 HUD，共存高德在切换中退出，CarPlay 导航期间 HUD 消失。

| 时间 | 记录 | 判断 |
| --- | --- | --- |
| 20:51:10 | 共存高德 ArcfoxVehicle 输出 HUD_STOP，车辆状态属性随后写入 1 | 共存来源也能关闭共享 HUD；日志本身不能证明退出是崩溃 |
| 20:51:24 | DiPlay write_ok active=true icon=1，车辆状态属性写入 0 | CarPlay HUD 指令成功到达车辆属性路径，不能单独证明实体显示 |
| 20:51:36 | NaviService 的 ClusterHudManger 输出停止导航，车辆状态属性写入 1 | 原生导航结束的停止指令能干扰其他来源 |
| 20:51:50 | NaviService 再次输出停止，车辆状态属性写入 1 | 停止指令并非只发送一次 |
| 20:51:54 | DiPlay route_stale_30s，随后 write_ok active=false；前后仍有 AirPlay feedback | DiPlay 因导航更新超时主动清空，不能将此等同于连接断开 |

车机当前共存包为 `com.autonavi.amaparcfox`，版本代码 872。只读复制的 APK 位于 `arcfox-capture/2026-10-10/amaparcfox-installed.apk`；其 classes26.dex 的 `local.arcfox.ArcfoxVehicle` 包含 updateHud、clearHud、setIntProperty/setProperty 调用，直接写车辆 HUD 属性，没有经过 NaviService 的统一输出入口。因此此前“只改 DiPlay 和 NaviService 即可”的设想不适用于当前共存版本。

### 修改范围

推荐统一 HUD 写入入口，导航 App 保留共存，不采用强制停止进程或要求远程结束所有 iPhone 地图导航的方式。

| APK / 包名 | 计划修改 |
| --- | --- |
| DiPlay / com.shihab.diplay | 将 CarPlay 导航数据、导航状态及来源状态交给 NaviService，停止直接写 HUD；修正极狐路径的 30 秒过期清空策略 |
| 共存高德 / com.autonavi.amaparcfox | 将导航数据和状态交给 NaviService，移除直接 HUD 更新及退出清零，保留其他适配 |
| NaviService / com.adayo.service.navi | 缓存三方状态、仲裁来源、统一发送 HUD；原生高德既有输入也必须经过仲裁，停止和清零不能绕过 |

原生高德 `com.autonavi.amapauto` 和 DirectRenderingCluster.apk 不修改。DiPlay 原有认证、非极狐导航输出及音频功能不在本方案修改范围内；本方案不处理仪表地图车标偏移，也不等同于完成仪表图层仲裁。

已只读确认 NaviService 位于 `/system/app/NaviService/NaviService.apk`，版本 V01.01.05 / versionCode=2，使用 android.uid.system / UID 1000。当前 APK 与先前提取文件 SHA-256 相同：`05636165975d7a00b0cb672b3183c502331abe6fafe4bbae5b35863caae9ee0b`。签名证书 SHA-256 与已有平台证书一致：`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。具备系统应用覆盖更新的包身份与签名条件，未实际安装验证；修改版须保留包名和共享 UID，版本代码不降低。

### 仲裁与切换规则

1. 同时导航时，主屏前台导航来源优先；仅浏览地图而未导航不取得 HUD 所有权。
2. 桌面、音乐、设置等非导航页面在前台时，保留最近取得所有权且仍在导航的来源。
3. 当前来源结束后，优先选择前台仍在导航的来源；否则选择最近使用且仍在导航的来源。全部结束才清空 HUD。来源状态不明时不得让旧来源无条件清空其他来源。
4. 非当前来源的数据更新、结束、退出和断开只更新自身缓存，不写车辆属性、不清空 HUD。
5. 切换后完整发送新来源的图标、距离、进度、道路和启停状态，不能依赖旧来源的去重缓存。可选道路字段的失败不应阻断基本转向输出。
6. 在最终发送队列处再次校验来源和切换代次，使已排队的旧更新、停止与清零失效；不得只在收到数据时做一次判断。
7. 前台判断覆盖嘟嘟桌面虚拟屏与分屏场景，不能仅依赖 DiPlay 的 onPause/onStop。多个导航窗口同时可见时的焦点判定需验证；电话弹窗、输入法等短暂遮挡不能造成来源反复切换。

NaviService 是唯一 HUD 写入者，不能采用多个 App 定时抢写属性的方式恢复显示。原生高德的周期状态刷新、导航结束以及其他直接触达 HUD 的代码路径必须一起核对，避免遗漏无条件停止指令。

### 数据接口与超时策略

新增接收接口的具体形式尚未定稿，优先使用可校验调用身份的显式 Binder 服务；如使用广播，应显式指定包/组件并限制发送权限。校验来源包与签名，不采信任意调用者自报的 source 字段。导航数据包含来源、会话代次、序号、导航状态、转向、距离、道路及更新时间，接收与仲裁串行处理。

区分导航数据变化、来源存活和明确结束。CarPlay 停车时没有新的导航字段，不应仅因 30 秒没有更新而清空；连接正常的 feedback 仅证明通道存活，不单独证明导航仍有效。应结合最后明确导航状态、连接/来源存活及结束事件保留指令，对真实断连、进程退出和服务重启设置有界清理与恢复流程。不要直接关闭所有超时，也不要为极狐修复改变其他车型共用的 BydHudRouteState 默认策略。

### 验证和交付顺序

先完成接口及状态机实现、本地来源仲裁和队列失效测试，并运行仓库 AGENTS.md 要求的完整 CI。共存高德与 NaviService 另核对反编译重打包兼容性、包身份、平台签名和更新版本。保留原 APK 与可回退更新，再进行用户授权的实车安装和测试。

实车验证：三方同时导航并反复切前台；后台来源结束；共存正常退出与异常退出；电话/桌面遮挡；停车超过 30 秒；当前来源结束后的回退；CarPlay 断连重连；NaviService 重启与车机重启。对照 owner 变化、来源/会话代次、车辆实际写入及 HUD 画面确认无混合、闪烁、旧清零和残留。仅有 write_ok 不能作为实体 HUD 测试通过的依据。

## 2026-10-10：极狐媒体与 Siri 音频适配

本轮用户确认导航音频已正常，音乐在自动通道 0 与媒体通道 3 均无声，Siri 能识别话语但未回答。只读音频采集位于 `arcfox-capture/2026-10-10/audio-music-siri.log` 与 `audio-after-volume.txt`。

音乐 AAC 解码、PCM 写入、AudioTrack 播放计数持续增长，没有对应写入错误；媒体键请求已获得 USAGE_MEDIA / stream 3 焦点。两次 dumpsys audio 均显示 STREAM_MUSIC Muted=true，用户调音量后仍然如此。音量事件历史记录共存高德多次向 STREAM_MUSIC_BT 发送 ADJUST_MUTE；这支持共享媒体静音干扰的判断，但不单独证明所有无声均由该调用造成。

DiPlay 在极狐、媒体选择 0 或 3、音乐开始播放且自己的媒体焦点已获授时，尝试一次 STREAM_MUSIC 的 ADJUST_UNMUTE。不会调用 setStreamVolume，不提高已保存音量；不周期性解除静音，播放持续期间的用户静音保留，下一次播放开始可以再次尝试恢复。其他车型、其他媒体通道与焦点未获授时不操作流静音。若其他 App 后续再次静音媒体，本适配不会持续与其抢写，仍需处理来源间静音行为。

极狐 Siri/ASSISTANT 下行使用传统 AudioTrack stream 17（车机 STREAM_VR），保留标准 usage 回退。申请实际 AudioTrack 属性对应的 AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK，语音 renderer 关闭后释放；极狐语音所需焦点不依赖原全局音频焦点开关。全局焦点开启且音乐轨存在时，语音期间选择语音焦点，结束后恢复原音乐请求。其他车型语音及电话路由不变，导航流 11 的既有适配保持。

本轮捕获的 speechrecognition Opus 下行包均被原版短包门槛过滤，inputQueuedTotal=0、outputBuffersTotal=0、totalWrittenFrames=0；用户确认能识别但没有回答。没有据此修改 Opus 协议或短包过滤，也不能把“未收到可播放回答”归因于 stream 17。语音路由适配补齐已知车机路径，不证明 iPhone 会产生回答；还需核验 Siri 回答时实际下行 PCM 与实体可听声音。

本项修改仅限 DiPlay，HUD 仲裁方案仍未实现。新 APK 需重新连接 CarPlay 后验证媒体恢复、Siri 实际回答、导航正常播报，以及播放期间用户静音、焦点丢失和断开重连。


## 2026-10-10：恢复完整仪表画布与三种地图状态的实测

按用户最新要求保留 1920×720 地图流、解码和绘制画布，撤掉 TextureView 的 1200×600 裁剪。viewArea 恢复整个视频画布，允许在 safeArea 外绘制地图。原方案的透明外侧会露出下层原生地图，这是本次撤销裁剪的直接原因。

1200×600 的区域 `(360,120)～(1560,720)` 只作为无障碍布局范围使用，不再充当窗口裁剪。默认位置百分比由 (50%,75%) 上移为 (50%,60%)；对应安全区域中心为 `(960,432)`，默认 safeArea 为 `(360,144)～(1560,720)`，即 1200×576。保留用户明确保存的位置，原位置设置仍需重新连接后生效。这里的“中心”是协议安全区域的几何中心，不能保证各 iPhone 地图 App 将车标画在该点。

改动前只读截图保存在 `arcfox-capture/2026-10-10/cluster-carplay-{idle,navigation,overview}-before.png`。地图状态车标约 `(960,630)`；导航状态约 `(1174,568)`；全览起点车标约 `(830,439)`、终点约 `(1518,540)`，路线整体偏右。三种状态的地图相机锚点不同，不能把安全区域中心直接当作车标位置，也不能用同一画面平移量宣称修复所有状态。全览应检查完整路线和路线布局，不要求起点车标居中。

本次本地修改修正透明露底与完整画布声明、上移默认安全区域。导航水平偏移和全览路线布局是否随完整 viewArea 改善仍需重新连接后实测，尚未证实彻底解决。未添加按截图猜测的固定平移、未缩放整个画面、未修改原车 APK、HUD 仲裁或主屏布局。


照片补充确认左右车辆/音乐信息、顶部速度和底部状态条会遮挡地图；因此全屏地图背景不代表全屏可读布局。重新核对参考高德 7.5：LargeScreenSurface 与 MapWidth/MapHeight 都是 1920×720，CarpositionHoriz=0.5、Carposition=0.70；PreviewScreenLeft/Right=450、Top/Bottom=80，数学上的全览布局范围为 `(450,80)～(1470,640)`，即 1020×560。摘录保存在 `arcfox-capture/2026-10-10/amap75-cluster-layout-reference.json`。这些是高德自身地图引擎的配置，不是 CarPlay 参数，也不能证明照片上所有原车显示模式的精确无遮挡矩形。

推荐保持“完整背景 + 中央安全布局”的分离：完整 1920×720 地图遮住原生地图，车标与全览路线单独避让仪表信息。当前按用户此前选择使用 1200×600 的最大布局范围；如果仍被实际信息遮挡，应按原车布局缩小安全区域，不能把视频裁成透明窗口。CarPlay 中的安全区域和高德本地 Carposition/PreviewScreen 并非同一接口，是否被 iPhone 高德在各状态采用必须实测。


本轮验证：AGENTS.md 完整 CI 通过（shared 1233 项，跳过 1；common 1035 项，0 失败；home 无测试任务源码；mobile/home/maphost lint 与 assembleDebug 通过）。assembleRelease 与 verifyStandaloneAuthentication 通过。交付 `build/arcfox-release/DiPlay-0.2.15-Arcfox-Audio-FullCluster-system.apk`，SHA-256 `bcfe843dbe1cdef1f907ce57809a1cebf9edc39e92d690aada0b812e7d55c446`；签名 SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。认证资源与官方 0.2.15 字节一致。未安装本 APK，未改动车机内容；音乐/Siri 可听性与地图各模式的最终位置均未实车验收。


2026-10-10 21:43:44 安装记录：用户授权覆盖安装后，共享 ADB 5041 连接发生阻塞；取消本任务等待中的客户端，保留其他安装任务。独立本机 ADB 5042 连接验证正常，使用 install --no-streaming -r 成功，16.5 MiB APK 传输耗时 1.533 秒。包管理确认 DiPlay 0.2.15 / versionCode 34、lastUpdateTime=21:43:44，应用数据保留。安装后的临时 APK 清理提示文件已不存在，安装命令返回 Success；实车地图和音频验证尚未开始。

## 2026-10-10：媒体无声实车定位与底层静音恢复

在原车音乐和 CarPlay 音乐同时无声时，对照车机 AudioManager 与真实 IAudioService：`AudioManager.isStreamMute(3)=false`，但 `IAudioService.isStreamMute(3)=true`。本 ROM 的公共 API 查询的是 MCU 主静音，不能判断 Android 软件媒体流是否静音；公共媒体音量也与服务端媒体流数值不同。因此只检查公共 API 的旧恢复逻辑未执行 ADJUST_UNMUTE。

只对服务端媒体流 3 调用 `adjustStreamVolume(3,100,0,"com.android.shell")`，随后服务端流 3 及别名流 15 均为未静音，用户确认原车音乐和 CarPlay 音乐均恢复。100 是 ADJUST_UNMUTE；未调用 setStreamVolume，未改导航 11 或语音 17。诊断临时 dex 已从车机删除。此结果证明此次音乐无声的直接原因是软件媒体静音，并非音乐 PCM 解码失败。

DiPlay 恢复逻辑改为优先反射 AudioManager.getService()，读取真实媒体静音；使用实车确认的四参数 IAudioService.adjustStreamVolume 方法解除流 3 静音。限定极狐、音乐通道 0/3、自身媒体焦点已获授，以及播放开始/媒体会话激活时的一次处理；保留公共 API 回退与错误日志，不设音量、不周期性抢写。其他车型、导航通道和原认证不变。该代码尚待完整 CI、构建与实车应用内验证，诊断调用成功不能替代 APK 内验证。

### 同画布的导航来源对照

已安装版本的 /info 声明完整 1920×720 viewArea、safeArea `(360,144)～(1560,720)`；解码实际输出 1920×720，没有额外 Arcfox TextureView 变换。高德导航截图车标约 `(1174,568)`；换用苹果地图导航后蓝色位置箭头约 `(895,461)`，截图已保存在本日期采集目录。苹果地图中国底图的“高德地图”标注不能当作当前导航应用名称；主屏截图与 iAP2 0x5201 参数 19 的 `Apple地图` 验证了导航来源。

两种应用的车标位置不同，且停车时实际定位可能离开路线相机中心，单张车标截图不足以确定固定相机偏移。不能将全部地图统一向左平移 214 像素，也不能把协议安全区几何中心当作必然的车标位置。接下来核验 iPhone 高德重新启动后的布局，区分旧布局缓存与应用自身的安全区域处理。

本轮完整 CI 已通过：shared 1233 项（跳过 1）、common 1038 项，无失败；mobile/home/maphost lint 与 assembleDebug 均通过。release 构建及 verifyStandaloneAuthentication 通过，认证资源与官方 0.2.15 字节一致。新包为 `build/arcfox-release/DiPlay-0.2.15-Arcfox-MediaService-FullCluster-system.apk`，SHA-256 `4582ef4671b010bb8fc941fa9f11726016cac39381a26c710cf671eeaca18b0b`，签名继续使用上述系统证书。保留前一版 APK 供回退。

用户随后确认车机暂时不能连接，已停止 ADB 尝试；新包未安装。地图映射未在这轮追加固定平移，导航车标居中和全览布局仍未解决；等待车机恢复连接后继续核验高德重启、导航跟随状态及全览。音乐的底层恢复调用已在车机验证有效，应用内自动恢复与 Siri 回答仍需实车验收。


### 2026-10-10：DiPlay 自动 HUD 与周期保活

根据当前要求，本轮仅修改 DiPlay，采用直接写车辆属性的周期恢复策略；上面的三方统一仲裁方案作为未来方案保留，尚未实现。

- 不增加 HUD 开关，不使用比亚迪 HUD 设置控制极狐。最后明确导航状态有效、当前 CarPlay 视频会话和对应 iAP2 控制通道存活时，自动输出 HUD。
- 导航图标、距离或道路变化立即发送；相同导航数据每 5 秒重新发送完整属性组（ICON、DISTANCE、BAR、可编码的 ROAD_TEXT、最后 STATUS=0）。被其他来源清除后，在下一次成功重发时恢复。1 秒检查计时，不重复发送结束清零。
- 极狐连接存活时，停车导致导航字段超过 30 秒不更新，不再仅因此隐藏 HUD。没有导航不会凭连接状态生成导航；明确 NoRouteSet/Arrived、空指令列表持续 3 秒、当前连接失效会停止输出。iAP2 通道交接允许 5 秒间隙。其他车型保留原有 30 秒超时。
- 控制器会话代次用于拒绝旧帧、旧清理任务及旧控制器结束事件，防止重连后误清新导航。结束时清理最多重试 10 秒；当前车辆整数属性不匹配则跳过清理，读取失败不盲目清零。属性相等只是尽力核对，不是车机提供的来源所有权标记。
- 保活不检查前台、不仲裁来源，也不会因被其他来源覆盖而永久停止。因此多来源同时导航仍可能互相覆盖；其他来源持续清零时也可能短暂消失。这次实现解决定期恢复，不能保证多来源无冲突。

本轮不修改音频路由、仪表地图几何、MFI 认证流程或原车 APK。车机目前不可连接，实体 HUD 恢复和多来源竞争效果仍需实车验证。

验证：HUD 生命周期专项 14 项测试通过；AGENTS.md 要求的完整单元测试、三个应用 lintDebug 与 assembleDebug 全部通过。公开源码凭据检查与 git diff --check 通过。以上为本地验证，不代表实体 HUD 已完成实车测试。


### 2026-10-11：固定仪表安全区域与原车方向盘缩放

本项覆盖此前围绕预期车标位置计算/缩小安全区域的极狐策略。极狐仍使用既有的 HDMI 仪表 Presentation；不增加 DiLink 4 的 ADB task 启动流程，不改变其他车型。

完整视频流、解码与窗口保持 1920×720、100% 比例，viewArea 不另设裁剪矩形；safeArea 固定为 left=500、right=500、top=80、bottom=30，即 `(500,80)～(1420,690)`、920×610，几何中心 `(960,385)`。safeAreaDrawOutside=true，地图背景仍铺满仪表。旧车标百分比/步进设置不再改变极狐安全矩形；该模式不保证各 iPhone 地图应用将实际车标放在安全区域中心。参数在下一次 CarPlay 连接协商生效。

方向盘输入依据本地 `BYD872_ARCFOX_STEERING_ZOOM_IMPLEMENTATION.md`、ArcfoxComm.java 及 2026-10-10 实车记录：被动订阅 `android.hardware.fgecomservice@1.0::IFgeComService`，注册事务 4、服务 ID 14；回调描述符 `IFgeComServiceCallback`、单向事务 1，MessageCallback 为 40 字节，content 字符串位于偏移 8、respond 位于偏移 24。当前已分析固件向多个订阅者分发；不替换原车回调、不调用该固件为空操作的 unregister、不向原车发送控制消息或 10122 广播。

- 仅解析 command.id=Navi（忽略大小写）、sub=guidecontrol；zoom=up 调用 zoomDashboardMap(true)，down 调用 false。
- CarPlay 现有 changeMapZoomLevel 命令指向仪表 ALT_UUID，zoomDirection=0/1。不放大解码视频，不触及主屏地图。
- 高德的绝对 zoomlevel 与 zoomDefault=17 不能映射到已验证的 CarPlay 接口，故忽略绝对级别和默认请求，不复制高德相机 maintain 逻辑。
- 只有显示中的物理仪表地图具有缩放资格；转向卡片、暂停、隐藏、断连与被替换的会话不能执行旧事件。原车仍可收到并响应相同输入，本项不实现多导航来源的前台仲裁。
- 每进程复用一个回调；仪表显示时连接并每 30 秒 ping，服务故障后再次连接注册。通用 Android 没有 HIDL 服务时记录不可用，不阻塞 CarPlay。宿主释放解绑目标，晚到/排队事件按宿主代次及 stream route 复核。

实现文件：ArcfoxWheelZoom.kt（解析、目标代次与生命周期）、ArcfoxWheelHidl.java（最小被动 HIDL 客户端）、CarPlayHostActivity.kt（物理仪表绑定）。tools/hidl-stubs 只提供 android.os 隐藏接口的编译签名，通过 compileOnly 使用，不能打入 APK；运行时使用车机框架实现。源码构建不依赖本地反编译目录或额外桥接 APK。协议根据已提取的固件接口核对，其他固件的回调分发和权限需验证。

专项测试验证了方向解析、字符串布尔值、无效/绝对/默认指令、无地图时拒绝、连续按键、旧宿主释放、排队事件及路由更换，以及固定安全矩形和完整画布。车机目前不可连接；这些测试不证明实车 HIDL 注册、iPhone 地图缩放响应或新安全区域布局已经验证。

本次验证：18 项极狐专项测试通过（方向盘 8、安全区域/卡片 6、屏幕选择/完整覆盖 4）；完整 AGENTS.md CI 的测试、三个应用 Lint 和 debug 构建全部通过。额外 HIDL 模拟验证了注册事务 4/服务 14、ping、避免重复注册、40 字节 UTF-8 字符串偏移和单向事务标志；未运行真实车机服务。


### 2026-10-11：上游 0.2.17 合并预检（未合并）

已 fetch origin/upstream；upstream/main 为 `4314bbe133599b21f40d23d3bf3de3f0d45353b4`（v0.2.17），当前极狐版本此前合入的上游基线为 `ef4e3fe5`（0.2.15）。仅在 /private/tmp 的独立 Git 仓库中，通过只读 objects alternates 与当前工作区源码快照运行 merge-tree；实际工作区、index 和 main 提交未进行合并。

包含现有未提交音频/HUD/地图修复及本次安全区域和方向盘改动的模拟合并产生三个文件、四处文本冲突：

- CarPlayHostActivity.kt：连接/断开回调中，极狐卡片链路挂起/恢复与上游 BYD 电话弹窗 guard 处理占同一位置。合并时应保留双方各自的车型条件和生命周期处理。
- CarPlayMediaKeys.kt：NowPlaying 更新中的 ArcfoxMusicSync.update 与上游 updateInitialAlbumRecovery 占同一位置，应保留两者及上游新的元数据快照读取方式。
- CarPlayController.kt：断开回调中的极狐 arcfoxOwner 清理参数与上游 initialArtworkReplay 缓存清理，应保留缓存清理及带 owner 参数的 endNow 调用。

ArcfoxClusterDisplay 固定安全区域、ArcfoxWheelZoom/Hidl、ArcfoxHudSync、本次 compileOnly HIDL stubs 无文本冲突。上游 MFI 核心目录、CarPlayClusterDisplay 和 DiLink4ClusterDisplay 没有该区间修改，但 iAP2 identification 重试、USB/NCM、Wi-Fi 扫描暂停及 app-audio 麦克风流程有更新。不能把无文本冲突视为实车兼容证明；解决冲突后必须在合并版本重跑完整 CI。新官方自动更新入口指向上游 release，系统签名适配版与官方包的签名不同，应核对该入口在 fork 中的行为。

结论：可以合入，经上述冲突处理后有明确保留双方逻辑的路径；不能直接无冲突合并。此预检未合并、未声称合并版本构建通过。

### 2026-10-11：实际合并 0.2.17 与极狐适配层

工作分支 `codex/arcfox-0.2.17-adapter`。先用 `9eb08fd1` 保存当前适配，随后以 `192a7a3f` 合入官方 tag `v0.2.17`（`4314bbe133599b21f40d23d3bf3de3f0d45353b4`）。上述“未合并”是此前预检时的状态，本节记录实际完成的合并。

冲突处理保留上游电话弹窗 guard、专辑封面恢复与断开后的 artwork replay 清理，以及极狐卡片生命周期和音乐同步。上游的连接、认证、USB/NCM、Wi-Fi 与麦克风更新一并保留；本次不另改官方 MFI 或 identification 实现。

极狐实现集中到两个同名包目录，继续编入原 DiPlay APK，不产生独立桥接 APK：

- `common/src/main/java/com/shilapi/xcertplay/vehicle/arcfox/`：仪表安全区域/卡片配置、原车卡片联动、音乐元数据/封面同步、媒体静音恢复、方向盘 HIDL 及缩放目标生命周期。
- `shared/src/main/java/com/shilapi/xcertplay/vehicle/arcfox/`：HUD 协议写入与保活、HUD 会话/控制通道代次、电话状态、语音长按、导航焦点请求/释放。
- `shared/.../vehicle/VehicleAdapters.kt`：车型识别、会话适配选择、电话及语音键入口。非 AAOP_COCKPIT 固件的会话适配为空实现。
- `shared/.../vehicle/VehicleAudioPolicy.kt`：音频策略入口。非极狐保留原语音通道；极狐语音通道 17、仅导航且实际选择通道 11 时启用极狐导航焦点。焦点请求仍使用实际 AudioTrack attributes，旧焦点回调继续通过代次拒绝。
- `common/.../vehicle/VehicleClusterAdapter.kt`：宿主会话开始/结束、物理仪表可见性和卡片可见性入口；极狐实现拥有自己的卡片挂起状态。
- `common/.../vehicle/VehicleMediaAdapter.kt`：NowPlaying、封面、结束、开始播放时的媒体恢复入口，Android MediaSession 的上游处理继续保留。

`CarPlayController` 只向适配器投递会话、控制通道与导航事件。极狐 HUD 不再通过 `BydNavigationOutputs` 的附加参数转发，该比亚迪类恢复为 0.2.17 的原有实现。`CarPlayHostActivity` 保留显示选择/配置调用，并向适配器通知实际地图/卡片可见性；`AndroidMediaSink` 保留 PCM 生命周期通知，不包含极狐焦点请求的具体协议实现。设置和持久化仍调用车型配置，未新增设置，也未改设置界面布局。

保持本轮前已经选择的行为：全屏仪表视频 1920×720，safeArea=500/500/80/30，920×610；导航有效时 HUD 每 5 秒保活；方向盘缩放只发送 CarPlay 地图相对缩放命令。重构没有追加车标平移或视频裁剪。

后续升级应合并上游 tag，再复核上述事件入口及默认车型行为，运行 AGENTS.md 完整 CI，使用对应官方发布包的认证资源并重新系统签名。这种封装减少核心文件中的协议和状态代码，但不能保证以后完全没有合并冲突。官方更新功能沿用上游地址和显式安装操作；官方 APK 使用不同签名、也不含此极狐适配，适配版应从本分支合并重建，不能依靠官方 APK 覆盖升级。

官方 0.2.17 APK 已按发布的 SHA256SUMS 校验；其离线认证资源与官方 0.2.15 一致。认证资源仅在临时目录参与 release 构建，不提交到源码。

验证：AGENTS.md 的完整 CI 通过（5 分 4 秒）；shared 1307、common 1113、home 4 项，共 2424 项，跳过 1 项，无失败。mobile/home/maphost 的 lintDebug 和 assembleDebug 全部通过。新增回归覆盖非极狐车型空适配/语音键不注册、极狐音频条件选择；断连菜单回归改为验证生命周期事件送达适配器。公开源码凭据检查与 diff --check 通过。实体车机目前未连接，以上结果不能代替实车仪表、HUD、方向盘和音频测试。

发布构建：`:mobile:verifyStandaloneAuthentication :mobile:assembleRelease` 通过（2 分 14 秒）。APK 为 `build/arcfox-release/DiPlay-0.2.17-Arcfox-Adapter-system.apk`，versionName=0.2.17、versionCode=36，SHA-256 `2f7bd394b8c16548440c33bffe84484080821dd9e725b6abcefcfdc222f9e873`。系统签名证书 SHA-256 仍为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，apksigner 验证通过。认证资源与经官方 SHA256SUMS 核验的 0.2.17 发布包字节一致；DEX 核验确认极狐适配/HIDL 回调类存在，四个 android.os 编译桩类定义未打入 APK。本轮没有安装、修改原车 APK 或推送远程。


### 2026-10-11：CarPlay 默认极狐品牌图标

从用户指定的本地 `ARCFOX_3.4.32_三功能_车钥匙综合优化_v1.3_登录007修复.apk` 提取 application-icon-640 对应 `res/Ryj.png`，原样保存为 `common/src/main/res/raw/ic_car_home_arcfox.png`（SHA-256 `4851b49030194dba9f53a73a29d23ee62ff74550c30465297702f9b0b0f01b77`）。蓝底白色极狐标志不重新绘制或裁剪。`VehicleBranding` 对 AAOP_COCKPIT 固件选择该资源；其他车型保持原图标，自定义图标优先。CarPlay 返回车机按钮和设置默认预览采用同一车型策略，旧宿主预览也在极狐车机显示该图标。图标随下一次 CarPlay 连接的 /info 发布；无需改变认证或主应用桌面图标。

本次完整 AGENTS.md CI 通过；系统签名 release 与 verifyStandaloneAuthentication 通过。新包 `build/arcfox-release/DiPlay-0.2.17-Arcfox-Icon-system.apk`，SHA-256 `74411f3f1c8d9fa0793b40f5d3a3b25633ed2e21f756e62da3a56904e9d49ba7`，签名证书保持前述系统证书，认证资源与官方 0.2.17 字节一致。资源表 raw/ic_car_home_arcfox 指向经构建工具缩短名称的 res/tp.png，实际打包图片与参考 APK 原图字节一致。极狐适配类与 HIDL 回调存在，四个编译桩类定义不在 APK 中。此次安装尝试报告 device offline，图标更新尚未安装；重新连接 CarPlay 后才能确认实体显示。


### 2026-10-11：统一车机版本与极狐 T 配置

首次引导的“DiLink 版本”改为“车机版本”，选项为 DiLink 3、DiLink 4、DiLink 5、极狐 T、不确定。`AAOP_COCKPIT/` 系统指纹自动默认选择极狐 T；无需先手动保存。镜像无法识别车型时可手动选择极狐 T，以查看配置和预览界面。

`shared/.../vehicle/VehicleProfile.kt` 提供共同的车型选择。显式选择优先于自动识别；“不确定”清除显式选择并恢复自动识别。读取旧 `diplay/setup_dilink_generation`，新选择写入 `diplay/vehicle_profile` 并同步旧键，保留已有比亚迪选择。首次引导保留原 DiLink 系统属性识别规则。

极狐 T 选择统一控制仪表流、安全区域、卡片配置、HUD 会话适配、音乐/电话/语音输出、方向盘适配和极狐返回图标。原车服务调用仍需真实极狐固件；模拟器选择车型不会获得这些硬件服务。切换车型标记下次连接生效，不主动断开正在运行的 CarPlay。音乐/导航通道、缓冲和焦点设置不因选择车型而重置；极狐固件的语音通道 17、导航实际通道 11 的焦点例外仍使用之前适配策略。

安全区域入口位于“设置 → 高级 → 仪表地图（实验性）”，选择极狐 T 后即显示，不要求已经连接 iPhone 或存在真实仪表显示。沿用原安全区域编辑框，极狐只使用编辑框内的本地预览，不启动比亚迪 ADB 仪表绿色边界预览。默认矩形 `(500,80)-(1420,690)`，920×610；左右各500，上80，下30。编辑后存于独立键 `xcertplay_airplay/arcfox_t_cluster_safe_area_1920x720`，不读取或覆盖 DiLink 4 的 `cluster_safe_area_1920x720`。重置只清除当前车型矩形，恢复默认区域。保存/重置在下次连接应用；正在连接时编辑框的“应用并重连”按钮明确执行重连。

仪表视频仍为完整1920×720，`viewArea=null`、`safeAreaDrawOutside=true`。保存矩形只改变发给 iPhone 的 `safeArea`，不裁剪整体视频、不从地图流推测车标坐标。极狐配置停用比亚迪 ADB/平台21输出路线，但保留其保存值供切回比亚迪使用。HUD 继续按有效导航自动显示与每5秒保活，没有额外显示开关。

新增回归验证覆盖自动默认、手动覆盖与恢复自动、旧车型选择迁移、极狐/BYD安全区域隔离、完整仪表分辨率和自定义安全区域、大字体窄屏/宽屏及阿拉伯语截图。完整 CI 和系统签名产物验证结果见本次构建记录。

本次完整 CI 已通过：shared 1309 项（1项跳过）、common 1117 项，0失败；home测试任务无测试源，任务成功。mobile/home/maphost的Lint和debug构建均通过。UI截图保存在 `common/build/reports/arcfox-profile-ui/`，包含600dp高密度、1280dp宽屏、1.5字体和阿拉伯语布局。
