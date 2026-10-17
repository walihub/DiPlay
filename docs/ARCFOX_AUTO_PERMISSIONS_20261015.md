# 极狐麦克风与定位自动授权

## 修改范围

仅 DiPlay 自身的 RECORD_AUDIO、ACCESS_COARSE_LOCATION、ACCESS_FINE_LOCATION。不申请后台定位，不授予其他应用权限，不调整电话、HUD 或音频路由。

系统签名不等于 Android 危险权限会自动授予。Manifest 已声明 GRANT_RUNTIME_PERMISSIONS，本次保留该声明，并在系统实际授予该特权后调用 PackageManager.grantRuntimePermission，目标限定为自身包名和当前用户。操作后重新检查权限，不能仅以 Binder 调用未抛异常作为成功。

旧实现仅在 CarPlayHostActivity 初始化时尝试，首页及覆盖安装入口缺失；车辆判断只看 AAOP_COCKPIT 指纹。新版采用现有 VehicleProfiles 极狐模式（包含自动识别与手选），并补齐：

- 首页 onCreate，在渲染及自动连接前检查。
- CarPlay 投屏页，继续保留原入口。
- BOOT_COMPLETED，独立于是否启用自动连接。
- MY_PACKAGE_REPLACED，仅修复授权，不打开界面或发起连接。

首次全新安装通常不会启动应用进程，因此在第一次打开 DiPlay 时完成。覆盖安装收到系统更新广播后自动补齐；若 OEM 不发送广播，下次打开或开机仍可补齐。未安装为平台签名、系统未授予 GRANT_RUNTIME_PERMISSIONS 或 OEM 拒绝授权时，保留原手动权限申请流程。无需联网或提前授权本机 ADB；旧自动授权流程中的 ADB 回退已移除，避免阻塞启动。

仅补缺失权限，重复运行无副作用。不改 system UID，不修改原车权限管理或安装器。

## 验证

完整 CI 与系统签名 Release 构建日志：`build/arcfox-auto-permissions-verified.log`。回归测试覆盖已有权限保留、重复执行、非极狐/无特权时跳过、单项拒绝后继续其他项，以及授权调用成功但权限未生效时报告失败。无车机在线验证，实机以“设置 → 应用 → DiPlay → 权限”和 DiPlay-ArcfoxPermissions 日志为准。

首次全量运行在既有 SettingsBackupServerTest.stopDoesNotFailWhileConnectionsAreEnding 的 ServerSocket.close 中停滞；保存堆栈为 build/arcfox-auto-permissions-test-threads.txt，终止该测试进程后重新执行完整 CI，没有修改备份服务代码。

最终完整 CI、Release lint、认证验证与构建均通过。自动授权回归测试 4 项通过。APK：build/arcfox-release/DiPlay-0.2.19-Arcfox-PhoneAudio-Hotspot-AutoPermissions-system.apk。签名证书 SHA-256：c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8。
