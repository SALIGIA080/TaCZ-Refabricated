package com.tacz.guns.event.ammo;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.util.AmmoBlockConfigRead;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class DestroyGlassBlock {
    /**
     * 【1025 delta】DestroyBlockHardnessThreshold 的默认值（AmmoConfig 尚无该配置项，
     * 等配置批补入后用 AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.get() 替换）。
     * 方块破坏判定改用 AmmoBlockConfigRead.canDestroy（DestroyBlocks 配置表）。
     */
    private static final double DESTROY_BLOCK_HARDNESS_THRESHOLD = 1.0;

    public static void onAmmoHitBlock(AmmoHitBlockEvent event) {
        if (AmmoConfig.DESTROY_GLASS.get()) {
            Level level = event.getLevel();
            BlockState state = event.getState();
            BlockPos pos = event.getHitResult().getBlockPos();
            float hardness = state.getDestroySpeed(level, pos);
            if (!(hardness < 0.0F) && !(hardness > DESTROY_BLOCK_HARDNESS_THRESHOLD)) {
                EntityKineticBullet ammo = event.getAmmo();
                if (AmmoBlockConfigRead.canDestroy(state)) {
                    level.destroyBlock(pos, false, ammo.getOwner());
                }
            }
        }
    }
}
