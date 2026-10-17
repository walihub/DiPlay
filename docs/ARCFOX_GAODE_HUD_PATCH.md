# 共存高德接入统一 HUD 仲裁的修改方式

记录日期：2026-10-12。本文件记录已经完成的 HudArbiter1 本地修改与构建方式，不包含尚未实施的导航路线取消、导航语音互斥或画中画交互识别。备选方案见 [ARCFOX_NAVIGATION_ARBITRATION_PLAN.md](ARCFOX_NAVIGATION_ARBITRATION_PLAN.md)。

## 基准包与源文件

基准包是用户指定的 v24 共存版：

```text
/Users/h/Documents/arcfox/gaode/build/比亚迪3.15.872_极狐适配_v24_气泡缩放1.2_共存版_platform_signed.apk
```

包名 `com.autonavi.amaparcfox`，versionCode `872`。基准 APK SHA-256：

```text
971243338de22e75cc9b6b82ccf499896db04c42d3766936c5a3856fb2501f9d
```

参考 Java 源和编译桩在 `/Users/h/Documents/arcfox/gaode/work/byd872-hud-keepalive-v23/`。虽然目录名是 v23，构建脚本先断言该目录 `classes26.dex` 与用户指定 v24 包内的同名 DEX 字节一致，才使用其 `src/Arcfox*.java`。此条件不满足就停止，不能换一个新版包后继续套补丁。

本工程修改脚本为 [build.py](../tools/arcfox-hud/build.py) 的 `gaode()`；共同提交客户端为 [HudSourceClient.java](../shared/src/main/java/com/shilapi/xcertplay/vehicle/arcfox/HudSourceClient.java)。生成的中间 Java 源位于 `build/hud-arbitration/gaode-src/`，不是持久维护入口；再次运行脚本会从参考源重新复制并修改。

## 具体修改

只修改附加适配层 `ArcfoxVehicle.java` 的 HUD 路径，并加入共同 Binder 客户端。原高德核心导航 SDK 不做方法补丁。

| 位置 | 修改行为 |
| --- | --- |
| 字段与启动路径 | 增加 `HudSourceClient hudSource`，已有 worker 未启动且识别为真实极狐时创建客户端；已有启动防重入条件保留 |
| `updateHud(boolean navigating)` | 导航有效且有新详情时更新缓存；缺少新详情时保留缓存；提交实际导航状态及 icon/distance/bar/road，不再由此方法直接写车辆 HUD 属性 |
| `clearHud()` | 向仲裁服务提交 `active=false`、零数值及空道路包，只退出共存来源，不直接清空物理 HUD；清除本地缓存 |
| 停止/清理路径 | 先提交退出状态，再关闭并清空客户端，然后继续原有 Car/车辆数据清理流程 |

`navigating` 继续来自 `NaviController.isInNaviStatus()`。导航状态与转向详情分开：不因短暂无新转向详情或原有详情超时就把有效导航当作结束。此轮共存提交的 bar 值为0，道路字节沿用已有格式。

`ownsHud` 在这个改版内记录的是本地导航来源是否活动，不代表 NaviService 最终授予了物理 HUD 所有权；最终来源以服务日志 `ArcfoxHudArbiter` 的 owner 为准。旧私有属性写入辅助方法可能仍存在于 DEX，不能以“类里还有方法”判断活动 HUD 路径仍直接写车机。

原有仪表地图、路口卡片、气泡、方向盘缩放、罗盘、车道、车辆数据、音频适配代码随附加适配层重新编译，但未在此轮改变其功能逻辑。包内除 `classes26.dex` 与签名外的条目保持字节一致。

## 客户端与服务协议

### 当前支持的导航来源

统一 HUD 仲裁目前接收三种来源：原车高德 `com.autonavi.amapauto` 经 NaviService 原有车辆属性写入路径进入仲裁；共存高德 `com.autonavi.amaparcfox` 和 DiPlay `com.shihab.diplay` 通过 Binder 注册并提交导航状态。仲裁器仅允许后两者作为外部客户端注册，并依据已活动来源和前台包名选择输出。

