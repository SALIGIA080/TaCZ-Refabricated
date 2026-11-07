package com.tacz.guns.client.gui.components.refit;

import com.tacz.guns.client.gui.GunRefitScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class InventoryAttachmentSlot extends Button implements IStackTooltip {
    private final int slotIndex;
    private final Inventory inventory;
    private final ItemStack displayStack;

    public InventoryAttachmentSlot(int pX, int pY, int slotIndex, Inventory inventory, Button.OnPress onPress) {
        this(pX, pY, slotIndex, inventory, ItemStack.EMPTY, onPress);
    }

    // 【0929 delta】虚拟 OEM 配件不在真实背包里，用 displayStack 直接显示
    public InventoryAttachmentSlot(int pX, int pY, ItemStack displayStack, Inventory inventory, Button.OnPress onPress) {
        this(pX, pY, -1, inventory, displayStack, onPress);
    }

    private InventoryAttachmentSlot(int pX, int pY, int slotIndex, Inventory inventory, ItemStack displayStack, Button.OnPress onPress) {
        super(pX, pY, 18, 18, Component.empty(), onPress, DEFAULT_NARRATION);
        this.slotIndex = slotIndex;
        this.inventory = inventory;
        this.displayStack = displayStack;
    }

    @Override
    public void renderTooltip(Consumer<ItemStack> consumer) {
        if (this.isHoveredOrFocused()) {
            ItemStack item = this.getItemStack();
            if (!item.isEmpty()) {
                consumer.accept(item);
            }
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int pMouseX, int pMouseY, float pPartialTick) {
        int x = getX(), y = getY();
        if (isHoveredOrFocused()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GunRefitScreen.SLOT_TEXTURE, x, y, 0, 0, width, height, 18, 18);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, GunRefitScreen.SLOT_TEXTURE, x + 1, y + 1, 1, 1, width - 2, height - 2, 18, 18);
        }
        graphics.item(getItemStack(), x + 1, y + 1);
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public ItemStack getItemStack() {
        if (!this.displayStack.isEmpty()) {
            return this.displayStack;
        }
        return 0 <= this.slotIndex && this.slotIndex < this.inventory.getContainerSize()
                ? this.inventory.getItem(this.slotIndex)
                : ItemStack.EMPTY;
    }
}
