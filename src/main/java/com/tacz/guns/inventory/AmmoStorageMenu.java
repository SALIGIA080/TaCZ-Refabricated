package com.tacz.guns.inventory;

import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.init.ModContainer;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageAmmoStorageContents;
import com.tacz.guns.util.AmmoStorageHelper;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.util.Unit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class AmmoStorageMenu extends AbstractContainerMenu {
   public static final ExtendedMenuType<AmmoStorageMenu, Unit> TYPE = new ExtendedMenuType<>(
         (id, inventory, data) -> new AmmoStorageMenu(id, inventory),
         Unit.STREAM_CODEC);
   public static final int STORAGE_ROWS = 3;
   public static final int STORAGE_COLUMNS = 9;
   public static final int STORAGE_SLOT_COUNT = 27;
   public static final int STATUS_HEIGHT = 26;
   public static final int STORAGE_Y = 44;
   public static final int INVENTORY_X = 8;
   public static final int INVENTORY_Y = 111;
   public static final int HOTBAR_Y = 169;
   private final Inventory playerInventory;
   @Nullable
   private final AmmoStorageBlockEntity storage;
   private List<AmmoStorageEntry> entries = List.of();
   private long sentRevision = -1L;
   private int capacityStacks;
   private long usedStacks;
   private int nextEntryId;

   public AmmoStorageMenu(int id, Inventory inventory) {
      this(id, inventory, null);
   }

   public AmmoStorageMenu(int id, Inventory inventory, @Nullable AmmoStorageBlockEntity storage) {
      super(TYPE, id);
      this.playerInventory = inventory;
      this.storage = storage;

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 111 + row * 18));
         }
      }

      for (int col = 0; col < 9; col++) {
         this.addSlot(new Slot(inventory, col, 8 + col * 18, 169));
      }
   }

   public List<AmmoStorageEntry> getEntries() {
      return this.entries;
   }

   public int getCapacityStacks() {
      return this.capacityStacks;
   }

   public long getUsedStacks() {
      return this.usedStacks;
   }

   public void setContents(int capacityStacks, long usedStacks, List<AmmoStorageEntry> entries) {
      if (this.playerInventory.player.level().isClientSide()) {
         this.capacityStacks = capacityStacks;
         this.usedStacks = usedStacks;
         this.entries = List.copyOf(entries);
      }
   }

   @Override
   public void broadcastChanges() {
      super.broadcastChanges();
      if (this.storage != null) {
         int capacity = this.storage.getCapacityStacks();
         long used = this.storage.getUsedStacks();
         if (this.storage.getInventoryRevision() == this.sentRevision && capacity == this.capacityStacks && used == this.usedStacks) {
            boolean remainingChanged = this.entries.stream().anyMatch(entry -> entry.remainingCapacity() != this.storage.getRemainingCapacity(entry.sample()));
            if (!remainingChanged) {
               return;
            }
         }

         List<AmmoStorageEntry> updated = new ArrayList<>();

         for (AmmoStorageInventory.Content content : this.storage.getContents()) {
            int id = -1;

            for (AmmoStorageEntry previous : this.entries) {
               if (ItemStack.isSameItemSameComponents(previous.sample(), content.sample())) {
                  id = previous.id();
                  break;
               }
            }

            updated.add(
               new AmmoStorageEntry(id >= 0 ? id : this.nextEntryId++, content.sample(), content.count(), this.storage.getRemainingCapacity(content.sample()))
            );
         }

         updated.sort(
            Comparator.<AmmoStorageEntry>comparingInt(entry -> AmmoStorageHelper.getSort(entry.sample()))
               .thenComparing(entry -> String.valueOf(AmmoStorageHelper.getAmmoId(entry.sample())))
               .thenComparing(entry -> String.valueOf(entry.sample().getComponents()))
         );
         this.entries = List.copyOf(updated);
         this.capacityStacks = capacity;
         this.usedStacks = used;
         this.sentRevision = this.storage.getInventoryRevision();
         NetworkHandler.sendToClientPlayer(
            new ServerMessageAmmoStorageContents(this.containerId, this.capacityStacks, this.usedStacks, this.entries), (ServerPlayer) this.playerInventory.player
         );
      }
   }

   public void handleInteraction(Player player, int entryId, AmmoStorageMenu.Action action) {
      if (this.storage != null && player.containerMenu == this && !player.isSpectator() && player.isAlive() && this.stillValid(player)) {
         AmmoStorageEntry entry = this.entries.stream().filter(value -> value.id() == entryId).findFirst().orElse(null);
         if (action == AmmoStorageMenu.Action.QUICK_MOVE) {
            if (entry != null) {
               this.storage.moveAmmoToInventory(this.playerInventory, 36, entry.sample(), this.getTransferStackSize(entry));
            }
         } else if (!this.getCarried().isEmpty()) {
            ItemStack held = this.getCarried();
            int requested = action == AmmoStorageMenu.Action.PICKUP_HALF ? 1 : held.getCount();
            int inserted = this.storage.insertAmmo(held.copyWithCount(requested), false);
            if (inserted > 0) {
               this.setCarried(held.copyWithCount(held.getCount() - inserted));
            }
         } else if (entry != null) {
            int amount = this.storage.extractAmmo(entry.sample(), this.getTransferStackSize(entry), true).getCount();
            if (action == AmmoStorageMenu.Action.PICKUP_HALF) {
               amount = amount / 2 + amount % 2;
            }

            this.setCarried(this.storage.extractAmmo(entry.sample(), amount, false));
         }

         this.broadcastChanges();
      }
   }

   private int getTransferStackSize(AmmoStorageEntry entry) {
      return Math.min(this.playerInventory.getMaxStackSize(), entry.sample().getMaxStackSize());
   }

   @Override
   public ItemStack quickMoveStack(Player player, int index) {
      if (this.storage != null && index >= 0 && index < this.slots.size() && !player.isSpectator() && this.stillValid(player)) {
         Slot slot = (Slot) this.slots.get(index);
         ItemStack stack = slot.getItem();
         if (stack.isEmpty()) {
            return ItemStack.EMPTY;
         } else {
            ItemStack original = stack.copy();
            if (AmmoStorageHelper.isValidAmmo(stack)) {
               int inserted = this.storage.insertAmmo(stack, false);
               if (inserted == 0) {
                  return ItemStack.EMPTY;
               }

               stack.shrink(inserted);
            } else if (!this.moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) {
               return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
               slot.setByPlayer(ItemStack.EMPTY);
            } else {
               slot.setChanged();
            }

            slot.onTake(player, stack);
            this.broadcastChanges();
            return original;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   @Override
   public boolean stillValid(Player player) {
      return player.level().isClientSide() || this.storage != null && this.storage.stillValid(player);
   }

   public static enum Action {
      PICKUP,
      PICKUP_HALF,
      QUICK_MOVE;
   }
}
