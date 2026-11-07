package com.tacz.guns.network.message;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class ClientMessagePlayerUnloadGun implements CustomPacketPayload {
    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "c2s_player_unload_gun");
    public static final CustomPacketPayload.Type<ClientMessagePlayerUnloadGun> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ClientMessagePlayerUnloadGun> CODEC = StreamCodec.ofMember(ClientMessagePlayerUnloadGun::write, ClientMessagePlayerUnloadGun::new);

    private final int slot;
    private final Identifier gunId;

    public ClientMessagePlayerUnloadGun(int slot, Identifier gunId) {
        this.slot = slot;
        this.gunId = gunId;
    }

    public ClientMessagePlayerUnloadGun(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readIdentifier());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeIdentifier(gunId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player, PacketSender responseSender) {
        if (player.isAlive() && !player.isSpectator() && slot >= 0 && slot < 9 && player.getInventory().getSelectedSlot() == slot) {
            ItemStack stack = player.getMainHandItem();
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun != null && gunId.equals(gun.getGunId(stack))) {
                CommonGunIndex index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
                if (index == null) {
                    showCannotUnload(player);
                } else if (index.canUnloadAmmo() && !gun.useInventoryAmmo(stack)) {
                    Bolt bolt = index.getGunData().getBolt();
                    long ammoBefore = getLoadedAmmoCount(gun, stack, bolt);
                    if (ammoBefore == 0L) {
                        player.sendOverlayMessage(Component.translatable("message.tacz.unload.no_ammo"));
                    } else {
                        Identifier gunDisplayId = gun.getGunDisplayId(stack);
                        IGunOperator.fromLivingEntity(player).unloadAmmo();
                        player.inventoryMenu.broadcastChanges();
                        if (getLoadedAmmoCount(gun, stack, bolt) < ammoBefore) {
                            NetworkHandler.sendToClientPlayer(new ServerMessageGunUnload(gunId, gunDisplayId), player);
                        } else {
                            showCannotUnload(player);
                        }
                    }
                } else {
                    player.sendOverlayMessage(Component.translatable("message.tacz.unload.unsupported"));
                }
            } else {
                showCannotUnload(player);
            }
        } else {
            showCannotUnload(player);
        }
    }

    private static long getLoadedAmmoCount(IGun gun, ItemStack stack, Bolt bolt) {
        return Math.max(0L, (long) gun.getCurrentAmmoCount(stack)) + (bolt != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack) ? 1L : 0L);
    }

    private static void showCannotUnload(ServerPlayer player) {
        player.sendOverlayMessage(Component.translatable("message.tacz.unload.cannot"));
    }
}
