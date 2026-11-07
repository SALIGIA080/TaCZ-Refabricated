package com.tacz.guns.client.gameplay;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.network.message.ClientMessagePlayerAim;
import com.tacz.guns.resource.modifier.custom.AdsModifier;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public class LocalPlayerAim {
    private final LocalPlayerDataHolder data;
    private final LocalPlayer player;

    public LocalPlayerAim(LocalPlayerDataHolder data, LocalPlayer player) {
        this.data = data;
        this.player = player;
    }

    public void aim(boolean isAim) {
        // 暂定为主手
        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            return;
        }
        Identifier gunId = iGun.getGunId(mainHandItem);
        var clientIdx = TimelessAPI.getClientGunIndex(gunId);
        clientIdx.ifPresent(gunIndex -> {
            // 0929：换弹/拉栓时强制退出瞄准 —— 若请求开镜但被拦截，且当前已在瞄准，则撤销
            boolean block = isAim && shouldBlockAim(gunIndex.getGunData());
            if (block) {
                if (data.clientIsAiming) {
                    data.clientIsAiming = false;
                    ClientPlayNetworking.send(new ClientMessagePlayerAim(false));
                }
            } else {
                data.clientIsAiming = isAim;
                // 发送切换开火模式的数据包，通知服务器
                ClientPlayNetworking.send(new ClientMessagePlayerAim(isAim));
            }
        });
    }

    public float getClientAimingProgress(float partialTicks) {
        return Mth.lerp(partialTicks, LocalPlayerDataHolder.oldAimingProgress, data.clientAimingProgress);
    }

    public boolean isAim() {
        return data.clientIsAiming;
    }

    public void tickAimingProgress() {
        ItemStack mainHandItem = player.getMainHandItem();
        // 如果主手物品不是枪械，则取消瞄准状态并将 aimingProgress 归零，返回。
        if (!(mainHandItem.getItem() instanceof IGun iGun)) {
            data.clientAimingProgress = 0;
            LocalPlayerDataHolder.oldAimingProgress = 0;
            return;
        }
        // 如果正在收枪，则不能瞄准
        if (com.tacz.guns.util.time.GunTime.nowMillis() - data.clientDrawTimestamp < 0) {
            data.clientIsAiming = false;
        }
        Identifier gunId = iGun.getGunId(mainHandItem);
        TimelessAPI.getCommonGunIndex(gunId).ifPresentOrElse(index -> {
            // 0929：换弹/拉栓时强制退出瞄准
            if (shouldBlockAim(index.getGunData())) {
                data.clientIsAiming = false;
            }
            float alphaProgress = this.getAlphaProgress(index.getGunData());
            this.aimProgressCalculate(alphaProgress);
        }, () -> {
            data.clientAimingProgress = 0;
            LocalPlayerDataHolder.oldAimingProgress = 0;
        });
    }

    /**
     * 0929：判断是否应阻止瞄准 —— 换弹（含启动瞬间）或拉栓，且枪械配置了对应强制退出项。
     */
    private boolean shouldBlockAim(GunData gunData) {
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        boolean isReloading = operator.getSynReloadState().getStateType().isReloading();
        boolean isReloadStarting = data.clientReloadStarting;
        boolean isBolting = data.isBolting || operator.getSynIsBolting();
        return data.isTimedStateLocked()
                || gunData.isForceAimExitOnReload() && (isReloading || isReloadStarting)
                || gunData.isForceAimExitOnBolt() && isBolting;
    }

    private void aimProgressCalculate(float alphaProgress) {
        LocalPlayerDataHolder.oldAimingProgress = data.clientAimingProgress;
        if (data.clientIsAiming) {
            // 处于执行瞄准状态，增加 aimingProgress
            data.clientAimingProgress += alphaProgress;
            if (data.clientAimingProgress > 1) {
                data.clientAimingProgress = 1;
            }
        } else {
            // 处于取消瞄准状态，减小 aimingProgress
            data.clientAimingProgress -= alphaProgress;
            if (data.clientAimingProgress < 0) {
                data.clientAimingProgress = 0;
            }
        }
        data.clientAimingTimestamp = com.tacz.guns.util.time.GunTime.nowMillis();
    }

    private float getAlphaProgress(GunData gunData) {
        float aimTime = gunData.getAimTime();
        IGunOperator operator = IGunOperator.fromLivingEntity(this.player);
        if (operator.getCacheProperty() != null) {
            aimTime = operator.getCacheProperty().<Float>getCache(AdsModifier.ID);
        }
        aimTime = Math.max(0, aimTime);
        return (com.tacz.guns.util.time.GunTime.nowMillis() - data.clientAimingTimestamp + 1) / (aimTime * 1000);
    }
}
