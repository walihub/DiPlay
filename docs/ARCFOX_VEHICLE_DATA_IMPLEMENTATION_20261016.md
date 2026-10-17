# 极狐只读车辆数据接入（2026-10-16）

## 交付边界

本次新增：DiPlay 极狐模式的车速／挡位输入；嘟嘟 fix51 的车速、四门及尾门状态输入。嘟嘟 v2 根据用户后续要求增加桌面四轮胎压／胎温卡片，温度采用本版约定的减 40 偏移，详见末尾。
未修改 NaviService、导航缩放、HUD、电话、蓝牙、媒体控制及 MFI 认证逻辑。媒体、窗口和方向盘已有适配保留，不因这次采集扩大控制范围。

初版 v1 未启用胎压／胎温；后续 v2 按用户确认的本版换算接入卡片。材料尚不足以完整证实单位枚举及 ENABLE 的无效值含义，v2 是明确指定换算的待实车验证版本，不把这一选择视为原厂协议已得到证明。SOC／续航／充电状态未启用。

## 依据

实车资料：`diagnostics/vehicle-readonly-20261016/`。BCM 实现 `VehicleConnection$1.onServiceConnected` 调用 `Car.getCarManager("property")`，并把 `CarPropertyManager` 传给各节点。因此直接走同一只读属性 API，无需复制 APK 中抛出 `Stub!` 的占位 SDK，无需安装桥接 APK。

仪表 `DirectRenderingCluster` 的 `android.car.cluster.sensors.Sensors` 同样使用以下两个标准属性：

| 属性 | ID | 解码 |
|---|---|---|
| PERF_VEHICLE_SPEED | 0x1160010e | Android 标准 m/s；速度取绝对值，方向由挡位表示 |
| GEAR_SELECTION | 0x11400400 | 4=P、2=R、1=N、8=D；16/32/64/128/256 为前进挡 |
| PERF_LE_FRONT_DOOR_STS | 0x21400329 | 稳定值 0=关、1=开 |
| PERF_RI_FRONT_DOOR_STS | 0x2140032a | 同上 |
| PERF_LE_RE_DOOR_STS | 0x2140032c | 左后，不按数值顺序排列 |
| PERF_RI_RE_DOOR_STS | 0x2140032b | 右后 |
| PLG_TR_SWT_STS_INDCN_CALLBACK | 0x2140041c | 仅采用稳定值 0/1；其他状态不当成关闭 |

门状态证据：BCM `DoorNode.loadInitializeData` 属性与字段对应，四门关闭检查要求 OpenState=0，打开检查为 1；尾门原值 0 被转换为内部关闭模式 2，原值 1 为打开模式 1。运动／异常值不发布为 bool。

实车 CarService 记录 speed 属性已有 2 个订阅客户端、gear 有 1 个，但本次没有完成移动中的单位实测。新客户端通过自己的 Car 实例读数，不注销其他客户端、不重启系统服务。最终仍需实车对照速度与挡位。

## DiPlay

- `VehicleAdapters.motionEnabled/wheelSpeed` 按现有 VehicleProfiles 分流：自动识别或手选极狐均使用极狐读取器；比亚迪保持原来源。
- 极狐车速输入默认开启，但尊重已有显式关闭的 wheel_speed_to_iphone 设置。它仍受原有“向 iPhone 提供定位”及 iPhone 请求车辆速度的条件控制。
- 不将比亚迪 ADB 设置默认改成开启，避免因此触发 autoservice 探测。
- 每 250ms 只读速度／挡位；不重复发送缓存的同一时间戳；速度超过 2 秒、无效状态、缺失挡位或不合法数值均不发送。
- 挡位变化清空旧方向的样本。停止／重连清空缓存，旧连接的迟到回调不能关闭新连接。
- 沿用 `$PASCD` 协议及原有定位发送链，未修改 GPS 或认证实现。
- 实车 CAR_SPEED 为 dangerous，CAR_POWERTRAIN 为 normal。读取前仅在已有 GRANT_RUNTIME_PERMISSIONS 权限时给本应用补 CAR_SPEED；无特权则读失败且不伪造数据。此操作与原有麦克风／定位授权互不替代。

