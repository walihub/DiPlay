# 极狐四项修复联合交付

> 此文为早先四项修复候选包的历史记录。Bluetooth/nFore 电话服务补丁因 persistent 系统应用更新限制，均不作为普通覆盖安装包交付。最终按用户要求改为[嘟嘟承接电话服务](ARCFOX_DUDU_PHONE_LEASE_20261015.md)，不使用独立伴随应用，不要混用候选包。NaviService 的原地图缩放修复不因此改动。

日期：2026-10-15。与“bug修复”会话在同一工作区协作，目标为离线修复与代码验证；没有连接或安装车机。不包含地图缩放回弹。

## 修复及职责

| 问题 | 实现 | 所属 APK |
| --- | --- | --- |
| 原车地图无法缩放 | 启动、开机与覆盖安装后确保原 NaviMidwareService 运行；已运行不重复启动 | NaviService |
| 来电电话键灯不同步 | 根据 CarPlay 通话阶段写车辆属性557845456：响铃1、拨号/接通2、结束0 | DiPlay 提交阶段，Bluetooth 写入 |
| 方向盘短按不能接听/挂断 | 报告 PhoneLink 铃声/通话状态并读回确认，使原车按键服务进入电话分支；DiPlay 接收 N6 电话键并调用接听/挂断 | DiPlay、Bluetooth |
| 强停 DiPlay 后蓝牙电话未恢复 | 外部服务持有存活 Binder 和持久恢复记录，客户端死亡后恢复本次断开的目标 HFP | Bluetooth；DiPlay 使用租约接口 |

电话逻辑没有加入 NaviService。蓝牙侧配套 nFore 服务包修正双手机配置的静态缓存，由“bug修复”会话实现。DiPlay 不自行设置双手机开关。原有认证资源与认证流程未作本次修改。

## 配套包

所有交付包位于 `build/arcfox-release/`：

- `DiPlay-0.2.19-Arcfox-FourFixes-system.apk`
- `Bluetooth-Arcfox-PhoneLease-DualCover-system.apk`
- `nFore-BT-UI-service-Arcfox-DualCache-system.apk`
- `NaviService-V01.01.05-HudArbiter2-NaviLifecycle-system.apk`

DiPlay 的新电话路径依赖配套 Bluetooth 服务。服务缺失、签名不匹配或无法确认所有权时，保留原车电话所有权，不回退到进程内断开 HFP。不能仅安装 DiPlay 就视为完成强停恢复修复。

## 验证边界

完整 AGENTS CI 已通过（`build/arcfox-four-fixes-final-ci.log`，BUILD SUCCESSFUL，3m33s）：shared 1470项（1项跳过）、common 1296项、home 6项，总计2771项通过、1项跳过、0失败；包括20项电话服务、3项导航生命周期测试。mobile/home/maphost 的 Debug lint 与构建全部通过。公开源码凭据检查也通过。

Release 的 verifyStandaloneAuthentication、lintRelease、assembleRelease 已通过（`build/arcfox-four-fixes-release.log`，1m29s）。四包均已验证系统签名，证书 SHA256：`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。蓝牙侧两包的 Manifest/资源表保持原包字节不变。

| APK | SHA256 |
| --- | --- |
| DiPlay | `db059e197de095fbabc65d908307f089b20a24df740d418e755d7e16af86e004` |
| Bluetooth | `0ad2b76a7b6d914d558cc395d4d5d8f04bd70670ae01def2540ee470aba4baa8` |
| nFore | `584d84b7d1d8cde7e554301a1c877e8c84a8e4745f2cdcfc62e67f8e63fd6a12` |
| NaviService | `7378a646b7519020d150f38e3bb8f3b5adb3a5d39af3f31c9f1b5601c1b105b4` |

离线服务测试编译 `tools/arcfox-bluetooth/java` 中实际打包的 Java 源码，覆盖目标手机为第二部、正常释放、Binder 死亡、异步断开、服务重启、快速 HFP 广播、另一部手机通话避让、旧会话隔离、签名/UID 拒绝、有线无地址以及明确拒绝断开后立即退出。

实车待验证：来电灯绿、接通灯红、挂断恢复；短按接听及挂断；应用内退出和任务管理器强停后原车 HFP 恢复；另一部手机通话不受影响；覆盖安装/重启后原车地图缩放。PhoneLink 原车服务实际语义、车辆属性授权及硬件时序不能由模拟 Binder 测试代替。

详细证据：[电话诊断](ARCFOX_PHONE_INCIDENT.md)、[电话接口](ARCFOX_PHONE_LEASE_PROTOCOL.md)、[原车缩放诊断](ARCFOX_NATIVE_ZOOM_INCIDENT.md)。
