package com.tacz.guns.client.renderer.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.tacz.guns.GunMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 挂板「是否已染色」的 {@code select} 属性，对应上游 1.21.1 的
 * {@code ItemProperties.register(..., "tacz:dyed", ...)}。
 *
 * <p>26.2 移除了 {@code ItemProperties}，物品模型变体改用
 * {@code minecraft:select} + 自定义属性。这里返回 0（未染色）/ 1（已染色），
 * 与挂板 item 模型 {@code overrides} 里旧的 {@code tacz:dyed} 谓词语义一致，
 * 迁移后由 {@code gun_pegboard.json} / {@code glow_gun_pegboard.json} 的
 * {@code select} 块匹配。
 */
public record GunPegboardDyedProperty() implements SelectItemModelProperty<Integer> {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "dyed");

    public static final MapCodec<GunPegboardDyedProperty> MAP_CODEC =
            MapCodec.unit(new GunPegboardDyedProperty());

    public static final SelectItemModelProperty.Type<GunPegboardDyedProperty, Integer> TYPE =
            SelectItemModelProperty.Type.create(MAP_CODEC, Codec.INT);

    @Override
    @Nullable
    public Integer get(ItemStack stack,
                       @Nullable ClientLevel level,
                       @Nullable LivingEntity entity,
                       int seed,
                       ItemDisplayContext displayContext) {
        return stack.has(DataComponents.DYED_COLOR) ? 1 : 0;
    }

    @Override
    public Codec<Integer> valueCodec() {
        return Codec.INT;
    }

    @Override
    public SelectItemModelProperty.Type<GunPegboardDyedProperty, Integer> type() {
        return TYPE;
    }
}
