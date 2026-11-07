package com.tacz.guns.client.gui;

import com.tacz.guns.inventory.AmmoStorageEntry;
import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.network.message.ClientMessageAmmoStorageClick;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class AmmoStorageScreen extends AbstractContainerScreen<AmmoStorageMenu> {
   private static final Identifier CONTAINER_LOCATION = Identifier.parse("textures/gui/container/generic_54.png");
   private static final int CONTAINER_ROWS = 3;
   private int virtualClickButton = -1;

   public AmmoStorageScreen(AmmoStorageMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title, 176, 194);
      this.inventoryLabelY = this.imageHeight - 94;
   }

   @Override
   public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractBackground(graphics, mouseX, mouseY, partialTick);
      int storageHeight = 97;
      graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_LOCATION, this.leftPos, this.topPos, 0, 0, this.imageWidth, 17, 256, 256);
      // 26.2：上游 1025 用 11 参「拉伸 blit」画 1px 分隔线，此处用 fill 近似。
      graphics.fill(this.leftPos, this.topPos + 17, this.leftPos + this.imageWidth, this.topPos + 18, 0xFF373737);
      graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_LOCATION, this.leftPos, this.topPos + 43, 0, 17, this.imageWidth, 54, 256, 256);
      graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_LOCATION, this.leftPos, this.topPos + storageHeight, 0, 126, this.imageWidth, 96, 256, 256);
   }

   @Override
   public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
      this.renderCapacity(graphics);
      List<AmmoStorageEntry> entries = this.menu.getEntries();

      for (int i = 0; i < entries.size(); i++) {
         AmmoStorageEntry entry = entries.get(i);
         int x = this.leftPos + 8 + i % 9 * 18;
         int y = this.topPos + 44 + i / 9 * 18;
         graphics.fakeItem(entry.sample(), x, y);
         this.renderSizeLabel(graphics, x, y, this.formatCount(entry.count()));
      }

      int hovered = this.getVirtualCell(mouseX, mouseY);
      if (hovered >= 0) {
         int x = this.leftPos + 8 + hovered % 9 * 18;
         int y = this.topPos + 44 + hovered / 9 * 18;
         graphics.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
      }

      this.renderTooltip(graphics, mouseX, mouseY);
   }

   private void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      if (this.isHoveringStatus(mouseX, mouseY)) {
         graphics.setTooltipForNextFrame(this.font, List.of(
               Component.translatable("gui.tacz.ammo_storage.capacity_detail", this.menu.getUsedStacks(), this.menu.getCapacityStacks()),
               Component.translatable("gui.tacz.ammo_storage.free_capacity", Math.max(0L, (long) this.menu.getCapacityStacks() - this.menu.getUsedStacks()))
            ), Optional.empty(), mouseX, mouseY);
      } else {
         int cell = this.getVirtualCell(mouseX, mouseY);
         if (cell >= 0 && cell < this.menu.getEntries().size() && this.menu.getCarried().isEmpty()) {
            AmmoStorageEntry entry = this.menu.getEntries().get(cell);
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.sample()));
            tooltip.add(Component.translatable("gui.tacz.ammo_storage.count", entry.count()).withStyle(ChatFormatting.GRAY));
            tooltip.add(
               Component.translatable("gui.tacz.ammo_storage.remaining", entry.remainingCapacity())
                  .withStyle(entry.remainingCapacity() == 0L ? ChatFormatting.RED : ChatFormatting.GRAY)
            );
            graphics.setTooltipForNextFrame(this.font, tooltip, entry.sample().getTooltipImage(), mouseX, mouseY);
         }
      }
   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isHovered) {
      double mouseX = event.x();
      double mouseY = event.y();
      int button = event.button();
      int cell = this.getVirtualCell(mouseX, mouseY);
      if (cell >= 0 && (button == 0 || button == 1)) {
         int entryId = cell < this.menu.getEntries().size() ? this.menu.getEntries().get(cell).id() : -1;
         AmmoStorageMenu.Action action = event.hasShiftDown()
            ? AmmoStorageMenu.Action.QUICK_MOVE
            : (button == 1 ? AmmoStorageMenu.Action.PICKUP_HALF : AmmoStorageMenu.Action.PICKUP);
         ClientPlayNetworking.send(new ClientMessageAmmoStorageClick(this.menu.containerId, entryId, action));
         this.virtualClickButton = button;
         return true;
      } else {
         return super.mouseClicked(event, isHovered);
      }
   }

   @Override
   public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
      return this.virtualClickButton >= 0 || super.mouseDragged(event, dragX, dragY);
   }

   @Override
   public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
      int button = event.button();
      if (button == this.virtualClickButton) {
         this.virtualClickButton = -1;
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }

   private int getVirtualCell(double mouseX, double mouseY) {
      int relX = (int) mouseX - this.leftPos - 8;
      int relY = (int) mouseY - this.topPos - 44;
      if (relX < 0 || relY < 0) {
         return -1;
      }
      int col = relX / 18;
      int row = relY / 18;
      if (col >= 9 || row >= 3) {
         return -1;
      }
      if (relX % 18 >= 16 || relY % 18 >= 16) {
         return -1;
      }
      return col + row * 9;
   }

   private boolean isHoveringStatus(int mouseX, int mouseY) {
      return mouseX >= this.leftPos + 8 && mouseX < this.leftPos + 168
         && mouseY >= this.topPos + 17 && mouseY < this.topPos + 38;
   }

   private void renderCapacity(GuiGraphicsExtractor graphics) {
      Component capacity = Component.translatable("gui.tacz.ammo_storage.capacity", this.menu.getUsedStacks(), this.menu.getCapacityStacks());
      Component types = Component.translatable("gui.tacz.ammo_storage.types", this.menu.getEntries().size(), 27);
      // 状态栏文字：分隔线(17) 与进度条(31) 之间高 14px，字体约 9px，垂直居中取 20；
      // 颜色由原版深灰(0xFF404040)提亮为 0xFFD4D4D4，避免灰字看不清。
      graphics.text(this.font, capacity, this.leftPos + 8, this.topPos + 20, 0xFFD4D4D4, false);
      graphics.text(
         this.font,
         types,
         this.leftPos + this.imageWidth - 8 - this.font.width(types),
         this.topPos + 20,
         this.menu.getEntries().size() == 27 ? 0xFFFF6060 : 0xFFD4D4D4,
         false
      );
      int x = this.leftPos + 8;
      int y = this.topPos + 31;
      double fullness = this.menu.getCapacityStacks() <= 0
         ? 0.0
         : Math.min(1.0, (double) this.menu.getUsedStacks() / this.menu.getCapacityStacks());
      int color = fullness >= 1.0 ? -3909048 : (fullness >= 0.75 ? -2644156 : -10114980);
      graphics.fill(x, y, x + 160, y + 6, -13158601);
      graphics.fill(x + 1, y + 1, x + 159, y + 5, -8947849);
      graphics.fill(x + 1, y + 1, x + 1 + (int) Math.round(158.0 * fullness), y + 5, color);
   }

   private String formatCount(long count) {
      if (count >= 1000000000L) {
         return this.formatCompact(count, 1000000000, "b");
      } else if (count >= 1000000L) {
         return this.formatCompact(count, 1000000, "m");
      } else {
         return count >= 1000L ? this.formatCompact(count, 1000, "k") : Long.toString(count);
      }
   }

   private String formatCompact(long count, int unit, String suffix) {
      long whole = count / unit;
      long decimal = count % unit / (unit / 10);
      return decimal != 0L && whole < 10L ? whole + "." + decimal + suffix : whole + suffix;
   }

   private void renderSizeLabel(GuiGraphicsExtractor graphics, int x, int y, String text) {
      float scale = 0.666F;
      Matrix3x2fStack pose = graphics.pose();
      pose.pushMatrix();
      pose.scale(scale, scale);
      graphics.text(this.font, text, (int) ((x + 17 - this.font.width(text) * scale) / scale), (int) ((y + 12) / scale), 0xFFFFFFFF, true);
      pose.popMatrix();
   }
}
