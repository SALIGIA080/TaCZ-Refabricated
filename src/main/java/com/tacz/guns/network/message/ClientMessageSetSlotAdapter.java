package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.SlotAdapterHelper;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ClientMessageSetSlotAdapter implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_set_slot_adapter");
    public static final CustomPacketPayload.Type<ClientMessageSetSlotAdapter> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageSetSlotAdapter> CODEC = StreamCodec.ofMember(ClientMessageSetSlotAdapter::write, ClientMessageSetSlotAdapter::new);

    private final int gunSlotIndex;
    private final AttachmentType attachmentType;
    @Nullable
    private final Identifier adapterId;

    public ClientMessageSetSlotAdapter(FriendlyByteBuf buf) {
        this.gunSlotIndex = buf.readInt();
        this.attachmentType = buf.readEnum(AttachmentType.class);
        this.adapterId = buf.readBoolean() ? buf.readIdentifier() : null;
    }

    public ClientMessageSetSlotAdapter(int gunSlotIndex, AttachmentType attachmentType, @Nullable Identifier adapterId) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
        this.adapterId = adapterId;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
        buf.writeBoolean(adapterId != null);
        if (adapterId != null) {
            buf.writeIdentifier(adapterId);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (attachmentType == AttachmentType.NONE) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (gunSlotIndex < 0 || gunSlotIndex >= inventory.getContainerSize()) {
            return;
        }
        ItemStack gunItem = inventory.getItem(gunSlotIndex);
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null || gun.hasAttachmentLock(gunItem) || !gun.allowAttachmentType(gunItem, attachmentType)) {
            return;
        }
        ItemStack attachmentItem = gun.getAttachment(gunItem, attachmentType);
        Identifier attachmentId = null;
        if (!attachmentItem.isEmpty()) {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
            if (attachment == null || attachment.getType(attachmentItem) != attachmentType) {
                return;
            }
            attachmentId = attachment.getAttachmentId(attachmentItem);
        }

        // 【1025 delta】触发改装成就判定
        ItemStack beforeRefit = gunItem.copy();
        if (adapterId == null) {
            if (attachmentId != null && !SlotAdapterHelper.allowsDirectAttachment(gunItem, attachmentId)) {
                return;
            }
            SlotAdapterHelper.removeSlotAdapter(gunItem, attachmentType);
        } else {
            Identifier currentAttachmentId = attachmentId;
            boolean validSelection = SlotAdapterHelper.getAvailableSlotAdapters(gunItem, attachmentType)
                    .stream()
                    .filter(entry -> entry.getKey().equals(adapterId))
                    .anyMatch(entry -> currentAttachmentId == null || entry.getValue().allowsAttachment(currentAttachmentId));
            if (!validSelection) {
                return;
            }
            SlotAdapterHelper.setSlotAdapter(gunItem, attachmentType, adapterId);
        }

        AttachmentPropertyManager.postChangeEvent(player, gunItem);
        GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem);
        player.inventoryMenu.broadcastChanges();
        NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
    }
}
