# DiPlay / 嘟嘟：车辆数据与接口清单

## 分析基线与证据等级

2026-10-16，DiPlay 当前工作区与 Dudu_PIP_fix51_高德关闭保活_其他应用退出后台_platform_signed.apk 对应解包目录 `/Users/h/Documents/arcfox/tmp/dudu_fix51`。未连接车机；不假定这是车上当前已安装的嘟嘟版本。

- 已实现：DiPlay 中已有的调用与协议；仍需核对具体固件的运行效果。
- 模型存在：嘟嘟包含对应字段、UI 或 SDK，不代表极狐的数据源已经接通。
- 待核实：极狐服务名、属性 ID、单位、有效值及订阅权限需要实车或对应服务 APK 证据。

## 数据清单

| 信息 | DiPlay 用途 | 嘟嘟用途/证据 | 极狐核实重点 |
|---|---|---|---|
| 经纬度、时间、精度、定位方向/速度、海拔 | Android 定位发送 iPhone；可选 | 桌面地图/定位相关功能需按实际配置核对 | LocationManager 可用 provider、定位新鲜度；定位速度不能当作 CAN 车速 |
| 真实车速、P/R/N/D、时间戳 | 车速辅助定位；原版读取针对 BYD | 通用 S2CCarInfo 有 speed；挡位用于可选倒车/停车联动，当前极狐接线未确认 | 真实车速来源、单位、挡位枚举、数据更新周期 |
| SOC、剩余续航、充电状态 | 已有 iAP2 电动车状态协议，极狐源未接入 | 电动车桌面卡片的候选数据，不能从通用 S2CCarInfo 推断已支持 | SOC 与仪表显示值差异、续航算法、充电状态枚举 |
| 剩余/总电量 Wh、满电续航、充电接口类型 | 电动车状态增强字段；部分可省略/配置 | 可选电量展示扩展，未确认现有极狐绑定 | BMS 可用能量与额定容量区分；不可用值不能填 0 |
| 四轮胎压、胎温 | 当前不需要 | S2CCarTirePressure / S2CTirePressureInfo 含四轮压力和温度 | 四轮顺序、kPa/bar、胎温单位、失联/无效标记 |
| 四门、机盖、后备箱 | 当前不需要 | S2CCarDoor 明确包含六个开闭字段 | 极性、左右前后对应、上电初始快照 |
| 电压、转速、温度、油耗等 | 当前不需要 | S2CCarInfo 与 OBD/driver 模型存在；主要是跨车型通用字段 | 电压具体是低压还是动力电池；燃油车字段不作为极狐必需项 |
| 大灯/昼夜、ACC/上下电、休眠唤醒 | 如需原车状态驱动主题/连接，可作为扩展；不是全部已接入 | 桌面主题和生命周期联动候选，具体绑定待查 | 明确用户是否需要，再核对状态接口；Android UI 夜间模式和车灯信号分开 |

## 交互接口（与车况数值分开）

1. **方向盘事件**：缩放、切歌、语音、短按/长按、按下/抬起。DiPlay 极狐缩放已有被动 HIDL serviceId=14，解析 Navi / guidecontrol 的 zoom up/down。绝对缩放级别不是现接口能力；电话键由原车处理。需要确认嘟嘟是否也消费相同事件，不能默认两个应用都应该响应。
2. **媒体中心与音频**：当前音频源、播放状态、标题/歌手/封面、媒体键和音频焦点；读取状态与播放/暂停/切歌命令应分开。嘟嘟有 ArcfoxMediaCenterSdk / IMediaCenterSdk 及回调接口，DiPlay 有极狐媒体适配。实车确认服务组件、回调范围、多客户端共存及源编号。
3. **电话状态**：原车负责接听/挂断和蓝牙电话；DiPlay 可选只读观察同一 iPhone 的原车通话与 SCO 状态，以让出通话音频。当前 nFore UiCommand 已知读取为 101（通话列表）、97（主手机通话音频状态）。嘟嘟不因本清单恢复旧 HFP 租约/强制恢复逻辑，也不启用双手机。无需采集完整号码或联系人。
4. **HUD / 仪表显示**：导航信息从地图/CarPlay 向车机输出，不属于车辆传感器输入。维持 NaviService HUD-only 边界；仪表窗口、路口卡片、透明区域等是显示接口，不能混入车况采集服务。
5. **任务、屏幕与画中画**：嘟嘟 fix51 使用虚拟显示器/任务迁移/关闭应用；DiPlay 需要明确真实仪表地图是否可见。前台包名、可见任务、displayId、bounds、焦点是 Android 窗口信息，并非 CAN 数据。PIP 可见不等于获得焦点；关闭普通应用可能 force-stop，不能靠 Activity.onDestroy 保证清理。
6. **服务可用状态**：两边都应区分未知、有效、过期、服务断开。嘟嘟 S2CCarState 已包含 infoReady、doorReady、tirePressureReady、fkReady，适配时应正确反馈，而不是用零值冒充成功。

