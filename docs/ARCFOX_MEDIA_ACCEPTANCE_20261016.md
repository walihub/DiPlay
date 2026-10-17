# 极狐媒体完善版验收记录

> 下述旧产物验收对应 MediaComplete 包；直连专用包的最终验收另列。
> 2026-10-16 后续变更：按用户要求删除 ARCFOX Link 回退及路由选择代码。媒体仅通过原车 HIDL 消息 114 直连；不可用时保留状态并按现有节流重试，不发送 Link 广播。下文回退描述仅为此前方案历史。

方案：[ARCFOX_MEDIA_COMPLETION_PLAN_20261016.md](ARCFOX_MEDIA_COMPLETION_PLAN_20261016.md)。协议依据：[ARCFOX_MEDIA_DIRECT_TRANSPORT_AUDIT_20261016.md](ARCFOX_MEDIA_DIRECT_TRANSPORT_AUDIT_20261016.md)。嘟嘟补丁明细：[README](../tools/arcfox-media/README.md)。

## 交付行为

DiPlay：正常音频模式下优先直连原车媒体消息 114，首次不可用时尝试旧 Link 接收器。一个连接会话固定成功通路，避免两路缓存写入竞争。播放进度节流、跳转即时同步、暂停冻结；封面引用缓存文件，写图失败不影响文字和进度。原车接管时停止发布，手机重新播放后恢复。来电／非正常音频模式挂起已选直连，不操纵电话或蓝牙状态。

嘟嘟：歌名、封面、状态、进度、控制对象统一采用同一个来源。原车源明确变化／新播放时让出 CarPlay，并以签名保护广播通知 DiPlay。首次源快照、服务重绑、metadata 和 progress 不抢源。手机新播放时可选择原车媒体插件 101（旧界面名仍是 QQ音乐）；手机会话退出且无原车接管播放时恢复此前播放器。

原来的车速、车门、胎压／胎温 classes4.dex 保留；本次不重新定义车辆属性或单位。

## 主 agent 离线核验

嘟嘟主 agent 复跑记录：`build/arcfox-media-root-verified.log`。包含 SourcePolicyTest（首次基线、重绑、IDLE、10000 次切源）、实际 MediaBridge 的假 Android 边界集成测试，以及原 SDK／manager 反射签名与 smali 寄存器字宽检查。

主 agent 独立 ZIP 比较：对 v2 仅改变 AndroidManifest.xml 和 classes.dex，仅新增 classes5.dex。其他 dex（包括车况 classes4）、资源及 assets 逐字节保持。签名验证通过，证书 SHA-256：`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。

嘟嘟产物：`build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v3_MediaConsistency_platform_signed.apk`，SHA-256 `4a28165964fc431f50015852d66fbd172ec4e600e58cf330b7426eff00eadea0`。

DiPlay 媒体专项 56 项回归通过，记录 `build/arcfox-media-focused.log`（1m16s）。AGENTS.md 要求的完整 CI 命令全部通过，记录 `build/arcfox-media-acceptance-final.log`（10m35s）：shared 1489 项、common 1344 项，共 2833 项，零失败／错误；home 测试任务无测试用例，三模块 lint 与 debug assemble 均通过。

Release assemble 与 lint 通过，记录 `build/arcfox-media-release.log`（2m36s）。DiPlay 产物：`build/arcfox-release/DiPlay-0.2.19-Arcfox-MediaComplete-system.apk`，SHA-256 `003dbc0c9b4161aa5b975a31d6a9b954a2516fa348aa042aa7d9b868ded694bd`。两个 APK 均验证为上述同一平台证书。构建脚本确认本地认证资产与此前版本逐字节一致，临时签名文件已清理；主 agent 复核最终产物哈希、嘟嘟签名及差异空白检查通过。

双向交接已加入真实桥接测试：原车 source/state 不变、手机 session 保留但已暂停时，DiPlay 的签名 YIELDED 通知能让嘟嘟恢复实际原车卡片。重复、过期、未来、错误来源及旧 generation 通知无副作用，接收后不反向再发通知，避免循环。

## 首次车辆验证

车辆当前未连接，以下均未实车验证。建议先安装新 DiPlay，再安装新嘟嘟，让协调广播的 signature 权限先有定义。

1. 原车音乐播放后，在 CarPlay 开始音乐。确认原车暂停、仪表和嘟嘟显示同一歌曲，进度连续，封面不随进度消失。
2. 播放中／暂停时跳转进度；切歌并观察封面晚到。通过嘟嘟和方向盘切歌，确认各执行一次且对象相同。
3. 切到原车音乐／蓝牙媒体。确认 CarPlay 暂停，嘟嘟显示并控制新源，持续的手机元数据不会抢回仪表或卡片。
4. 再次从手机主动播放，确认重新接管；退出 DiPlay 后确认卡片清理或恢复此前播放器。
5. 观察媒体服务自然断线重连及 CarPlay 断开重连：源基线不抢占，封面与进度恢复，排队旧命令不误发新源。
6. 导航、Siri、来电和通话期间检查仪表优先级。直连消息返回不代表物理显示回执；车机 SELinux、HIDL 权限及共享封面目录读权限需要实机确认。

不为测试擅自修改／重启车辆服务，不以静态 ABI 或离线模拟证明物理仪表效果。

## 直连专用版最终验收

按用户要求彻底删除 Link 媒体发送器与路由选择器及相关兼容测试。源码检索无 ArcfoxLinkMusicTransport、ArcfoxMusicRouteSelector、CarLifeMusicReceiver 或 CARPLAY_MUSIC 引用。原生不可用时只保留快照并节流重试。

完整 CI 通过（12m9s），日志 `build/arcfox-media-direct-only-ci.log`；当前单测 XML 共 2827 项，零失败／错误。Release 构建及 lint 通过，日志 `build/arcfox-media-direct-only-release.log`，系统签名与认证素材逐字节校验通过。

最终交付 `build/arcfox-release/DiPlay-0.2.19-Arcfox-MediaDirectOnly-system.apk`，SHA-256 `d0a769f1ac0094687d26471831682843481cc6d6cc41a65b1f40b95493293e05`。替代此前 MediaComplete 包；嘟嘟 v3 无需更新。实车验证项仍适用。
