# 极狐蓝牙系统 APK 补丁

输入为只读抓取的 `arcfox-capture/2026-10-14-reboot-risk/Bluetooth.apk` 与
`nFore_BT_UI_service.apk`。运行 `python3 tools/arcfox-bluetooth/build.py` 会保留原 APK 的
二进制 Manifest 和资源，只替换 Dalvik 代码，以原车平台证书签名到 `build/`。

`Bluetooth.apk` 只负责 AVRCP 封面更新：

- 将 AVRCP 封面取图按设备及句柄限频，标题变化、切歌或断连时重置；广播按图片字节去重，并只发送给已确认的原车蓝牙电话接收包。

`nFore_BT_UI_service.apk` 统一管理蓝牙电话和 CarPlay 的 HFP 会话：在自身
`BtService` 启动完成后调用原有 `UiCommand.setDualDeviceEnable(true)`；同时修复该
setter 只持久化 SharedPreferences、未同步静态 `Competitor.dualDeviceEnable` 的问题。
DiPlay 以 `arcfox.phone.lease.v1` 绑定现有导出的 `BtService`，按目标地址租借 HFP；
DiPlay 进程死亡后由 nFore 恢复目标 HFP、电话灯和 PhoneLink 输出。原有
`CUSTOMER_SERVICE` 与 proxy Binder action 保持原样。只对平台签名的 DiPlay
开放新会话事务，其他调用者不能取得会话。

封面广播已限定原车蓝牙电话包并去除每秒重复发送。该广播 action 仍不是 Android
受保护广播；首张封面等必要更新在部分固件上仍可能产生一次 `am_wtf`。彻底消除这类
告警还需将 action 声明为受保护广播或改造接收端权限，当前保留原厂二进制 Manifest
与资源以降低系统蓝牙更新风险。

原服务应用是 persistent、system UID 的 `/system/app` 包。AOSP PackageManager 对
普通替换安装明确拒绝 persistent 应用更新；不能将平台签名视为可通过 `adb install -r`
部署的证据。目标固件是否存在厂商例外尚未验证，通常需要 OEM 系统更新渠道。
更新时蓝牙连接会中断，且若 nFore 更新失败，新的 DiPlay 电话租约不能绑定。
部署顺序须先更新 nFore 服务，再更新 Bluetooth，最后更新 DiPlay；当前尚未在车机安装或验证。
车机测试至少覆盖：两部手机 HFP 同时连接；第二手机
来电时 DiPlay 不覆盖原生电话；DiPlay 正常退出及强制停止后目标 HFP 恢复；固定封面句柄
手机切歌换封面；暂停时封面广播不再每秒重复；原车蓝牙电话封面仍正常。

原包与补丁包平台证书 SHA-256 均为
`c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`。
# 当前交付入口（2026-10-15）

当前使用 `build_dudu.py`，把电话租约服务加入用户指定的嘟嘟 fix51，配合 DiPlay 客户端。`build.py` 的 Bluetooth/nFore 系统补丁及 `build_bridge.py` 的独立应用仅为历史方案，不用于本轮安装。详见 `docs/ARCFOX_DUDU_PHONE_LEASE_20261015.md`。下文旧系统包说明保留作诊断记录。
