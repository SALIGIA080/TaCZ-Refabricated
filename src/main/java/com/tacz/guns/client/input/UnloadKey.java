package com.tacz.guns.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.util.InputExtraCheck;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class UnloadKey {
    // 1025 默认是 Alt+R；Fabric 26.2 的 KeyMapping 不支持 KeyModifier(Alt)，改用独立键 U 避免与 ReloadKey(R) 冲突。
    public static final KeyMapping UNLOAD_KEY = new KeyMapping("key.tacz.unload.desc",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            TaCZKeyCategory.TACZ);

    public static void onUnloadPress() {
        if (InputExtraCheck.isInGame()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && player.isAlive() && !player.isSpectator()) {
                IClientPlayerGunOperator.fromLocalPlayer(player).unloadAmmo();
            }
        }
    }
}
