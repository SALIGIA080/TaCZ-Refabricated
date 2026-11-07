package com.tacz.guns.client.model.functional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.IFunctionalSubmitter;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.particle.ThirdPersonMuzzleParticleManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import it.unimi.dsi.fastutil.Pair;
import com.tacz.guns.util.time.GunTime;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

public class MuzzleFlashRender implements IFunctionalSubmitter {
    private static final SlotModel MUZZLE_FLASH_MODEL = new SlotModel(true);
    /**
     * 50ms 显示时间
     */
    private static final long TIME_RANGE = 50;
    /** 【FlashDebug 探针】上次日志输出的墙上时钟（1s 节流，挂 ScopeMaskDebug 总开关）。 */
    private static long flashDebugLastLogMs = 0L;
    public static boolean isSelf = false;
    private static long shootTimeStamp = -1;
    private static boolean muzzleFlashStartMark = false;
    private static float muzzleFlashRandomRotate = 0;
    private static Matrix3f muzzleFlashNormal = new Matrix3f();
    private static Matrix4f muzzleFlashPose = new Matrix4f();
    /** 1025：第三人称枪口贴图火焰的按实体时间戳（与第一人称 shootTimeStamp 分离）。 */
    private static final Map<LivingEntity, Long> TP_SHOOT_TIMESTAMPS = new WeakHashMap<>();

    private final BedrockGunModel bedrockGunModel;

    public MuzzleFlashRender(BedrockGunModel bedrockGunModel) {
        this.bedrockGunModel = bedrockGunModel;
    }

    public static void onShoot() {
        // 记录开火时间戳
        shootTimeStamp = GunTime.nowMillis();
        // 记录枪口火焰启动标记
        muzzleFlashStartMark = true;
        // 随机给予枪口火焰的旋转
        muzzleFlashRandomRotate = (float) (Math.random() * 360);
    }

    /** 1025：第三人称开火时按实体登记时间戳，供 extract 的第三人称分支消费。 */
    public static void onEntityShoot(LivingEntity entity) {
        TP_SHOOT_TIMESTAMPS.put(entity, GunTime.nowMillis());
    }

    private static void renderMuzzleFlash(GunDisplayInstance display, PoseStack poseStack, BedrockModel bedrockModel, long time) {
        MuzzleFlash muzzleFlash = display.getMuzzleFlash();
        if (muzzleFlash == null) {
            return;
        }
        // 【1025 对齐 · renew 火焰不渲染贴图大面片】与 extract() 同因，renew 枪火焰
        // 交给 Snowstorm 粒子，旧贴图大面片链（render → renderMuzzleFlash）亦需跳过。
        if (muzzleFlash.isRenew()) {
            return;
        }
        if (muzzleFlashStartMark) {
            muzzleFlashNormal = new Matrix3f(poseStack.last().normal());
            muzzleFlashPose = new Matrix4f(poseStack.last().pose());
        }
        bedrockModel.delegateRender((poseStack1, vertexConsumer1, transformType1, light, overlay) -> doRender(light, overlay, muzzleFlash, time));
    }

