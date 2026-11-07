package com.tacz.guns.client.init;

import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 挂板（Gun Pegboard）染色注册。
 *
 * <p>26.2 移除了 Forge 的 {@code RegisterColorHandlersEvent.Block/Item} 以及
 * {@code ColorProviderRegistry.ITEM}、{@code ItemProperties}。本类改用：
 * <ul>
 *   <li><b>方块染色</b>：{@link BlockColors#register(List, Block...)} +
 *       {@link BlockTintSource#colorInWorld}，从 {@link GunPegboardBlockEntity#getColor()}
 *       读取染色。</li>
 *   <li><b>物品染色</b>：由原版 {@code minecraft:dye} tint 完成（{@code GunPegboardItem}
 *       已实现 {@code DyeableLeatherItem}），无需在此注册。</li>
 *   <li><b>普通/染色模型切换</b>：由 {@code GunPegboardDyedProperty}
 *       （{@code minecraft:select} 属性）完成，见 TaCZFabricClient 注册。</li>
 * </ul>
 */
public final class GunPegboardColors {
    private GunPegboardColors() {
    }

    public static void register() {
        Minecraft mc = Minecraft.getInstance();
        Block[] pegboards = new Block[]{
                ModBlocks.GUN_PEGBOARD,
                ModBlocks.GLOW_GUN_PEGBOARD
        };
        mc.getBlockColors().register(List.of(new PegboardBlockTintSource()), pegboards);
    }

    private static final class PegboardBlockTintSource implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            if (level != null && pos != null && level.getBlockEntity(pos) instanceof GunPegboardBlockEntity pegboard && pegboard.getColor() >= 0) {
                return pegboard.getColor();
            }
            return -1;
        }
    }
}
