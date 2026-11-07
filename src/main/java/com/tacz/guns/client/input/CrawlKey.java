package com.tacz.guns.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.config.sync.SyncConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

import static com.tacz.guns.util.InputExtraCheck.isInGame;

@Environment(EnvType.CLIENT)
public class CrawlKey {
    public static final KeyMapping CRAWL_KEY = new KeyMapping("key.tacz.crawl.desc",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            TaCZKeyCategory.TACZ);

    private static boolean controllerCrawlDown;

    public static boolean onCrawlPress() {
        return onCrawlInput(true);
    }

    public static boolean onCrawlControllerPress(boolean isPress) {
        controllerCrawlDown = isPress;
        return onCrawlInput(isPress);
    }

    public static void setControllerCrawlDown(boolean isDown) {
        controllerCrawlDown = isDown;
    }

    public static void onCrawlHoldingInput(Minecraft client) {
        if (!isInGame()) {
            controllerCrawlDown = false;
        }
        if (KeyConfig.HOLD_TO_CRAWL.get()) {
            onCrawlInput(false);
        }
    }

    private static boolean onCrawlInput(boolean isPress) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!(player instanceof IClientPlayerGunOperator operator)) {
            return false;
        }
        boolean holdToCrawl = KeyConfig.HOLD_TO_CRAWL.get();
        IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
        boolean inputEnabled = isInGame() && SyncConfig.ENABLE_CRAWL.get();
        boolean playerAllowed = inputEnabled && !player.isSpectator() && !player.isPassenger();
        boolean crawlAllowed = playerAllowed && (iGun == null ? SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get() : iGun.isCanCrawl(player.getMainHandItem()));
        if (!crawlAllowed) {
            if (holdToCrawl && operator.isCrawl()) {
                operator.crawl(false);
            }
            return false;
        } else if (!isPress && !holdToCrawl) {
            return false;
        } else {
            boolean crawl = holdToCrawl ? CRAWL_KEY.isDown() || controllerCrawlDown : !operator.isCrawl();
            if (operator.isCrawl() != crawl) {
                operator.crawl(crawl);
            }
            return true;
        }
    }
}