## 嘟嘟

基线为实车提取的 installed-com.dudu.autoui.apk，与用户原 fix51 完整 SHA-256 一致：
`6fb558b257d57a7baeb897fadc534342b834d45832625c0c6ab9a45cf292e38b`。

新增非导出初始化 Provider 和 classes4.dex。只在 AAOP_COCKPIT 固件初始化。每秒读取速度和五个门状态，经已有 EventBus 发布：

- `com.dudu.autoui.manage.i.g.u(int)`：已有车速事件，m/s 转 km/h。
- `com.dudu.autoui.manage.i.g.f(boolean × 5)`：顺序为尾门、左前、右前、左后、右后。复用原车门显示及用户现有相关联动，不添加新车辆控制命令。

所有原 dex、资源、assets 构建后逐项比较，要求字节一致；只改 Manifest 并追加新 dex。没有复用带云端上报的胎压回调，也没有加入蓝牙恢复／电话租约。

无效或缺失字段不发布，不造“车门已关”事件。现有嘟嘟门模型只有 bool，不能显示“未知”；失联时可能保留最后一帧，暂不为此改动桌面布局。车速走原有超时回退路径。

## 胎压待补的证据

BCM `TirePressureNode` 的原始属性已明确：

- 单位：0x21400bee（压力）、0x21400bef（温度）。
- 压力有效／状态：0x21400bf0..bf3。
- 温度有效／状态：0x21400bf4..bf7。
- 压力值：0x21400bf8..bfb。
- 温度值：0x21400bfc..bff。
- 每组顺序均为左前、右前、左后、右后。

该节点主要缓存与转发整数，未找到能证明最终工程单位的显示换算。标准 `TIRE_PRESSURE` 不能替代为这些厂商 ID 的单位依据。后续需要实际显示代码／信号定义，或原车显示与各原始字段的成组对照，再启用嘟嘟胎压卡片。显示小数位不足以决定倍率。

## 构建与验证

构建脚本：`tools/arcfox-vehicle/build_dudu.py`、`build_diplay.py`。后者仅复用上一版 APK 的两份本地运行认证素材，最终逐字节核对；临时素材与签名文件在临时目录，结束删除。签名沿用用户 platform.pk8/platform.x509.pem。

测试：ArcfoxMotionBufferTest（数值、挡位、方向变化、时间戳、缓存及重连），ArcfoxCarReaderTest（真实反射边界的假服务、无效状态、旧回调隔离），ArcfoxVehicleInputProfileTest（极狐选择、比亚迪默认、显式关闭），以及 tools/arcfox-vehicle/tests/VehicleValuesTest.java（嘟嘟单位／门位／无效值）。

离线测试不等于车辆验证。首次安装应对照原车车速、五个门和 iPhone 定位数据；不能据此宣称胎压已完成。

最终完整 CI 成功，记录 `build/arcfox-vehicle-verified-retry.log`（255 tasks）。新增三个 shared 测试类合计 9 项通过；嘟嘟独立 VehicleValuesTest 通过。此前两次遇到原有 SettingsBackupServerTest 在 ServerSocket.close 卡住，已保留线程栈，未修改该无关功能。Release 构建和 lint 成功，记录 `build/arcfox-vehicle-release.log`。

产物：`build/arcfox-release/DiPlay-0.2.19-Arcfox-VehicleData-system.apk`、`build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v1_platform_signed.apk`。两个包签名验证通过，证书 SHA-256 均为 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。DiPlay 认证素材与基线逐字节一致；嘟嘟原 dex／资源／assets 逐字节一致。未安装至车机。

### 补充静态追踪

