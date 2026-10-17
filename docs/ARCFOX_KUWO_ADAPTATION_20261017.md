# 酷我车机版 7.1.8.21 极狐适配

> 后续实测修订：重签酷我会导致播放资源URL生成失败；本文件中的系统签名酷我v1～v3不应继续使用。最终改为原签名酷我＋嘟嘟v8外部适配，详见 [原签名修复](ARCFOX_KUWO_ORIGINAL_SIGNATURE_FIX_20261017.md)。

> 后续 v5 已增加自动选择：无需手选酷我插件，见 [自动适配补充](ARCFOX_KUWO_AUTOSELECT_20261017.md)。以下 v4 的手选说明为历史记录。

## 输入与产物

输入：`/Users/h/Documents/arcfox/app/酷我音乐车机版 v7.1.8.21.apk`，包名 `cn.kuwo.kwmusiccar`，版本号 71821，targetSdk 30。

- 酷我：`build/arcfox-release/Kuwo-7.1.8.21-Arcfox-v1-system.apk`
- 嘟嘟：`build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v4_KuwoMediaSession_platform_signed.apk`

两包均使用用户提供的 `platform.pk8`／`platform.x509.pem` 签名。不安装或修改任何车辆内容。本次不更改 DiPlay、NaviService、蓝牙电话、HUD、酷我登录／会员、解码器、音频流或焦点逻辑。

## 酷我内嵌适配

仅在 `Build.FINGERPRINT` 以 `AAOP_COCKPIT/` 开头时启用。

原 APK 的 `KwMediaSessionService.q()` 创建 support MediaSession。适配在原方法末尾取得 `MediaSessionCompat.c()` → `Token.e()` → 平台 MediaSession.Token，注册其 MediaController 回调；不创建竞争的 MediaSession，不增加常驻 Android 服务。`x()` 释放原会话前解除对应适配，旧 token 的退出不影响新 token。

歌曲、歌手、时长、进度、状态、Bitmap 封面来自该原有 MediaSession。播放中按单调时钟外推位置、按时长封顶，暂停冻结，周期 1 秒刷新，暂停状态 10 秒重发；失败节流后重试。

仪表输出使用已核对的 IFgeComService transaction 1、40 字节 Message：ID 114、JSON @8、respond @24。JSON 为 `command.id=meidainfo`、`sub=id3info`，包含 `mediatype=19`、`songname`、`singername`、`totalplaytime`、`curplaytime`、`status`、`mute`、`albumpic`。毫秒转秒沿用原车规则：余数大于 500 才进位。

19 是已经验证过结构的第三方音乐显示通道，不是新增的“酷我”原车源编号。本适配不伪造 QQ／CarPlay 连接、不登记媒体源，不注册或注销方向盘 HIDL callback，也不调用原车音源切换。仪表是否按此通道显示专属来源图标由原车决定，本次不修改仪表 APK。

封面存放 `/mnt/ota/media/kuwo_music_<SHA256>.png`，仪表消息仅带文件名。原子写入、世界可读、内容寻址复用；只清理本应用前缀且超过 24 小时的 PNG，不删除 DiPlay 或原车文件。图片上限 4096 边长／4M 像素／PNG 2MiB。写图失败时继续同步文字和进度，并重试封面。

其他音乐 MediaSession 新进入播放时让出仪表；自己的新播放边沿重新接管，元数据／进度不会抢回。音频模式非 NORMAL 暂停发布。无 MediaSession 的原车播放器仍依赖酷我原有音频焦点处理。本次新增 `MEDIA_CONTENT_CONTROL` 签名权限用于只读观察其他会话；没有额外抢焦点、取消静音或电话控制。

## 方向盘

补充原车已确认的 `adayo.keyEvent.onKeyDown.N6`：`InputKey=16` 下一首、`15` 上一首。只在酷我拥有仪表来源、实际正在播放、音频模式 NORMAL，且原会话 actions 支持时响应，后台也可接收。暂停时的标准播放／恢复继续由原 MediaSession／嘟嘟明确指令处理；不猜测其他原始键值。

新增原始按键被转换成包限定的酷我既有 `cn.kuwo.kwmusicauto.action.MEDIA_BUTTON`，`Hardware_MediaButton=true`。在原 `KwCarPlay$q.onReceive` 的统一入口按命令与来源去重：350ms 内相同命令的 raw／standard 双路径只执行一次，同一路径连续真实按键不被吞掉。保留原有 MediaSession 和标准媒体键路径。

## 嘟嘟为什么需要同步更新

静态核对确认旧插件的 AIDL descriptor 虽然仍是 `cn.kuwo.autosdk.IAutoSdkAidlInterface`，事务布局却不兼容。例如事务 4：旧客户端期望返回 int，新酷我返回 Music List；之后还有编号移位，不能只因包名相同就认定兼容。

