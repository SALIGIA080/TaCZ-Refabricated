package com.tacz.guns.block;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.util.AmmoStorageHelper;
import com.tacz.guns.util.AmmoStorageInteractionHelper;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AmmoStorageBlock extends BaseEntityBlock {
   public static final MapCodec<AmmoStorageBlock> CODEC = simpleCodec(AmmoStorageBlock::new);
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final int IRON_CAPACITY_STACKS = 216;
   public static final int GOLD_CAPACITY_STACKS = 432;
   public static final int DIAMOND_CAPACITY_STACKS = 864;
   private final int capacityStacks;

   public AmmoStorageBlock(Properties properties) {
      this(properties, IRON_CAPACITY_STACKS);
   }

   public AmmoStorageBlock(Properties properties, int capacityStacks) {
      super(properties);
      this.capacityStacks = capacityStacks;
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
   }

   public int getCapacityStacks() {
      return this.capacityStacks;
   }

   @Override
   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(FACING);
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new AmmoStorageBlockEntity(pos, state);
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   /**
    * 手持物品交互（弹药盒装卸）。
    */
   @Override
   protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!(level.getBlockEntity(pos) instanceof AmmoStorageBlockEntity storage)) {
         return InteractionResult.PASS;
      }
      if (!(held.getItem() instanceof IAmmoBox ammoBox)) {
         return InteractionResult.TRY_WITH_EMPTY_HAND;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      return player.isShiftKeyDown()
         ? AmmoStorageInteractionHelper.unloadAmmoBox(storage, held, ammoBox, player)
         : this.fillAmmoBox(storage, held, ammoBox, player);
   }

   /**
    * 空手（或非弹药盒）交互：打开弹药库界面。
    */
   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      // 潜行时放行：潜行+弹药盒的卸载由 AmmoStorageInteractionEvent（UseBlockCallback）处理
      if (player.isShiftKeyDown()) {
         return InteractionResult.PASS;
      }
      if (!(level.getBlockEntity(pos) instanceof AmmoStorageBlockEntity storage)) {
         return InteractionResult.PASS;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      if (player instanceof ServerPlayer serverPlayer) {
         // 26.2：方块实体自身实现 MenuProvider，直接交给 openMenu；客户端不再需要坐标，
         // 内容通过 ServerMessageAmmoStorageContents 同步。
         serverPlayer.openMenu(storage);
      }
      return InteractionResult.CONSUME;
   }

   private InteractionResult fillAmmoBox(AmmoStorageBlockEntity storage, ItemStack boxStack, IAmmoBox ammoBox, Player player) {
      Identifier ammoId = ammoBox.getAmmoId(boxStack);
      if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
         player.sendOverlayMessage(Component.translatable("message.tacz.ammo_storage.ammo_box_empty"));
         return InteractionResult.CONSUME;
      } else {
         int current = ammoBox.getAmmoCount(boxStack);
         int levelMultiplier = ammoBox.getAmmoLevel(boxStack) + 1;
         int max = AmmoStorageHelper.getStackSize(AmmoStorageHelper.createAmmoStack(ammoId, 1))
            * SyncConfig.AMMO_BOX_STACK_SIZE.get()
            * levelMultiplier;
         int need = Math.max(0, max - current);
         if (need <= 0) {
            return InteractionResult.CONSUME;
         } else {
            ItemStack extracted = storage.extractAmmo(ammoId, need, false);
            if (extracted.isEmpty()) {
               player.sendOverlayMessage(Component.translatable("message.tacz.ammo_storage.no_ammo_box_stock"));
               return InteractionResult.CONSUME;
            } else {
               ammoBox.setAmmoCount(boxStack, current + extracted.getCount());
               player.sendOverlayMessage(Component.translatable("message.tacz.ammo_storage.picked", extracted.getHoverName(), extracted.getCount()));
               player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
               return InteractionResult.CONSUME;
            }
         }
      }
   }

   @Override
   protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
      BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
      ItemStack stack = new ItemStack(state.getBlock());
      if (blockEntity instanceof AmmoStorageBlockEntity storage) {
         // 26.2：setBlockEntityData 改收 TagValueOutput
         net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput
            .createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, builder.getLevel().registryAccess());
         storage.saveWithoutMetadata(output);
         if (!output.isEmpty()) {
            BlockItem.setBlockEntityData(stack, ModBlocks.AMMO_STORAGE_BE, output);
         }
      }

      return Collections.singletonList(stack);
   }

   @Override
   protected boolean hasAnalogOutputSignal(BlockState state) {
      return true;
   }

   @Override
   protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
      return level.getBlockEntity(pos) instanceof AmmoStorageBlockEntity storage ? storage.getComparatorSignal() : 0;
   }

   @Override
   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }
}
