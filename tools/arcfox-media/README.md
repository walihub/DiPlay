# 嘟嘟极狐媒体一致性补丁

基线为已完成胎压/胎温/车门/车速接入的 fix51 v2，不修改 vehicle Java。

## 数据与控制

- 原车 `ArcfoxMediaCenterSdk.mBinder.getLastMediaType()` 是原车源依据。
- `getMediaInfo` / `getPlayStatus` 用已确认源请求初始快照；进度沿同一源回调读取。
- 后台来源的歌曲、进度、状态一律拒绝，不再允许类型 4/5/6 绕过过滤。
- `PlayState.PLAYING=3` 映射播放中，修正原回调反向映射。
- DiPlay 包 `com.shihab.diplay` 的活跃 MediaSession 播放边沿接管卡片；歌曲、封面、进度、状态来自同一 session，控制发给该 session 的 TransportControls。
- 原车明确源变化或当前原车源新的播放边沿优先；持续播放的旧 DiPlay session 不会在下一次轮询抢回来。
- 不发送切换车辆源命令，不操作蓝牙电话，不修改音频焦点。
- 初始原车源快照/重绑基线不抢占已播放的 DiPlay；原车 IDLE 只清原车卡片。
- 切源、原车服务失联、DiPlay session 销毁时清空旧信息；generation/revision 丢弃退出或切源前排队的界面更新。
- SDK 原有 ServiceConnection 内自行 bindService 重连；新 binder 出现后重新读取源和快照。
- 取消旧 QQ 直连及重复 raw binder 绑定，防止绕过统一回调的第二路写入。
- 所有SDK回调进入同一worker，按callback实例和连接generation校验；旧回调不能污染新插件。
- 排队控制同时校验revision；会话控制重新检查活跃token和PlaybackState actions。
- 封面只在metadata内容或Bitmap像素变化时更新，进度每秒独立刷新。
- 不再乐观更新按钮状态，状态以回调/MediaSession 为准。

## 协调广播

仅当 DiPlay session 拥有卡片且原车已确认接管时发一次：

- action: `com.shihab.diplay.action.ARCFOX_NATIVE_MEDIA_SELECTED`
- package: `com.shihab.diplay`
- receiver permission: `com.shihab.diplay.permission.ARCFOX_MEDIA_COORDINATION` (signature，由 DiPlay 定义)
- extras: sourceId=int, reason=`source-change`/`native-play`, observedAt=elapsedRealtime, origin=`com.dudu.autoui`

元数据及进度不触发该广播。DiPlay 缺失时广播无接收端，无副作用。

## 构建、验证与边界

`python3 tools/arcfox-media/build_dudu.py`

脚本用 apktool 解码原 v2 APK，定点改写主 dex 中以下三个类的必要方法，追加 classes5.dex。最终 ZIP 使用原 v2 的所有资源/其他 dex/资产，只替换主 dex 与局部增加 uses-permission 的二进制 Manifest。原 classes4.dex 胎压车况适配逐字节保留。平台签名使用指定 platform.pk8/platform.x509.pem。

离线策略测试：

```
javac -d build/arcfox-media-tests tools/arcfox-media/java/local/arcfox/media/SourcePolicy.java tools/arcfox-media/java/local/arcfox/media/CallbackFence.java tools/arcfox-media/tests/SourcePolicyTest.java
java -cp build/arcfox-media-tests SourcePolicyTest
```

尚需实车验收：SDK 原车进度单位、媒体服务断线重连、原车无 MediaSession 播放同源边沿、嘟嘟卡片实际封面清空、DiPlay session 权限可用性。仅系统签名并不能代替实车功能验证。

## 三个原类的修改范围

1. `ArcfoxMediaPlugin`：`a(Context,r)` 移除旧 QQ/raw 重复绑定并启动 bridge，SDK 初始化保留；`sendEvent(int)` 统一路由；`h()/i()` 去掉未经回调确认的乐观状态；`c()` 失效桥接并异步清理回调。
2. `DuDuMediaCallback`：`onMediaInfoChanged`、`onMediaPlayStateChanged`、`onMediaPlayProgressChanged`、`onServiceConnectStatus` 委托带 callback 身份的 worker 入口。
3. `music.p.s()`：非 Arcfox 控制器重新初始化时重新建立已有设计中的 qqMonitor，不再因旧 qqMonitor 对象非 null 而跳过。没有修改其他播放器类。

