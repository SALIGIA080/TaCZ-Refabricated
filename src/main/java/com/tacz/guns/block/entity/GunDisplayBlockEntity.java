package com.tacz.guns.block.entity;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public interface GunDisplayBlockEntity {
   ItemStack getItem(int var1);

   int getRotation(int var1);

   DisplayGunSlot getDisplaySlot(int var1);

   int getGridSize();

   Direction getSlotFacing(int var1);

   boolean isDisplayGlowing();

   @NotNull
   BlockEntity getBlockEntity();
}
