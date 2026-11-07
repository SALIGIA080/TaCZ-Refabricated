package com.tacz.guns.client.tooltip;

import com.tacz.guns.inventory.tooltip.AmmoStorageTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/**
 * 弹药库物品提示。26.2 移植说明：GuiGraphics 已移除，
 * 改用 {@link GuiGraphicsExtractor} 的 extract 回调。
 */
public class ClientAmmoStorageTooltip implements ClientTooltipComponent {
   private static final int ENTRY_HEIGHT = 28;
   private final AmmoStorageTooltip tooltip;

   public ClientAmmoStorageTooltip(AmmoStorageTooltip tooltip) {
      this.tooltip = tooltip;
   }

   @Override
   public int getHeight(Font font) {
      int height = this.tooltip.getEntries().size() * ENTRY_HEIGHT;
      return this.tooltip.getHidden() > 0 ? height + 10 : height;
   }

   @Override
   public int getWidth(Font font) {
      int width = 0;

      for (AmmoStorageTooltip.Entry entry : this.tooltip.getEntries()) {
         width = Math.max(width, font.width(entry.getAmmoName()));
         width = Math.max(width, font.width(getCount(entry)));
      }

      if (this.tooltip.getHidden() > 0) {
         width = Math.max(width, font.width(this.getHidden()));
      }

      return width + 22;
   }

   @Override
   public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
      int yOffset = y;

      for (AmmoStorageTooltip.Entry entry : this.tooltip.getEntries()) {
         graphics.text(font, entry.getAmmoName(), x + 20, yOffset + 4, 0xFFffaa00);
         graphics.text(font, getCount(entry), x + 20, yOffset + 15, 0xFF666666);
         yOffset += ENTRY_HEIGHT;
      }

      if (this.tooltip.getHidden() > 0) {
         graphics.text(font, this.getHidden(), x, yOffset, 0xFF9e9e9e);
      }
   }

   @Override
   public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
      int yOffset = y;

      for (AmmoStorageTooltip.Entry entry : this.tooltip.getEntries()) {
         graphics.item(entry.ammo(), x, yOffset + 5);
         yOffset += ENTRY_HEIGHT;
      }
   }

   private static Component getCount(AmmoStorageTooltip.Entry entry) {
      return Component.translatable("tooltip.tacz.ammo_box.count", entry.count());
   }

   private Component getHidden() {
      return Component.translatable("container.shulkerBox.more", this.tooltip.getHidden()).withStyle(ChatFormatting.ITALIC);
   }
}
