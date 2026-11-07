package com.tacz.guns.event;

import com.tacz.guns.config.util.AmmoBlockConfigRead;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import net.neoforged.fml.config.ModConfig;

public class LoadingConfigEvent {
    private static final String CONFIG_NAME = "tacz-server.toml";
    /** AmmoConfig 注册在 COMMON 配置（tacz-common.toml）下。 */
    private static final String COMMON_CONFIG_NAME = "tacz-common.toml";

    /**
     * 客户端和服务端启动时，会触发此事件
     */
    public static void onLoadingConfig(ModConfig config) {
        // FCAP 保存断桥修复：记下 ModConfig 实例，Cloth 保存后好调 getLoadedConfig().save()。
        com.tacz.guns.config.ConfigPersist.track(config);
        String fileName = config.getFileName();
        // 【1025 delta】AmmoBlockConfigRead 随 common 配置加载/重载初始化
        if (COMMON_CONFIG_NAME.equals(fileName)) {
            AmmoBlockConfigRead.init();
        }
        if (CONFIG_NAME.equals(fileName)) {
            HeadShotAABBConfigRead.init();
            InteractKeyConfigRead.init();
            GunPegboardItemConfigRead.init();
        }
    }

    /**
     * 玩家进入服务端，或者服务端自动重置配置时，会触发此方法
     */
    public static void onReloadingConfig(ModConfig config) {
        com.tacz.guns.config.ConfigPersist.track(config);
        String fileName = config.getFileName();
        // 【1025 delta】AmmoBlockConfigRead 随 common 配置加载/重载初始化
        if (COMMON_CONFIG_NAME.equals(fileName)) {
            AmmoBlockConfigRead.init();
        }
        if (CONFIG_NAME.equals(fileName)) {
            HeadShotAABBConfigRead.init();
            InteractKeyConfigRead.init();
            GunPegboardItemConfigRead.init();
//            if (FabricLoader.getInstance().getEnvironmentType()== EnvType.CLIENT) ClientGunPackDownloadManager::downloadClientGunPack;
        }
    }
}
