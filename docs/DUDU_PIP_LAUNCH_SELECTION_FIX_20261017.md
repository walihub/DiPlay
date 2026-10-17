# PIP 启动应用选择修复（2026-10-17）

用户确认原窗口标题为“选择导航”，并要求 PIP 候选与嘟嘟隐藏应用设置同步，不再附加其他过滤。

原 `minimalism/g2/m2.y()` 在卡片类型 106 时构建 `m2$f` 导航选择器，排除音乐（含酷我），回调 `d1 -> m2.c(AppInfo)` 又会修改全局默认导航。不能只把酷我从排除表删掉。

修复将类型 106 的 PIP 选择器换为通用 `m2$g`，回调独立保存 `dudu_pip_launch_override/nav_launch_package`，立刻更新当前卡片 `setAppClazz`。初始化、刷新事件、预选、全屏打开等七处读取统一经 `PipLaunchOverride.resolve`；未选过覆盖时回退原默认导航。102/103/104 等其他 PIP 槽不读写此覆盖。现有 router 的多应用槽映射不改。

PIP 候选统一使用 `manage/h/y.e()`，该方法本身执行 `SDATA_HIDE_APPS` 过滤。PIP selector 的额外排除数组改为空，构造参数固定选择 `e()` 分支。设置中的 PIP 映射与基类排除数组同样置空。

严格白名单由 `pip_patch.WHITE_LIST` 定义，共 11 个 smali 文件：

- `minimalism/g2/m2.smali` 与新增 `m2$ArcfoxPipSelect.smali`
- `minimalism/g2/m2$g.smali`
- `dpip/DoublePipRootView$d.smali`、`dpip/ShuDoublePipRootView$d.smali`
- `unbounded/UnboundedRootView$o.smali`、`unbounded/UnboundedMiniRootView$m.smali`
- `launcher/view/BasePipView.smali`、`unbounded/pip/UnBasePipView.smali`
- `nnset/content/home/v2/o.smali`、`nnset/content/more/p3.smali`

`m2.u()` 和 `m2.c(AppInfo)` 保留原默认导航选择与写入逻辑，`m2$f`、`g2/l2` 导航专用候选不改。普通导航设置继续使用原车默认导航；PIP 菜单使用已有“选择应用”文案，无新增资源。

集成：构建脚本在 decoded 树调用 `pip_patch.patch(decoded)`，将 `pip-java` Java 源编入新 dex；common 测试源加入此目录。

验证：

- `python3 tools/arcfox-kuwo/tests/test_pip_patch.py` 通过，检查 11 文件白名单、7 处读取和原导航方法不变。
- `PipLaunchOverrideTest` 的 3 项测试通过：即时更新与重开持久化、其他槽隔离、无效选择。
- AGENTS.md 要求的完整 CI 通过（4 分 37 秒）；2878 项测试，0 失败、0 错误、1 跳过。日志：`build/arcfox-fixes-20261017/ci-pip-v9.log`。
- 独立代码审查未发现遗漏的 PIP 过滤或默认导航配置污染。

产物：`build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v9_PipAppsHiddenSync_platform_signed.apk`，已安装到 Android 11 镜像。使用既有 platform 证书；SHA256：`a21aa3d0ce9371108cd9c8ffee9a922cbb046790246f4d484d2b7db9dce04881`。

与 v8 比较仅 classes.dex/classes6.dex 变化；manifest、资源、assets 和其他 dex 不变。酷我保持原始签名 APK，本次不修改播放器。

镜像只读探针确认：五个实际 PIP 选择器均无额外排除数组，q=false；AppManager.e 包含酷我。日志：`build/dudu-pip-probe/v9-result.log`。临时探针已卸载，原隐藏及独立启动偏好已恢复。隐藏/取消隐藏与独立选择持久化的完整运行交互未完成，持久化由上述单元测试覆盖；不宣称已验证真实车机 PIP 画面。主动启动 Launcher 的探针步骤被自动审批拒绝，后续采用不启动 UI 的只读检查，未绕过限制。
