package com.tacz.guns.compat.playeranimator;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.pal.PalAnimationManager;
import com.tacz.guns.compat.playeranimator.pal.PalAssetManager;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import java.io.File;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Compatibility facade migrated from the discontinued KosmX PlayerAnimator to PAL 1.2.5. */
public final class PlayerAnimatorCompat {
    public static final Identifier LOWER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "lower_animation");
    public static final Identifier LOOP_UPPER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "loop_upper_animation");
    public static final Identifier ONCE_UPPER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "once_upper_animation");
    public static final Identifier ROTATION_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rotation");

    private static final String PAL = "player_animation_library";
    private static boolean installed;

    private PlayerAnimatorCompat() {
    }

    public static void init() {
        installed = FabricLoader.getInstance().isModLoaded(PAL);
        if (installed) {
            PalAnimationManager.init();
        }
    }

    public static boolean loadAnimationFromZip(ZipFile zipFile, String zipPath) {
        return installed && PalAssetManager.INSTANCE.load(zipFile, zipPath);
    }

    public static void loadAnimationFromFile(File file) {
        if (installed) {
            PalAssetManager.INSTANCE.load(file);
        }
    }

    public static void clearAllAnimationCache() {
        if (installed) {
            PalAssetManager.INSTANCE.clearAll();
        }
    }

    public static boolean hasPlayerAnimator3rd(LivingEntity livingEntity, GunDisplayInstance display) {
        return installed && livingEntity instanceof AbstractClientPlayer
                && PalAnimationManager.hasAnimations(display);
    }

    /**
     * 1025：判断该实体当前是否具备播放动画的姿态前提（手持枪、非睡眠/攀爬/游泳/滑翔）。
     * 与 PAL 是否安装无关，供第三人称动画分派在非 PAL 路径下复用。
     */
    public static boolean canPlayAnimation(LivingEntity entity) {
        return IGun.getIGunOrNull(entity.getMainHandItem()) != null
                && entity.getPose() != Pose.SLEEPING
                && !entity.onClimbable()
                && !entity.isSwimming()
                && entity.getPose() != Pose.FALL_FLYING;
    }

    public static void stopAllAnimation(LivingEntity livingEntity) {
        stopAllAnimation(livingEntity, 8);
    }

    public static void stopAllAnimation(LivingEntity livingEntity, int fadeTime) {
        if (installed && livingEntity instanceof AbstractClientPlayer player) {
            PalAnimationManager.stopAll(player, fadeTime);
        }
    }

    public static void playAnimation(LivingEntity livingEntity, GunDisplayInstance display, float limbSwingAmount) {
        if (installed && livingEntity instanceof AbstractClientPlayer player) {
            PalAnimationManager.play(player, display, limbSwingAmount);
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    /**
     * 1025：捕获玩家模型的动画状态并返回恢复器（KosmX 时代捕获 emote supplier 与 bend）。
     * PAL 路线下第三方动画由 {@link PalAnimationManager} 管理、模型每帧由
     * {@code AvatarRenderState} 重建，无跨帧状态需要恢复 —— 返回空恢复器，
     * 保留 1025 的调用契约（{@code PlayerModelRenderState} 使用）。
     */
    public static Runnable captureModelState(net.minecraft.client.model.player.PlayerModel model) {
        return () -> {
        };
    }

    public static void registerReloadListener(Consumer<IdentifiableResourceReloadListener> register) {
        if (installed) {
            register.accept(PalAssetManager.INSTANCE);
        }
    }
}
