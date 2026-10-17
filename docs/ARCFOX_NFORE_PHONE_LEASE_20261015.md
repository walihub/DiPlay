# 电话会话迁移至 nFore 服务

> 已停止部署：原车 nFore、Bluetooth、AdayoBluetoothPhone 均为 persistent 系统应用，常规覆盖更新受 PackageManager 限制。本方案保留为系统镜像/OTA 路径的代码记录，不作为普通 APK 安装交付。最终按用户要求由[嘟嘟承接电话服务](ARCFOX_DUDU_PHONE_LEASE_20261015.md)，独立伴随应用方案也已停止。

2026-10-15，根据用户新的职责要求与“bug修复”协同调整。此版替代同日早先将电话租约放在 Bluetooth.apk 的候选包，旧包与旧散列仅作历史记录，不应混用。

## 职责

- DiPlay：向 nFore 提交 CarPlay 会话存活 Binder、目标手机地址、通话阶段；根据返回的所有权处理电话音频、仪表状态和方向盘电话键。
- nFore_BT_UI_service.apk：双手机开启、目标手机 HFP 切换、进程死亡恢复、电话灯和 PhoneLink 通话状态。唯一新增电话输出写入者。
- Bluetooth.apk：仅修复封面重复更新，不新增双手机开关或电话租约。
- NaviService.apk：原地图缩放服务启动修复及原有 HUD 功能，不参与电话；继续使用已验证的 NaviLifecycle 包。

## 接口与兼容性

DiPlay 显式绑定 `com.nforetek.bt.customer.service/com.nforetek.bt.customer.service.BtService`，action/descriptor 为 `arcfox.phone.lease.v1`。事务1至4与已有协议一致；签名校验、UID核对、Binder死亡监听、持久恢复和旧会话隔离不变。

已直接核对原 nFore APK Manifest：BtService 为 enabled/exported，应用为 persistent、system UID、directBootAware。原 CUSTOMER_SERVICE/proxy 的 Binder 接口保留；租约使用独立 action，不能把新事务发送给旧 vendor Binder。

新 DiPlay 不再绑定 Bluetooth NfJniService，也不回退到进程内断开 HFP。必须配合新版 nFore；服务缺失或所有权未确认时由原车保留电话。没有修改认证资源、认证流程或导航缩放参数。

接口细节见 [电话会话协议](ARCFOX_PHONE_LEASE_PROTOCOL.md)。本次没有连接或改动车机。

## 配套包

统一归档于 `build/arcfox-release/`：

- `DiPlay-0.2.19-Arcfox-NForePhoneLease-system.apk`
- `nFore-BT-UI-service-Arcfox-PhoneDual-v2-system.apk`
- `Bluetooth-Arcfox-CoverOnly-v2-system.apk`
- `NaviService-V01.01.05-HudArbiter2-NaviLifecycle-system.apk`（本轮未改）

这是本地候选包，并不表示车机允许覆盖安装。nFore 是原车 persistent 系统应用，安装限制必须单独核对，不能仅凭同签名判断可以普通覆盖安装。
