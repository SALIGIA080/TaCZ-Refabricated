package com.tacz.guns.init;

import cn.sh1rocu.tacz.api.event.AddPackFindersEvent;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.network.HandshakeNetworking;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.GunPackLoader;

public final class CommonRegistry {
    private static boolean LOAD_COMPLETE = false;

    public static void onSetupEvent() {
        AddPackFindersEvent.CALLBACK.register(CommonRegistry::onAddPackFinders);
        NetworkHandler.registerC2SPackets();
        HandshakeNetworking.init();
        ModSyncedEntityData.init();
        // 【0929 delta】靶车命中成就触发器必须注册进 TRIGGER_TYPES，否则 TargetMinecart 的 trigger 永远不触发
        com.tacz.guns.advancement.TargetHitTrigger.register();
        com.tacz.guns.advancement.GunRefitTrigger.register();
    }

    public static void onLoadComplete() {
        LOAD_COMPLETE = true;
    }

    public static boolean isLoadComplete() {
        return LOAD_COMPLETE;
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        // 修复：之前 GunPackLoader.packType 仅按环境类型（CLIENT/SERVER）设定一次，
        // 导致在单机环境中，SERVER_DATA 的 repository 也拿到 CLIENT_RESOURCES 的 Pack，
        // 使得 gunpack 的 data/（配方、index）与 assets/（模型、display）不能同时被两端看到。
        // 现在改为按当前 event 的 packType 动态设定，保证客户端与服务端各拿对应类型的包。
        GunPackLoader.INSTANCE.packType = event.getPackType();
        event.addRepositorySource(GunPackLoader.INSTANCE);
    }
}
