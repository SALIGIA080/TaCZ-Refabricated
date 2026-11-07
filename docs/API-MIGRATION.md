# TACZ API Migration: 官方 1.2.0 → Minecraft 26.2（Fabric）

> 本表记录将官方 [Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ)
> （NeoForge 1.21.1，官方 1.2.0 正式版）移植到 **Minecraft 26.2 / Fabric** 时，
> 所涉及的 Minecraft / NeoForge / Blaze3D API 变化与对应处理方式。
>
> 目标加载器：**Fabric**。26.1 起 Minecraft 不再混淆，故无 mappings 依赖。

---

## 〇、与上游版本的关系

- 本移植以官方 **1.2.0 正式版**为基线（NeoForge 1.21.1 / Forge 1.20.1）。
- 镜片窗口（Scope Lens）以官方 1.2.0（`MCModderAnchor/TACZ` `1.21.1` 分支）为 1:1 基准。
- 移植过程**参照最多**的是 [q14433686-arch/TaCZ_Refabricated_Unofficial](https://github.com/q14433686-arch/TaCZ_Refabricated_Unofficial)
  的 26.2 Fabric 非官方移植（内置 TML 高模渲染，作者 VellEagle）；本移植将其高模渲染库由 TML 替换为 SimpleBedrockModel。
- 早期源码**格式**与授权链参照 [Sh1roCu/TACZ-Refabricated](https://github.com/Sh1roCu/TACZ-Refabricated)
  （1.21.1 Fabric，版本号 `0.7.0-forge1.1.8-hotfix`，包名 `cn.sh1rocu` 源于此）。
- 许可证：**GPL-3.0**（与官方一致；默认枪包美术资源另有 CC BY-NC-ND 4.0，见 `LICENSES.md`）。

> **叠甲声明**：本移植是个人、非专业的兴趣项目，本意是为朋友的私人服务器所做的
> Minecraft 26.2 移植；大量代码是「许愿式开发」的产物，欢迎有心者参考、提 issue / PR。
> 它不是 TaCZ / LRTactical / SimpleBedrockModel 的官方作品或下位替代，也不受原作者背书。
> 代码授权链为官方 TaCZ（GPL-3.0）→ Sh1roCu/TACZ-Refabricated（GPL-3.0）→ 本移植（GPL-3.0），
> 符合 copyleft 要求；第三方库按各自许可（SBM 为 LGPL-3.0、MAE 为 MIT）内嵌分发，
> 本仓库不主张对上游资源的所有权。如任何许可/署名/出处标注缺失或错误，请在 issue 指出。
> 本仓库不收集、不联网上报任何数据，公开提交已刻意清除本机信息。

---

## 一、纯改名 / 改包（机械替换）

| 旧（1.20.1 / NeoForge） | 新（26.2 / Fabric） | 说明 |
|---|---|---|
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` | 类改名 |
| `net.minecraft.client.renderer.RenderType` | `net.minecraft.client.renderer.rendertype.RenderType` | 改包；另拆出 `PreparedRenderType` |
| `net.minecraft.client.renderer.RenderStateShard` | `net.minecraft.client.renderer.RenderPipeline` | 类改名（26.2 用 pipeline 声明式） |
| `net.minecraft.client.renderer.VertexConsumer` | `com.mojang.blaze3d.vertex.VertexConsumer` | 改包到 Blaze3D |
| `net.minecraft.client.renderer.RenderSystem` | `com.mojang.blaze3d.systems.RenderSystem` | 改包到 Blaze3D |
| `net.minecraftforge.client.event.ScreenEvent` | （无） | Fabric 无逐帧 Screen 事件（见 §三） |

## 二、已移除 / 需重写替代

| 旧（1.20.1 / NeoForge） | 26.2 状态 | 替代方案 |
|---|---|---|
| `MultiBufferSource` | 已移除 | `SubmitNodeCollector` / `PreparedRenderType` 提交式体系 |
| `Tesselator` / `VertexMultiConsumer` | 已移除 | `BufferBuilder` / `SheetedDecalTextureGenerator` |
| `ChunkRenderDispatcher` | 已移除 | collector 架构 |
| `LightTexture` | 已移除 | 光照走新管线 / `BindGroupLayout` |
| `VertexBuffer` | 已移除（改 `GpuBuffer` / `StagedVertexBuffer`） | `RenderSystem.getDevice().createBuffer(...)` |
| `DefaultVertexFormat.EMPTY` | 已移除 | `null` / 空数组 |
| NeoForge `accesstransformer.cfg` | 不适用 | Fabric `accesswidener` |

## 三、渲染架构范式变化（需人工重写）

| 旧范式（即时渲染） | 新范式（26.2 提交式渲染） |
|---|---|
| `MultiBufferSource` + `VertexConsumer` 即时写入 | `SubmitNodeCollector` 提交自定义几何 |
| `consumer.vertex(x,y,z).color(..).uv(..).endVertex()` | `collector.submitCustomGeometry(pose, renderType, (pose, consumer) -> {...})` |
| 手动管线 `RenderStateShard` 组合 | `RenderPipeline.builder()` 链式声明 + `RenderPipelines.register(...)` |
| `RenderSystem.setShader(...)` / uniform 逐项设置 | `BindGroupLayout` + `GpuBuffer`（UBO） |

## 四、RenderPipeline 关键签名对照

| 26.1.x | 26.2 |
|---|---|
| `withVertexFormat(...)` | `withVertexBinding(int index, VertexFormat format)` |
| 在 `VertexFormat` 内给 `Mode` | 单独 `withPrimitiveTopology(PrimitiveTopology)` |
| `withUniform(...)` / `withSampler(...)` | `withBindGroupLayout(...)` |
| `withColorTargetState(...)` | 需 int 索引；另有 `withUnusedColorTargetState(int)` |

- `VertexFormatElement` 改为 record：`(String name, int offset, GpuFormat format)`
- 枚举外移：`VertexFormat.IndexType` → `com.mojang.blaze3d.IndexType`；`VertexFormat.Mode` → `PrimitiveTopology`
- `drawIndexed` 签名增至 5 参：`drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)`
- 深度比较反转：`LESS_THAN_OR_EQUAL` → `GREATER_THAN_OR_EQUAL`
- `Item.inventoryTick` 签名变为 `(ItemStack, ServerLevel, Entity, EquipmentSlot)`

## 五、有界差异（因 26.2 API/架构变更产生的降级，如实记录）

> 以下差异是 **26.2 渲染 API 架构变更**导致的、无法 1:1 等价实现的部分，非实现遗漏。
> 均已经 `javap` 反编译 26.2 本体（`minecraft-merged-deobf-26.2.jar`）逐项核实（见 `HANDOFF.md`「有界差异逐项核实」）。

| 功能 | 上游做法（1.21.1） | 26.2 移植做法 | 差异原因 |
|---|---|---|---|
| 镜片窗口 stencil | 目镜几何 stencil ∩ 正圆 stencil（`stencilFunc`/`stencilOp`） | 目镜掩码直采门限 ∩ 正圆门限（`scope_lens.fsh` 采样 `ScopeMaskRenderer` 画的目镜几何 R=1 覆盖区） | 26.2 `DepthStencilState` 只含深度字段、零模板字段，无模板缓冲公开 API |
| 发光件 energy-swirl | 自定义 RenderStateShard（`energy_swirl` + identity UV） | 自建管线「`energy_swirl` shader + 无混合 + identity UV」 | ~~曾误用 emissive 近似~~ 已修正：`RenderTypes.energySwirl(...)` 在 26.2 仍存在，1:1 对齐 |
| 实例光照（SBM v2） | `MeshIntegerAttributes` 运行时改 UV2 属性来源（256×256 整数属性表 + `glVertexAttribIPointer`） | CPU 顶点副本补丁 + `CommandEncoder.writeToBuffer` 整段回写 | `RenderPipeline.withVertexBinding` 静态声明，无运行时改 UV2 属性来源钩子 |
| 热成像 polygonOffset | `GlStateManager.polygonOffset(-1, -2)` | `DepthStencilState.depthBiasScaleFactor/depthBiasConstant` | `GlStateManager$PolygonOffsetState` 是旧 GL 状态遗留，非管线公开 API |
| 热成像实体范围 | 拦截实体模型 + custom geometry | 只拦截 `submitModel` 路径（custom geometry 不入剪影） | `LivingEntityRenderState` 无 entity 字段（extract→submit 快照架构） |
| ScreenItemVboContext | Forge `ScreenEvent.Render` PRE/POST 逐帧计数 | 「屏幕打开即活跃」保守等价 | Fabric 无逐帧 Screen 事件 |
| PlayerModelRenderState | attackTime/swimAmount 等字段在模型上 | 只快照部件姿态；`captureModelState` 在 PAL 路线返回空恢复器 | 字段迁 `AvatarRenderState`，模型不再持有 |
| 粒子窗口 | `setParticleWindowRenderer`（`BedrockModel.render` 内模板窗口插点） | `FirstPersonRenderHandler.renderParticlesNow(...)` + `renderParticlesInModelWindow()` 返回 true 避免双绘 | `render`→`submit` 快照重构，`SubmitNodeCollector` 取代 `MultiBufferSource` |

> ⚠️ 上表「镜片窗口」「实例光照」「热成像」「ScreenItemVboContext」「PlayerModelRenderState」为架构降级；
> 「发光件」已修复回 1:1；「粒子窗口」为等价接法。均为已知边界，社区复用时请留意。

## 六、Scope 透镜链（镜片渲染）专项

官方 1.2.0 镜片窗口 = **目镜几何 stencil ∩ 正圆 stencil**，`scope_lens.fsh` 无 discard，窗口全靠 stencil。
26.2 无模板缓冲公开 API，等价裁剪由 shader 端「双门限取交集」承担：

1. **目镜掩码门限**：`ScopeMaskRenderer` 用目镜几何本身画 R=1 覆盖区（与官方写 stencil 是同一份几何），
   shader 采样 `Sampler2`，`< 0.5` discard。
2. **正圆门限**：圆心 = 目镜节点 pivot 的 x/y 投影（z 丢弃，对齐官方 `centerX = x*16*90`、固定 z=-90），
   半径 = 官方固定 80 的屏幕等价 `f × (80/90) × (height/2)`（`f = 1/tan(hudFov/2)`）。
   - 圆心用「判别式」：pivot 投影落在目镜 AABB 内用 pivot（规避 AMG10 斜切镜框片污染 AABB 中心）；
     落在 AABB 外（retro_2x 的 pivot z 与镜片本体差 8 方块）回退 AABB 中心。

详见 `HANDOFF-SCOPE-LENS.md`。

## 七、第三方依赖

| 依赖 | 用途 | 许可 | 处理 |
|---|---|---|---|
| [SimpleBedrockModel（26.2 移植）](https://github.com/SALIGIA080/SimpleBedrockModel)（SBM） | 基岩模型/动画加载渲染 | LGPL-3.0 | jar-in-jar 嵌入（`simplebedrockmodel-fabric-2.6.2+mc26.2.jar`） |
| [MaydayAnimationEngine](https://github.com/286799714/MaydayAnimationEngine)（MAE） | 动画关键帧基类 | MIT | jar-in-jar 嵌入 |
| [Forge Config API Port](https://modrinth.com/mod/forge-config-api-port) | 配置系统运行时依赖 | — | 外部依赖 |

> SBM 的完整授权链（衍生作品）：官方 [MCModderAnchor/SimpleBedrockModel](https://github.com/MCModderAnchor/SimpleBedrockModel)
> （LGPL-3.0）→ [Sh1roCu/SimpleBedrockModel-Fabric](https://github.com/Sh1roCu/SimpleBedrockModel-Fabric)
> → [SALIGIA080/SimpleBedrockModel](https://github.com/SALIGIA080/SimpleBedrockModel)（26.2 移植）。
> 本项目内嵌的是 SALIGIA080 的 26.2 移植产物（版本 `2.6.2+mc26.2`）。

---

> 本表为迁移说明，非上游 API 的完整文档。上游 API 的权威说明请以 [MCModderAnchor/TACZ](https://github.com/MCModderAnchor/TACZ) 为准。