## 播放器选择条件

原车媒体中心控制器 ID 为 **101**，`s.b(Integer)` 对应包 `com.arcfox.media`，现有插件 `f()` 的 UI 名称仍为 **QQ音乐**。`p.s()` 在其他播放器初始化后原本就创建常驻 Arcfox `qqMonitor`，因此无需用户先选择 101 才能发现 DiPlay。

- 只有 DiPlay session 从非播放进入播放，或确认当前原车源新的 PLAYING 边沿，才自动选择 101。已是 101 时不重复初始化。
- metadata、progress、首次源快照、服务重绑快照均不选择播放器。qqMonitor 在未选 101 时不向桌面写入歌曲、封面、进度或状态。
- DiPlay接管时缓存原播放器；DiPlay session 消失后等待 1.5 秒接收原车状态，若没有其他原车播放接管且仍选 101，则恢复原播放器。
- 原车新播放接管后放弃恢复旧播放器，继续使用原车媒体中心。
- 没有 DiPlay 且没有新的原车播放时，保持用户原播放器，qqMonitor 不抢卡片。

完整离线验证：`tools/arcfox-media/test.sh`，再执行 `python3 tools/arcfox-media/tests/test_patch.py`。前者编译实际 bridge 配假 Android Handler/MediaController 和实际签名的反射 manager/SDK，验证非 Arcfox → DiPlay 播放 → 101 一次 → metadata 不重选 → session 退出恢复 → 原车接管；另覆盖服务基线、IDLE、旧 callback、进度过滤、封面去重、控制 actions 和发送前源复查。后者核对生成 smali 的寄存器字宽、反射方法与原始 SDK/manager ABI。

## DiPlay 主动让出媒体的反向通知

DiPlay 通过永久失焦或其他 Android MediaSession 确认车辆接管时，可能没有原车源编号变化／新的原车 PLAYING 回调。为避免嘟嘟继续保留已暂停的 DiPlay 卡片，bridge 动态接收可选通知：

- action: `com.shihab.diplay.action.ARCFOX_CARPLAY_MEDIA_YIELDED`
- package: `com.dudu.autoui`
- extras: origin=`com.shihab.diplay`, observedAt=elapsedRealtime
- receiver 以 `com.shihab.diplay.permission.ARCFOX_MEDIA_COORDINATION` 注册，要求发送方持有签名权限。

注册在 worker 执行；退出后失效 generation 并异步解除注册。只接受正确 origin/action、非未来且不超过 10 秒、未重复的时间戳；回到 worker 后再次检查 generation。仅当前由 DiPlay session 拥有卡片时清除它，并通过当前 SDK binder 重读真实源、歌曲及状态。该路径不切换车辆源、不选择其他控制器、不回发正向接管广播。无 DiPlay owner 时不改变卡片。

实际桥接集成测试补充验证了：SDK 源／状态均未改变、手机 session 仍存在但暂停时恢复原车；重复、过期、未来、错误 origin、旧 generation 以及无 DiPlay 时无额外界面／SDK刷新副作用。

## 连续 PLAYING 的 MediaSession 更换

MediaBridge 在仍由 DiPlay session 持有卡片时，先比较新旧 session token。token 改变即清理缓存、增加 revision，再应用新 metadata／封面／当前文本；不依赖 PLAYING→非PLAYING 边沿。旧 token 已排队的 UI、歌词和控制因此失效。已让给原车时，换 token 本身不会重新夺取原车音源。

真实 bridge 集成回归覆盖：旧 session 的 metadata／歌词尚未 flush，换入同为 PLAYING 的新 token，旧内容和旧控制被拒绝，新封面／文字保持；原车已接管时新 token 不抢回。
