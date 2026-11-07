package com.tacz.guns.client.animation.third;

import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;

import java.util.List;
import java.util.stream.Stream;

/**
 * 玩家模型的「临时姿态」快照（1025 PlayerModelRenderState 的 26.2 迁移）。
 *
 * <p>1025 在摆拍第三人称持枪姿态前保存模型全部部件姿态与 attackTime/swimAmount/
 * crouching/riding/young/手臂姿态等字段，close() 时恢复。26.2 的这些字段已迁入
 * {@code AvatarRenderState} 逐帧重建（模型不再持有），无需快照；部件姿态
 * （{@code storePose}/{@code loadPose}）与可见性/缩放仍需保存恢复 —— 恢复语义与 1025 一致。</p>
 *
 * <p>动画恢复器来自 {@link PlayerAnimatorCompat#captureModelState}（1025 同位契约）。</p>
 */
public final class PlayerModelRenderState implements AutoCloseable {
    private final PlayerModel model;
    private final List<PlayerModelRenderState.PartState> parts;
    private final Runnable restoreAnimation;

    public PlayerModelRenderState(PlayerModel model) {
        this.model = model;
        this.parts = Stream.of(
                model.head, model.hat, model.body,
                model.leftArm, model.rightArm, model.leftLeg, model.rightLeg,
                model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants
        )
                .flatMap(part -> part.getAllParts().stream())
                .distinct()
                .map(PlayerModelRenderState.PartState::new)
                .toList();
        this.restoreAnimation = PlayerAnimatorCompat.captureModelState(model);
    }

    public void prepareArm(HumanoidArm arm, boolean visible) {
        if (arm == HumanoidArm.RIGHT) {
            this.model.rightArm.visible = visible;
            this.model.rightSleeve.visible = visible;
        } else {
            this.model.leftArm.visible = visible;
            this.model.leftSleeve.visible = visible;
        }
    }

    @Override
    public void close() {
        this.restoreAnimation.run();
        this.parts.forEach(PlayerModelRenderState.PartState::restore);
    }

    private record PartState(ModelPart part, PartPose pose, float xScale, float yScale, float zScale, boolean visible, boolean skipDraw) {
        private PartState(ModelPart part) {
            this(part, part.storePose(), part.xScale, part.yScale, part.zScale, part.visible, part.skipDraw);
        }

        private void restore() {
            this.part.loadPose(this.pose);
            this.part.xScale = this.xScale;
            this.part.yScale = this.yScale;
            this.part.zScale = this.zScale;
            this.part.visible = this.visible;
            this.part.skipDraw = this.skipDraw;
        }
    }
}
