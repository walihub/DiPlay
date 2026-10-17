# 嘟嘟原车／CarPlay 当前歌词接入

本版同步歌词，不提供歌曲播放列表。内部 `List<LrcRow>` 是原嘟嘟歌词展示契约，与歌曲列表无关。

## 原车真实生产证据

原包：`/Users/h/Documents/arcfox/app/多媒体_250619_V1.3.8.apk`，aapt 包名 `com.arcfox.media`。只读解包在 `build/arcfox-analysis/native-media-player`。

- QQ `smali_classes4/.../MusicQQSyncPolicyHelp.smali` 的 `sendMediaLyric`（约 1750–1811 行）新建 MediaInfo，设置 songId→id、songName→name、mediaType=0、allLyric 后发送。classes15 同名逻辑也保留这些字段。因此真实 QQ 歌词并非只有 allLyric 的匿名对象。
- USB `smali_classes5/.../MusicUsbMediaSyncPolicyHelp$sendLyricSyncPolicy$1.smali`（约 134–255 行）设置 playPath、name、artist、album，再设置 allLyric 并发送。classes17 对应版本同样具有身份。
- QQ `MusicQQCoreMgr.smali` 约 4467–4471 行调用 `getCurrentLyric(currentProgress)`，结果作为 `sendPlayProgress(current,total,currentLyric)` 最后参数。`getCurrentLyric` 最终返回 SysLyricRow.getContent，不是路径。
- USB `MusicUsbCoreMgr$usbAudioPlayerObserver$1.smali` 约 866–872 行也调用 `getCurrentLyric` 后发送相同参数。
- QQ getCurrentLyric 直接把当前播放进度与 SysLyricRow.startTime 比较；SDK进度保持毫秒。仪表 MIS_MediaCenter.parseTime 再除以1000转换显示秒。本桥接不再乘／除1000。

## 展示契约与换算

现有 `DuDuMediaCallback.onMediaLyricChanged(int,MediaInfo)` 原为空实现，仍是正确原生接入点，不另建播放器插件。

- MediaInfo.getAllLyric → SysLyricRow，使用真实生产者也使用的 `getStartTimeString`、`getContent`。标准 `mm:ss[.fff]` 转为毫秒；允许秒小数1/2/3位，拒绝非法秒、超范围时间，不按数值大小猜单位。
- 原车 Meter 的消费者也使用 startTimeString/content 拼接 LRC，本桥接采用同一来源。不需要猜没有单位注释的裸数值。
- 嘟嘟 `com.dudu.autoui.common.y0.c.a` 是 LrcRow：a=时间字符串，b=startMs，c=endMs，d=文本。结束时间取下一行，末行取开始+10000ms，与原嘟嘟解析器习惯一致。
- manager `a(List,int)` 生成歌词事件；第二参数是 **offsetTime 毫秒**，UI将它与播放progress相加，不是行号。本版为0。
- manager `a(int current,int total)` 原进度事件继续驱动滚动，保持毫秒及原边界保护。
- 同时间翻译行合并文本，排序去重；最多2000行，每行最多2048字符。

## 来源与生命周期

- 所有歌词回调进入同一 worker，验证 callback 身份、generation、revision 和当前原车 source；CarPlay持有卡片时拒绝原车歌词。
- 新歌曲的metadata先清旧歌词；有身份的歌词必须匹配当前id／名称等已知字段，迟到的旧歌回调丢弃。
- 同内容全量歌词不重复发，进度不重新发送整首歌词。没有全量歌词时，SDK当前行文本作为单行兜底，start=0/end=MAX；逐行替换而非累计伪造时间轴。
- 匿名 MediaInfo 不直接绑定当前歌。按内容去重、每track最多2次读取当前源的权威 `getMediaInfo`，只消费返回的带身份metadata/allLyric。若返回也无法证明身份，不猜测显示匿名整首歌词。
- 切源、断线和owner变化清空；UI排队事件再次核对generation/revision/source/owner。worker渲染保留原event epoch，避免退出后旧重活又带新epoch发布。

## CarPlay 当前文本

用户确认 iPhone 导航外的音乐应用会将逐句歌词放在 CarPlay 歌名/title 字段。本版 `MediaBridge.session()` 将**实际收到的当前title**同步到嘟嘟歌词区，原标题显示保持：

- 仅展示当前一行，start=0/end=MAX，不缓存历史句子，不虚构整首时间轴。
- 收到普通标题时显示当前标题，不能宣称所有title都是歌词。
- 同内容去重，最多2048字符；空title、切源、断开清空。
- 同样受generation/revision/source/owner围栏保护，不修改DiPlay协议和歌曲身份逻辑。

## 合包调用

将 `tools/arcfox-media/java/local/arcfox/media/*.java` 编入 classes5（新增 NativeLyrics.java 与 CurrentTextLyrics.java）。

现有v3主dex基线的定点补丁：

```python
from lyrics_patch import patch_lyrics
patch_lyrics(decoded_directory)
```

函数位于 `tools/arcfox-media/lyrics_patch.py`，幂等，只替换 DuDuMediaCallback.onMediaLyricChanged 与 onMediaPlayProgressChanged。后者用 `invoke-static/range {p0 .. p6}` 将最后的currentLyric一并传入 MediaBridge。旧四参数Java progress方法保留兼容既有测试／调用。

## 验证

`sh tools/arcfox-media/test.sh` 与 `python3 tools/arcfox-media/tests/test_patch.py` 均通过；日志 `build/arcfox-native-lyrics-tests.log`。

测试覆盖：真实反射LrcRow字段/offset契约、毫秒时间、排序及结束时间、无效行、源过滤、切歌清理、旧track拒绝、全量去重、当前行去重、匿名消息只读权威快照确认、CarPlay title逐句替换/长度上限/空值/切源清空，以及smali寄存器宽度。

尚未实车确认最终歌词组件渲染；不声称所有第三方来源都有全量歌词。未改音频、HUD、电话或歌曲列表。

## 原嘟嘟标题事件的显示顺序

真实 `ui/activity/launcher/w0/x.smali` 的标题事件 `onEvent(t/i)` 会设置 `e=false` 并刷新视图；歌词事件 `onEvent(t/d)` 根据列表是否非空恢复 `e=true`。因此只有“调用过歌词接口”并不表示歌词可见。

本版原车与 CarPlay 均先发布原标题／封面事件，再发布歌词事件。原车相同metadata不会重复标题事件；metadata更新但歌词内容没变时，标题事件后仍重放当前歌词快照。CarPlay title之外的metadata/art变化也会让原manager重发标题，因此同样在它之后恢复当前行。进度单独刷新，不重发整首歌词。

集成stub已模拟真实副作用：`a(title,artist,cover)` 将 lyricVisible=false，`a(List,offset)` 根据列表恢复。测试断言事件顺序及最后可见状态，覆盖逐句title、同title元数据变更和原车同歌词封面变化。
