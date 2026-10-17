# 极狐电话音频设置与热点更新

基于 0.2.19 极狐适配版；仅移植上游 0.2.20 的热点功能相关提交，不宣称完整合并 0.2.20。

## 电话边界

高级 → 电话音频（实验性），仅极狐模式显示，默认“系统默认”。选择“原车蓝牙优先”后下次连接生效，不主动中断现有会话。

该模式只读取 nFore UiCommand 的 getHfpCallList（101）与 getHfpAudioConnectionState（97）。通话列表前后读取一致、手机地址与本次无线 CarPlay 的 iPhone 一致、SCO 状态为 140 且通话有效后，关闭 DiPlay 对应的 telephony 播放与录音，避免双路通话音频。原车确认来电时抑制 CarPlay alert 铃声，界面保留。未选择此模式时不启动观察服务。

不会发送接听/挂断、蓝牙连接/断开、电话灯、PhoneLink 或音频转移命令。不使用嘟嘟租约，不修改 NaviService 电话职责，不启用双手机功能。原车电话按键不被 DiPlay 消费。

这不是强制 iPhone 改用 HFP：若原车尚未取得通话音频、读取失败、地址不匹配或有线连接无法确认手机，保留 CarPlay 音频。确认接管后，手动切到手机听筒不会重新启动 CarPlay 录音；电话结束或新的原车通话身份出现后重新判定。已有的 BYD Telecom 模拟电话路由在此极狐模式下不启用，避免重复占用。

已确认来电期间的 CarPlay alert 在极狐上使用铃声流 2，不再作为导航流 11；普通导航、Siri 和媒体映射不变。

## 热点

移植 bcc5013a（仅热点文件）、791a89d4、d8e688d3、c55241e3、b2790600、322c4759、ab087735 的热点相关改动：非 BYD 直接尝试系统接口、Android 11 shell 回退、受限隐藏 API 豁免、Android 8 起的支持、主页权限请求，以及热点关闭时的连接提示与继续操作。

保留认证及 USB/MFi 路径。自动开启热点仍受系统能力和权限约束，不能将离线测试视为极狐实车成功。

## 包的职责

- 新 DiPlay：电话可选让出音频、热点移植、此前的仪表 Surface 发布顺序修复。
- 上版 DiPlay：NativeBluetooth-WheelSurfaceFix，供对照回退，没有本次电话选项及热点移植。
- NaviService：HudArbiter3-HudOnly。只处理 HUD 仲裁；不管理电话、蓝牙或 NaviMidwareService 生命周期，也不增加原车缩放逻辑。

验证日志和打包校验结果在 build/arcfox-phone-hotspot-*；本次不连接或安装到车机。

## 验证结果

完整 CI 与 Release lint、verifyStandaloneAuthentication、assembleRelease 全部通过，日志 `build/arcfox-phone-hotspot-verified-build.log`。

- shared: {'tests': 1480, 'failures': 0, 'errors': 0, 'skipped': 1}
- common: {'tests': 1311, 'failures': 0, 'errors': 0, 'skipped': 0}
- home: {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}

设置截图：`common/build/arcfox-phone-screenshots/`，含窄屏、大字体及阿拉伯语。最终 APK 系统签名已核验，旧电话接管类未包含；校验值见 `build/arcfox-release/PhoneHotspot-SHA256.txt`。
