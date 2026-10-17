# DiPlay 仪表缩放资格时序缺陷

2026-10-15，离线对照历史提交并复现。仅修改 DiPlay；NaviService 不参与此次修复。

## 当前版本是否包含缩放

包含。链路为 `IFgeComService serviceId=14 → ArcfoxWheelHidl → ArcfoxWheelZoomCommand → ArcfoxWheelZoomTarget → CarPlayController.zoomDashboardMap → AirPlaySession.changeMapZoomLevel`。

`Navi/guidecontrol` 中的 `zoom=up/down` 分别生成 `zoomDirection=0/1`，目标为仪表 `ALT_UUID`。绝对级别、默认恢复、纯转向卡片、隐藏地图和失效会话不发送缩放。无需 NaviService 中转。

历史核对：`dcef24a4` 已包含独立订阅；`625af669` 保留导航结束隐藏时的订阅；`3bb042c5` 增加地图重新显示时立即检查连接。当前 HIDL 实现和 CarPlay 缩放命令未被电话撤回版本删除。

## 可复现的问题

`ClusterMapPresentation.onSurfaceTextureAvailable` 原代码：

```kotlin
outputSurface = Surface(texture).also(onSurface)
```

`also` 内的回调发生在赋值之前。极狐使用 TextureView，回调经主线程同步进入 `CarPlayHostActivity.onClusterSurface`，并调用 `updateClusterMapShown`。后者要求 `presentation.outputSurface === clusterSurface` 才将物理仪表标记为可见。

因此新 Surface 已交给解码器，但 Presentation 字段仍为 null 或旧值，缩放资格被设为 false。回调返回之后才赋值，且此时没有再次通知主界面。直到下一次相关状态刷新，地图可显示但缩放被过滤；窗口/纹理重建会再次触发这个顺序问题。这是代码层面的确定缺陷，不代表已证明每次实车失效均由它造成。

同一写法在此前 0.2.17 适配提交中已存在，不是这次撤回电话逻辑引入，也不能归因于 NaviService 的 HUD 改动。

## 修复与验证

先创建 Surface、赋值 `outputSurface`，再执行 `onSurface(surface)`。不放宽隐藏地图和旧会话的保护，不改原车订阅、地图尺寸、安全区域或 CarPlay 协议。

回归测试 `ClusterMapPresentationTest.arcfoxTexturePublishesPhysicalSurfaceBeforeNotifyingTheHost` 在同步回调中检查宿主可见的 Surface 身份，并覆盖首次创建及纹理销毁后重建。旧实现执行失败（`build/arcfox-zoom-surface-before-fix.xml`），失败原因是回调期间物理 Surface 身份不一致。修复后的完整 CI 日志：`build/arcfox-zoom-surface-ci.log`。

实车仍需核对首次连接、开始导航、结束后重新导航、断开重连四种场景；本修复不包含用户此前暂缓的地图缩放后自动回弹问题。

验证结果：AGENTS.md 完整 CI 通过；新增回归在修复后通过，既有极狐缩放 9 项及地图资格 2 项测试通过。认证验证、Release lint 和 Release 构建通过，日志为 `build/arcfox-zoom-surface-release.log`。

系统签名包：`build/arcfox-release/DiPlay-0.2.19-Arcfox-NativeBluetooth-WheelSurfaceFix-system.apk`。沿用电话交回原车蓝牙的上一版改动；NaviService APK 未重新修改。已通过平台签名校验，尚未上车安装。
