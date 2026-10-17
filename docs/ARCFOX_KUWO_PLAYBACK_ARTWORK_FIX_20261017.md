# 酷我 v2 播放与封面修复（2026-10-17）

> 后续实测修订：重签酷我会导致播放资源URL生成失败；本文件中的系统签名酷我v1～v3不应继续使用。最终改为原签名酷我＋嘟嘟v8外部适配，详见 [原签名修复](ARCFOX_KUWO_ORIGINAL_SIGNATURE_FIX_20261017.md)。

## 播放回归的静态证据

原包为 `/Users/h/Documents/arcfox/app/酷我音乐车机版 v7.1.8.21.apk`。
注入器只改 `KwMediaSessionService.q/x` 生命周期和 `KwCarPlay$q.onReceive` 去重入口，原 decoder、网络、资源、原 SDK 未改。
但 v1 新增 `KuwoBridge.Instance.refresh()` 每秒枚举 MediaSession，把“首次观察到的其他 PLAYING token”当成刚开始播放，然后直接 `controller.getTransportControls().pause()`。
观察不到 session 不等于它此前未播放；同包辅助 session 也未排除。这条新路径可以误暂停原播放器，是可从代码复现的回归风险。
目前没有这次现场播放失败的 logcat，不能据此宣称已证明它是现场唯一原因。

v2 删除观察器主动 pause；原播放器和车机原有音频焦点继续管理播放。
仍用播放变化决定仪表显示归属，且同包其他 session 不参与竞争。

## 原包封面生产链

`build/kuwo-analysis/decoded/smali/cn/kuwo/mod/mediaSession/KwMediaSessionService.smali`：

- `m(ZZ)` 构建 `ALBUM_ART_URI`，音乐分支来自原 `b3/l.l(Music)` 或 `Music.x`。
- `F(true)` → `G(true,true)` → `m(true,true)` → `h(PlayerState.m(), builder, true)`。
- `h()` 查询原 `v6/d.i(url)` 缓存；未命中时调用原 `v6/d.f(url, v6/b.e)` 异步 loader。
- `v6/b.invoke(Bitmap)` → `KwMediaSessionService.a(Bitmap)` → `r(Bitmap)` → `F(false)`，将已加载 bitmap 重新发布到原 MediaSession。

v1 仪表发送器只消费 `ALBUM_ART/ART` bitmap，对 URI-only 快照不触发原 loader，并在同曲任一 bitmap 为空的快照中清掉此前封面。
v2 在原 service 生命周期内，对新曲/新 URI 调用上述原 `F(true)`，由原 loader/cache 处理，并通过原 MediaSession 通知所有观察者。
没有新建 session、没有自行网络下载，也不假定任意 URI 一定能下载。
请求有界：同曲/URI 最多 3 次、重试间隔至少 5 秒；首次有 URI 即使带默认 bitmap 也允许触发一次真实加载。
同曲同 URI 暂时无 bitmap 时保留已发布封面；歌曲/URI 改变立即清旧封面。
`/mnt/ota/media` 写封面失败会限频记录 `Instrument cover file unavailable`，文字照常发送。

## 检查

- 独立 `javac -source 8 -target 8` 编译酷我嵌入 Java 通过。
- `python3 tools/arcfox-kuwo/tests/test_playback_artwork.py` 通过：无观察器 pause、同包隔离、原 loader ABI/回调存在、3 次/5 秒重试策略。
- 新增 `KuwoPlaybackArtworkRegressionTest` 供统一 Robolectric 验收：原 F(true) 请求、发现其他播放 session 不发暂停、同曲无 bitmap 保留封面、换曲清理。
- 本任务不跑全仓 Gradle、不构建最终 APK，由主任务统一验收/打包。

## 实车仍需确认

原 loader 成功获得实际封面、共享封面目录可写及仪表读取；车机同时存在其他播放器时不再误暂停；原网络/账号/播放服务如果仍报错需要其具体日志。
不触碰原账号、授权、URL、解码逻辑。v2 默认构建输出名改为 `Kuwo-7.1.8.21-Arcfox-v2-system.apk`。
