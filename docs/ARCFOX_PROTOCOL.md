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
| 默认安全区 | 上 50%、下/左/右 0；默认车标目标为水平 50%、垂直 75% |
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

极狐复用上游的“小窗口”备用布局：手动选择关闭或开启，选择“地图＋自定义导航卡”时独立设置卡片位置、大小、透明度和日夜主题。卡片仍绘制在 DiPlay 的仪表 Presentation 内，坐标按整个 1920×720 屏幕计算；预览也按同样坐标显示。它不会调用原车小窗口切换接口，也不改变 DirectRenderingCluster。地图仍使用 CarPlay stream 111。

没有采集到极狐原车全屏/小窗状态的可靠接口，因此极狐隐藏比亚迪的自动检测选项，已有 AUTO 值按关闭处理。非极狐原有 OFF/ON/AUTO 行为保持。车标位置改用上游 1% 精度；极狐默认 (50%,75%)，旧偏移以该位置为基准迁移，显式保存的新百分比优先。小窗未设置车标时继承全屏位置，避免直接套用比亚迪右侧车标默认值。车标设置需重新连接，卡片设置沿用上游即时更新。

本地测试可验证坐标、设置迁移、渲染与构建；原车遮挡区域、导航数据到卡片及 HUD 的实际显示仍需车机验证。

### 高德 7.5 卡片位置参考

参考 APK SHA-256：`734986014d6717a4a11678da053e4128e102554ebea81b91e7820bb7f7339f50`。

参考用户指定的 `高德地图7.5_极狐适配_补必要包_关闭仪表3D车道线_DPI140_覆盖物1.0_车标0.85_platform_signed.apk`，读取 `assets/res/HmiRes/MultipleScreen.json`：大仪表 WindowId=1002，Surface=1920×720；巡航/导航 RenderConfig 均为 UiLeft=85、UiTop=130、UiWidth=720、UiHeight=-1、Dpi=140、MapOverlayScale=1.0。这些是 UI 容器字段，不能直接认定为内部 TBT 卡片矩形。Widget WindowId=1003，SurfaceWidth/Height=0、IsSurfaceVisiable=0、CreateSurfaceWhenInit=0，地图和 UI 均禁用。

DiPlay 以 (85,130) 的 UI 原点作为默认位置参考，保留自身卡片比例和默认 40% 大小（222×84 像素），换算到 1% 中心网格为 X=10%、Y=24%，实际左上角 (81,130)，横向误差 4 像素。仅在未保存小窗/全屏卡片位置时使用极狐默认值，已有用户位置优先。不改变此前要求的地图车标 (50%,75%)。高德 Carposition=0.70、CarIconScale=0.85 属于其地图引擎设置，未用于覆盖 CarPlay 设置。Dpi=140 是此 JSON 的配置值，不能单独证明运行时 Android View 密度。
