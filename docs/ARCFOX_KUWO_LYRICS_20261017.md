# 原签名酷我歌词同步（2026-10-17）

本次只改嘟嘟适配，不修改酷我原 APK、签名、播放认证或音频，不接入播放列表。原先源端 LyricOwner 绑定方案不用于交付。

## 原始数据与嘟嘟契约

原酷我 `lyric/d.b()` 将歌词放入原 MediaSession extras。Bundle 的键是 `AUDIO_LYRIC`，值为 JSON 字符串：

```json
{"AUDIO_LYRIC":[{"startTime":1000,"time":2000,"text":"歌词"}],"resultCode":20000}
```

startTime/time 都是毫秒，time 是行持续时长，结束=startTime+time。20000成功；20001/20002/20003为开始下载、无词、失败，数组为空。

嘟嘟 `r.a(List<LrcRow>,int)` 是歌词接口，第二参数是毫秒偏移，本次0。LrcRow 的 b(long)设开始、a(long)设结束、a(String)设文字。a(position,duration)沿原进度接口驱动滚动，暂停冻结，seek使用新实际位置。

## 原版无歌曲标识的边界

原JSON和Bundle没有 Music/rid。原 j.f 在排队前已丢弃 Music，R3→E不再核对，因此 extras 回调时读取“当前metadata ID”不能证明歌词来自该歌。原 Autosdk 能读当前 Music，但不能给已经排队的旧歌词补出原始 Music 归属；本轮未找到可直接依赖且带曲目身份的跨进程歌词缓存接口。不读其他 App 私有文件、不假装拥有确定关联。

为保留原签名播放器，本轮采用嘟嘟侧保守门控：

1. 新 Session 首次接入时，如果当前 metadata 有效、两次读取的 live extras 一致，可读取已加载的当前歌词，避免先播歌再打开插件时整首无词。同 token 后续切歌清空旧词并记住当时缓存，旧缓存不直接复用。
2. 首次快照之后，仅接受本 Session 有效 callback 的新 extras；确认 callback payload 与 controller 当前 extras 相同，排除已被更新取代的排队回调。
3. 使用完整 metadata身份（ID、标题、歌手、专辑、时长）维持当前曲状态，不仅比较可能为0的本地歌曲rid；原歌词此前已被接收并跨曲退休的payload不会重新恢复。
4. 对已更新内容解析时间戳。重复内容不重建，成功空词/失败结果清空。
5. Session/token/controllerGeneration/插件选中状态/revision都用于隔离退出或切源后的回调。

**限制**：原协议没有歌词歌曲ID，以上不能绝对识别“此前从未见过、刚好在新曲期间交付”的旧歌词；不能宣称解决了所有上游迟到竞态。同 token 切歌后，相同歌词JSON被复用或新词只以旧缓存形式提供时，保守策略可能暂不显示，等待后续新歌词回调/下一曲。新 token 首次一致快照是可用性取舍，并非具备歌词ID的绝对归属证明。这是保持原签名的明确取舍。

兼容历史带 ARCFOX_LYRIC_MEDIA_ID/TITLE/ARTIST 的数据时仍逐项匹配，但原版无标记数据不会被统一拒绝。

## 发布与滚动

实际嘟嘟多个主题的标题事件会关闭歌词active。刷新在同一主线程render内按“标题/封面 → 歌词行 → 进度”发送；封面单独更新而重发标题时也重新应用歌词，防止停止滚动。只接收原词，无词不伪造。

## 验证与范围

- Java对Android30编译。
- KuwoLyricsTest：毫秒与模型、前后seek、空词/失败、切歌清理、旧缓存和已知旧payload隔离、rid=0不同曲身份。
- KuwoArcfoxAdapterTest真实插件流程：模拟标题清active、歌词激活后进度，检查封面更新顺序；原版无标记新extras回调及首次已加载快照均可激活歌词。
- JSON最多512 Ki字符、4096行；时间非负整数且不溢出，文字单行16 Ki，格式错误清空。
- 最终CI及实车结果由主任务记录。本轮不重签酷我，仅交付更新后的嘟嘟。