进一步检查已采集的 `android.hardware.automotive.vehicle@2.0-service`，发现路径 `/etc/adayo/vehicle/receive.json`、`/etc/adayo/vehicle/send.json`、`/system/etc/adayo/vehicle/VehicleConfig.json`，以及 BCM 的 `SPLITE_BIT_DATA_INTEGER`／`SPLITE_BIT_DATA_FLOAT` 和 `multi` 转换日志。BCM 的 TirePressureNode 另引用 `/system/etc/adayo/bcm/plugin/carsetting/registIDs.json`。这些是下一步只读提取的具体配置路径；本地当前未找到对应文件。存在通用转换逻辑不等于已证明胎压字段使用某个倍率。

确认方法：优先从 receive.json 及 VehicleConfig.json 查上述 TPMS 属性的信号映射和换算；再同时读取四轮数值、单位及 ENABLE 状态，与原车胎压页面对照。温度应至少有两个不同温度时的对照点，以区分倍率和偏移；无需人为放气或修改单位。若配置仅记录映射、不含工程单位，则仍需显示端代码或协议定义，不能用数值外观代替证据。

已检查本次 CarSetting 的 Java／Smali、Cocos 脚本、资源，及 AAOP_MeterInFoService、DirectRenderingCluster。CarSetting 存在 bar/Kpa 字符串和牵引胎压图资源，但在对应业务代码中尚未找到这些显示值到本车四轮属性的完整换算关系，不能据资源字符串推断倍率。仪表导航投屏 APK 也不等于物理仪表全部车况 UI 的实现。CarService 文本记录了 TPMS 属性的客户端订阅，但没有直接列出这些字段的实时值；实时原始值应结合下述 logcat 证据判断。

### 历史日志补核：存在原始数值记录

此前仅以最近采集和四轮独立字段判断材料不足，遗漏了历史日志中的通用 TPMS 属性。重新扫描 diagnostics 与 arcfox-capture 的文本日志后，确认：

- `0x21400bec`（TPMS_SNGTIRET_CB）：历史原始值 58～67；10 月 16 日为 65／66／67。
- `0x21400bed`（TPMS_SNGTIREP_CB）：历史原始值 243～255；10 月 16 日为 254／255／249。
- `diagnostics/arcfox/2026-10-14/arcfox-repro-20261014.log:83232`：右前温度 `0x21400bfd=62`；84173 行变为 63。BCM 对应消息 `MSG_EVENT_TIRE_TEMPURATURE_RF_VALUE` 同样转发 62／63。
- 对应 CarSetting 日志的 `tempurature=0` 不能当作实际胎温：该回调按固定 `temp_value` 等 key 读取所有消息，遇到右前独立消息时可能读取不到该通用 key，从 Bundle.getInt 获得默认 0。

原始值汇总和示例行保存为 `diagnostics/vehicle-readonly-20261016/tpms-historical-evidence.json`。这些记录可确认数据确实经过 HAL／BCM，但尚未直接给出工程单位公式。压力按 kPa 解释、温度减 40 都只能作为待验证候选，不能作为已确认协议；日志未配套原车显示读数，也未找到单位／有效状态值的完整记录。当前仍不启用推测换算。

用户补充实车四轮胎压显示均在 2.4～2.5 范围。若原车显示单位为 bar，历史原值 243～251 按 kPa 解释后为 2.43～2.51 bar，与该范围一致，支持 `bar = raw / 100.0` 这一候选。该补充不是与日志同时间、逐轮配对的读数，尚不足以确认全部原始值的语义。254／255 还需结合有效状态和显示取舍规则核对，特别不能直接认定 255 是有效压力或无效哨兵值。胎温的单位和偏移不因这一胎压对照而得到确认。

### 胎温单位专项核对结论

2026-10-16 再次核对本地采集后，不能认定 raw=62 表示 62℉，也不能认定其表示 62℃或 raw−40。`(62−32)×5/9=16.67℃` 仅是算术示例，不是极狐协议结论。

