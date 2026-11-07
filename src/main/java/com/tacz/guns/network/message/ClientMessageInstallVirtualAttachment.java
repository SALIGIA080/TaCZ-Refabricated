package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.VirtualOemAttachment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ClientMessageInstallVirtualAttachment implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_install_virtual_attachment");
    public static final CustomPacketPayload.Type<ClientMessageInstallVirtualAttachment> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageInstallVirtualAttachment> CODEC = StreamCodec.ofMember(ClientMessageInstallVirtualAttachment::write, ClientMessageInstallVirtualAttachment::new);

    private final int gunSlotIndex;
    private final AttachmentType attachmentType;
    private final Identifier attachmentId;

    public ClientMessageInstallVirtualAttachment(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readEnum(AttachmentType.class), buf.readIdentifier());
    }

    public ClientMessageInstallVirtualAttachment(int gunSlotIndex, AttachmentType attachmentType, Identifier attachmentId) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
        this.attachmentId = attachmentId;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
        buf.writeIdentifier(attachmentId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        Inventory inventory = player.getInventory();
        ItemStack gunItem = inventory.getItem(gunSlotIndex);
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null || iGun.hasAttachmentLock(gunItem)) {
            return;
        }
        if (!VirtualOemAttachment.isAllowedVirtualOem(iGun.getGunId(gunItem), attachmentId, attachmentType)) {
            return;
        }
        ItemStack virtualAttachment = AttachmentItemBuilder.create().setId(attachmentId).build();
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(virtualAttachment);
        if (iAttachment == null
                || iAttachment.getType(virtualAttachment) != attachmentType
                || !iGun.allowAttachment(gunItem, virtualAttachment)) {
            return;
        }
        ItemStack oldAttachmentItem = iGun.getAttachment(gunItem, attachmentType);
        if (!oldAttachmentItem.isEmpty() && !VirtualOemAttachment.isMarked(oldAttachmentItem) && inventory.getFreeSlot() == -1) {
            return;
        }
        VirtualOemAttachment.mark(virtualAttachment);
        // 【1025 delta】触发改装成就判定
        ItemStack beforeRefit = gunItem.copy();
        iGun.installAttachment(gunItem, virtualAttachment);
        GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem);
        if (!oldAttachmentItem.isEmpty() && !VirtualOemAttachment.isMarked(oldAttachmentItem)) {
            inventory.add(oldAttachmentItem);
        }

        AttachmentPropertyManager.postChangeEvent(player, gunItem);
        player.inventoryMenu.broadcastChanges();
        NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
    }
}
