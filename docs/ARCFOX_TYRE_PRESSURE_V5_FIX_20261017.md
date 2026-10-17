# 嘟嘟 v5 胎压十倍偏小修复（2026-10-17）

## 根因

实际 v5 包 `build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v5_KuwoAutoSelect_platform_signed.apk` 反编译后，`build/arcfox-analysis/dudu-v5-pressure/smali_classes4/local/arcfox/vehicle/VehicleValues.smali` 的 tyres 方法在约 421 行加载 `100.0f`，然后 `div-float`。源代码对应 `tools/arcfox-vehicle/java/local/arcfox/vehicle/VehicleValues.java` 的 `pressure / 100.0f`。

它读取的是四轮独立属性 `0x21400bf8..0x21400bfb`。之前选择除以 100 的依据来自另一字段 `0x21400bed` 的原始 243–255，错误地将通用/报文字段倍率用于四轮独立属性。此前文档已经指出这一跨字段倍率尚未确认，并记录 MCU 中疑似四轮 25/25/25/26 的值。最新实测“嘟嘟四轮全部 0.2，原车 2.4–2.5”确认当前结果有十倍倍率错误。

卡片桥直接将计算后的 Float 传给原模型 b/d/a/c(Float)，模型 setter 仅保存该 Float，不做倍率转换。没有其他车速、温度或音乐转换介入该路径。

## 计算及证据边界

四轮独立属性固定改为 `bar = raw / 10.0f`：24→2.4、25→2.5；旧代码24→0.24、25→0.25，卡片显示一位小数时约为0.2。这里按已知属性和实测偏差修倍率，不根据每次读到的大小自动选单位，不把0x21400bed的值复制给四轮。

当前未拿到本次 v5 运行时的逐轮原始日志，不能把24/25描述成这次实际采集到的四轮精确值，也不能由0.2反算唯一原值。采用本次实测范围和既有字段证据确定十倍修正。协议单位枚举/完整有效状态仍不作猜测。

## 修改及验证

- VehicleValues.java：仅独立四轮胎压除数100→10，温度保持原减40。
- VehicleProvider.java：只纠正诊断日志文字为 `wheel pressure=raw/10 bar`。
- VehicleValuesTest.java：四轮24/25/24/25得到2.4/2.5/2.4/2.5，保留缺失、无效、门位、速度及原温度测试。
- 运行 `PYTHONPYCACHEPREFIX=/private/tmp/arcfox-vehicle-test-cache python3 tools/arcfox-vehicle/test.py`，VehicleValuesTest 和 TyreCardBridgeTest 均通过。

未构建最终 APK，未连接或修改车机，未变更温度/音乐/电话/导航。交由主 agent 验收合包。