    private static void doRender(int light, int overlay, MuzzleFlash muzzleFlash, long time) {
        if (muzzleFlashNormal != null && muzzleFlashPose != null) {
            float scale = 0.5f * muzzleFlash.getScale();
            float scaleTime = TIME_RANGE / 2.0f;
            scale = time < scaleTime ? (scale * (time / scaleTime)) : scale;
            muzzleFlashStartMark = false;
            // 【2026-08-11 核实】枪口火光的正式实现在 extract()（submitCustomGeometry
            // 双层提交，含镜内掩码裁剪），在役正常。本 doRender 属于旧即时渲染链
            // （render → renderMuzzleFlash → delegateRender → 本方法），整条旧链在 26.2
            // 无调用方（BedrockModel.renderInto 零调用），保持空实现；勿在此续写。
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void extract(ExtractionContext context) {
        // 【RecoilDebug 隔离】第 27.4 轮：运行时关闭枪口火光，用于定位斜向"后坐力固定侧偏"的视觉载体
        if (com.tacz.guns.config.client.RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH != null
                && com.tacz.guns.config.client.RenderConfig.DEBUG_DISABLE_MUZZLE_FLASH.get()) {
            return;
        }
        if (IrisCompat.isRenderShadow()) {
            return;
        }
        // 1025：区分第一/第三人称。
        // 第一人称：仅自机（isSelf）在射击窗口内渲染，用 shootTimeStamp。
        // 第三人称：按当前渲染实体（currentEntity）查 TP_SHOOT_TIMESTAMPS，
        //           命中时间窗才渲染贴图火焰（对齐 1025 的 render 第三人称分支）。
        boolean firstPerson = context.displayContext() != null && context.displayContext().firstPerson();
        long time;
        if (firstPerson) {
            if (!isSelf) {
                return;
            }
            time = GunTime.nowMillis() - shootTimeStamp;
        } else {
            LivingEntity entity = ThirdPersonMuzzleParticleManager.currentEntity;
            if (entity == null) {
                return;
            }
            Long ts = TP_SHOOT_TIMESTAMPS.get(entity);
            if (ts == null) {
                return;
            }
            time = GunTime.nowMillis() - ts;
            if (time < 0 || time > TIME_RANGE) {
                TP_SHOOT_TIMESTAMPS.remove(entity);
                return;
            }
        }
        if (time < 0 || time > TIME_RANGE) {
            return;
        }

        ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();
        GunDisplayInstance display = TimelessAPI.getGunDisplay(currentGunItem).orElse(null);
        if (display == null || display.getMuzzleFlash() == null) {
            return;
        }

        ItemStack muzzleAttachment = bedrockGunModel.getCurrentAttachmentItem().get(AttachmentType.MUZZLE);
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(muzzleAttachment);
        if (iAttachment != null) {
            var index = TimelessAPI.getCommonAttachmentIndex(iAttachment.getAttachmentId(muzzleAttachment)).orElse(null);
            if (index != null) {
                var modifier = index.getData().getModifier();
                if (modifier.containsKey(SilenceModifier.ID)
                        && modifier.get(SilenceModifier.ID).getValue() instanceof Pair<?, ?> pair
                        && ((Pair<Integer, Boolean>) pair).right()) {
                    return;
                }
            }
        }

        MuzzleFlash muzzleFlash = display.getMuzzleFlash();
        // 【1025 对齐 · renew 火焰不渲染贴图大面片】官方 1.2.0 的 MuzzleFlashRender
        // 在 render() 里对 `muzzleFlash.isRenew()` 直接 return：renew 类型枪（如 TEC-9）
        // 的火焰完全交给 Snowstorm 粒子系统（first_person_flash 粒子），不再叠加贴图
        // 大面片。移植版此前漏掉该判断，导致 renew 枪同时渲染贴图大面片 + Snowstorm
        // 粒子，贴图大面片盖在粒子散花之上，正是用户所见「一坨粘连、无像素散花」的根因。
        if (muzzleFlash.isRenew()) {
            return;
        }
        float scale = 0.5f * muzzleFlash.getScale();
        float scaleTime = TIME_RANGE / 2.0f;
        if (time < scaleTime) {
            scale *= time / scaleTime;
        }
        float frozenScale = scale;
        float frozenRotation = muzzleFlashRandomRotate;
        PoseStack frozenPose = context.poseStack();
        int light = context.light();
        int overlay = context.overlay();
        muzzleFlashStartMark = false;

        // 【镜内裁切 · 枪口火光大面片层】开火后坐瞬间枪身下沉，火光会短暂探进目镜口径，
        // 在镜片里糊一块亮斑。透视口径契约要求口径内一切视模像素都不出现，
        // 故掩码就绪时把大面片换成裁剪版渲染类型（blend/深度/着色与 vanilla
        // entityTranslucent 逐状态一致，仅多一步掩码 discard）。
        // 辉光涡旋层（energySwirl）因其 26.2 shader 被折叠进共享实现、未逆向确认，
        // 本轮不动 —— 残余至多是镜内仍见缩半的柔光，不属于回归风险。
        // （本 extract 在枪模 functional 遍历中被调用，时序晚于瞄具登记掩码，故此刻
        //   读 ScopeBodyRenderTypes.maskReadyForViewmodel 即可拿到当帧结果。）
        boolean flashMaskReady = com.tacz.guns.client.render.scope.ScopeBodyRenderTypes
                .maskReadyForViewmodel(context.displayContext() != null && context.displayContext().firstPerson());
        // 【FlashDebug 探针】用户实测「枪身/配件已裁、火光仍在镜内」——时序桌面推演机制上
        // 成立，需要现场确认是哪个分量把开关打掉了。挂在 ScopeMaskDebug 下（同属掩码诊断），
        // 火光窗口内 1 秒节流，常态零噪音。
        if (com.tacz.guns.config.client.RenderConfig.SCOPE_MASK_DEBUG.get()
                && System.currentTimeMillis() - flashDebugLastLogMs > 1000L) {
            flashDebugLastLogMs = System.currentTimeMillis();
            com.tacz.guns.GunMod.LOGGER.info("[TACZ FlashDebug] ready={} ctx={} enable={} irisUnsafe={} geomEmpty={} targetOk={}",
                    flashMaskReady,
                    context.displayContext(),
                    com.tacz.guns.config.client.RenderConfig.SCOPE_MASK_ENABLE.get(),
                    IrisCompat.shouldDisableScopeMaskUnderShaderPack(),
                    com.tacz.guns.client.render.scope.ScopeMaskGeometry.isEmpty(),
                    com.tacz.guns.client.render.scope.ScopeMaskTextureHandle.syncToMaskTarget());
        }
        RenderType flashQuadType = flashMaskReady
                ? com.tacz.guns.client.render.scope.ScopeBodyRenderTypes.flashTranslucent(muzzleFlash.getTexture())
                : RenderTypes.entityTranslucent(muzzleFlash.getTexture());

        context.add(collector -> {
            PoseStack backgroundPose = new PoseStack();
            backgroundPose.last().pose().set(frozenPose.last().pose());
            backgroundPose.last().normal().set(frozenPose.last().normal());
            backgroundPose.scale(frozenScale, frozenScale, frozenScale);
            backgroundPose.mulPose(Axis.ZP.rotationDegrees(frozenRotation));
            backgroundPose.translate(0, -1, 0);
            collector.submitCustomGeometry(backgroundPose, flashQuadType,
                    (pose, buffer) -> MUZZLE_FLASH_MODEL.renderToBuffer(
                            backgroundPose, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F));

            PoseStack glowPose = new PoseStack();
            glowPose.last().pose().set(frozenPose.last().pose());
            glowPose.last().normal().set(frozenPose.last().normal());
            glowPose.scale(frozenScale / 2, frozenScale / 2, frozenScale / 2);
            glowPose.mulPose(Axis.ZP.rotationDegrees(frozenRotation));
            glowPose.translate(0, -0.9, 0);
            // 【辉光层的正式收口】LayerAssignment 裁决实验结论：镜内火团一直来自这层，
            // 大面片从未失手。26.2 把 swirl 折叠回通用 entity shader（core/entity +
            // APPLY_TEXTURE_MATRIX），我们的 scope_body 就是它的逐字节拷贝，于是直接
            // 立一条 mask 版 swirl 管线（ADDITIVE 观感不变 + 镜内 discard）。
            RenderType glowType = flashMaskReady
                    ? com.tacz.guns.client.render.scope.ScopeBodyRenderTypes.flashSwirlClipped(muzzleFlash.getTexture())
                    : RenderTypes.energySwirl(muzzleFlash.getTexture(), 1, 1);
            collector.submitCustomGeometry(glowPose, glowType,
                    (pose, buffer) -> MUZZLE_FLASH_MODEL.renderToBuffer(
                            glowPose, buffer, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F));
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(PoseStack poseStack, VertexConsumer vertexBuffer, ItemDisplayContext transformType, int light, int overlay) {
        if (IrisCompat.isRenderShadow()) {
            return;
        }
        if (!isSelf) {
            return;
        }
        long time = GunTime.nowMillis() - shootTimeStamp;
        if (time > TIME_RANGE) {
            return;
        }
        ItemStack currentGunItem = bedrockGunModel.getCurrentGunItem();

        TimelessAPI.getGunDisplay(currentGunItem).ifPresent(display -> {
            ItemStack muzzleAttachment = bedrockGunModel.getCurrentAttachmentItem().get(AttachmentType.MUZZLE);
            IAttachment iAttachment = IAttachment.getIAttachmentOrNull(muzzleAttachment);
            if (iAttachment != null) {
                Identifier attachmentId = iAttachment.getAttachmentId(muzzleAttachment);
                TimelessAPI.getCommonAttachmentIndex(attachmentId).ifPresent(index -> {
                    var modifier = index.getData().getModifier();
                    if (modifier.containsKey(SilenceModifier.ID) && modifier.get(SilenceModifier.ID).getValue() instanceof Pair<?, ?> pair) {
                        // 如果安装了消音器，则不渲染枪口火光
                        if (((Pair<Integer, Boolean>) pair).right()) {
                            return;
                        }
                    }
                    renderMuzzleFlash(display, poseStack, bedrockGunModel, time);
                });
            } else {
                renderMuzzleFlash(display, poseStack, bedrockGunModel, time);
            }
        });
    }

    /** 1.20.0：退出世界时复位。 */
    public static void resetTimestamps() {
        shootTimeStamp = -1L;
        TP_SHOOT_TIMESTAMPS.clear();
    }

}
