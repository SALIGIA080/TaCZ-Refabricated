package com.tacz.guns.client.gameplay;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ClientMessagePlayerCancelReload;
import com.tacz.guns.network.message.ClientMessagePlayerReloadGun;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class LocalPlayerReload {
    private final LocalPlayerDataHolder data;
    private final LocalPlayer player;

    public LocalPlayerReload(LocalPlayerDataHolder data, LocalPlayer player) {
        this.data = data;
        this.player = player;
    }

    public void cancelReload() {
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof AbstractGunItem)) {
            return;
        }

        TimelessAPI.getGunDisplay(mainHandItem).ifPresent(this::cancelReloadWithDisplay);
    }

    /**
     * Stable client-side reload-cancellation hook after display data has been resolved.
     */
    protected void cancelReloadWithDisplay(GunDisplayInstance display) {
        // 如果没在换弹，则返回
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        ReloadState reloadState = gunOperator.getSynReloadState();
        if (!reloadState.getStateType().isReloading()) {
            return;
        }
        // 发包通知服务器
        ClientPlayNetworking.send(new ClientMessagePlayerCancelReload());
        // 执行本地取消换弹逻辑
        this.triggerClientReloadCancelAnimation(display);
    }

    public void reload() {
        // 暂定只有主手可以装弹
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        Identifier gunId = gunItem.getGunId(mainHandItem);
        GunData gunData = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getGunData).orElse(null);
        if (gunData == null) {
            return;
        }
        TimelessAPI.getGunDisplay(mainHandItem)
                .ifPresent(display -> reloadWithDisplay(gunItem, display, gunData, mainHandItem));
    }

    /**
     * Stable client-side reload hook after display data has been resolved.
     */
    protected void reloadWithDisplay(AbstractGunItem gunItem, GunDisplayInstance display, GunData gunData, ItemStack mainHandItem) {
        // 检查是否为背包直读
        if (gunItem.useInventoryAmmo(mainHandItem)) {
            return;
        }
        // 0929：检查状态锁 + 充能中不允许换弹
        if (data.clientStateLock || data.isCharging) {
            return;
        }
        // 0929：开火后 100ms 内不允许换弹
        if (com.tacz.guns.util.time.GunTime.isWithin(data.clientShootTimestamp, 100L)) {
            return;
        }
        // 弹药简单检查
        boolean canReload = gunItem.canReload(player, mainHandItem);
        if (IGunOperator.fromLivingEntity(player).needCheckAmmo() && !canReload) {
            return;
        }
        // 锁上状态锁
        data.lockState(this::isReloadLockActive);
        // 0929：换弹启动标志置位（供 LocalPlayerAim 强制退出瞄准）
        data.clientReloadStarting = true;
        data.chargeProgress = 0f;
        // 触发换弹事件
        GunReloadEvent gunReloadEvent = new GunReloadEvent(player, player.getMainHandItem(), LogicalSide.CLIENT);
        GunReloadEvent.CALLBACK.invoker().post(gunReloadEvent);
        if (gunReloadEvent.isCanceled()) {
            // 0929：事件被取消，复位换弹启动标志
            data.clientReloadStarting = false;
            return;
        }
        // 0929：换弹成功且枪械配置了「换弹强制退出瞄准」，则主动关镜
        if (gunData.isForceAimExitOnReload()) {
            IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
        }
        // 发包通知服务器
        ClientPlayNetworking.send(new ClientMessagePlayerReloadGun());
        // 执行客户端 reload 相关内容
        this.triggerClientReloadAnimation(gunItem, display, gunData, mainHandItem);
    }

    protected boolean isReloadLockActive(IGunOperator operator) {
        return operator.getSynReloadState().getStateType().isReloading();
    }

    protected void triggerClientReloadAnimation(IGun iGun, GunDisplayInstance display, GunData gunData, ItemStack mainHandItem) {
        var animationStateMachine = display.getAnimationStateMachine();
        if (animationStateMachine != null) {
            Bolt boltType = gunData.getBolt();
            boolean noAmmo;
            if (boltType == Bolt.OPEN_BOLT) {
                noAmmo = iGun.getCurrentAmmoCount(mainHandItem) <= 0;
            } else {
                noAmmo = !iGun.hasBulletInBarrel(mainHandItem);
            }
            // 触发 reload，停止播放声音
            SoundPlayManager.stopPlayGunSound();
            SoundPlayManager.playReloadSound(player, display, noAmmo);
            animationStateMachine.trigger(GunAnimationConstant.INPUT_RELOAD);
            // 【案例⑧ 探针】换弹开始标记：gated by RecoilDebug，给逐帧探针序列提供相位零点
            if (com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG != null
                    && com.tacz.guns.config.client.RenderConfig.RECOIL_DEBUG.get()) {
                com.tacz.guns.GunMod.LOGGER.info(
                        "[TACZ Case08] RELOAD_START ms={} facing=({},{})",
                        com.tacz.guns.util.time.GunTime.nowMillis(),
                        String.format(java.util.Locale.ROOT, "%.4f", player.getXRot()),
                        String.format(java.util.Locale.ROOT, "%.4f", net.minecraft.util.Mth.wrapDegrees(player.getYRot())));
            }
        }
    }

    protected void triggerClientReloadCancelAnimation(GunDisplayInstance display) {
        var animationStateMachine = display.getAnimationStateMachine();
        if (animationStateMachine != null) {
            animationStateMachine.trigger(GunAnimationConstant.INPUT_CANCEL_RELOAD);
        }
    }
}
