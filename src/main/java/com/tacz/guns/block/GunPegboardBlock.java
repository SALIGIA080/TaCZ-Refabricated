package com.tacz.guns.block;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 枪械挂板。26.2 移植说明：
 * <ul>
 *   <li>交互拆分为 {@link #useItemOn} / {@link #useWithoutItem}；</li>
 *   <li>26.2 移除了 onRemove，掉落逻辑改由 {@link #getDrops} 处理（不可放 setRemoved：区块卸载会触发）；</li>
 *   <li>活塞行为改为 Properties.pushReaction（注册时配置）。</li>
 *   <li>1025 新增转角（SHAPE 属性）与染色（DYED 属性），配合 {@link PegboardShape} 与
 *       {@link GunPegboardBlockEntity} 的 getColor/setColor。</li>
 * </ul>
 */
public class GunPegboardBlock extends BaseEntityBlock {
   public static final MapCodec<GunPegboardBlock> CODEC = simpleCodec(p -> new GunPegboardBlock(p, false));
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final BooleanProperty DYED = BooleanProperty.create("dyed");
   public static final EnumProperty<PegboardShape> SHAPE = EnumProperty.create("shape", PegboardShape.class);
   private static final Identifier CONTENTS = Identifier.fromNamespaceAndPath("tacz", "contents");
   private static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0);
   private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0);
   private static final VoxelShape WEST_SHAPE = Block.box(13.0, 0.0, 0.0, 16.0, 16.0, 16.0);
   private static final VoxelShape EAST_SHAPE = Block.box(0.0, 0.0, 0.0, 3.0, 16.0, 16.0);
   private final boolean glowing;

   public GunPegboardBlock(Properties properties) {
      this(properties, false);
   }

   public GunPegboardBlock(Properties properties, boolean glowing) {
      super(properties);
      this.glowing = glowing;
      this.registerDefaultState(
         this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DYED, false).setValue(SHAPE, PegboardShape.STRAIGHT)
      );
   }

   public boolean isGlowing() {
      return this.glowing;
   }

   @Override
   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(FACING, DYED, SHAPE);
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new GunPegboardBlockEntity(pos, state);
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      Direction clickedFace = context.getClickedFace();
      BlockPos target = context.getClickedPos().relative(clickedFace.getOpposite());
      BlockState targetState = context.getLevel().getBlockState(target);
      BlockState state;
      if (!context.replacingClickedOnBlock() && targetState.getBlock() instanceof GunPegboardBlock) {
         Direction targetFacing = targetState.getValue(FACING);
         if (GunPegboardSlotHelper.hasCorner(targetState) && clickedFace == targetState.getValue(SHAPE).getCornerFacing(targetFacing)) {
            targetFacing = clickedFace;
         }
         Direction playerFacing = context.getHorizontalDirection();
         Direction placedFacing = playerFacing.getAxis() == targetFacing.getAxis() ? targetFacing : playerFacing.getOpposite();
         state = this.defaultBlockState().setValue(FACING, placedFacing);
      } else if (clickedFace.getAxis() == Axis.Y) {
         state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
      } else {
         state = this.defaultBlockState().setValue(FACING, clickedFace);
      }
      return state.setValue(SHAPE, this.getConnectedShape(state, context.getLevel(), context.getClickedPos()));
   }

   private PegboardShape getConnectedShape(BlockState state, BlockGetter level, BlockPos pos) {
      Direction facing = state.getValue(FACING);
      BlockState front = level.getBlockState(pos.relative(facing));
      if (front.getBlock() instanceof GunPegboardBlock) {
         Direction otherFacing = front.getValue(FACING);
         if (otherFacing == facing.getCounterClockWise() && !hasStraightConnection(level, pos.relative(facing.getClockWise()), facing)) {
            return PegboardShape.CORNER_LEFT;
         }
         if (otherFacing == facing.getClockWise() && !hasStraightConnection(level, pos.relative(facing.getCounterClockWise()), facing)) {
            return PegboardShape.CORNER_RIGHT;
         }
      }
      return PegboardShape.STRAIGHT;
   }

   private static boolean hasStraightConnection(BlockGetter level, BlockPos pos, Direction facing) {
      BlockState neighbor = level.getBlockState(pos);
      return neighbor.getBlock() instanceof GunPegboardBlock && neighbor.getValue(FACING) == facing;
   }

   @Override
   protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos, Direction direction, BlockPos facingPos, BlockState facingState, RandomSource random) {
      return state.setValue(SHAPE, this.getConnectedShape(state, level, currentPos));
   }

   @Override
   protected BlockState rotate(BlockState state, Rotation rotation) {
      return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
   }

   @Override
   protected BlockState mirror(BlockState state, Mirror mirror) {
      if (mirror == Mirror.NONE) {
         return state;
      }
      PegboardShape shape = switch (state.getValue(SHAPE)) {
         case CORNER_LEFT -> PegboardShape.CORNER_RIGHT;
         case CORNER_RIGHT -> PegboardShape.CORNER_LEFT;
         case STRAIGHT -> PegboardShape.STRAIGHT;
      };
      return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(SHAPE, shape);
   }

   @Override
   protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (player.isShiftKeyDown()) {
         return InteractionResult.PASS;
      }
      int slot = GunPegboardSlotHelper.getSlot(state, pos, hit.getLocation());
      if (slot < 0) {
         return InteractionResult.PASS;
      }
      if (!(level.getBlockEntity(pos) instanceof GunPegboardBlockEntity pegboard)) {
         return InteractionResult.PASS;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      ItemStack stored = pegboard.getItem(slot);
      if (stored.isEmpty()) {
         if (GunPegboardItemConfigRead.canPlace(held) && pegboard.setItem(slot, held)) {
            if (!player.getAbilities().instabuild) {
               held.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
         }
         return InteractionResult.PASS;
      }
      return rotateSlot(level, pos, pegboard, slot);
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (player.isShiftKeyDown()) {
         return InteractionResult.PASS;
      }
      int slot = GunPegboardSlotHelper.getSlot(state, pos, hit.getLocation());
      if (slot < 0) {
         return InteractionResult.PASS;
      }
      if (!(level.getBlockEntity(pos) instanceof GunPegboardBlockEntity pegboard)) {
         return InteractionResult.PASS;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      if (pegboard.getItem(slot).isEmpty()) {
         return InteractionResult.PASS;
      }
      return rotateSlot(level, pos, pegboard, slot);
   }

   private static InteractionResult rotateSlot(Level level, BlockPos pos, GunPegboardBlockEntity pegboard, int slot) {
      if (pegboard.rotateItem(slot)) {
         level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
         return InteractionResult.CONSUME;
      }
      return InteractionResult.PASS;
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      VoxelShape plate = getPlateShape(state.getValue(FACING));
      return state.getValue(SHAPE) == PegboardShape.STRAIGHT
         ? plate
         : Shapes.or(plate, getPlateShape(state.getValue(SHAPE).getCornerFacing(state.getValue(FACING))));
   }

   private static VoxelShape getPlateShape(Direction facing) {
      return switch (facing) {
         case SOUTH -> SOUTH_SHAPE;
         case WEST -> WEST_SHAPE;
         case EAST -> EAST_SHAPE;
         case NORTH -> NORTH_SHAPE;
         default -> Shapes.block();
      };
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.getShape(state, level, pos, context);
   }

   @Override
   public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
      ItemStack stack = new ItemStack(this.asItem());
      if (level.getBlockEntity(pos) instanceof GunPegboardBlockEntity pegboard && pegboard.getColor() >= 0) {
         stack.set(DataComponents.DYED_COLOR, new DyedItemColor(pegboard.getColor()));
      }
      return stack;
   }

   @Override
   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   /**
    * 26.2 掉落入口：方块真正被破坏时由掉落表机制调用，把挂板上的展示物品作为掉落物。
    * 只在破坏流程触发，区块卸载 / 重载不会走到这里，从而避免反复退出重进刷出展示物。
    */
   @Override
   protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
      BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
      if (blockEntity instanceof GunPegboardBlockEntity pegboard) {
         List<ItemStack> items = pegboard.getItemsForDrop();
         if (!items.isEmpty()) {
            builder.withDynamicDrop(CONTENTS, consumer -> items.forEach(consumer));
         }
      }
      return super.getDrops(state, builder);
   }
}
