# 极狐 CarPlay 封面缺失：静态定位与修复

## 已确定的代码问题

没有本次现场日志，以下是可由源码和原车 APK 确定的独立缺陷，不能宣称是本次实车全部原因。

1. **嘟嘟错误地把 Bitmap 当 URL。** 原始 `music.p.b(String, Bitmap, boolean)` 先检查 String 非空，非空则 `coverType=2`，只有 String 为空且 Bitmap 非空才 `coverType=3`。旧 `MediaBridge.session()` 传入 `"arcfox-diplay"` 和有效 Bitmap，真实消费端会尝试加载不存在的 URL，而不是使用 Bitmap。修复为 String 参数 `null`。相应集成 stub 现模拟真实分支，并断言实际收到 Bitmap 事件，不能只按调用签名判成功。新的酷我桥接由其任务同步修复相同问题。
2. **仪表封面与元数据乱序可丢失。** `CarPlayMediaKeys.onArtworkChanged()` 原来在任意线程直接把原始 bytes 及 UI 层 retained nowPlaying 送往 Arcfox publisher；nowPlaying 只在主线程异步更新。publisher 尚无歌曲或 reference 不一致会丢弃该封面。之后虽然 Android 解码缓存可以等元数据，仪表发送端却没有重放。
3. **歌词标题更新会清空仪表封面。** Arcfox publisher 把任何 title 变化认作换曲，清空只发送一次的 cover，而现有 Android MediaSession 已使用 `albumTrackChanged` 避免歌词标题造成这种误判。直连 sender 的文件 identity 也包含 title。
4. **失败解码被视作恢复完成。** `ambientColorCache[id]=null` 后使用 `containsKey(id)` 判断完成，非有效图像也会阻止恢复。原恢复还只随氛围灯 ALBUM 开关启用，与极狐封面输出目标不一致。
5. **存在的缓存文件不等于有效图像。** 原 cover store 对任何非零文件直接返回文件名，跳过图像校验。损坏或截断缓存可被当成 ready。

## 本次实现

- 原始 transfer 继续由现有有界 artwork worker 解码；解码成功后生成最大 384px 的 PNG。调色板失败不丢弃有效封面。
- 主线程用新 `NowPlayingArtworkPayloads` 按 transferId 保存最多 4 份成功解码 PNG；只对精确匹配当前 metadata 的 reference 重放，不猜最近封面。
- metadata 先到或 decode 先到，两条路径均可送到 MediaSession 和极狐 publisher；同一 PNG 对象只发布一次，进度回调不会重复拷贝整张图片。
- 极狐 publisher 与原 MediaSession 复用 `albumTrackChanged`，歌词 title 更新保留当前封面；真正专辑／歌手／来源／reference／可识别换曲仍清空。
- Native sender 文件 identity 不再使用歌词 title。内部 PNG 上限统一为 2MiB；实际 384px PNG 明显小于原始大图上限，仍保持压缩 payload/图像数量有界。
- Android MediaMetadata 保留原有标准 `ALBUM_ART` 和 `DISPLAY_ICON` Bitmap；不再增加重复 Bitmap 键，避免系统媒体监听方额外复制。
- 恢复完成依据是当前 Bitmap 确实解码成功；真实极狐配置即使没有开启氛围灯，也可使用现有每媒体一次的有界订阅恢复。未更改认证或传输协议。
- cover store 校验输入，首次/文件变化时验证缓存可解码；损坏缓存原子重写，不能只靠文件存在返回文件名。仅管理 `diplay_music_<sha256>.png`。
- 新日志：`DiPlay-MediaKeys: artwork decoded reference=… valid=… current=…`。HIDL dispatch 成功仍不代表仪表已消费图片。
- 不增加 CarLink 回退，不改电话、音频路由、导航。

## 验证与仍缺的车机证据

专项测试包括 payload 精确 reference/上限/失败清理、实际 MediaSession 乱序重放、失败解码不完成恢复、歌词标题保留仪表封面、损坏缓存修复、恶意存在的非图像缓存不得成功、Metadata 标准 Bitmap 键、嘟嘟真实 URL/Bitmap 分类。

`tools/arcfox-media/test.sh` 已通过；日志 `build/arcfox-artwork-bridge-tests.log`。Kotlin/Robolectric 新测试交由 root 统一 Gradle 验收；本任务不构建最终 APK。

仍需要下次实车对照：

- 同一次播放的 `iap2 artwork transfer` 与 `artwork decoded` 行，确认 reference/字节数/图像有效性。
- `/mnt/ota/media/diplay_music_*.png` 是否创建、其他服务 UID 是否实际可读，SELinux 是否拒绝，以及仪表消费者是否正确消费 `albumpic` 文件名。
- 系统 MediaSession 对应封面 Bitmap 是否可见，嘟嘟最终卡片是否收到 `coverType=3` 并渲染。

目前只能确认静态缺陷已修，不能宣称 `/mnt/ota/media` 的设备权限、仪表硬件读取或最终渲染已经实测。
