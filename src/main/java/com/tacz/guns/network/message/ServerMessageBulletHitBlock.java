package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.HitBlockEffectHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class ServerMessageBulletHitBlock implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "s2c_bullet_hit_block");
    public static final CustomPacketPayload.Type<ServerMessageBulletHitBlock> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ServerMessageBulletHitBlock> CODEC = StreamCodec.ofMember(ServerMessageBulletHitBlock::write, ServerMessageBulletHitBlock::new);

    private final double hitX;
    private final double hitY;
    private final double hitZ;
    private final Direction direction;
    private final BlockPos blockPos;
    private final String ammoId;
    private final String gunId;
    private final String gunDisplayId;
    private final boolean incendiary;

    public ServerMessageBulletHitBlock(Vec3 hitPos, Direction direction, BlockPos blockPos, String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        this(hitPos.x, hitPos.y, hitPos.z, direction, blockPos, ammoId, gunId, gunDisplayId, incendiary);
    }

    public ServerMessageBulletHitBlock(
            double hitX, double hitY, double hitZ, Direction direction, BlockPos blockPos, String ammoId, String gunId, String gunDisplayId, boolean incendiary
    ) {
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.direction = direction;
        this.blockPos = blockPos;
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.incendiary = incendiary;
    }

    public ServerMessageBulletHitBlock(FriendlyByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readEnum(Direction.class), buf.readBlockPos(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(hitX);
        buf.writeDouble(hitY);
        buf.writeDouble(hitZ);
        buf.writeEnum(direction);
        buf.writeBlockPos(blockPos);
        buf.writeUtf(ammoId);
        buf.writeUtf(gunId);
        buf.writeUtf(gunDisplayId);
        buf.writeBoolean(incendiary);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Environment(EnvType.CLIENT)
    public void handle(LocalPlayer player, PacketSender responseSender) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            HitBlockEffectHandler.handle(
                    level,
                    new Vec3(hitX, hitY, hitZ),
                    direction,
                    blockPos,
                    ammoId,
                    gunId,
                    gunDisplayId,
                    incendiary
            );
        }
    }
}
