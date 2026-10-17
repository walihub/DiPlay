# 嘟嘟播放列表：静态调查与可行性（2026-10-17）

用户最终决定：本轮不接入播放列表，先实现嘟嘟中酷我与原车音乐的歌词同步；原有胎压、播放和封面修复继续保留。下述列表内容仅为调查结论和隔离原型，不代表已集成。

## 先纠正接口判断

`com.dudu.autoui.manage.music.r.a(List,int)` 的泛型实际是
`common.y0.c.a`，即 `LrcRow` 歌词行，不能用来传歌曲列表。

真正的播放器列表接口在 `music.l`：

- `a(): boolean`：播放器是否可加载列表；
- `e(): List<music.n>`：歌曲列表；
- `a(int,title,artist,path)`：点击列表歌曲。

`music.n` 有歌曲索引、总数、名称、歌手和路径。原酷我 n1 插件没有覆写上述接口，继承空列表与“不支持”；这不是单纯刷新慢。

## 酷我车机版 7.1.8.21

**可实现，难度中等，需要同时更新酷我与嘟嘟 APK。**

原酷我 `KwMediaSessionService.A()` 虽设置 Queue，但只放当前歌曲一条，ID 是 description 的 hashCode；它不是当前完整播放队列。

已确认原始真实数据和控制：

- `cn.kuwo.autosdk.a.M0()` 委托 `PlayerStateManager.u0().w0()` 返回真实 `List<Music>`。
- Music 字段 h 为歌曲 rid，i 为名称，j 为歌手。
- 原 `p0.E().i0(Music)` 是“play3”入口，保留广告和原播放器判断，通过当前列表查找歌曲后调用 `w2(list,index,0)`。不可拿 `l2(int)` 代替：它是 seek。
- Support Callback 映射 `onSkipToQueueItem -> f(long) -> MediaSessionCompat$c.A(long)` 已核对。

方案是在**原有** MediaSession 发布真实列表，并增加选择队列项的回调；嘟嘟覆写 n1 的上述三个列表接口。列表项携带实例、列表版本、歌曲 ID；点击前重新核对真实队列，切源或列表重排后的旧点击拒绝。

边界：FM/MV 等非歌曲模式先返回空列表；不伪造单曲“完整列表”。大列表需要额外验证 Binder 大小、翻页、UI 刷新和内存；版权/广告限制沿原播放器处理，不绕过。

方案验证原型位于 `tools/arcfox-kuwo/proposals/playlist/`，**未接入构建或 Gradle sourceSets，未装入本轮 APK**。

## 原车极狐音乐

**分来源实现，难度中到高，不能承诺一个接口覆盖所有原车源。**

当前使用的 `IMediaCenterSdk` 已确认有：

- `getMusicUsblist(callback)` 与 `showMusicUsblistSuccess(List,int,String)`；
- `getLocalRadioList()`、`getLocalRadioCollectList()`；
- `play(MediaVoiceData)` 和来源相关控制；
- `playMediaListByQq(List,...)` 是提交播放列表，并不能证明存在读取当前 QQ 队列的接口。

USB/电台可先适配：需要再确认返回列表元素结构、歌曲标识、点击播放参数以及来源切换时回调生命周期。电台列表本质是台站列表，不能冒充歌曲队列。当前 MediaBridge 只接歌曲快照/状态/进度，没有列表接收和点击映射。

原车在线音乐与蓝牙要单独确认：蓝牙 AVRCP 是否提供可浏览队列取决于手机与蓝牙服务；不能从当前单曲元数据推导完整列表。应先补嘟嘟读取能力，尽量不改原车服务；若服务未公开真实队列，再评估特定播放器接口，不跨源猜测。

预计改嘟嘟 APK；是否还需要改原车播放器，应在确认每一来源的数据出口之后决定，目前不能断言。

## DiPlay / CarPlay

**现有路径不具备完整列表，难度最高，暂不建议纳入这轮修复。**

当前 `CarPlayMediaKeys` 通过 NowPlaying 接收当前歌名/歌手/时长/封面/进度，发布 Android MediaSession；未调用 `setQueue`，也没有完整队列选择控制。

iAP2 catalog 虽列有 Media Library 0x4C00–0x4C09 消息名，但消息目录不是已实现可用的完整队列协议；媒体资料库也不等于所有第三方音乐 App 的“正在播放列表”。不能承诺网易云、QQ、酷我等 iPhone App 的当前队列可完整读取。

要做需增加 DiPlay 对实际 iPhone 协议的请求、解析、队列标识及选歌控制，并验证不同 App 的可用性，然后嘟嘟消费真正的 Queue。需要至少修改 DiPlay 与嘟嘟，并进行手机连接抓包/协议验证。不得用当前歌曲构造一条列表声称已支持，也不能把视频 insertPlayQueueItem 当成音乐队列。

## 决策建议

优先单独做酷我完整列表；其次按来源做原车 USB/电台。CarPlay 保留当前歌曲和上一首/下一首控制，完整队列待协议证据充分再决定。

以上是静态可行性结论和隔离原型结果，不代表实车验收。用户确认范围之前不集成列表。
