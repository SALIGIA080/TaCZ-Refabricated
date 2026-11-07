# TaCZ Refabricated

> **Timeless & Classics Guns: Zero（TACZ）官方 1.2.0 正式版的 Minecraft 26.2 / Fabric 移植。**
> **An unofficial Minecraft 26.2 / Fabric port of the official TACZ 1.2.0 release.**

本移植追踪官方 [MCModderAnchor/TACZ](https://github.com/MCModderAnchor/TACZ) 的 **1.2.0 正式版**源码，将其完整移植到 Minecraft 26.2 Fabric。这是一个社区迁移，未经 TACZ 官方团队审核或背书。
This port tracks the official 1.2.0 source and migrates it to Minecraft 26.2 Fabric. It is a community migration, not reviewed or endorsed by the TACZ Dev Team.

---

## 简介 / About

一个**为朋友私人服务器所作**的 Minecraft 26.2 Fabric 移植，将官方 TACZ 1.2.0 的完整功能集迁移到 26.2 的渲染与网络 API 上。
A Minecraft 26.2 Fabric port, originally made for a friend's private server, migrating the full official 1.2.0 feature set onto the 26.2 rendering and networking APIs.

### 主要功能 / Key features

- **瞄准镜镜头效果**：边缘畸变、色差、染色、暗角；夜视、白热/黑热/铁虹热成像；测距仪、弹道落点预测、瞄具弹药计数。
  Scope lens effects: distortion, chromatic aberration, tinting, vignetting; night vision and thermal modes; rangefinder, ballistic prediction, sight ammo counters.
- **枪械挂板与展示柜**：可连接/转角/双面的发光挂板（可染色、带放置预览），可上锁的展示柜。
  Gun pegboards & display cases: connectable, cornered, double-sided, dyeable, with placement previews; lockable cases.
- **弹药系统**：铁/金/钻石三级弹药库，可升级、可携带；默认 `U` 键卸弹。
  Ammo systems: iron/gold/diamond ammo storage (upgradeable, portable); ammo unloading on `U`.
- **自定义开镜**：可配置 ADS 路径与 FOV 预设，分姿后坐力推进。
  Customizable aiming: configurable ADS paths and FOV presets, posture-split recoil.
- **枪包筛选器**：创造模式侧边栏枪包筛选，图标可在元数据中配置。
  Gun pack filter: creative-sidebar filter with metadata-configurable icons.
- **JEI/REI 查询**：附件与弹药查询，支持配方转移。
  JEI/REI queries for attachments and ammo, with recipe transfer.
- **第三方高模渲染**：基于 SimpleBedrockModel（jar-in-jar 内嵌）的基岩模型/动画渲染。
  Third-party HD rendering via SimpleBedrockModel (bundled jar-in-jar).
- **原厂配件**：内置机瞄/护木/枪托，改装界面切换，可移除或展开。
  Factory attachments: built-in iron sights, handguards, stocks, switchable and removable.
- **枪声引怪**：开火吸引附近敌对/中立生物，消音器可避免（可配置关闭）。
  Gunshot aggro: firing alerts nearby mobs unless suppressed (configurable).
- **枪口火光照亮**：开火照亮第一人称枪械与手部。
  Muzzle-flash illumination: firing briefly lights the first-person gun and hand.
- **进度与挑战**：内置 TaCZ 进度树，覆盖合成/改装/收藏/射击。
  Advancements: built-in TaCZ advancement tree.

### 环境要求 / Requirements

| 项目 Item | 要求 Requirement |
|---|---|
| Minecraft | **26.2** |
| Loader | **Fabric Loader 0.19.3+** |
| Java | **25+** |
| Fabric API | 0.155.2+26.2 |
| Forge Config API Port | 26.2.1+（硬依赖 / required） |

仅提供 Fabric 构建，勿与 Forge / NeoForge 版混装。
Only a Fabric build is provided; do not mix with the Forge / NeoForge builds.

---

## 安装 / Installation

1. 安装 Minecraft 26.2、Fabric Loader 0.19.3+ 与 Java 25+。
   Install Minecraft 26.2, Fabric Loader 0.19.3+ and Java 25+.
2. 安装 Fabric API 和 Forge Config API Port 26.2.1+。
   Install Fabric API and Forge Config API Port 26.2.1+.
3. 从 [Releases](../../releases) 下载本 mod 的 `.jar`。
   Download this mod's `.jar` from [Releases](../../releases).
4. 把三个 `.jar` 放入 `.minecraft/mods/`。
   Put the three `.jar`s into `.minecraft/mods/`.
5. 启动游戏。/ Launch the game.

### 第三方枪包 / Third-party gun packs

现代枪包放在 **`.minecraft/tacz/`**（zip 或目录），包根目录必须有 `gunpack.meta.json`；旧布局包放在 `.minecraft/tacz_backup/` 后执行 `/tacz convert` 转换。
Modern gun packs go in **`.minecraft/tacz/`** (zip or directory), root must contain `gunpack.meta.json`; legacy packs go in `.minecraft/tacz_backup/` and convert via `/tacz convert`.

---

## 从源码构建 / Building from source

需要 Java 25。/ Requires Java 25:

```bash
./gradlew build
```

产物位于 `build/libs/`。/ The artifact is in `build/libs/`.

---

## API 迁移对照 / API migration reference

官方 TACZ（NeoForge 1.21.1）到 Minecraft 26.2 / Fabric 的 API 变化、渲染架构迁移、已知有界差异（降级），见 [`docs/API-MIGRATION.md`](docs/API-MIGRATION.md)。
See [`docs/API-MIGRATION.md`](docs/API-MIGRATION.md) for API changes, rendering-architecture migration, and known bounded differences (degradations) from the official TACZ (NeoForge 1.21.1) to Minecraft 26.2 / Fabric.

---

## 已知边界与免责声明 / Known limitations & disclaimer

- 本移植是**个人、非专业的兴趣项目**，本意是为朋友的私人服务器所作。大量代码是「许愿式开发」的产物，
  难免存在 Bug、粗糙之处和非 1:1 的近似实现。
  This is a personal, non-professional hobby project made for a friend's private server. Much of the code is the
  result of "wishful programming", so bugs, rough edges, and non-1:1 approximations are to be expected.
- **可能包含未知 bug**。本仓库按「原样」提供，不附带任何担保。
  **It may contain unknown bugs.** Provided "as is", without any warranty.
- 由于 Minecraft 26.2 渲染架构变更，部分功能为等价降级实现（详见 API 迁移对照文档），
  与官方 1.2.0 存在有界视觉/性能差异。
  Due to Minecraft 26.2 rendering-architecture changes, some features are implemented as equivalent degradations
  (see the API migration reference), with bounded visual/performance differences from official 1.2.0.
- 欢迎有心者参考、提 issue、提 PR。请勿把本移植的问题提交给 TACZ 原作者。
  Anyone interested is welcome to reference, open issues, or submit PRs. Do not report this port's issues to the
  TACZ original authors.

---

## 致谢 / Acknowledgments

本移植的诞生离不开以下项目与贡献者，在此一并致谢（顺序不代表贡献大小）：
This port would not exist without the following projects and contributors (order does not imply magnitude):

- **[MCModderAnchor/TACZ](https://github.com/MCModderAnchor/TACZ)** —— 官方 Timeless and Classics Zero，
  本移植的源头与功能基准。衷心感谢 TACZ Dev Team（ChloePrime 等）的构建与开源。
  The official Timeless and Classics Zero, the source and functional baseline. Thanks to the TACZ Dev Team (ChloePrime et al.).
- **[q14433686-arch/TaCZ_Refabricated_Unofficial](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial)**
  （JIAN_FISH 与参与贡献者）—— 26.2 Fabric 非官方移植（内置 TML 高模渲染，作者 VellEagle），
  **本移植在代码与资源结构上参照最多的来源**。本移植将其高模渲染库由 TML 替换为 SimpleBedrockModel。
  (JIAN_FISH and contributors) — the 26.2 Fabric unofficial port (bundled TML high-poly renderer by VellEagle),
  the source this port references most heavily in code and resource structure. This port replaces its TML renderer with SimpleBedrockModel.
- **[Sh1roCu/TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)** —— 1.21.1 Fabric 移植（版本号
  `0.7.0-forge1.1.8-hotfix`），本移植的源码**格式**与授权链参照（包名 `cn.sh1rocu` 即源于此）。
  The 1.21.1 Fabric port (`0.7.0-forge1.1.8-hotfix`); this port references its source **layout** and license chain
  (the package name `cn.sh1rocu` originates here).
- **[SimpleBedrockModel（26.2 移植）](https://github.com/SALIGIA080/SimpleBedrockModel)** —— 基岩模型/动画渲染库（jar-in-jar 内嵌）。
  其上游为官方 [MCModderAnchor/SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel)（LGPL-3.0），
  完整授权链见 [LICENSES.md](LICENSES.md)。
  Bedrock model/animation rendering library (bundled jar-in-jar). Its upstream is the official
  [MCModderAnchor/SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel) (LGPL-3.0); see
  [LICENSES.md](LICENSES.md) for the full license chain.
- **[MaydayAnimationEngine](https://github.com/286799714/MaydayAnimationEngine)** —— 动画关键帧基础设施（MIT），
  随官方 TACZ 源码链带入的上游依赖。
  Animation keyframe infrastructure (MIT), an upstream dependency inherited via the official TACZ source chain.
- **[LRTactical](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements)** —— 兼容框架所移植的源码部分，
  随官方 TACZ 源码链带入的上游依赖。
  The source portions of the compatibility framework ported here, an upstream dependency inherited via the official TACZ source chain.
- **[MrCrayfish/Framework](https://github.com/MrCrayfish/Framework)** —— 实体同步序列化框架（LGPL），
  随官方 TACZ 源码链带入的上游依赖。
  Entity sync serialization framework (LGPL), an upstream dependency inherited via the official TACZ source chain.
- 以及所有为本移植提供反馈、测试与协助的朋友与贡献者。
  And all friends and contributors who provided feedback, testing, and assistance.

---

## 许可 / License

- 代码 / Code：**GPL-3.0**（与官方一致 / same as upstream）。
- 默认枪包美术资源 / Default gun pack assets：**CC BY-NC-ND 4.0**。
- SimpleBedrockModel：**LGPL-3.0**；MaydayAnimationEngine：**MIT**；MrCrayfish/Framework：**LGPL**。

详见 [`LICENSE`](LICENSE) 与 [`LICENSES.md`](LICENSES.md)。本仓库不主张对上游资源的所有权，
代码兼容某个第三方内容包也不代表取得、转授或改变了该内容包的许可。
See [`LICENSE`](LICENSE) and [`LICENSES.md`](LICENSES.md). This repository does not claim ownership of upstream
assets; compatibility with a third-party content pack does not grant, sublicense, or alter that pack's license.

如任何许可声明、署名或出处标注缺失/错误，请在 issue 中指出，我们会第一时间核实、补正或移除。
If any license notice, attribution, or source annotation is missing or incorrect, please open an issue; it will be
verified, corrected, or removed promptly.

---

## 隐私与数据 / Privacy & data

- 本仓库**不收集、不联网上报**任何用户数据、日志或遥测。
  This repository does not collect or report any user data, logs, or telemetry.
- 提交反馈时请自行对 `latest.log` / 崩溃报告脱敏（其中可能含你的本机路径与账户名）。
  When reporting, please redact `latest.log` / crash reports yourself (they may contain your local paths and account name).

---

## 反馈 / Feedback

请在仓库 Issues 提交，并提供以下信息，以便我们定位问题：
Please open an issue with the following information to help us diagnose:

1. 完整 `latest.log` 或崩溃报告；/ The full `latest.log` or crash report.
2. 本 mod 的版本号，以及 Fabric API、Forge Config API Port 的版本；
   / This mod's version, plus the Fabric API and Forge Config API Port versions.
3. 第三方枪包和可选模组的名称与版本；/ Names and versions of third-party gun packs and optional mods.
4. 复现环境说明：在**只保留本 mod 和两个必需前置（Fabric API、Forge Config API Port）**、
   移除其他所有模组和枪包后，该问题是否仍会出现？这能帮我们判断问题是本 mod 自身引起，
   还是与某个第三方模组/枪包冲突导致。
   / Reproduction note: after keeping only this mod and the two required prerequisites (Fabric API,
   Forge Config API Port) and removing all other mods and gun packs, does the issue still occur? This helps
   determine whether it is caused by this mod itself or by a conflict with a third-party mod / gun pack.

> **维护说明 / Maintenance note**：本项目是个人兴趣移植，作者可能**无心、也无力及时处理每一个 issue**。
> 因此 issue 可能长期得不到回复或修复。**欢迎有心者 fork 并自行开发、修改、修复**——这正是开源的本意。
> This is a personal hobby port; the author may not have the time or energy to respond to every issue promptly,
> so issues may go unanswered or unfixed for a long time. You are welcome to fork and develop/modify/fix it yourself —
> that is the whole point of open source.
