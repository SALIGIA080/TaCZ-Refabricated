package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.inventory.AmmoStorageEntry;
import com.tacz.guns.inventory.AmmoStorageMenu;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record ServerMessageAmmoStorageContents(int containerId, int capacityStacks, long usedStacks, List<AmmoStorageEntry> entries) implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_ammo_storage_contents");
    public static final CustomPacketPayload.Type<ServerMessageAmmoStorageContents> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessageAmmoStorageContents> CODEC = StreamCodec.ofMember(ServerMessageAmmoStorageContents::write, ServerMessageAmmoStorageContents::decode);

    public ServerMessageAmmoStorageContents(int containerId, int capacityStacks, long usedStacks, List<AmmoStorageEntry> entries) {
        this.containerId = containerId;
        this.capacityStacks = capacityStacks;
        this.usedStacks = usedStacks;
        this.entries = List.copyOf(entries);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(capacityStacks);
        buf.writeVarLong(usedStacks);
        buf.writeVarInt(entries.size());
        for (AmmoStorageEntry entry : entries) {
            buf.writeVarInt(entry.id());
            ItemStack.STREAM_CODEC.encode(buf, entry.sample());
            buf.writeVarLong(entry.count());
            buf.writeVarLong(entry.remainingCapacity());
        }
    }

    public static ServerMessageAmmoStorageContents decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int capacityStacks = buf.readVarInt();
        long usedStacks = buf.readVarLong();
        int size = buf.readVarInt();
        if (capacityStacks >= 0 && usedStacks >= 0L && size >= 0 && size <= 27) {
            List<AmmoStorageEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int id = buf.readVarInt();
                ItemStack sample = ItemStack.STREAM_CODEC.decode(buf);
                long count = buf.readVarLong();
                long remaining = buf.readVarLong();
                if (id < 0 || sample.isEmpty() || count <= 0L || remaining < 0L) {
                    throw new DecoderException("Invalid ammo storage entry");
                }
                entries.add(new AmmoStorageEntry(id, sample, count, remaining));
            }
            return new ServerMessageAmmoStorageContents(containerId, capacityStacks, usedStacks, entries);
        } else {
            throw new DecoderException("Invalid ammo storage entry count: " + size);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        updateContents(this);
    }

    @Environment(EnvType.CLIENT)
    private static void updateContents(ServerMessageAmmoStorageContents message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof AmmoStorageMenu menu && menu.containerId == message.containerId) {
            menu.setContents(message.capacityStacks, message.usedStacks, message.entries);
        }
    }
}
