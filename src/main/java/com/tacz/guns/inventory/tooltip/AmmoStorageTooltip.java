package com.tacz.guns.inventory.tooltip;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public class AmmoStorageTooltip implements TooltipComponent {
   private final List<AmmoStorageTooltip.Entry> entries;
   private final int hidden;

   public AmmoStorageTooltip(List<AmmoStorageTooltip.Entry> entries, int hidden) {
      this.entries = entries;
      this.hidden = hidden;
   }

   public List<AmmoStorageTooltip.Entry> getEntries() {
      return this.entries;
   }

   public int getHidden() {
      return this.hidden;
   }

   public record Entry(ItemStack ammo, long count) {
      public Component getAmmoName() {
         return this.ammo.getHoverName();
      }
   }
}
