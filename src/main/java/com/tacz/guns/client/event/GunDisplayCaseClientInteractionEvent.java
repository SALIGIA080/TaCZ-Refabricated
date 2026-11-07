package com.tacz.guns.client.event;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.client.gui.overlay.DisplayCaseLockHintOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;

@Environment(EnvType.CLIENT)
public class GunDisplayCaseClientInteractionEvent {
   private static boolean wasUseDown = false;

   /**
    * Fabric 没有 InputEvent.Key/MouseButton 这类「原始输入事件」，
    * 改用客户端 tick 里对 keyUse 做上升沿检测（不 consumeClick，不影响正常使用键开门/拿取）。
    * 注册点：TaCZFabricClient 的 ClientTickEvents.END_CLIENT_TICK。
    */
   public static void onClientTick(Minecraft minecraft) {
      LocalPlayer player = minecraft.player;
      if (player == null) {
         wasUseDown = false;
         return;
      }
      boolean useDown = minecraft.options.keyUse.isDown();
      if (useDown && !wasUseDown && !player.isSpectator()) {
         if (player.getMainHandItem().isEmpty() && !player.isShiftKeyDown()) {
            showHintForTarget(minecraft, false);
         }
      }
      wasUseDown = useDown;
   }

   public static void showHintForTarget(Minecraft minecraft, boolean lockedOnly) {
      if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK && minecraft.level != null) {
         BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
         if (state.getBlock() instanceof GunDisplayCaseBlock) {
            boolean locked = state.getValue(GunDisplayCaseBlock.LOCKED);
            if (!lockedOnly || locked) {
               DisplayCaseLockHintOverlay.show(createHint(locked, minecraft));
            }
         }
      }
   }

   private static Component createHint(boolean locked, Minecraft minecraft) {
      String shiftKey = minecraft.options.keyShift.getTranslatedKeyMessage().getString();
      String useKey = minecraft.options.keyUse.getTranslatedKeyMessage().getString();
      String chord = shiftKey + " + " + useKey;
      return Component.translatable(locked ? "gui.tacz.display_case.locked_hint" : "gui.tacz.display_case.unlocked_hint", chord);
   }

   public static boolean holdingGun(LocalPlayer player) {
      return player.getMainHandItem().getItem() instanceof IGun;
   }
}
