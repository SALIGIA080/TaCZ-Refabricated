package com.tacz.guns.inventory;

import net.minecraft.world.item.ItemStack;

public record AmmoStorageEntry(int id, ItemStack sample, long count, long remainingCapacity) {
   public AmmoStorageEntry(int id, ItemStack sample, long count, long remainingCapacity) {
      sample = sample.copyWithCount(1);
      this.id = id;
      this.sample = sample;
      this.count = count;
      this.remainingCapacity = remainingCapacity;
   }
}
