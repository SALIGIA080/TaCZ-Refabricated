package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.advancement.GunRefitTrigger;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.util.VariantHelper;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ClientMessageSetAttachmentVariant implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_set_attachment_variant");
    public static final CustomPacketPayload.Type<ClientMessageSetAttachmentVariant> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageSetAttachmentVariant> CODEC = StreamCodec.ofMember(ClientMessageSetAttachmentVariant::write, ClientMessageSetAttachmentVariant::new);

    private final int gunSlotIndex;
    private final AttachmentType attachmentType;
    @Nullable
    private final Identifier variantId;

    public ClientMessageSetAttachmentVariant(FriendlyByteBuf buf) {
        this.gunSlotIndex = buf.readInt();
        this.attachmentType = buf.readEnum(AttachmentType.class);
        this.variantId = buf.readBoolean() ? buf.readIdentifier() : null;
    }

    public ClientMessageSetAttachmentVariant(int gunSlotIndex, AttachmentType attachmentType, @Nullable Identifier variantId) {
        this.gunSlotIndex = gunSlotIndex;
        this.attachmentType = attachmentType;
        this.variantId = variantId;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(gunSlotIndex);
        buf.writeEnum(attachmentType);
        buf.writeBoolean(variantId != null);
        if (variantId != null) {
            buf.writeIdentifier(variantId);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        Inventory inventory = player.getInventory();
        ItemStack gunItem = inventory.getItem(gunSlotIndex);
        IGun gun = IGun.getIGunOrNull(gunItem);
        if (gun == null || gun.hasAttachmentLock(gunItem)) {
            return;
        }
        ItemStack attachmentItem = gun.getAttachment(gunItem, attachmentType);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (attachment == null || attachmentItem.isEmpty()) {
            return;
        }
        // 【1025 delta】触发改装成就判定
        ItemStack beforeRefit = gunItem.copy();
        Identifier attachmentId = attachment.getAttachmentId(attachmentItem);
        if (variantId == null) {
            VariantHelper.removeVariant(attachmentItem);
        } else {
            boolean valid = TimelessAPI.getCommonAttachmentVariantIndex(variantId).map(index -> index.appliesTo(attachmentId)).orElse(false);
            if (!valid) {
                return;
            }
            VariantHelper.setVariant(attachmentItem, variantId);
        }

        gun.installAttachment(gunItem, attachmentItem);
        GunRefitTrigger.INSTANCE.trigger(player, beforeRefit, gunItem);
        AttachmentPropertyManager.postChangeEvent(player, gunItem);
        player.inventoryMenu.broadcastChanges();
        NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
    }
}