1. 历史日志及最近采集中，`0x21400bef`／十进制 `557845487` 只找到 CarService 的客户端注册记录，未找到单位实际值或 `MSG_EVENT_TIRE_TEMPURATURE_UNIT` 的值记录。
2. `TirePressureNode.loadInitializeData` 从该属性读取整数存入 mTempuratureUnit；查询单位只通过 Bundle 的 `value` 返回此整数；事件处理同样直接存储。Bean 的 getter／setter 没有枚举定义和温度换算。
3. CarSetting 的 `BytesHandlerTools.fahrenheitToCelsius` 确实包含 `(value−32)×5/9`，但在已解码 CarSetting 的全部 smali 中没有发现调用该方法的指令，不能证明 TPMS 使用它。
4. VTP 的 CommonFragment 有摄氏／华氏按钮，但 `setTemperatureSettingItem` 只更新按钮选中状态和文字样式，没有读取／写入 TPMS 单位属性的调用；不能拿按钮顺序当作 `0x21400bef` 的枚举定义。
5. 原车 ServeFragment／VtpServeFragment 的胎压监听把四个整数转到 Handler，并未给出胎温单位公式。当前已有的独立右前字段日志只能确认 raw 62→63 经 HAL 和 BCM 原样转发。

本地证据缺口明确为：实际单位枚举值及其定义，或该固件的接收信号换算配置／最终显示换算代码。之前发现的 receive.json／VehicleConfig.json 尚不在本地。找到这些材料前，华氏换算与减 40 都不能进入正式显示逻辑。

## v2 桌面胎压卡片（用户指定偏移版本）

用户在上述专项核对后明确选择“这版用偏移”“接入桌面胎压卡片”。本版因此将胎温减 40 作为指定的验证配置，而非确认了原厂编码。DiPlay APK 不因这个卡片改动重打包。

- 压力读四轮独立属性 0x21400bf8..bfb，按当前候选 `bar=raw/100`；温度读 0x21400bfc..bff，按 `℃=raw−40`。顺序均为左前、右前、左后、右后。禁止把通用 TPMS 单值复制给四轮。
- HAL status 非 0、读取失败、字段缺失均为空；温度仅接受整数 0..254（拒绝 255／65535），压力接受整数 0..1000。未解释的单位和 ENABLE 字段只记录，不擅自指定枚举含义。每 30 秒记录一次所有 TPMS 原值，便于首次实车确认；不依据数值大小自动切换倍率。
- 重要限制：现有压力对照主要来自通用 `0x21400bed`，不能单独证明四轮独立字段也使用同一倍率。MCU 报文还有疑似四轮压力 25／25／25／26 的连续数，映射及比例未证实；若独立字段实测为 25，需要确认是否应除以 10，不能宣称 v2 的压力换算已获实车验证。
- `TyreCardBridge` 更新原 `manage.w.i.f.d()` 缓存，沿用 common.s0.c 的八个 setter；发布原卡片事件。`TyreCardSource` 接入 manager.i() 的数据源就绪检查，避免只发事件但界面拒绝刷新。数据全失效时先清空可见缓存，再标记未就绪；重连重新显示；未拿到任何有效值时不接管原数据源。
- 不调用旧的胎压接收 callback，因此不新增其历史写入和 UserCarService.reportCarInfo 调用。原事件总线上的已有订阅者仍按用户既有设置运行，例如插件转发；不宣称事件只对界面私有。
- 新 source 的 g/h 仅有编译期 ABI 声明，构建检查禁止打包这些嘟嘟占位类。所有原 dex／资源／assets 仍逐字节一致，只改 Manifest 并新增 classes4.dex。

离线验证：`tools/arcfox-vehicle/test.py` 覆盖四轮压力和温度偏移、错误字段、无效及部分缺失；TyreCardBridgeTest 覆盖卡片就绪门控、缓存 setter 对应、数据清空、重连、关闭恢复旧 source。完整仓库 CI 见 `build/arcfox-tyre-ci.log`。系统签名产物为 `build/arcfox-release/Dudu_fix51_ArcfoxVehicleData_v2_TyreCard_Offset40_platform_signed.apk`。