**车机版百度地图尚未作为独立来源接入。** 当前允许注册的包名中没有百度地图，也没有将它的导航数据转换为 `active/icon/distance/bar/road` 的适配路径。因此百度地图在前台并不会自动取得 HUD；只增加包名也不足以支持。后续要先确认它向 NaviService 提交导航状态、转向、距离和道路信息的接口及格式，再实现来源接入、身份校验与前台选择，并在实车验证。iPhone 百度地图经 CarPlay 发送的导航数据属于 DiPlay 来源，不能据此认定车机版百度地图已获支持。

客户端绑定显式组件：

```text
com.adayo.service.navi/local.arcfox.hud.HudArbiterService
```

Binder descriptor 为 `arcfox.hud.sources.v1`：REGISTER(1) 提交客户端存活 Binder 并取得会话代次；UPDATE(2) 提交代次、递增序号和含 active/icon/distance/bar/road 的 Bundle。客户端每2秒提交最新状态，服务每5秒重发最终选中的 HUD。调用失败会解绑并重新注册，不回退到直接写车机属性。

服务入口要求 CAR_VENDOR_EXTENSION，校验调用 UID 对应包及与服务一致的签名；共存 v24 基准包已有 CAR_VENDOR_EXTENSION 和 QUERY_ALL_PACKAGES，本轮没有重写其 Manifest。新包必须与更新后的 NaviService 配套使用；旧服务没有该组件时，共存不会获得新的 HUD 输出。完整语义见 [ARCFOX_PROTOCOL.md](ARCFOX_PROTOCOL.md)。

## 构建与打包

脚本使用本机 JDK21、Android30 编译 API、build-tools30.0.3 的 dx，以及 build-tools36.0.0 的 zipalign/apksigner；路径在 `build.py` 顶部声明。

1. 校验基准 `classes26.dex` 与参考源产物相同。
2. 将 `src/Arcfox*.java` 复制到本工程构建目录，并修改 `ArcfoxVehicle.java`。
3. 独立编译 `stubs/` 供适配层编译时引用；编译桩不随新适配层 DEX 打包。
4. 将全部 `Arcfox*.java` 与共同 `HudSourceClient.java` 编译为 Java8 字节码，用 dx 生成替换 DEX（min-sdk 参数28）。基准 Manifest 的 minSdk24 保持，因此此次验证对象仍是实际 Android11 极狐车机，不能据此声称新增适配适用于 Android24—27。
5. 复制基准 APK 所有非 META-INF 条目，只替换 `classes26.dex`。
6. 去除旧签名，处理资源压缩、执行 zipalign，使用现有 platform.pk8/platform.x509.pem 签名并验证。私钥不复制进源码仓库。
7. 断言非签名条目集合相同，且内容不同的条目严格只有 `classes26.dex`；生成 APK SHA-256 文件。

当前工具同时构建 NaviService 与共存包。需先准备原 NaviService 解码目录和 apktool framework，详见 [工具说明](../tools/arcfox-hud/README.md)。运行：

```sh
cd /Users/h/Documents/Diplay
python3 tools/arcfox-hud/build.py
```

当前源码、编译桩、SDK 和签名输入依赖本机 arcfox 工程；不是只克隆本 Git 仓库即可独立重建共存高德。

## 产物与验证范围

已生成：

```text
/Users/h/Documents/Diplay/build/arcfox-release/高德-比亚迪3.15.872-v24-气泡1.2-共存-HudArbiter1-system.apk
SHA-256: e6224778dba393708d1551dedf35e0e8cd0c27be6ab7491f54806e2898b70a8c
```

系统签名证书 SHA-256 为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。工具的 Java 来源选择回归、共存包条目比较和签名验证已通过。DiPlay 完整 CI、Binder 身份/代次/序号/死亡回调回归也已通过；这些不等于高德导航引擎或车机 HUD 已实测。验证汇总位于 `build/arcfox-release/Arcfox-Media-HudArbiter1-validation.json`。

三包本轮尚未安装，原生高德 APK 未修改。用户本次只要求补充文档，不重建或安装 APK。
