# 酷我“服务器忙、连接失败”：原签名修复（2026-10-17）

## 已复现的因果边界

用户确认正常源为 `/Users/h/Documents/arcfox/app/酷我音乐车机版 v7.1.8.21.apk`。使用同一份测试数据完成以下对照：

| 对照 | 是否修改播放/适配代码 | APK签名 | 镜像结果 |
| --- | --- | --- | --- |
| v3适配包 | 有适配，无原播放代码修改 | 平台签名 | 播放URL为空，NETWORK_ERROR_ANTISTEALING |
| 仅重签对照 | **没有任何代码/Manifest改动** | 平台签名 | 同样MESSAGE_URL_IS_EMPTY |
| 原文件 | 没有修改任何字节 | 原v2/v3签名 | 正常PLAYING，播放进度推进 |

初次使用apksigner默认校验显示Missing META-INF/MANIFEST.MF，是因为源APK声明minSdk21但没有v1签名。它在目标Android11上有有效v2/v3签名；`apksigner verify --min-sdk-version 24`验证通过。**不是无签名APK**。

- 原文件SHA-256：`92a115fec3631945607e1d4b5d52d1b311844c59dd91ff6f68f883d0a0707742`。
- 原证书SHA-1：`44bc3100f89f3a2fd5b25203fe84ef72a8bd51e4`。
- 平台证书SHA-1：`27196e386b875e76adf700e7ea84e4c6eee33dfa`。

日志中的MusicPay_OK说明歌曲信息/付费状态请求已成功；原native `c3.V3(...)`生成播放资源URL为空，随后本地报告防盗链失败，尚未发送资源下载请求。重签是已验证的失效变量，但没有证明native内部某一具体分支如何比较证书；不将Java预期签名字符串与上述对照混为一谈。没有修改、仿造或绕过原认证/防盗链检查。

## 最终方案

酷我保留原文件、原签名；极狐适配移到已使用平台签名的嘟嘟进程：

- `KuwoExternalVehicle`由已有KuwoAutoSelect后台观察器驱动，读取原MediaSession，沿原Instrument的114/HIDL协议发布仪表音乐信息。
- 只在嘟嘟当前控制器为酷我6且媒体归属有效时发布/处理按键，原车切101后即停止覆盖。方向盘N6仅走原TransportControls上一首/下一首，不额外广播第二条播放命令。
- 不请求音频焦点、不暂停其他会话、不新建竞争MediaSession；音频、会员、解码仍由原播放器负责。
- `KuwoArtwork`读取原Metadata中的Bitmap或原始公开封面URI。异步有界加载/缩放，最多4缓存项、2MiB、512px、2次尝试，保留标准TLS；两个消费者共享下载，过期曲目/token/URI不回调旧图。不跨进程反射原F(true)，不拼接歌曲资源请求。
- 原`AUDIO_LYRIC`可直接消费，不再要求修改版ARCFOX标记。新token首次接入允许当前Metadata与两次读取一致的extras快照，解决先播歌再开插件无词；同token后续切歌清词并隔离已知旧payload。原协议没有歌曲ID，不能绝对识别所有播放器内部迟到；不宣称拥有源端强绑定。
- 保留标题→歌词→进度的发布顺序、切源/重连清理、已有胎压和CarPlay/原车歌词修复。
- 播放列表仍不接入。

`build_kuwo.py`入口现在转到`prepare_original.py`，只验证原v2/v3签名并逐字节复制原APK，不再构建新的重签播放器；历史patch函数仅供旧回归诊断。`build_dudu_kuwo.py`入口统一转到当前组合构建，避免误建旧v5。

## 真实镜像验收

生产Java helper编译进独立Android Instrumentation测试APK，安装到主镜像，实际读取原播放器。该临时测试APK已卸载，不进入正式包。

`build/kuwo-device-harness/result.log`：

- 富士山下：PLAYING；原HTTP封面成功解码194×194；原歌词3745字节，解析73行，首行0～5600ms。
- 调用原标准skipToNext，实际换至人间半途并继续PLAYING；另一封面194×194；歌词2780字节、55行，首行0～1032ms。
- 未调整音量/焦点、未修改原APK或登录/认证/购买状态。

额外对照镜像使用临时写时复制数据盘，验收后已关闭并清理。主镜像替换签名不同的旧包之前备份内部/外部数据，再安装原文件并恢复配置及播放记录。备份位于`build/arcfox-fixes-20261017/mirror-backup/`，不进入发布APK。

仪表硬件、方向盘物理信号和真实车机音源切换仍需实车验收；镜像通过原播放器实际控制及图片/歌词接口，离线回归覆盖外部桥门控/生命周期。

## 最终检查及产物

最终组合已安装到主镜像，酷我原数据已恢复。嘟嘟独立签名验证通过；主DEX/classes4/classes5更新、classes6包含外部桥/原协议歌词/共享封面，其余原资源/Manifest/classes2/classes3逐字节保留。KuwoExternalVehicle、KuwoArtwork、Instrument、KuwoLyrics均包含于classes6，未包含源端LyricOwner或播放列表原型。

- 嘟嘟：`Dudu_fix51_ArcfoxVehicleData_v8_OriginalKuwoCompatible_platform_signed.apk`，平台签名；SHA-256 `901b9d19f9991e8ea3cf708475252640af5f400ef2159a4160b19b69b3dc1552`。
- 酷我：`Kuwo-7.1.8.21-original-signed.apk`，与用户原文件逐字节相同；SHA-256 `92a115fec3631945607e1d4b5d52d1b311844c59dd91ff6f68f883d0a0707742`。

镜像数据备份后完成换签恢复。车机仍安装旧平台签名酷我时，原签名包无法直接覆盖，后续安装必须先保护数据再换包，不能以再次重签解决。

最终完整CI通过（5m26s）：2875项测试、0失败、0错误、1项原有跳过；三个lint及mobile/home/maphost assembleDebug均通过。测试运行器固定JDK21，所有仓库要求任务均运行，没有排除测试。日志 `build/arcfox-fixes-20261017/ci-original-kuwo-final.log`。

实际MediaBridge编译集成测试、原歌词转换与ABI、Kuwo原接口ABI、git diff --check也通过。镜像原版播放、原标准切歌、真实封面解码与实际全量歌词读取已通过，物理仪表/方向盘仍需车机复测。
