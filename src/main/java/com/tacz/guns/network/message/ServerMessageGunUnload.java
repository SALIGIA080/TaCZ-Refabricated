package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.sound.SoundPlayManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

public class ServerMessageGunUnload implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_gun_unload");
    public static final CustomPacketPayload.Type<ServerMessageGunUnload> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageGunUnload> CODEC = StreamCodec.ofMember(ServerMessageGunUnload::write, ServerMessageGunUnload::new);

    private final Identifier gunId;
    private final Identifier gunDisplayId;

    public ServerMessageGunUnload(Identifier gunId, Identifier gunDisplayId) {
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
    }

    public ServerMessageGunUnload(FriendlyByteBuf buf) {
        this(buf.readIdentifier(), buf.readIdentifier());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeIdentifier(gunId);
        buf.writeIdentifier(gunDisplayId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        playSound(this);
    }

    @Environment(EnvType.CLIENT)
    private static void playSound(ServerMessageGunUnload message) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            SoundPlayManager.playUnloadSound(player, message.gunId, message.gunDisplayId);
        }
    }
}
