package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.util.AmmoStorageHelper;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientMessageAmmoStorageInteract implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_ammo_storage_interact");
    public static final CustomPacketPayload.Type<ClientMessageAmmoStorageInteract> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessageAmmoStorageInteract> CODEC = StreamCodec.ofMember(ClientMessageAmmoStorageInteract::write, ClientMessageAmmoStorageInteract::new);

    private static final long PICKUP_COOLDOWN_TICKS = 5L;
    private static final long ACCUMULATE_WINDOW_TICKS = 20L;

    /**
     * 26.2 移植说明：Forge 版用 {@code player.getPersistentData()} 存交互冷却，
     * Fabric 无此 API；改为服务端内存表（冷却/连击提示无需持久化）。
     */
    private static final Map<UUID, Long> LAST_PICKUP = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ACCUMULATE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> ACCUMULATE_AMMO = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACCUMULATE_COUNT = new ConcurrentHashMap<>();

    private final BlockPos pos;

    public ClientMessageAmmoStorageInteract(BlockPos pos) {
        this.pos = pos;
    }

    public ClientMessageAmmoStorageInteract(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (player.isSpectator()) {
            return;
        }
        UUID uuid = player.getUUID();
        long gameTime = player.level().getGameTime();
        long lastPickup = LAST_PICKUP.getOrDefault(uuid, 0L);
        if (lastPickup > 0L && gameTime - lastPickup < PICKUP_COOLDOWN_TICKS) {
            return;
        }
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 36.0) {
            return;
        }
        ItemStack gunStack = player.getMainHandItem();
        if (!(gunStack.getItem() instanceof IGun gun)) {
            return;
        }
        Identifier ammoId = TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack))
                .map(index -> index.getGunData().getAmmoId())
                .orElse(null);
        if (ammoId == null) {
            return;
        }
        if (!(player.level().getBlockEntity(pos) instanceof AmmoStorageBlockEntity storage)) {
            return;
        }
        int amount = AmmoStorageHelper.getStackSize(AmmoStorageHelper.createAmmoStack(ammoId, 1));
        ItemStack extracted = storage.extractAmmo(ammoId, amount, false);
        LAST_PICKUP.put(uuid, gameTime);
        if (extracted.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.tacz.ammo_storage.no_gun_stock"));
            return;
        }
        int count = extracted.getCount();
        if (!player.getInventory().add(extracted)) {
            player.drop(extracted, false);
        }

        int accumulatedCount = getAccumulatedCount(uuid, ammoId, count, gameTime);
        player.sendOverlayMessage(
                Component.translatable("message.tacz.ammo_storage.picked",
                        AmmoStorageHelper.createAmmoStack(ammoId, 1).getHoverName(), accumulatedCount)
        );
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static int getAccumulatedCount(UUID uuid, Identifier ammoId, int count, long gameTime) {
        String ammoKey = ammoId.toString();
        long lastAccumulate = LAST_ACCUMULATE.getOrDefault(uuid, 0L);
        String lastAmmoKey = ACCUMULATE_AMMO.get(uuid);
        int accumulated = count;
        if (lastAccumulate > 0L && gameTime - lastAccumulate <= ACCUMULATE_WINDOW_TICKS && ammoKey.equals(lastAmmoKey)) {
            accumulated = count + ACCUMULATE_COUNT.getOrDefault(uuid, 0);
        }

        LAST_ACCUMULATE.put(uuid, gameTime);
        ACCUMULATE_AMMO.put(uuid, ammoKey);
        ACCUMULATE_COUNT.put(uuid, accumulated);
        return accumulated;
    }
}
