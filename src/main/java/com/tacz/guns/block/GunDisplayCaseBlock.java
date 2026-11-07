package com.tacz.guns.block;

import com.mojang.serialization.MapCodec;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
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
 * 枪械展示柜。26.2 移植说明：
 * <ul>
 *   <li>交互拆分为 {@link #useItemOn} / {@link #useWithoutItem}（潜行开合锁走 useWithoutItem）；</li>
 *   <li>26.2 移除了 onRemove，掉落逻辑移到 {@link GunDisplayCaseBlockEntity#setRemoved()}；</li>
 *   <li>updateShape 换用 26.2 新签名；活塞行为改为 Properties.pushReaction。</li>
 * </ul>
 */
public class GunDisplayCaseBlock extends BaseEntityBlock {
   public static final MapCodec<GunDisplayCaseBlock> CODEC = simpleCodec(GunDisplayCaseBlock::new);
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   public static final BooleanProperty LOCKED = BooleanProperty.create("locked");
   public static final BooleanProperty LEFT = BooleanProperty.create("left");
   public static final BooleanProperty RIGHT = BooleanProperty.create("right");
   public static final BooleanProperty UP = BooleanProperty.create("up");
   public static final BooleanProperty DOWN = BooleanProperty.create("down");
   private static final int MAX_GROUP_TOGGLE = 512;
   private static final int LOCK_UPDATE_FLAGS = 18;
   /** 动态掉落键，必须与 {@code loot_table/blocks/gun_display_case.json} 里的 dynamic 条目同名。 */
   private static final Identifier CONTENTS = Identifier.fromNamespaceAndPath("tacz", "contents");
   private static final VoxelShape NORTH_LOCKED_SHAPE = Block.box(0.0, 0.0, 4.0, 16.0, 16.0, 16.0);
   private static final VoxelShape SOUTH_LOCKED_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 12.0);
   private static final VoxelShape WEST_LOCKED_SHAPE = Block.box(4.0, 0.0, 0.0, 16.0, 16.0, 16.0);
   private static final VoxelShape EAST_LOCKED_SHAPE = Block.box(0.0, 0.0, 0.0, 12.0, 16.0, 16.0);
   private static final VoxelShape NORTH_BACK_PANEL_SHAPE = Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0);
   private static final VoxelShape SOUTH_BACK_PANEL_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0);
   private static final VoxelShape WEST_BACK_PANEL_SHAPE = Block.box(13.0, 0.0, 0.0, 16.0, 16.0, 16.0);
   private static final VoxelShape EAST_BACK_PANEL_SHAPE = Block.box(0.0, 0.0, 0.0, 3.0, 16.0, 16.0);
   private static final VoxelShape NORTH_OPEN_SHAPE = Shapes.or(
      NORTH_BACK_PANEL_SHAPE,
      Block.box(0.0, 0.0, 4.0, 1.0, 16.0, 16.0),
      Block.box(15.0, 0.0, 4.0, 16.0, 16.0, 16.0),
      Block.box(0.0, 0.0, 4.0, 16.0, 1.0, 16.0),
      Block.box(0.0, 15.0, 4.0, 16.0, 16.0, 16.0)
   );
   private static final VoxelShape SOUTH_OPEN_SHAPE = Shapes.or(
      SOUTH_BACK_PANEL_SHAPE,
      Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 12.0),
      Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 12.0),
      Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 12.0),
      Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 12.0)
   );
   private static final VoxelShape WEST_OPEN_SHAPE = Shapes.or(
      WEST_BACK_PANEL_SHAPE,
      Block.box(4.0, 0.0, 0.0, 16.0, 16.0, 1.0),
      Block.box(4.0, 0.0, 15.0, 12.0, 16.0, 16.0),
      Block.box(4.0, 0.0, 0.0, 16.0, 1.0, 16.0),
      Block.box(4.0, 15.0, 0.0, 16.0, 16.0, 16.0)
   );
   private static final VoxelShape EAST_OPEN_SHAPE = Shapes.or(
      EAST_BACK_PANEL_SHAPE,
      Block.box(0.0, 0.0, 0.0, 12.0, 16.0, 1.0),
      Block.box(0.0, 0.0, 15.0, 12.0, 16.0, 16.0),
      Block.box(0.0, 0.0, 0.0, 12.0, 1.0, 16.0),
      Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0)
   );

   public GunDisplayCaseBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(
         this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LOCKED, true)
            .setValue(LEFT, false)
            .setValue(RIGHT, false)
            .setValue(UP, false)
            .setValue(DOWN, false)
      );
   }

   @Override
   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(FACING, LOCKED, LEFT, RIGHT, UP, DOWN);
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new GunDisplayCaseBlockEntity(pos, state);
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      Level level = context.getLevel();
      BlockPos pos = context.getClickedPos();
      Direction clickedFace = context.getClickedFace();
      BlockPos clickedPos = pos.relative(clickedFace.getOpposite());
      BlockState clickedState = level.getBlockState(clickedPos);
      boolean clickedDisplayCase = clickedState.getBlock() instanceof GunDisplayCaseBlock;
      Direction facing = getPlacementFacing(context, clickedState, clickedDisplayCase);
      BlockState state = this.defaultBlockState().setValue(FACING, facing);
      if (context.isSecondaryUseActive()) {
         return state;
      } else {
         Set<BlockPos> forceConnectedPositions = clickedDisplayCase ? collectConnectedCases(level, clickedPos, facing) : null;
         return this.updateConnections(level, pos, state, forceConnectedPositions);
      }
   }

   @Override
   protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos currentPos, Direction direction, BlockPos facingPos, BlockState facingState, RandomSource random) {
      BooleanProperty property = propertyForDirection(state.getValue(FACING), direction);
      return property != null && state.getValue(property) && !connectsTo(state, facingState) ? state.setValue(property, false) : state;
   }

   @Override
   protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
      super.onPlace(state, level, pos, oldState, movedByPiston);
      if (!level.isClientSide() && !oldState.is(state.getBlock())) {
         syncConnectedNeighbors(level, pos, state);
      }
   }

   /**
    * 空手 / 潜行交互：潜行切换锁；空手在未锁定时旋转展品。
    * 注意：26.2 中潜行且任意手持有物品时走本回调，需放行持枪潜行（交给枪自己的逻辑）。
    */
   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (player.isShiftKeyDown()) {
         if (player.getMainHandItem().getItem() instanceof IGun) {
            return InteractionResult.PASS;
         }
         this.toggleLockFromInteraction(level, pos, state);
         return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
      }
      if (state.getValue(LOCKED)) {
         return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
      }
      int slot = GunDisplayCaseSlotHelper.getSlot(state, pos, hit.getLocation());
      if (slot < 0) {
         return InteractionResult.PASS;
      }
      if (!(level.getBlockEntity(pos) instanceof GunDisplayCaseBlockEntity displayCase)) {
         return InteractionResult.PASS;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      ItemStack stored = displayCase.getItem(slot);
      if (stored.isEmpty()) {
         return InteractionResult.CONSUME;
      }
      return rotateSlot(level, pos, displayCase, slot);
   }

   /** 持物交互：未锁定时放入 / 旋转展品；潜行（非持枪）时切换锁。 */
   @Override
   protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      boolean holdingGun = held.getItem() instanceof IGun;
      // 26.2 中潜行时原版不会调用 useWithoutItem（空手交互被跳过），
      // 因此潜行切换锁必须在这里处理，否则「潜行+右键」永远只触发蹲下、无任何效果。
      if (player.isShiftKeyDown() && !holdingGun) {
         this.toggleLockFromInteraction(level, pos, state);
         return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
      }
      if (state.getValue(LOCKED)) {
         return holdingGun ? InteractionResult.SUCCESS : InteractionResult.PASS;
      }
      int slot = GunDisplayCaseSlotHelper.getSlot(state, pos, hit.getLocation());
      if (slot < 0) {
         return InteractionResult.PASS;
      }
      if (!(level.getBlockEntity(pos) instanceof GunDisplayCaseBlockEntity displayCase)) {
         return InteractionResult.PASS;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      ItemStack stored = displayCase.getItem(slot);
      if (stored.isEmpty()) {
         if (GunPegboardItemConfigRead.canPlace(held) && displayCase.setItem(slot, held)) {
            if (!player.getAbilities().instabuild) {
               held.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
         }
         return InteractionResult.PASS;
      }
      return rotateSlot(level, pos, displayCase, slot);
   }

   private static InteractionResult rotateSlot(Level level, BlockPos pos, GunDisplayCaseBlockEntity displayCase, int slot) {
      if (displayCase.rotateItem(slot)) {
         level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
         return InteractionResult.CONSUME;
      }
      return InteractionResult.PASS;
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      if (state.getValue(LOCKED)) {
         return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_LOCKED_SHAPE;
            case WEST -> WEST_LOCKED_SHAPE;
            case EAST -> EAST_LOCKED_SHAPE;
            case NORTH -> NORTH_LOCKED_SHAPE;
            default -> Shapes.block();
         };
      } else {
         return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_BACK_PANEL_SHAPE;
            case WEST -> WEST_BACK_PANEL_SHAPE;
            case EAST -> EAST_BACK_PANEL_SHAPE;
            case NORTH -> NORTH_BACK_PANEL_SHAPE;
            default -> Shapes.block();
         };
      }
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      if (state.getValue(LOCKED)) {
         return this.getShape(state, level, pos, context);
      } else {
         return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH_OPEN_SHAPE;
            case WEST -> WEST_OPEN_SHAPE;
            case EAST -> EAST_OPEN_SHAPE;
            case NORTH -> NORTH_OPEN_SHAPE;
            default -> Shapes.block();
         };
      }
   }

   @Override
   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   /**
    * 26.2 掉落入口：方块真正被破坏时由掉落表机制调用，把展示柜内的物品作为掉落物。
    * 只在破坏流程触发，区块卸载 / 重载不会走到这里，从而避免「枪掉出但柜内仍在」的复制。
    */
   @Override
   protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
      BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
      if (blockEntity instanceof GunDisplayCaseBlockEntity displayCase) {
         List<ItemStack> items = displayCase.getItemsForDrop();
         if (!items.isEmpty()) {
            builder.withDynamicDrop(CONTENTS, consumer -> items.forEach(consumer));
         }
      }
      return super.getDrops(state, builder);
   }

   private BlockState updateConnections(BlockGetter level, BlockPos pos, BlockState state, @Nullable Set<BlockPos> forceConnectedPositions) {
      Direction facing = state.getValue(FACING);
      return state
         .setValue(LEFT, shouldAutoConnectTo(level, pos, state, leftOf(facing), forceConnectedPositions))
         .setValue(RIGHT, shouldAutoConnectTo(level, pos, state, rightOf(facing), forceConnectedPositions))
         .setValue(UP, shouldAutoConnectTo(level, pos, state, Direction.UP, forceConnectedPositions))
         .setValue(DOWN, shouldAutoConnectTo(level, pos, state, Direction.DOWN, forceConnectedPositions));
   }

   private static Direction getPlacementFacing(BlockPlaceContext context, BlockState clickedState, boolean clickedDisplayCase) {
      if (clickedDisplayCase) {
         return clickedState.getValue(FACING);
      } else {
         Direction clickedFace = context.getClickedFace();
         return clickedFace.getAxis() == Axis.Y ? context.getHorizontalDirection().getOpposite() : clickedFace;
      }
   }

   private static boolean shouldAutoConnectTo(
      BlockGetter level, BlockPos pos, BlockState state, Direction direction, @Nullable Set<BlockPos> forceConnectedPositions
   ) {
      BlockPos neighborPos = pos.relative(direction);
      BlockState neighborState = level.getBlockState(neighborPos);
      if (!connectsTo(state, neighborState)) {
         return false;
      } else if (forceConnectedPositions != null && forceConnectedPositions.contains(neighborPos)) {
         return true;
      } else {
         BooleanProperty neighborProperty = propertyForDirection(neighborState.getValue(FACING), direction.getOpposite());
         return neighborProperty != null && neighborState.getValue(neighborProperty);
      }
   }

   private static Set<BlockPos> collectConnectedCases(BlockGetter level, BlockPos start, Direction facing) {
      ArrayDeque<BlockPos> queue = new ArrayDeque<>();
      Set<BlockPos> checked = new HashSet<>();
      Set<BlockPos> connected = new HashSet<>();
      queue.add(start);

      while (!queue.isEmpty() && checked.size() < MAX_GROUP_TOGGLE) {
         BlockPos pos = queue.removeFirst();
         if (checked.add(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof GunDisplayCaseBlock && state.getValue(FACING) == facing) {
               connected.add(pos);
               queueConnectedCases(queue, pos, state, facing);
            }
         }
      }

      return connected;
   }

   private static void syncConnectedNeighbors(Level level, BlockPos pos, BlockState state) {
      Direction facing = state.getValue(FACING);
      syncConnectedNeighbor(level, pos, state, leftOf(facing), LEFT);
      syncConnectedNeighbor(level, pos, state, rightOf(facing), RIGHT);
      syncConnectedNeighbor(level, pos, state, Direction.UP, UP);
      syncConnectedNeighbor(level, pos, state, Direction.DOWN, DOWN);
   }

   private static void syncConnectedNeighbor(Level level, BlockPos pos, BlockState state, Direction direction, BooleanProperty property) {
      if (state.getValue(property)) {
         BlockPos neighborPos = pos.relative(direction);
         BlockState neighborState = level.getBlockState(neighborPos);
         if (connectsTo(state, neighborState)) {
            BooleanProperty neighborProperty = propertyForDirection(neighborState.getValue(FACING), direction.getOpposite());
            if (neighborProperty != null && !neighborState.getValue(neighborProperty)) {
               level.setBlock(neighborPos, neighborState.setValue(neighborProperty, true), LOCK_UPDATE_FLAGS);
            }
         }
      }
   }

   private static boolean connectsTo(BlockState state, BlockState neighbor) {
      return neighbor.getBlock() instanceof GunDisplayCaseBlock && neighbor.getValue(FACING) == state.getValue(FACING);
   }

   private static BooleanProperty propertyForDirection(Direction facing, Direction direction) {
      if (direction == Direction.UP) {
         return UP;
      } else if (direction == Direction.DOWN) {
         return DOWN;
      } else if (direction == leftOf(facing)) {
         return LEFT;
      } else {
         return direction == rightOf(facing) ? RIGHT : null;
      }
   }

   private static Direction leftOf(Direction facing) {
      return facing.getCounterClockWise();
   }

   private static Direction rightOf(Direction facing) {
      return facing.getClockWise();
   }

   public void toggleLockFromInteraction(Level level, BlockPos pos, BlockState state) {
      if (!level.isClientSide()) {
         this.toggleLinkedCases(level, pos, !state.getValue(LOCKED));
         level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, state.getValue(LOCKED) ? 1.1F : 0.9F);
      }
   }

   private void toggleLinkedCases(Level level, BlockPos start, boolean locked) {
      BlockState startState = level.getBlockState(start);
      if (startState.getBlock() instanceof GunDisplayCaseBlock) {
         Direction facing = startState.getValue(FACING);
         ArrayDeque<BlockPos> queue = new ArrayDeque<>();
         Set<BlockPos> visited = new HashSet<>();
         queue.add(start);

         while (!queue.isEmpty() && visited.size() < MAX_GROUP_TOGGLE) {
            BlockPos pos = queue.removeFirst();
            if (visited.add(pos)) {
               BlockState state = level.getBlockState(pos);
               if (state.getBlock() instanceof GunDisplayCaseBlock && state.getValue(FACING) == facing) {
                  if (state.getValue(LOCKED) != locked) {
                     level.setBlock(pos, state.setValue(LOCKED, locked), LOCK_UPDATE_FLAGS);
                  }

                  queueConnectedCases(queue, pos, state, facing);
               }
            }
         }
      }
   }

   private static void queueConnectedCases(ArrayDeque<BlockPos> queue, BlockPos pos, BlockState state, Direction facing) {
      if (state.getValue(LEFT)) {
         queue.add(pos.relative(leftOf(facing)));
      }

      if (state.getValue(RIGHT)) {
         queue.add(pos.relative(rightOf(facing)));
      }

      if (state.getValue(UP)) {
         queue.add(pos.above());
      }

      if (state.getValue(DOWN)) {
         queue.add(pos.below());
      }
   }
}
