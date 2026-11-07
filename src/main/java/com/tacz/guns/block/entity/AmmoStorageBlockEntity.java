package com.tacz.guns.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.block.AmmoStorageBlock;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.inventory.AmmoStorageInventory;
import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.util.AmmoStorageHelper;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.util.Unit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class AmmoStorageBlockEntity extends BlockEntity implements ExtendedMenuProvider<Unit> {
   public static final BlockEntityType<AmmoStorageBlockEntity> TYPE = new BlockEntityType<>(
         AmmoStorageBlockEntity::new,
         Set.of(ModBlocks.AMMO_STORAGE, ModBlocks.AMMO_STORAGE_GOLD, ModBlocks.AMMO_STORAGE_DIAMOND)
   );
   public static final int MAX_AMMO_TYPES = 27;
   private static final String ENTRIES_TAG = "Entries";

   /**
    * 持久化条目。26.2 移植说明：Forge 版手写 CompoundTag 读写，
    * 现改为 ValueInput/ValueOutput + Codec。
    */
   private record SavedEntry(int slot, ItemStack stack, int count) {
      static final Codec<SavedEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("Slot").forGetter(SavedEntry::slot),
            ItemStack.OPTIONAL_CODEC.fieldOf("Stack").forGetter(SavedEntry::stack),
            Codec.INT.fieldOf("Count").forGetter(SavedEntry::count)
      ).apply(instance, SavedEntry::new));
   }

   private final AmmoStorageInventory inventory = new AmmoStorageInventory(
         MAX_AMMO_TYPES, this::getCapacityStacks, AmmoStorageHelper::isValidAmmo, AmmoStorageHelper::getStackSize, this::onInventoryChanged
   );

   public AmmoStorageBlockEntity(BlockPos pos, BlockState state) {
      super(TYPE, pos, state);
   }

   public int insertAmmo(ItemStack stack, boolean simulate) {
      return this.inventory.insert(stack, simulate);
   }

   public ItemStack extractAmmo(ItemStack sample, int count, boolean simulate) {
      return this.inventory.extract(sample, count, simulate);
   }

   public ItemStack extractAmmo(Identifier ammoId, int count, boolean simulate) {
      for (AmmoStorageInventory.Content content : this.inventory.getContents()) {
         if (ammoId.equals(AmmoStorageHelper.getAmmoId(content.sample()))) {
            return this.inventory.extract(content.sample(), count, simulate);
         }
      }
      return ItemStack.EMPTY;
   }

   public int moveAmmoToInventory(Container target, int targetSlots, ItemStack sample, int count) {
      return this.inventory.moveToInventory(target, targetSlots, sample, count);
   }

   public List<AmmoStorageInventory.Content> getContents() {
      return this.inventory.getContents();
   }

   public long getInventoryRevision() {
      return this.inventory.getRevision();
   }

   public long getUsedStacks() {
      return this.inventory.getUsedStacks();
   }

   public long getRemainingCapacity(ItemStack sample) {
      return this.inventory.getRemainingCapacity(sample);
   }

   public int getComparatorSignal() {
      return this.inventory.getComparatorSignal();
   }

   public boolean stillValid(Player player) {
      return this.level != null
         && this.level.getBlockEntity(this.worldPosition) == this
         && player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
   }

   @Override
   public void loadAdditional(ValueInput input) {
      super.loadAdditional(input);

      for (SavedEntry saved : input.listOrEmpty(ENTRIES_TAG, SavedEntry.CODEC)) {
         ItemStack stack = saved.stack();
         int count = saved.count();
         if (AmmoStorageHelper.isValidAmmo(stack) && count > 0) {
            stack.setCount(count);
            this.inventory.insert(stack, false);
         }
      }
   }

   @Override
   protected void saveAdditional(ValueOutput output) {
      super.saveAdditional(output);
      List<SavedEntry> list = new ArrayList<>();

      int slot = 0;
      for (AmmoStorageInventory.Content content : this.inventory.getContents()) {
         if (content.count() > 0L) {
            list.add(new SavedEntry(slot, content.sample(), (int) content.count()));
         }
         slot++;
      }

      if (!list.isEmpty()) {
         var typedList = output.list(ENTRIES_TAG, SavedEntry.CODEC);
         list.forEach(typedList::add);
      }
   }

   @Override
   public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
      CompoundTag tag = super.getUpdateTag(provider);
      List<SavedEntry> list = new ArrayList<>();
      int slot = 0;
      for (AmmoStorageInventory.Content content : this.inventory.getContents()) {
         if (content.count() > 0L) {
            list.add(new SavedEntry(slot, content.sample(), (int) content.count()));
         }
         slot++;
      }
      if (!list.isEmpty()) {
         tag.put(ENTRIES_TAG, SavedEntry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, list).result().orElseGet(net.minecraft.nbt.ListTag::new));
      }
      return tag;
   }

   @Nullable
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

    public Component getDisplayName() {
      return Component.translatable("gui.tacz.ammo_storage.title");
   }

   @Override
   public Unit getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
      return Unit.INSTANCE;
   }

   @Nullable
   public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
      return new AmmoStorageMenu(id, inventory, this);
   }

   private void onInventoryChanged() {
      this.setChanged();
      if (this.level != null && !this.level.isClientSide()) {
         this.level.updateNeighbourForOutputSignal(this.worldPosition, this.getBlockState().getBlock());
      }
   }

   public int getCapacityStacks() {
      return this.getBlockState().getBlock() instanceof AmmoStorageBlock storage ? storage.getCapacityStacks() : 216;
   }
}
