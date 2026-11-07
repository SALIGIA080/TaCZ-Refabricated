package com.tacz.guns.client.gui.overlay;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 展示柜锁定提示覆盖层。26.2 Fabric 化：
 * 渲染挂 HudElementRegistry（见 ClientSetupEvent），倒计时挂 ClientTickEvents.END_CLIENT_TICK。
 */
@Environment(EnvType.CLIENT)
public class DisplayCaseLockHintOverlay {
   private static final int DISPLAY_TICKS = 40;
   private static final int Y_OFFSET = 32;
   private static Component message = Component.empty();
   private static int ticksRemaining;

   public static void show(Component component) {
      message = component;
      ticksRemaining = DISPLAY_TICKS;
   }

   public static void render(GuiGraphicsExtractor graphics, float partialTick) {
      if (ticksRemaining > 0) {
         Minecraft minecraft = Minecraft.getInstance();
         Font font = minecraft.font;
         int width = minecraft.getWindow().getGuiScaledWidth();
         int height = minecraft.getWindow().getGuiScaledHeight();
         graphics.text(
            font, message, (int) ((width - font.width(message)) / 2.0F), (int) (height / 2.0F + Y_OFFSET),
            0xFFFFFF00, false
         );
      }
   }

   public static void onClientTick(Minecraft minecraft) {
      if (ticksRemaining > 0) {
         ticksRemaining--;
      }
   }
}
