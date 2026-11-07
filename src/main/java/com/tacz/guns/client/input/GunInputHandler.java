package com.tacz.guns.client.input;

import cn.sh1rocu.tacz.api.event.InputEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class GunInputHandler {
    private static final List<PressBinding> PRESS_BINDINGS = List.of(
            new PressBinding(UnloadKey.UNLOAD_KEY, UnloadKey::onUnloadPress),
            new PressBinding(AimKey.AIM_KEY, AimKey::onAimPress),
            new PressBinding(CrawlKey.CRAWL_KEY, CrawlKey::onCrawlPress),
            new PressBinding(ReloadKey.RELOAD_KEY, ReloadKey::onReloadPress),
            new PressBinding(InspectKey.INSPECT_KEY, InspectKey::onInspectPress),
            new PressBinding(FireSelectKey.FIRE_SELECT_KEY, FireSelectKey::onFireSelectPress),
            new PressBinding(MeleeKey.MELEE_KEY, MeleeKey::onMeleePress),
            new PressBinding(ZoomKey.ZOOM_KEY, ZoomKey::onZoomPress),
            new PressBinding(InteractKey.INTERACT_KEY, InteractKey::onInteractPress),
            new PressBinding(RefitKey.REFIT_KEY, RefitKey::onRefitPress),
            new PressBinding(ConfigKey.OPEN_CONFIG_KEY, ConfigKey::onOpenConfig)
    );

    private GunInputHandler() {
    }

    public static void init() {
        InputEvent.Key.EVENT.register(GunInputHandler::onKeyPress);
        InputEvent.MouseButton.Post.EVENT.register(GunInputHandler::onMousePress);
        InputEvent.MouseButton.Pre.EVENT.register(GunInputHandler::onMousePressPre);
    }

    public static void onKeyPress(InputEvent.Key event) {
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(event.getKey());
        if (event.getAction() == 0) {
            onRelease(key);
        }
        if (event.getAction() == 1) {
            if (!closeRefitScreen(key)) {
                onPress(key);
            }
        }
    }

    public static void onMousePress(InputEvent.MouseButton.Post event) {
        InputConstants.Key key = Type.MOUSE.getOrCreate(event.getButton());
        if (event.getAction() == 0) {
            onRelease(key);
        }
        if (event.getAction() == 1) {
            onPress(key);
        }
    }

    public static void onMousePressPre(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == 1 && closeRefitScreen(Type.MOUSE.getOrCreate(event.getButton()))) {
            event.setCanceled(true);
        }
    }

    private static void onPress(InputConstants.Key key) {
        for (PressBinding binding : PRESS_BINDINGS) {
            if (!InputExtraCheck.isInGame()) {
                return;
            }
            if (binding.keyMapping().matches(key)) {
                binding.action().run();
                if (binding.keyMapping() == UnloadKey.UNLOAD_KEY) {
                    return;
                }
            }
        }
    }

    private static boolean closeRefitScreen(InputConstants.Key key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.overlay() == null && mc.isWindowActive() && mc.gui.screen() instanceof GunRefitScreen) {
            if (key.equals(RefitKey.REFIT_KEY.getDefaultKey())) {
                mc.gui.screen().onClose();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static void onRelease(InputConstants.Key key) {
        for (PressBinding binding : PRESS_BINDINGS) {
            if (key.equals(binding.keyMapping().getDefaultKey())) {
                binding.keyMapping().setDown(false);
            }
        }
        if (key.equals(ShootKey.SHOOT_KEY.getDefaultKey())) {
            ShootKey.SHOOT_KEY.setDown(false);
        }
    }

    private record PressBinding(KeyMapping keyMapping, Runnable action) {
    }
}
