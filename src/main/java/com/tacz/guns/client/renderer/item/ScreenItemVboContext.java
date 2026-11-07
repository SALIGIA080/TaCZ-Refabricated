package com.tacz.guns.client.renderer.item;

import net.minecraft.client.Minecraft;

/**
 * 「GUI 屏幕正在渲染」判据（1025 ScreenItemVboContext 的 Fabric 等价）。
 *
 * <p>1025 挂 Forge ScreenEvent.Render 的 PRE/POST 做逐帧深度计数；本分支的
 * fabric-screen-api 版本没有 render 事件，改为保守等价：只要屏幕开着就视为活跃 ——
 * 判据只用于「跳过 GUI 场景下的静态 VBO 物品渲染」，多跳的只是回退到普通渲染路径，
 * 方向安全（宁可少用 VBO，不可把 GUI 预览画进世界批）。</p>
 */
public final class ScreenItemVboContext {

    private ScreenItemVboContext() {
    }

    public static boolean isActive() {
        return Minecraft.getInstance().gui.screen() != null;
    }
}
