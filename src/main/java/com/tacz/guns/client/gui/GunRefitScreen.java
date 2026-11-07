package com.tacz.guns.client.gui;

import cn.sh1rocu.tacz.mixin.accessor.ScreenAccessor;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.refit.*;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ClientMessageInstallVirtualAttachment;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageSetAttachmentVariant;
import com.tacz.guns.network.message.ClientMessageSetSlotAdapter;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.resource.index.CommonAttachmentVariantIndex;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;
import com.tacz.guns.sound.SoundManager;
import com.tacz.guns.util.SlotAdapterHelper;
import com.tacz.guns.util.VariantHelper;
import com.tacz.guns.util.VirtualOemAttachment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class GunRefitScreen extends Screen {
    public static final Identifier SLOT_TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/refit_slot.png");
    public static final Identifier TURN_PAGE_TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/refit_turn_page.png");
    public static final Identifier UNLOAD_TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/refit_unload.png");
    public static final Identifier ICONS_TEXTURE = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "textures/gui/refit_slot_icons.png");

    public static final int ICON_UV_SIZE = 32;
    public static final int SLOT_SIZE = 18;
    private static final int VARIANT_BUTTON_WIDTH = 78;
    private static final int VARIANT_BUTTON_HEIGHT = 12;
    private static final int VARIANT_BUTTON_SPACING = 1;
    private static final int INVENTORY_ATTACHMENT_START_Y = 50;
    private static final int LIST_COLUMN_GAP = 4;
    private static final int INVENTORY_ATTACHMENT_SLOT_COUNT = 8;
    private static boolean HIDE_GUN_PROPERTY_DIAGRAMS = true;

    private int currentPage = 0;

    public GunRefitScreen() {
        super(Component.literal("Gun Refit Screen"));
        RefitTransform.init();
    }

    public static int getSlotTextureXOffset(ItemStack gunItem, AttachmentType attachmentType) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return -1;
        }
        if (!iGun.allowAttachmentType(gunItem, attachmentType)) {
            return ICON_UV_SIZE * 6;
        }
        switch (attachmentType) {
            case GRIP -> {
                return 0;
            }
            case LASER -> {
                return ICON_UV_SIZE;
            }
            case MUZZLE -> {
                return ICON_UV_SIZE * 2;
            }
            case SCOPE -> {
                return ICON_UV_SIZE * 3;
            }
            case STOCK -> {
                return ICON_UV_SIZE * 4;
            }
            case EXTENDED_MAG -> {
                return ICON_UV_SIZE * 5;
            }
        }
        return -1;
    }

    public static int getSlotsTextureWidth() {
        return ICON_UV_SIZE * 7;
    }

    @Override
    public void init() {
        this.clearWidgets();
        // 添加配件槽位
        this.addAttachmentTypeButtons();
        // 添加可选配件列表
        this.addInventoryAttachmentButtons();
        // 添加属性图隐藏按钮
        if (HIDE_GUN_PROPERTY_DIAGRAMS) {
            this.addRenderableWidget(new FlatColorButton(11, 11, 288, 16,
                    Component.translatable("gui.tacz.gun_refit.property_diagrams.show"), b -> switchHideButton()));
        } else {
            this.addRenderableWidget(new FlatColorButton(14, 14, 12, 12, Component.literal("S"), b -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null || player.isSpectator()) return;
                if (IGun.mainHandHoldGun(player)) {
                    IClientPlayerGunOperator.fromLocalPlayer(player).fireSelect();
                    this.init();
                }
            }).setTooltips(Component.translatable("gui.tacz.gun_refit.property_diagrams.fire_mode.switch")));
            int buttonYOffset = GunPropertyDiagrams.getHidePropertyButtonYOffset();
            this.addRenderableWidget(new FlatColorButton(11, buttonYOffset, 288, 12,
                    Component.translatable("gui.tacz.gun_refit.property_diagrams.hide"), b -> switchHideButton()));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float pPartialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, pPartialTick);

        if (!HIDE_GUN_PROPERTY_DIAGRAMS) {
            GunPropertyDiagrams.draw(graphics, font, 11, 11);
        }

        // 26.2 tooltip API: widgets push tooltip contents through their existing consumer hooks.
        ((ScreenAccessor) this).tacz$getRenderables().stream().filter(w -> w instanceof IComponentTooltip).forEach(w -> {
            IComponentTooltip tooltipWidget = (IComponentTooltip) w;
            tooltipWidget.renderTooltip(lines -> graphics.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mouseX, mouseY));
        });
        ((ScreenAccessor) this).tacz$getRenderables().stream().filter(w -> w instanceof IStackTooltip).forEach(w -> {
            IStackTooltip tooltipWidget = (IStackTooltip) w;
            tooltipWidget.renderTooltip(stack -> {
                if (!stack.isEmpty()) {
                    graphics.setTooltipForNextFrame(font, Screen.getTooltipFromItem(Minecraft.getInstance(), stack), stack.getTooltipImage(), mouseX, mouseY);
                }
            });
        });
    }

    /**
     * 改装界面<b>不要</b>全屏模糊 —— 与上游 1.21.1 行为一致。
     *
     * <p>上游 {@code GunRefitScreen} 里有一个空实现的
     * <pre>
     * &#64;Override protected void renderBlurredBackground(float partialTick) { }
     * </pre>
     * 移植时漏掉了，于是走 vanilla 默认实现，改装界面糊上一层背景模糊。
     *
     * <p>26.2 的对应方法改名为 {@code extractBlurredBackground(GuiGraphicsExtractor)}，
     * 调用链（字节码确认）：
     * <pre>
     * Screen#extractBackground
     *   -> Screen#extractBlurredBackground
     *        -> if (options.getMenuBackgroundBlurriness() != 0)
     *               graphics.blurBeforeThisStratum();
     * </pre>
     * 覆写为空即可精确复刻上游「不模糊」的效果。
     *
     * <p>这里必须留空而不是不覆写：玩家一边看着枪模型一边装配件，
     * 背景模糊会把枪身也一起糊掉（模糊是整个 stratum 之前的全屏后处理），
     * 严重影响观察配件外观 —— 这正是上游特意关掉它的原因。
     */
    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addInventoryAttachmentButtons() {
        LocalPlayer player = this.minecraft.player;
        if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE || player == null) {
            return;
        }
        int startX = this.width - 30;
        int startY = INVENTORY_ATTACHMENT_START_Y;
        int pageStart = currentPage * INVENTORY_ATTACHMENT_SLOT_COUNT;
        int count = 0;
        int currentY = startY;
        Inventory inventory = player.getInventory();
        ItemStack gunItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gunItem);
        // 【0929 delta】已显示的配件 id 集合，虚拟 OEM 配件列表据此去重
        Set<Identifier> displayedAttachmentIds = new HashSet<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack inventoryItem = inventory.getItem(i);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(inventoryItem);
            if (attachment != null && iGun != null && attachment.getType(inventoryItem) == RefitTransform.getCurrentTransformType()) {
                if (!iGun.allowAttachment(gunItem, inventoryItem)) {
                    continue;
                }
                displayedAttachmentIds.add(attachment.getAttachmentId(inventoryItem));
                count++;
                if (count <= pageStart) {
                    continue;
                }
                if (count > pageStart + INVENTORY_ATTACHMENT_SLOT_COUNT) {
                    continue;
                }
                InventoryAttachmentSlot button = new InventoryAttachmentSlot(startX, currentY, i, inventory, b -> {
                    int slotIndex = ((InventoryAttachmentSlot) b).getSlotIndex();
                    SoundPlayManager.playerRefitSound(inventory.getItem(slotIndex), player, SoundManager.INSTALL_SOUND);
                    ClientMessageRefitGun message = new ClientMessageRefitGun(slotIndex, inventory.getSelectedSlot(), RefitTransform.getCurrentTransformType());
                    ClientPlayNetworking.send(message);
                });
                this.addRenderableWidget(button);
                currentY = currentY + SLOT_SIZE;
            }
        }
        // 【0929 delta】追加虚拟 OEM 配件（不在真实背包、由枪械内置默认配件提供的可选列表）
        if (iGun != null) {
            for (ItemStack virtualAttachment : getVirtualOemAttachments(gunItem, iGun, RefitTransform.getCurrentTransformType(), displayedAttachmentIds)) {
                count++;
                if (count > pageStart && count <= pageStart + INVENTORY_ATTACHMENT_SLOT_COUNT) {
                    InventoryAttachmentSlot button = new InventoryAttachmentSlot(
                            startX,
                            currentY,
                            virtualAttachment,
                            inventory,
                            b -> {
                                ItemStack attachmentStack = ((InventoryAttachmentSlot) b).getItemStack();
                                IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
                                if (attachment != null) {
                                    SoundPlayManager.playerRefitSound(attachmentStack, player, SoundManager.INSTALL_SOUND);
                                    ClientMessageInstallVirtualAttachment message = new ClientMessageInstallVirtualAttachment(
                                            inventory.getSelectedSlot(), RefitTransform.getCurrentTransformType(), attachment.getAttachmentId(attachmentStack)
                                    );
                                    ClientPlayNetworking.send(message);
                                }
                            }
                    );
                    this.addRenderableWidget(button);
                    currentY += SLOT_SIZE;
                }
            }
        }
        int totalPage = (count - 1) / INVENTORY_ATTACHMENT_SLOT_COUNT;
        RefitTurnPageButton turnPageButtonUp = new RefitTurnPageButton(startX, startY - 10, true, b -> {
            if (currentPage > 0) {
                currentPage--;
                init();
            }
        });
        RefitTurnPageButton turnPageButtonDown = new RefitTurnPageButton(startX, startY + SLOT_SIZE * INVENTORY_ATTACHMENT_SLOT_COUNT + 2, false, b -> {
            if (currentPage < totalPage) {
                currentPage++;
                init();
            }
        });
        if (currentPage < totalPage) {
            this.addRenderableWidget(turnPageButtonDown);
        }
        if (currentPage > 0) {
            this.addRenderableWidget(turnPageButtonUp);
        }
    }

    private void addAttachmentTypeButtons() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
        if (iGun == null) {
            return;
        }
        int startX = this.width - 30;
        int startY = 10;
        Inventory inventory = player.getInventory();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE) {
                    TimelessAPI.getGunDisplay(player.getMainHandItem())
                            .map(GunDisplayInstance::getLaserConfig)
                            .ifPresent(laserConfig -> {
                                if (laserConfig.canEdit()) {
                                    // 添加镭射颜色选择器
                                    HSVSliderGroup hsvSliderGroup = new HSVSliderGroup(width - 140, height - 64, 120, 16, inventory, inventory.getSelectedSlot(), AttachmentType.NONE);
                                    this.addRenderableWidget(hsvSliderGroup.getHueSlider());
                                    this.addRenderableWidget(hsvSliderGroup.getSaturationSlider());
                                }
                            });
                }
                continue;
            }
            GunAttachmentSlot button = new GunAttachmentSlot(startX, startY, type, inventory.getSelectedSlot(), inventory, b -> {
                AttachmentType buttonType = ((GunAttachmentSlot) b).getType();
                // 如果这个槽位不允许安装配件，则默认退回概览，不选中槽位。
                if (!((GunAttachmentSlot) b).isAllow()) {
                    if (RefitTransform.changeRefitScreenView(AttachmentType.NONE)) {
                        this.init();
                    }
                    return;
                }
                // 点击的是当前选中的槽位，则退回概览
                if (RefitTransform.getCurrentTransformType() == buttonType && buttonType != AttachmentType.NONE) {
                    if (RefitTransform.changeRefitScreenView(AttachmentType.NONE)) {
                        this.init();
                    }
                    return;
                }
                // 切换选中的槽位。
                if (RefitTransform.changeRefitScreenView(buttonType)) {
                    this.init();
                }
            });
            if (RefitTransform.getCurrentTransformType() == type) {
                button.setSelected(true);
                // 【0929 delta】变体列 + 槽位适配器列的横向布局坐标
                int adapterListX = this.width - 30 - VARIANT_BUTTON_WIDTH - LIST_COLUMN_GAP;
                boolean hasSlotAdapters = !SlotAdapterHelper.getAvailableSlotAdapters(player.getMainHandItem(), type).isEmpty();
                int variantListX = hasSlotAdapters ? adapterListX - VARIANT_BUTTON_WIDTH - LIST_COLUMN_GAP : startX + SLOT_SIZE - VARIANT_BUTTON_WIDTH;
                // 添加拆卸配件按钮
                RefitUnloadButton unloadButton = new RefitUnloadButton(startX + 5, startY + SLOT_SIZE + 2, b -> {
                    ItemStack attachmentItem = button.getAttachmentItem();
                    if (!attachmentItem.isEmpty()) {
                        int freeSlot = inventory.getFreeSlot();
                        // 【0929 delta】虚拟 OEM 配件没有实体，卸载不占用背包空格
                        if (freeSlot == -1 && !VirtualOemAttachment.isMarked(attachmentItem)) {
                            player.sendSystemMessage(Component.translatable("gui.tacz.gun_refit.unload.no_space"));
                        } else {
                            SoundPlayManager.playerRefitSound(attachmentItem, player, SoundManager.UNINSTALL_SOUND);
                            ClientMessageUnloadAttachment message = new ClientMessageUnloadAttachment(inventory.getSelectedSlot(), RefitTransform.getCurrentTransformType());
                            ClientPlayNetworking.send(message);
                        }
                    }
                });
                if (!button.getAttachmentItem().isEmpty()) {
                    this.addRenderableWidget(unloadButton);
                    // 【0929 delta】添加配件变体选择按钮
                    this.addVariantButtons(type, button.getAttachmentItem(), inventory, variantListX, startY + SLOT_SIZE + 14);

                    if (button.getAttachmentItem().getItem() instanceof IAttachment iAttachment) {
                        TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(button.getAttachmentItem()))
                                .map(ClientAttachmentIndex::getLaserConfig)
                                .ifPresent(laserConfig -> {
                                    if (laserConfig.canEdit()) {
                                        // 添加镭射颜色选择器
                                        HSVSliderGroup hsvSliderGroup = new HSVSliderGroup(width - 140, height - 64, 120, 16, inventory, inventory.getSelectedSlot(), type);
                                        this.addRenderableWidget(hsvSliderGroup.getHueSlider());
                                        this.addRenderableWidget(hsvSliderGroup.getSaturationSlider());
                                    }
                                });
                    }
                }
                // 【0929 delta】添加槽位适配器选择按钮
                this.addSlotAdapterButtons(type, button.getAttachmentItem(), inventory, adapterListX, INVENTORY_ATTACHMENT_START_Y);
            }
            this.addRenderableWidget(button);
            startX = startX - SLOT_SIZE;
        }
    }

    @Override
    public void onClose() {
        // 关闭界面时，一次性上传所有的染色数据
        LocalPlayer player = this.minecraft.player;
        if (player != null) {
            ItemStack gun = player.getMainHandItem();
            if (player.getMainHandItem().getItem() instanceof IGun) {
                ClientMessageLaserColor message = new ClientMessageLaserColor(gun, player.getInventory().getSelectedSlot());
                ClientPlayNetworking.send(message);
            }
        }
        super.onClose();
    }

    private void switchHideButton() {
        HIDE_GUN_PROPERTY_DIAGRAMS = !HIDE_GUN_PROPERTY_DIAGRAMS;
        this.init();
    }

    // ===== 0929 delta：变体选择 + 槽位适配器选择 + 虚拟 OEM 配件 =====

    private void addVariantButtons(AttachmentType type, ItemStack attachmentItem, Inventory inventory, int x, int y) {
        if (attachmentItem.getItem() instanceof IAttachment attachment) {
            Identifier attachmentId = attachment.getAttachmentId(attachmentItem);
            List<Map.Entry<Identifier, CommonAttachmentVariantIndex>> variants = TimelessAPI.getAllCommonAttachmentVariantIndex()
                    .stream()
                    .filter(entry -> entry.getValue().appliesTo(attachmentId))
                    .sorted(Comparator.comparingInt(entry -> entry.getValue().getSort()))
                    .toList();
            if (!variants.isEmpty()) {
                Identifier current = VariantHelper.getVariant(attachmentItem);
                Component standardLabel = current == null
                        ? Component.literal("> ").append(Component.translatable("gui.tacz.refit.variant_default"))
                        : Component.translatable("gui.tacz.refit.variant_default");
                this.addRenderableWidget(new FlatColorButton(x, y, VARIANT_BUTTON_WIDTH, VARIANT_BUTTON_HEIGHT, standardLabel, b -> {
                    ClientPlayNetworking.send(new ClientMessageSetAttachmentVariant(inventory.getSelectedSlot(), type, null));
                    this.init();
                }));
                int currentY = y + VARIANT_BUTTON_HEIGHT + VARIANT_BUTTON_SPACING;

                for (Map.Entry<Identifier, CommonAttachmentVariantIndex> entry : variants) {
                    Identifier variantId = entry.getKey();
                    Component name = Component.translatable(entry.getValue().getPojo().getName());
                    Component label = variantId.equals(current) ? Component.literal("> ").append(name) : name;
                    this.addRenderableWidget(new FlatColorButton(x, currentY, VARIANT_BUTTON_WIDTH, VARIANT_BUTTON_HEIGHT, label, b -> {
                        ClientPlayNetworking.send(new ClientMessageSetAttachmentVariant(inventory.getSelectedSlot(), type, variantId));
                        this.init();
                    }));
                    currentY += VARIANT_BUTTON_HEIGHT + VARIANT_BUTTON_SPACING;
                }
            }
        }
    }

    private void addSlotAdapterButtons(AttachmentType type, ItemStack attachmentItem, Inventory inventory, int x, int y) {
        ItemStack gunItem = inventory.getItem(inventory.getSelectedSlot());
        List<Map.Entry<Identifier, CommonSlotAdapterIndex>> adapters = SlotAdapterHelper.getAvailableSlotAdapters(gunItem, type);
        if (!adapters.isEmpty()) {
            Identifier attachmentId = null;
            if (!attachmentItem.isEmpty()) {
                if (!(attachmentItem.getItem() instanceof IAttachment attachment)) {
                    return;
                }
                attachmentId = attachment.getAttachmentId(attachmentItem);
            }

            Identifier current = SlotAdapterHelper.getSelectedAvailableSlotAdapter(gunItem, type);
            Identifier currentAttachmentId = attachmentId;
            boolean noneSelected = current == null;
            Component noneLabel = noneSelected
                    ? Component.literal("> ").append(Component.translatable("gui.tacz.refit.adapter_none"))
                    : Component.translatable("gui.tacz.refit.adapter_none");
            FlatColorButton noneButton = new FlatColorButton(x, y, VARIANT_BUTTON_WIDTH, VARIANT_BUTTON_HEIGHT, noneLabel, b -> {
                this.currentPage = 0;
                ClientPlayNetworking.send(new ClientMessageSetSlotAdapter(inventory.getSelectedSlot(), type, null));
                this.init();
            });
            if (attachmentId != null && !SlotAdapterHelper.allowsDirectAttachment(gunItem, attachmentId)) {
                noneButton.active = false;
                noneButton.setTooltips(Component.translatable("gui.tacz.refit.adapter_requires_unload"));
            }

            this.addRenderableWidget(noneButton);
            int currentY = y + VARIANT_BUTTON_HEIGHT + VARIANT_BUTTON_SPACING;

            for (Map.Entry<Identifier, CommonSlotAdapterIndex> entry : adapters) {
                Identifier adapterId = entry.getKey();
                Component name = Component.translatable(entry.getValue().getPojo().getName());
                Component label = adapterId.equals(current) ? Component.literal("> ").append(name) : name;
                FlatColorButton adapterButton = new FlatColorButton(x, currentY, VARIANT_BUTTON_WIDTH, VARIANT_BUTTON_HEIGHT, label, b -> {
                    this.currentPage = 0;
                    ClientPlayNetworking.send(new ClientMessageSetSlotAdapter(inventory.getSelectedSlot(), type, adapterId));
                    this.init();
                });
                if (currentAttachmentId != null && !entry.getValue().allowsAttachment(currentAttachmentId)) {
                    adapterButton.active = false;
                    adapterButton.setTooltips(Component.translatable("gui.tacz.refit.adapter_requires_unload"));
                }

                this.addRenderableWidget(adapterButton);
                currentY += VARIANT_BUTTON_HEIGHT + VARIANT_BUTTON_SPACING;
            }
        }
    }

    private static List<ItemStack> getVirtualOemAttachments(ItemStack gunItem, IGun iGun, AttachmentType type, Set<Identifier> excludedAttachmentIds) {
        Identifier currentAttachmentId = iGun.getAttachmentId(gunItem, type);
        return TimelessAPI.getAllCommonAttachmentIndex()
                .stream()
                .filter(entry -> entry.getValue().getType() == type)
                .filter(entry -> VirtualOemAttachment.isAllowedVirtualOem(iGun.getGunId(gunItem), entry.getKey(), type))
                .filter(entry -> VirtualOemAttachment.shouldShowIcon(iGun.getGunId(gunItem), entry.getKey(), type))
                .filter(entry -> !excludedAttachmentIds.contains(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> entry.getValue().getSort()))
                .map(entry -> Pair.of(entry.getKey(), AttachmentItemBuilder.create().setId(entry.getKey()).build()))
                .filter(pair -> !pair.getKey().equals(currentAttachmentId))
                .filter(pair -> iGun.allowAttachment(gunItem, pair.getValue()))
                .map(Pair::getValue)
                .toList();
    }
}
