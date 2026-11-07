package com.tacz.guns.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.init.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 枪械展示柜方块实体。26.2 移植说明：
 * <ul>
 *   <li>持久化改为 ValueInput/ValueOutput + Codec（旋转数组用 int[] 存储）；</li>
 *   <li>26.2 移除了 Block.onRemove，方块被破坏时的掉落在 {@link #setRemoved()} 处理；</li>
 *   <li>26.2 移除了 getRenderBoundingBox，渲染范围由渲染器控制。</li>
 * </ul>
 */
public class GunDisplayCaseBlockEntity extends BlockEntity implements GunDisplayBlockEntity {
   public static final int GRID_SIZE = 4;
   public static final int SLOT_COUNT = 16;
   public static final BlockEntityType<GunDisplayCaseBlockEntity> TYPE = new BlockEntityType<>(
         GunDisplayCaseBlockEntity::new,
         Set.of(ModBlocks.GUN_DISPLAY_CASE)
   );
   private static final String ITEMS_TAG = "Items";
   private static final String ROTATIONS_TAG = "Rotations";

   private record SavedEntry(int slot, ItemStack stack) {
      static final Codec<SavedEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("Slot").forGetter(SavedEntry::slot),
            ItemStack.OPTIONAL_CODEC.fieldOf("Item").forGetter(SavedEntry::stack)
      ).apply(instance, SavedEntry::new));
   }

   private final ItemStack[] items = new ItemStack[SLOT_COUNT];
   private final byte[] rotations = new byte[SLOT_COUNT];
   private final DisplayGunSlot[] displaySlots = new DisplayGunSlot[SLOT_COUNT];

   public GunDisplayCaseBlockEntity(BlockPos pos, BlockState state) {
      super(TYPE, pos, state);

      for (int i = 0; i < this.items.length; i++) {
         this.items[i] = ItemStack.EMPTY;
      }
   }

   public ItemStack getItem(int slot) {
      return isValidSlot(slot) ? this.items[slot] : ItemStack.EMPTY;
   }

   public int getRotation(int slot) {
      return isValidSlot(slot) ? this.rotations[slot] & 7 : 0;
   }

   public DisplayGunSlot getDisplaySlot(int slot) {
      if (!isValidSlot(slot)) {
         throw new IndexOutOfBoundsException(slot);
      } else {
         DisplayGunSlot handle = this.displaySlots[slot];
         if (handle == null) {
            this.displaySlots[slot] = handle = new DisplayGunSlot(this, slot);
         }

         return handle;
      }
   }

   @Override
   public int getGridSize() {
      return 4;
   }

   @Override
   public Direction getSlotFacing(int slot) {
      return this.getBlockState().getValue(GunDisplayCaseBlock.FACING);
   }

   @Override
   public boolean isDisplayGlowing() {
      return true;
   }

   @Override
   public BlockEntity getBlockEntity() {
      return this;
   }

   private void displaySlotChanged(int slot) {
      if (this.displaySlots[slot] != null) {
         this.displaySlots[slot].changed();
      }
   }

   public boolean setItem(int slot, ItemStack stack) {
      if (isValidSlot(slot) && this.items[slot].isEmpty()) {
         ItemStack stored = stack.copy();
         stored.setCount(1);
         this.items[slot] = stored;
         this.rotations[slot] = 0;
         this.displaySlotChanged(slot);
         this.update();
         return true;
      } else {
         return false;
      }
   }

   public boolean rotateItem(int slot) {
      if (isValidSlot(slot) && !this.items[slot].isEmpty()) {
         this.rotations[slot] = (byte)(this.rotations[slot] + 1 & 7);
         if (this.displaySlots[slot] != null) {
            this.displaySlots[slot].transformed();
         }

         this.update();
         return true;
      } else {
         return false;
      }
   }

   public boolean dropItem(int slot) {
      if (isValidSlot(slot) && !this.items[slot].isEmpty() && this.level != null) {
         Direction facing = this.getBlockState().getValue(GunDisplayCaseBlock.FACING);
         Block.popResource(this.level, this.worldPosition.relative(facing), this.items[slot]);
         this.items[slot] = ItemStack.EMPTY;
         this.rotations[slot] = 0;
         this.displaySlotChanged(slot);
         this.update();
         return true;
      } else {
         return false;
      }
   }

   public boolean removeItem(int slot) {
      if (isValidSlot(slot) && !this.items[slot].isEmpty()) {
         this.items[slot] = ItemStack.EMPTY;
         this.rotations[slot] = 0;
         this.displaySlotChanged(slot);
         this.update();
         return true;
      } else {
         return false;
      }
   }

   public void dropAllItems() {
      if (this.level != null) {
         Direction facing = this.getBlockState().getValue(GunDisplayCaseBlock.FACING);

         for (int i = 0; i < SLOT_COUNT; i++) {
            if (!this.items[i].isEmpty()) {
               Block.popResource(this.level, this.worldPosition.relative(facing), this.items[i]);
               this.items[i] = ItemStack.EMPTY;
               this.rotations[i] = 0;
               this.displaySlotChanged(i);
            }
         }

         this.update();
      }
   }

   /** 供 {@link GunDisplayCaseBlock#getDrops} 在方块被破坏时收集展示物品作为掉落物。 */
   public List<ItemStack> getItemsForDrop() {
      List<ItemStack> result = new ArrayList<>();
      for (int i = 0; i < SLOT_COUNT; i++) {
         if (!this.items[i].isEmpty()) {
            result.add(this.items[i].copy());
         }
      }
      return result;
   }

   /**
    * 注意：26.2 中 setRemoved() 会在区块卸载 / 重载（LevelChunk.clearAllBlockEntities）时被调用，
    * 不能在这里掉落展示物品，否则进游戏加载存档时会误触发「枪掉出但柜内仍在」的复制。
    * 掉落改由 {@link GunDisplayCaseBlock#getDrops} 在方块真正被破坏时处理。
    */
   @Override
   public void setRemoved() {
      super.setRemoved();
   }

   private void update() {
      this.setChanged();
      if (this.level != null) {
         BlockState state = this.getBlockState();
         this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
      }
   }

   private static boolean isValidSlot(int slot) {
      return slot >= 0 && slot < SLOT_COUNT;
   }

   @Override
   public void loadAdditional(ValueInput input) {
      super.loadAdditional(input);
      ItemStack[] previousItems = this.items.clone();
      byte[] previousRotations = this.rotations.clone();

      for (int i = 0; i < this.items.length; i++) {
         this.items[i] = ItemStack.EMPTY;
         this.rotations[i] = 0;
      }

      for (SavedEntry saved : input.listOrEmpty(ITEMS_TAG, SavedEntry.CODEC)) {
         int slot = saved.slot() & 255;
         if (isValidSlot(slot) && !saved.stack().isEmpty()) {
            this.items[slot] = saved.stack();
         }
      }

      int[] savedRotations = input.getIntArray(ROTATIONS_TAG).orElse(new int[0]);
      for (int i = 0; i < Math.min(savedRotations.length, this.rotations.length); i++) {
         this.rotations[i] = (byte) savedRotations[i];
      }

      for (int ix = 0; ix < this.displaySlots.length; ix++) {
         DisplayGunSlot handle = this.displaySlots[ix];
         if (handle != null) {
            if (!ItemStack.matches(previousItems[ix], this.items[ix])) {
               handle.changed();
            } else if (previousRotations[ix] != this.rotations[ix]) {
               handle.transformed();
            }
         }
      }
   }

   @Override
   protected void saveAdditional(ValueOutput output) {
      super.saveAdditional(output);
      List<SavedEntry> list = new ArrayList<>();

      for (int i = 0; i < this.items.length; i++) {
         if (!this.items[i].isEmpty()) {
            list.add(new SavedEntry(i, this.items[i]));
         }
      }

      if (!list.isEmpty()) {
         var typedList = output.list(ITEMS_TAG, SavedEntry.CODEC);
         list.forEach(typedList::add);
      }

      int[] rotationInts = new int[this.rotations.length];
      for (int i = 0; i < this.rotations.length; i++) {
         rotationInts[i] = this.rotations[i];
      }
      output.putIntArray(ROTATIONS_TAG, rotationInts);
   }

   @Override
   public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
      CompoundTag tag = super.getUpdateTag(provider);
      List<SavedEntry> list = new ArrayList<>();
      for (int i = 0; i < this.items.length; i++) {
         if (!this.items[i].isEmpty()) {
            list.add(new SavedEntry(i, this.items[i]));
         }
      }
      if (!list.isEmpty()) {
         SavedEntry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, list).result().ifPresent(t -> tag.put(ITEMS_TAG, t));
      }
      int[] rotationInts = new int[this.rotations.length];
      for (int i = 0; i < this.rotations.length; i++) {
         rotationInts[i] = this.rotations[i];
      }
      tag.putIntArray(ROTATIONS_TAG, rotationInts);
      return tag;
   }

   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
