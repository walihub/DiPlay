# 极狐媒体与胎压修复、歌词接入验收（2026-10-17）

> 后续实测修订：重签酷我会导致播放资源URL生成失败；本文件中的系统签名酷我v1～v3不应继续使用。最终改为原签名酷我＋嘟嘟v8外部适配，详见 [原签名修复](ARCFOX_KUWO_ORIGINAL_SIGNATURE_FIX_20261017.md)。

## 范围

保留既有极狐适配，本轮修复胎压十倍误差、酷我播放被观察器暂停、仪表与嘟嘟封面路径，新增嘟嘟歌词显示。歌曲播放列表未接入，其原型仍位于 `tools/arcfox-kuwo/proposals/playlist/`，构建不包含它。

没有修改认证资源、导航/HUD、电话/蓝牙、音频通道及原车系统 APK。

## 已确认原因及最终行为

1. 嘟嘟四轮独立压力字段 `0x21400bf8..bfb` 使用了来自另一个打包字段的 `/100`，与实际十倍偏差一致。本轮改为 `/10`（24/25 → 2.4/2.5），胎温仍 `raw−40`，无效值和断连处理保留。独立轮字段本次没有实车 raw 抓取，不将回归输入24/25冒充现场日志。
2. 酷我新增观察器会在发现其他 PLAYING 会话时主动 pause，可能包含同包辅助会话或历史状态。移除该主动暂停；保留原播放器焦点处理。同包会话隔离，封面使用原服务 `F(true)` 与原缓存/加载器，最多三次有界请求；不改登录、解码、版权判断。
3. 嘟嘟封面接口先判断非空 URL 再判断 Bitmap；原桥错误传入 `arcfox-diplay` / `arcfox-kuwo`，导致真实 Bitmap 被当 URL。两条路径均改为 `null URL + Bitmap`，真实分类契约由测试覆盖。
4. DiPlay 只在成功解码后发布规范封面，支持封面与曲目元数据先后到达、匹配传输 ID、有界缓存与恢复。动态歌词标题不清除同曲封面；坏缓存不当作成功。

## 歌词能力

- 酷我：原 `AUDIO_LYRIC` 提供整首带毫秒时间戳歌词，嘟嘟使用原歌词视图与播放进度滚动。歌词入队前绑定实际 Music，发布前再次核对，拒绝跨曲迟到、未知归属及旧会话数据。需本轮酷我和嘟嘟配套更新。
- 原车音乐：真实 com.arcfox.media 中 QQ/USB 发布 `MediaInfo.id/name/allLyric`；原嘟嘟歌词回调为空，本轮补齐。使用 LRC 时间字符串转换，进度毫秒已核对真实生产者；只有当前来源及歌曲匹配时消费。匿名消息只触发有界权威快照重读。服务只有当前行时按当前行显示，不冒充全量歌词。
- CarPlay：用户确认 iPhone 音乐应用将当前歌词逐句写入歌名字段。已有 DiPlay 会原样发布 TITLE，原嘟嘟只显示在标题区。本轮同时写入歌词区域：新句替换旧句，未提供动态歌词时显示当前歌名；不累计伪造整首时间轴。当前链路未确认整首歌词和对应时间戳接口，不承诺完整滚动歌词。
- 原嘟嘟标题事件会将歌词视图 active 置 false，因此必须在标题之后发布歌词，再配合进度；新增回归模拟了此真实副作用，防止“模型有词但界面不滚动”。

## 构建与检查

独立检查通过：

- 四轮压力/温度转换、卡片来源与断连恢复 Java 测试。
- 酷我真实 APK 挂钩 ABI、原封面加载器与无观察器 pause 的检查。
- 酷我歌词/实际插件共16项 Robolectric 回归，含标题事件使歌词失活的真实副作用。
- 原车与 CarPlay 媒体桥集成、歌词时间格式/身份与匿名快照、动态当前行、发布顺序、旧回调拒绝测试。
- 所有三个 APK 均重新独立验证 v2 平台签名，证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`，使用用户指定的 platform.pk8/platform.x509.pem。
- ZIP差异白名单核对：嘟嘟仅更换 main/classes4/classes5、增加classes6；酷我仅更换 main/Manifest、增加classes3。其他原条目逐字节保留，非签名 META-INF服务描述符保留；DEX中无KuwoQueue。DiPlay构建明确核对原认证资源逐字节保留。

最终产物 SHA-256：

- `Dudu_fix51_ArcfoxVehicleData_v7_TyreArtworkLyrics_platform_signed.apk`
  `d660ad0a96a94547c1e5b771b62dae1a7712b8e2a1e0f7cb9a753ac7b6f0ea7f`
- `Kuwo-7.1.8.21-Arcfox-v3-Lyrics-system.apk`
  `9c41958d508fa4317274f9fa4f6081feaa5887dc0ab81afa90240695eeca7a61`
- `DiPlay-0.2.19-Arcfox-MediaArtworkFix-system.apk`
  `a98082e318201634e07897904f2615f1d81f63911864e995404b6e4713db587c`

仓库要求的完整 CI 已通过（3m40s）：

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest \
  :mobile:lintDebug :home:lintDebug :maphost:lintDebug \
  :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

使用 `tools/arcfox-kuwo/tests/jdk21.gradle` 将测试运行器固定到 JDK21，避免本机JDK25原有 SettingsBackupServerTest 的 ServerSocket.close 卡住；没有排除测试或更改上述任务。2863项测试中2862通过，1项原有跳过，0失败/0错误；三个lint和三个assemble均通过。日志 `build/arcfox-fixes-20261017/ci-lyrics.log`。

最后独立复核补齐DiPlay新旧token均PLAYING时的隔离：切token递增revision并清旧歌词/封面，拒绝排队旧UI与命令；原车已接管时不因token更换抢回。独立编译实际桥接测试和ABI检查再次通过，重打嘟嘟并再次验证签名与ZIP差异；其他两包不变。该工具Java目录不在Gradle生产/测试sourceSets内，使用实际桥接独立编译测试验收。

所有检查为离线验证，尚未代替本版实车验收。

相关依据：

- `ARCFOX_TYRE_PRESSURE_V5_FIX_20261017.md`
- `ARCFOX_KUWO_PLAYBACK_ARTWORK_FIX_20261017.md`
- `ARCFOX_CARPLAY_ARTWORK_FIX_20261017.md`
- `ARCFOX_KUWO_LYRICS_20261017.md`
- `ARCFOX_DUDU_NATIVE_LYRICS_20261017.md`
- `ARCFOX_MUSIC_PLAYLIST_FEASIBILITY_20261017.md`