## 下次连接 ADB 的只读定位计划

先确认设备序列号，所有命令显式 `adb -s <serial>`，不安装、不清空日志、不启停服务、不调用未知 Binder transaction、不写属性。

1. 记录 ROM 指纹、Android 版本、当前用户；核对 DiPlay、嘟嘟、原车仪表、车况服务、媒体中心、蓝牙与 NaviService 的实际版本/组件。
2. 获取 `service list`、`dumpsys -l`、支持时的 `lshal`，区分 Android CarService、厂商 Binder、HIDL、应用 service；不能仅凭服务名认定数据来源。
3. 只读 `dumpsys package <包名>` / `dumpsys activity services`，检查注册服务、权限、共享 UID、绑定关系；列清单后只提取相关且本地缺失/版本不一致的 APK 与必要 framework。
4. 记录 `dumpsys location`、`dumpsys audio`、`dumpsys media_session`、`dumpsys display`、`dumpsys activity activities`、`dumpsys window`；仅对确实存在且支持 dump 的车辆服务执行查询。
5. 使用有边界时长的 logcat 记录操作前后对照，不清空原日志。停车条件下人工操作车门、大灯、媒体源、方向盘；挡位/车辆运动数据使用已有历史日志或安全的现场流程，不能用静止时的 0 证明车速接口正确。充电数据等待实际状态变化。
6. 静态确认 getter/订阅协议后，再用只读诊断程序验证：初始值、变化回调、两个客户端同时订阅、单个客户端退出/崩溃、服务重启后的重连。若需安装探针或改变运行状态，另行明确，不混同纯日志采集。

每个字段记录：服务/接口/方法或 propertyId、读写性质、原始值与解码值、单位/倍率、枚举、采样频率、时间戳/新鲜度、权限/UID、回调是否多客户端、服务死亡行为。敏感地址与位置日志保留在本地，分享时脱敏。

## 实施顺序

P0：真实车速、挡位、SOC、剩余续航；方向盘/媒体多客户端观察；核对窗口可见性。
P1：四门/机盖/尾门、胎压/胎温、充电状态。
P2：剩余/总能量、原车主题/休眠联动以及用户实际需要的桌面扩展。

优先共用数据格式和无副作用读取库，不直接把“统一服务”放入 NaviService。先确认厂商是否支持多客户端；如确需单点订阅，再单独设计共享服务归属与生命周期。读状态与发控制命令分层，避免一方退出影响另一方。

## 源码证据索引

DiPlay：AndroidCarPlayLocationProvider.kt、transport/VehicleSpeedNmea.kt、transport/Iap2VehicleStatus.kt、hud/BydVehicleFields.kt、vehicle/arcfox/ArcfoxNativePhone.kt、ArcfoxWheelZoom.kt。
嘟嘟解包：smali_classes2/com/wow/dudu/commonBridge/warp/carinfo/s2c/S2CCarInfo.smali、S2CCarDoor.smali、S2CCarTirePressure.smali、S2CCarState.smali；warp/driver/s2c/S2CCarInfo.smali；com/arcfox/media/center/ArcfoxMediaCenterSdk.smali。PIP 行为参考 /Users/h/Documents/arcfox/docs/DUDU_PIP_FIX51.md。

## 本次采集后的实施结果

2026-10-16 后续已完成只读实车采集，资料在 `diagnostics/vehicle-readonly-20261016/`。以上“未连接车机”描述是最初分析时点，最新实现和证据边界见 [极狐车辆数据接入](ARCFOX_VEHICLE_DATA_IMPLEMENTATION_20261016.md)。

本次启用 DiPlay 极狐车速／挡位、嘟嘟车速／四门及尾门。胎压／胎温只确认了原始字段，工程单位和无效值未核实，未启用显示；不把 SDK 字段存在当成实车已验证。

后续 v2：按用户“这版用偏移，接入桌面胎压卡片”要求，增加嘟嘟桌面胎压／胎温显示。胎温采用 raw−40；压力暂固定 raw/100 bar，四轮独立字段倍率仍待实车核对。详见实现文档的 v2 小节。该选择不等于原厂单位枚举已确认。
