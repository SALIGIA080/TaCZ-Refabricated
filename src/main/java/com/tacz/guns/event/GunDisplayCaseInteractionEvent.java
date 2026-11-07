package com.tacz.guns.event;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 展示柜交互事件。26.2 移植说明：Forge RightClickBlock/LeftClickBlock →
 * Fabric UseBlockCallback / AttackBlockCallback，在 {@code TaCZFabric#subscribeEvents} 注册。
 */
public class GunDisplayCaseInteractionEvent {
   /** 潜行 + 副手持枪时右键展示柜：切换锁（与方块自身的潜行判定互补）。 */
   public static InteractionResult onRightClickBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      }
      if (!player.isShiftKeyDown() || player.getMainHandItem().getItem() instanceof IGun) {
         return InteractionResult.PASS;
      }
      if (!(player.getOffhandItem().getItem() instanceof IGun)) {
         return InteractionResult.PASS;
      }
      BlockPos pos = hit.getBlockPos();
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof GunDisplayCaseBlock displayCaseBlock) {
         displayCaseBlock.toggleLockFromInteraction(level, pos, state);
         return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
      }
      return InteractionResult.PASS;
   }

   /** 左键点击未上锁的展示柜：取下对应槽位的展品（创造模式需潜行才真掉落）。 */
   public static InteractionResult onLeftClickBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
      BlockState state = level.getBlockState(pos);
      if (!(state.getBlock() instanceof GunDisplayCaseBlock) || state.getValue(GunDisplayCaseBlock.LOCKED)) {
         return InteractionResult.PASS;
      }
      if (!(player.pick(6.0, 0.0F, false) instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) {
         return InteractionResult.PASS;
      }
      int slot = GunDisplayCaseSlotHelper.getSlot(state, pos, hit.getLocation());
      if (slot < 0) {
         return InteractionResult.PASS;
      }
      if (!(level.getBlockEntity(pos) instanceof GunDisplayCaseBlockEntity displayCase) || displayCase.getItem(slot).isEmpty()) {
         return InteractionResult.PASS;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      boolean shouldDrop = !player.isCreative() || player.isShiftKeyDown();
      if (shouldDrop ? displayCase.dropItem(slot) : displayCase.removeItem(slot)) {
         level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
      }
      return InteractionResult.SUCCESS;
   }
}
