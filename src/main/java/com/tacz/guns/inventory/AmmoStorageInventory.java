package com.tacz.guns.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AmmoStorageInventory {
   private final AmmoStorageInventory.Entry[] entries;
   private final IntSupplier capacityStacks;
   private final Predicate<ItemStack> validator;
   private final ToIntFunction<ItemStack> stackSize;
   private final Runnable onChanged;
   private long revision;

   public AmmoStorageInventory(int types, IntSupplier capacityStacks, Predicate<ItemStack> validator, ToIntFunction<ItemStack> stackSize, Runnable onChanged) {
      this.entries = new AmmoStorageInventory.Entry[types];
      this.capacityStacks = capacityStacks;
      this.validator = validator;
      this.stackSize = stackSize;
      this.onChanged = onChanged;
   }

   public long getRevision() {
      return this.revision;
   }

   public int getCapacityStacks() {
      return Math.max(0, this.capacityStacks.getAsInt());
   }

   public long getUsedStacks() {
      long used = 0L;

      for (AmmoStorageInventory.Entry entry : this.entries) {
         if (entry != null) {
            used = saturatedAdd(used, this.stacksFor(entry.count, entry.sample));
         }
      }

      return used;
   }

   public int getUsedTypes() {
      int used = 0;

      for (AmmoStorageInventory.Entry entry : this.entries) {
         if (entry != null) {
            used++;
         }
      }

      return used;
   }

   public long getRemainingCapacity(ItemStack sample) {
      if (!sample.isEmpty() && this.validator.test(sample)) {
         int slot = this.findMatchingSlot(sample);
         if (slot < 0 && this.findEmptySlot() < 0) {
            return 0L;
         } else {
            long used = this.getUsedStacks();
            int capacity = this.getCapacityStacks();
            if (used > capacity) {
               return 0L;
            } else {
               long count = slot < 0 ? 0L : this.entries[slot].count;
               long availableStacks = capacity - used + this.stacksFor(count, sample);
               return Math.max(0L, availableStacks * this.getStackSize(sample) - count);
            }
         }
      } else {
         return 0L;
      }
   }

   public int insert(ItemStack stack, boolean simulate) {
      if (!stack.isEmpty() && this.validator.test(stack)) {
         int slot = this.findMatchingSlot(stack);
         if (slot < 0) {
            slot = this.findEmptySlot();
         }

         int inserted = slot < 0 ? 0 : (int)this.insertIntoSlot(slot, stack, stack.getCount(), simulate);
         if (!simulate && inserted > 0) {
            this.changed();
         }

         return inserted;
      } else {
         return 0;
      }
   }

   public int getSlots() {
      return this.entries.length;
   }

   @NotNull
   public ItemStack getStackInSlot(int slot) {
      AmmoStorageInventory.Entry entry = this.getEntry(slot);
      return entry == null ? ItemStack.EMPTY : entry.sample.copyWithCount((int)Math.min(2147483647L, entry.count));
   }

   @NotNull
   public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
      if (!this.isItemValid(slot, stack)) {
         return stack;
      } else {
         int inserted = (int)this.insertIntoSlot(slot, stack, stack.getCount(), simulate);
         if (!simulate && inserted > 0) {
            this.changed();
         }

         return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
      }
   }

   private long insertIntoSlot(int slot, ItemStack sample, long amount, boolean simulate) {
      AmmoStorageInventory.Entry entry = this.entries[slot];
      int matching = this.findMatchingSlot(sample);
      if (amount > 0L && (entry == null || ItemStack.isSameItemSameComponents(entry.sample, sample)) && (matching < 0 || matching == slot)) {
         long inserted = Math.min(amount, this.getRemainingCapacity(sample));
         if (!simulate && inserted > 0L) {
            if (entry == null) {
               this.entries[slot] = new AmmoStorageInventory.Entry(sample.copyWithCount(1), inserted);
            } else {
               entry.count += inserted;
            }
         }

         return inserted;
      } else {
         return 0L;
      }
   }

   @NotNull
   public ItemStack extractItem(int slot, int amount, boolean simulate) {
      AmmoStorageInventory.Entry entry = this.getEntry(slot);
      return entry != null && amount > 0 ? this.extractFromSlot(slot, Math.min(amount, Math.max(0, entry.sample.getMaxStackSize())), simulate) : ItemStack.EMPTY;
   }

   public ItemStack extract(ItemStack sample, int amount, boolean simulate) {
      int slot = sample.isEmpty() ? -1 : this.findMatchingSlot(sample);
      return slot < 0 ? ItemStack.EMPTY : this.extractFromSlot(slot, amount, simulate);
   }

   private ItemStack extractFromSlot(int slot, int amount, boolean simulate) {
      AmmoStorageInventory.Entry entry = this.entries[slot];
      int extracted = (int)Math.min((long)Math.max(0, amount), entry.count);
      if (extracted == 0) {
         return ItemStack.EMPTY;
      } else {
         ItemStack result = entry.sample.copyWithCount(extracted);
         if (!simulate) {
            entry.count -= extracted;
            if (entry.count == 0L) {
               this.entries[slot] = null;
            }

            this.changed();
         }

         return result;
      }
   }

   public int moveToInventory(Container target, int targetSlots, ItemStack sample, int amount) {
      if (!sample.isEmpty() && amount > 0) {
         int remaining = amount;
         int end = Math.min(targetSlots, target.getContainerSize());

         for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = end - 1; slot >= 0 && remaining > 0; slot--) {
               ItemStack existing = target.getItem(slot);
               if (existing.isEmpty() == (pass == 1) && target.canPlaceItem(slot, sample) && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, sample))) {
                  int limit = Math.min(target.getMaxStackSize(), sample.getMaxStackSize());
                  int accepted = Math.min(remaining, Math.max(0, limit - existing.getCount()));
                  ItemStack extracted = this.extract(sample, accepted, false);
                  if (!extracted.isEmpty()) {
                     target.setItem(slot, extracted.copyWithCount(existing.getCount() + extracted.getCount()));
                     remaining -= extracted.getCount();
                  }
               }
            }
         }

         int moved = amount - remaining;
         if (moved > 0) {
            target.setChanged();
         }

         return moved;
      } else {
         return 0;
      }
   }

   public int getSlotLimit(int slot) {
      if (slot >= 0 && slot < this.entries.length) {
         AmmoStorageInventory.Entry entry = this.entries[slot];
         return entry == null ? Integer.MAX_VALUE : (int)Math.min(2147483647L, (long)this.getCapacityStacks() * this.getStackSize(entry.sample));
      } else {
         return 0;
      }
   }

   public boolean isItemValid(int slot, @NotNull ItemStack stack) {
      return slot >= 0 && slot < this.entries.length && !stack.isEmpty() && this.validator.test(stack);
   }

   public List<AmmoStorageInventory.Content> getContents() {
      List<AmmoStorageInventory.Content> result = new ArrayList<>();

      for (AmmoStorageInventory.Entry entry : this.entries) {
         if (entry != null) {
            result.add(new AmmoStorageInventory.Content(entry.sample.copy(), entry.count));
         }
      }

      return List.copyOf(result);
   }

   public int getComparatorSignal() {
      long used = this.getUsedStacks();
      int capacity = Math.max(1, this.getCapacityStacks());
      return used == 0L ? 0 : (int)(14L * Math.min(used, (long)capacity) / capacity + 1L);
   }

   private int getStackSize(ItemStack sample) {
      return Math.max(1, this.stackSize.applyAsInt(sample));
   }

   private long stacksFor(long count, ItemStack sample) {
      return count == 0L ? 0L : 1L + (count - 1L) / this.getStackSize(sample);
   }

   private static long saturatedAdd(long left, long right) {
      return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
   }

   private int findMatchingSlot(ItemStack sample) {
      for (int slot = 0; slot < this.entries.length; slot++) {
         if (this.entries[slot] != null && ItemStack.isSameItemSameComponents(this.entries[slot].sample, sample)) {
            return slot;
         }
      }

      return -1;
   }

   private int findEmptySlot() {
      for (int slot = 0; slot < this.entries.length; slot++) {
         if (this.entries[slot] == null) {
            return slot;
         }
      }

      return -1;
   }

   private AmmoStorageInventory.Entry getEntry(int slot) {
      return slot >= 0 && slot < this.entries.length ? this.entries[slot] : null;
   }

   private void changed() {
      this.revision++;
      this.onChanged.run();
   }

   public record Content(ItemStack sample, long count) {
   }

   private static final class Entry {
      private final ItemStack sample;
      private long count;

      private Entry(ItemStack sample, long count) {
         this.sample = sample;
         this.count = count;
      }
   }
}
