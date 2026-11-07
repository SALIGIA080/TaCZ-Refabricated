package com.tacz.guns.client.gameplay;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ClientMessagePlayerUnloadGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class LocalPlayerUnload {
   private final LocalPlayerDataHolder data;
   private final LocalPlayer player;

   public LocalPlayerUnload(LocalPlayerDataHolder data, LocalPlayer player) {
      this.data = data;
      this.player = player;
   }

   public void unloadAmmo() {
      if (this.isLocalIdle()) {
         this.showCannotUnload();
      } else {
         ItemStack stack = this.player.getMainHandItem();
         if (!(stack.getItem() instanceof AbstractGunItem gun)) {
            this.showCannotUnload();
         } else {
            IGunOperator var6 = IGunOperator.fromLivingEntity(this.player);
            if (this.isSyncIdle(var6)) {
               this.showCannotUnload();
            } else {
               Identifier gunId = gun.getGunId(stack);
               CommonGunIndex index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
               if (index == null) {
                  this.showCannotUnload();
               } else if (index.canUnloadAmmo() && !gun.useInventoryAmmo(stack)) {
                  if (gun.getCurrentAmmoCount(stack) > 0 || index.getGunData().getBolt() != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack)) {
                     this.data.lockState();
                     this.data.autoReloadSuppressed = true;
                     this.data.chargeProgress = 0.0F;
                     TimelessAPI.getGunDisplay(stack).ifPresent(display -> {
                        SoundPlayManager.stopPlayGunSound();
                        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = display.getAnimationStateMachine();
                        if (stateMachine != null) {
                           stateMachine.trigger("inspect_break_out");
                        }
                     });
                     if (Minecraft.getInstance().gameMode != null) {
                        Minecraft.getInstance().gameMode.ensureHasSentCarriedItem();
                     }

                     ClientPlayNetworking.send(new ClientMessagePlayerUnloadGun(this.player.getInventory().getSelectedSlot(), gunId));
                  } else {
                     this.player.sendOverlayMessage(Component.translatable("message.tacz.unload.no_ammo"));
                  }
               } else {
                  this.player.sendOverlayMessage(Component.translatable("message.tacz.unload.unsupported"));
               }
            }
         }
      }
   }

   private void showCannotUnload() {
      this.player.sendOverlayMessage(Component.translatable("message.tacz.unload.cannot"));
   }

   private boolean isSyncIdle(IGunOperator operator) {
      return operator.getSynReloadState().getStateType().isReloading()
         || operator.getSynIsBolting()
         || operator.getSynShootCoolDown() != 0L
         || operator.getSynDrawCoolDown() != 0L
         || operator.getSynMeleeCoolDown() != 0L
         || operator.getSynSprintTime() > 0.0F
         || IClientPlayerGunOperator.fromLocalPlayer(this.player).getClientShootCoolDown() > 0L;
   }

   private boolean isLocalIdle() {
      return !this.player.isAlive()
         || this.player.isSpectator()
         || this.data.clientStateLock
         || this.data.isCharging
         || this.data.isBolting
         || this.data.clientReloadStarting
         || !this.data.isShootRecorded
         || this.data.hasPendingShoot()
         || this.data.clientIsAiming
         || this.data.clientAimingProgress > 0.0F
         || this.player.isSprinting();
   }
}