因此在极狐上，嘟嘟原来的酷我车载版插件 `plugin/n1` 改用平台 MediaSession：仅筛选 `cn.kuwo.kwmusiccar` 的会话，标题、歌手、Bitmap 封面、状态和进度取自同一 token；上一首／下一首／播放／暂停使用该 token 的 TransportControls，并检查 actions。插件退出、token 替换、切换播放器后拒绝排队的旧 UI／控制，不控制其他应用。

保留原插件初始化的 superclass 调用、包名及显示名。非极狐保留旧 SDK 分支。仅改 init、cleanup、refresh、next、previous、play、pause 共 7 处入口；没有改其他播放器。使用时在嘟嘟选择 **酷我音乐车载版**，不选择 QQ音乐／原车媒体插件。本次没有增加新的设置页。

旧 SDK 的歌词、歌单、播放模式等私有能力不声称已迁移；本次范围是上述卡片显示及四项播放控制，酷我自身界面中的功能保持原样。

## 构建及离线验证

实际 Java 适配源码经 `common` 的 test sourceSet 编译测试，不进入 DiPlay 正式 APK。

```sh
python3 tools/arcfox-kuwo/tests/test_patch.py
./gradlew :common:testDebugUnitTest --tests '*KuwoArcfoxAdapterTest'
python3 tools/arcfox-kuwo/build_kuwo.py
python3 tools/arcfox-kuwo/build_dudu_kuwo.py
```

静态测试使用本地解码目录 `build/kuwo-analysis/decoded`／`dudu`，核对原 APK 的真实 AIDL 差异、token 访问器、生命周期钩子、寄存器及嘟嘟 callback／manager ABI。

Robolectric 使用实际打包 Java 源码，验证：播放源边沿、进度溢出与暂停、N6 映射和去重、真实 token 接入／接收器释放、JSON 结构、封面目录失败继续发文字、仅清理自有文件、文件丢失重写、无 HAL 时返回失败、嘟嘟仅选择酷我会话、actions 过滤、旧 UI／退出命令失效。

酷我只改变 AndroidManifest.xml（增加签名媒体观察权限）和 classes.dex，增加 classes3.dex；classes2、全部资源、assets、lib 逐字节保留。嘟嘟基于 v3，只改变 classes.dex，增加 classes6.dex；AndroidManifest.xml、所有资源及 classes2/3/4/5 逐字节保留。构建脚本逐项校验并验证签名。

原 APK 的全部非签名 META-INF 项也逐字节保留，尤其 `META-INF/services/a3.b` 等 ServiceLoader 配置；只替换 `.SF/.RSA/.DSA/.EC`、MANIFEST.MF 等旧签名条目。

最终产物 SHA256：酷我 `6805a28d3d9f258abb4f843c0a7a8661f95850cd644f7bd7f0c9ca62824644f5`；嘟嘟 `1cf77cc26b06501da2cca343c933df5266384760af684a3c8d6872f1146da4cd`。主 agent 独立 ZIP 核验记录 `build/kuwo-analysis/artifact-verification.log`，无非签名条目丢失。两包证书 SHA256 均为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。

最终整仓 CI 记录：`build/kuwo-analysis/ci-final.log`；专项：`tests-final.log`；构建：`build-final.log`／`dudu-build-final.log`。12 项最终专项测试通过（15 秒）。本机默认 JDK 25 的原有 SettingsBackupServerTest 在 ServerSocket.close 原生调用处阻塞，线程栈保留于 `build/kuwo-analysis/ci-threads.txt`；未修改备份服务代码。使用现有 JDK 21 的测试 launcher 后，备份服务与酷我专项一起通过（`jdk21-tests.log`，15 秒）。整仓 CI 同样使用 JDK 21 测试 launcher，所有规定任务保持不变；可复现的 init script 为 `tools/arcfox-kuwo/tests/jdk21.gradle`。整仓 CI 全部通过（5m18s）：2839 项测试零失败／错误，三个模块 lint 与 debug assemble 通过；home 测试任务无用例。

## 实车验收

离线通过不等于物理仪表效果已验证。仍需停车时确认：

1. 安装两包，在嘟嘟选酷我车载版；播放一首歌，观察仪表与卡片标题、封面、状态、进度。
2. 前台／后台单次与连续按方向盘上下曲，确认一次按键切一首；用嘟嘟播放、暂停、上下曲确认对象始终是酷我。
3. 切到原车／CarPlay 播放，再从酷我主动播放，观察仪表不被旧元数据反复覆盖。
4. 暂停、切歌、封面晚到、退出和重启酷我／嘟嘟，确认旧卡片不会写到新播放器。
5. 检查车机的 HIDL／SELinux 与共享封面目录权限，以及电话、导航期间原有音频焦点行为。仅有平台签名不代替这些实机验证。
