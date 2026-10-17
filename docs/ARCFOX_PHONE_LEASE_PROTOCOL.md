# DiPlay 与 Bluetooth 电话会话协议

> 2026-10-15 更新：下述自动启动/电话接管方案已撤回；当前实现见 [HUD-only 与原车蓝牙电话](ARCFOX_HUD_ONLY_NATIVE_PHONE_20261015.md)。历史证据保留，不作为当前安装方案。

状态：2026-10-15，原车蓝牙应用均存在 persistent 更新限制，用户指定由嘟嘟 fix51 承接电话租约，不增加独立伴随应用、不覆盖原车蓝牙 APK。DiPlay 与嘟嘟使用下述合同，实车验证待用户执行。NaviService 不参与电话。

## 绑定与鉴权

- 显式组件：`com.dudu.autoui/local.arcfox.phone.PhoneBridgeService`
- Intent action、Binder descriptor：`arcfox.phone.lease.v1`
- 服务要求签名权限 `com.dudu.autoui.permission.ARCFOX_PHONE_LEASE`，DiPlay 声明 uses-permission 与嘟嘟包可见性。
- 绑定前启动服务（Android 8+ 使用 startForegroundService），嘟嘟中的服务建立前台/started 生命周期；不能仅依赖 BIND_AUTO_CREATE，否则 DiPlay 退出会失去最后绑定。
- 原 nFore 的 CUSTOMER_SERVICE/proxy action 和 Binder 保持原样；嘟嘟作为客户端调用 UiCommand，不覆盖原服务。
- 不挂接嘟嘟的打开/关闭应用菜单，也不根据前台窗口判断电话归属。电话会话由 CarPlay 连接、通话阶段和 lifetime Binder 管理。
- 客户端绑定前检查签名一致；服务端必须核对调用 UID 所属包为 `com.shihab.diplay` 且签名一致，不能只信任报文中的包名。
- 每个请求开头是 interface token，然后 `writeString("com.shihab.diplay")`。
- 每个响应先 `writeNoException`；失败使用 Binder exception 或明确的拒绝值。

## 事务

| 事务码 | 请求剩余字段 | 响应 | 含义 |
| --- | --- | --- | --- |
| 1 | lifetime Binder、目标蓝牙地址（可空） | long lease；0 拒绝 | 建立会话；同一 lifetime 的重试必须幂等 |
| 2 | long lease | int 0/1 | 0 未获得电话所有权；1 服务确认 CarPlay 可负责电话 |
| 3 | long lease、int phase | int 0/1 | 提交通话状态，返回是否接受 |
| 4 | long lease | int 0/1 | 结束会话、触发恢复；不能释放另一代会话 |

phase：0 空闲、1 响铃、2 拨号、3 接通。它独立于导航/HUD 生命周期。只停止导航不能释放电话会话。

## 服务端必须保证

- 在断开 HFP 前持久化恢复地址，成功建立 Binder 死亡监听；请求成功不等于实际连接状态已改变。
- 按目标地址查询/断开/恢复，不能仅查主手机，也不能 `reqBtDisconnectAll`。DiPlay 和嘟嘟均不设置双手机开关，相关开启代码已按用户要求删除。
- 目标地址为空时，仅在 txn215 确认 HFP 列表为空且无原生通话时授予输出权限；不调用 txn95/98/99，不盲目断开任何手机。
- Binder 死亡和正常释放走同一恢复机制；服务自身重启后读取持久化记录恢复。
- 成功或结果未知的断开请求需要观察目标断开再连回，才能清除恢复记录；失败保留记录并重试。明确拒绝且没有此前未决请求时，保持连接即可完成恢复，不等待不存在的断开。
- 旧 token/lease 的迟到通知不能影响新会话。
- 服务端统一写电话灯和 PhoneLink；DiPlay 最终只提交 phase，不能保留第二个并行写入者。
- 只清理本会话发布的电话状态；另一部手机原生来电/通话时不覆盖它的灯或通话状态。

## 原车输出参考

灯通过 CarPropertyManager `setIntProperty(557845456, 0, value)`：响铃为1，拨号/接通为2，空闲为0。原车实测证实。

PhoneLink：组件 `com.adayo.phonelink/com.adayo.phonelink.PhoneLinkService`，action `com.adayo.action.PhoneLinkService`，descriptor `com.adayo.phonelinkproxy.IPhoneLink`。

- 事务47 reportPlayStatus：token、int active、int source=3、int volume。铃声volume=5，通话volume=2。
- 阶段切换先 START 新类型再 STOP 旧类型，避免响铃到接通出现短暂空闲。
- 事务5 getPhoneLinkData：token、int source=3；响应 exception、int Parcelable存在标记、byte connection、byte navigation、byte phoneCall，随后还有其他字段。
- 原生按键服务通过 phoneCall 判断是否发 N6 InputKey=5 的短按广播，因此必须读回确认；实际服务未反编译完整，模拟合同测试不能代替真实服务语义验证。

## 客户端退化行为

服务缺失、签名不匹配、请求失败或所有权未确认时，不能回退到 DiPlay 进程内直接断 HFP。否则强制停止后仍无法恢复。绑定/请求异常用同一 lifetime 重试；正常关闭时确认释放后解除绑定。
