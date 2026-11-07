package com.tacz.guns.client.event;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.gui.overlay.GunHudOverlay;
import com.tacz.guns.client.gui.overlay.KillAmountOverlay;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.client.resource.pojo.display.gun.RangefinderManager;
import com.tacz.guns.util.time.GunTime;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

/**
 * 退出世界时复位全部基于 {@link GunTime} 的客户端时间戳。
 * 26.2 移植说明：Forge ClientPlayerNetworkEvent.LoggingOut →
 * Fabric {@code ClientPlayConnectionEvents.DISCONNECT}，在 TaCZFabricClient 注册。
 * （SBM 的 ThirdPersonMuzzleParticleManager 未移植，对应调用移除。）
 */
@Environment(EnvType.CLIENT)
public final class ClientGunTimeResetEvent {
   private ClientGunTimeResetEvent() {
   }

   public static void onDisconnect(ClientPacketListener handler, Minecraft client) {
      LocalPlayer player = client.player;
      if (player != null) {
         LocalPlayerDataHolder dataHolder = IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder();
         dataHolder.cancelShootFutures();
         dataHolder.resetAmmoCountCache();
      }

      GunTime.resetEpoch();
      LocalPlayerDataHolder.clientClickButtonTimestamp = -1L;
      RefitTransform.resetTimestamps();
      RenderCrosshairEvent.resetTimestamps();
      KillAmountOverlay.resetTimestamp();
      ClientHitMark.resetTimestamp();
      GunHudOverlay.resetTimestamp();
      FirstPersonRenderGunEvent.resetTimestamps();
      CameraSetupEvent.resetTimestamp();
      MuzzleFlashRender.resetTimestamps();
      RangefinderManager.resetTimestamps();
      GunHurtBobTweak.resetTimestamp();
      // 1025：ScopeLensRenderer.close() 挂在 ClientGunTimeResetEvent 上 ——
      // 跨世界/断线时释放镜头/热成像的 GPU 资源与帧内状态。
      com.tacz.guns.client.renderer.scope.ScopeLensRenderer.close();
   }
}
