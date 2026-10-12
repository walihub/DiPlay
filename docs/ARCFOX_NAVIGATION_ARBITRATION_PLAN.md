# 极狐导航互斥备选方案

记录日期：2026-10-12。用户明确要求本次只记录，不实施。本文件中的新增接口、输出限制及小窗规则均是后续方案，不是当前 APK 已有功能。

## 当前已实现的范围

NaviService 统一仲裁三方 HUD：原生高德 `com.autonavi.amapauto`、共存高德 `com.autonavi.amaparcfox`、DiPlay `com.shihab.diplay`。前台且导航有效的来源优先，非导航页面在前台时保留当前有效来源；当前来源结束后再选择其他有效来源。

当前启动 CarPlay 导航不会结束高德导航，也没有做三方导航语音互斥。音乐播放互斥与导航路线互斥是不同功能，不能用媒体 PAUSE 或音频焦点证明路线已经结束。

当前前台识别读取系统焦点任务的 `topActivity`，优先尝试 `getFocusedRootTaskInfo` / `getFocusedStackInfo`，失败时读取 `getRunningTasks(1)`。没有专门识别画中画、悬浮窗口或最近点击的小窗。两个导航都在小窗时，系统报告的焦点可能是其中一个小窗，也可能是桌面；此时不能保证“最近操作的导航优先”。

当前实现及验证见 [ARCFOX_PROTOCOL.md](ARCFOX_PROTOCOL.md) 的“音乐互斥、Siri 压低和三方 HUD 仲裁”。

## 方案一：导航输出互斥

保留各应用的路线，由统一仲裁器决定哪个来源可以输出 HUD、仪表地图和导航播报。非选中来源保留导航，重新选中后可以恢复输出。相较强制结束路线，更容易恢复用户操作。

后续需要增加带来源、会话代次和递增序号的所有权通知，让 DiPlay 和共存高德分别控制自己的仪表窗口与导航音频。只控制导航音频，不抑制音乐、电话或 Siri。HUD 继续通过现有统一写入服务输出。

原生高德的仪表地图和播报也需要找到并验证对应控制接口。当前 NaviService 的 HUD 拦截并不同时控制原生高德的仪表地图或导航声音；不能只扩展 HUD 服务就声称三方所有输出已互斥。音频焦点可能只使播报交替播放，不能代替完整来源授权。

小窗场景可考虑“主画面有效导航优先；只有小窗导航时，最近明确操作的有效导航优先；操作桌面或音乐时保留当前来源”。需要先采集极狐的实际任务、窗口模式和焦点变化，确认点击小窗能否被可靠识别。此规则仅作为备选，不修改已确认的当前前台优先策略。

## 方案二：只允许一条导航运行

若未来要求真正结束其他路线，可在新的有效导航开始时，由统一仲裁服务向其他来源发出结束导航请求。停止请求应携带目标来源及目标会话代次，接收端确认目标仍是同一会话后执行；等待实际导航结束状态确认，避免旧停止消息结束用户刚启动的新路线。

建议将触发点限定为明确的新导航开始，不因普通前后台切换、重复状态心跳或小窗焦点变化反复取消路线。该触发策略尚未获用户确认。

| 来源 | 本地分析找到的依据 | 尚需验证的部分 |
| --- | --- | --- |
| 原生高德 | NaviService 内 `AmapNaviJsonListener.endCurrentGuide()` 构造 `protocolId=0x138CE`，即 `PROTOCOL_TRIP_ENDNAVIGATION`，经 `JsonProtocolManager.request(String)` 发出请求 | 原车高德是否响应、请求是否准确送达原生包、结束状态回传及导航播报是否停止 |
| 共存高德 | `com.autosdk.bussiness.navi.NaviController.stopNavi()` 返回 boolean；内部调用 `stopNavi(false)`，进一步调用 `GuideService.stopNavi(mNaviId)` | 在应用自身进程中增加受保护的停止请求接收端，核对执行线程、返回值及实际结束状态 |
| CarPlay | 当前 DiPlay 源码未找到通用的结束 iPhone 导航接口；现有 iAP2 导航状态是输入信息 | 不能假定反向发送状态能取消路线；需确认有合法、应用可用的取消协议后才可承诺路线互斥 |

因此，CarPlay 开始时请求两个高德结束导航有代码依据，但没有完成实际调用与车机验证。高德开始时撤下 CarPlay 的 HUD/仪表输出并抑制其导航播报，也不等于取消 iPhone 内的路线。现有资料不足以承诺对称的三方路线取消。

## 接口依据的本地位置

- 原车服务原包：`arcfox-capture/2026-10-07/NaviService.apk`。
- 原车服务解码：`build/hud-arbitration/naviservice-base/smali/com/adayo/service/navi/appmap/automap/AmapNaviJsonListener.smali`，方法 `endCurrentGuide()` 和 `requsetAmap(Object)`。
- 协议常量：上述解码目录的 `smali/com/autonavi/amapauto/jsonsdk/ProtocolID.smali`，`PROTOCOL_TRIP_ENDNAVIGATION`。
- 共存 SDK 方法参考：`/Users/h/Documents/arcfox/gaode/work/byd872-bubble-scale-1_2/smali_classes21/com/autosdk/bussiness/navi/NaviController.smali`，`stopNavi()` / `stopNavi(boolean)`。这是本地分析参考；实施前仍需核对目标基准包 DEX，不能仅按方法名跨版本套用。
- DiPlay 导航输入状态：`shared/src/main/java/com/shilapi/xcertplay/navigation/CarPlayNavigationState.kt`。
- 已有 HUD 仲裁：`tools/arcfox-hud/service-src/local/arcfox/hud/HudArbiter.java`、`HudSelection.java`。

## 未来验证清单

验证原生→CarPlay、CarPlay→共存、共存→原生，以及两个小窗、主画面与小窗、桌面在前台的组合。区分路线结束、HUD 切换、仪表窗口撤下和播报停止四种结果。覆盖重新规划路线、导航到达、旧会话消息、应用退出和服务重启；停止接口失败时记录失败，不能把来源隐藏当作路线取消成功。

本次没有增加上述功能，没有重新构建 APK，也没有连接或修改车机。
