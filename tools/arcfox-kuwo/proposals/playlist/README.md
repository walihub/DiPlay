# 隔离的播放列表方案原型

用户要求先评估再决定；此目录不是本轮修复，不加入 APK。

- `java/`：验证酷我真实队列发布与原播放入口的候选实现。
- `dudu-java/`：候选列表读取/点击代码快照，并非当前构建源。
- `queue_patch.py`：未被构建器导入/调用的候选 smali 钩子。
- `tests/test_queue.py`：原 APK ABI 与候选队列生命周期离线实验。

本原型只验证基本接口，不是生产验收；未完成大列表分页、Binder预算、UI列表更新、所有原播放模式与实车测试。不要自动启用。

正式范围判断见 `docs/ARCFOX_MUSIC_PLAYLIST_FEASIBILITY_20261017.md`。
