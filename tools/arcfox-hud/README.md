# 极狐三方 HUD 仲裁

协议和行为记录见 [ARCFOX_PROTOCOL.md](../../docs/ARCFOX_PROTOCOL.md) 的“音乐互斥、Siri 压低和三方 HUD 仲裁”。

共存高德逐项修改及校验方式见 [ARCFOX_GAODE_HUD_PATCH.md](../../docs/ARCFOX_GAODE_HUD_PATCH.md)。尚未实施的导航互斥方案见 [ARCFOX_NAVIGATION_ARBITRATION_PLAN.md](../../docs/ARCFOX_NAVIGATION_ARBITRATION_PLAN.md)。

本工具生成 NaviService 与共存高德的系统签名包，DiPlay 用工程正常 release 流程构建。三个新包配套使用；工具不会调用 ADB 或安装应用。

## 本机输入

- 原车 `arcfox-capture/2026-10-07/NaviService.apk` 的 apktool 解码目录：`build/hud-arbitration/naviservice-base`。
- apktool 解码/构建所需 framework：`build/hud-arbitration/framework/1.apk`。
- 用户指定共存包：`/Users/h/Documents/arcfox/gaode/build/比亚迪3.15.872_极狐适配_v24_气泡缩放1.2_共存版_platform_signed.apk`。
- 与该包 classes26.dex 相同的源与编译桩：`/Users/h/Documents/arcfox/gaode/work/byd872-hud-keepalive-v23/`。
- SDK、JDK21、apktool、系统签名位于本机 arcfox 工程；具体路径见 `build.py`。私钥不复制进本工程。

初次解码用该工程已有 apktool/framework；`naviservice-base` 必须是未修改的原包解码结果。随后运行：

```sh
python3 tools/arcfox-hud/build.py
```

输出在 `build/arcfox-release/`。构建会运行来源选择测试，验证初始化在 Application.onCreate 而非构造函数，检查原共存 DEX 与参考源产物一致，并断言共存包除 classes26.dex 与签名外所有条目保持一致。

`service-src` 也是 common 的单元测试 Java 源目录，实际打包的服务实现受到 `HudArbiterProtocolTest` 验证；不会进入 DiPlay 的生产 DEX。来源选择纯 Java 回归为 `SelectionTest.java`。系统权限、前台获取、车机写入及实际声音需要实车验证。
