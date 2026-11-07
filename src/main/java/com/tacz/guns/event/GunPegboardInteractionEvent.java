package com.tacz.guns.event;

import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 左键点击挂板：取下对应槽位的展品（创造模式需潜行才真掉落）。
 * 26.2 移植说明：Forge LeftClickBlock → Fabric AttackBlockCallback，
 * 在 {@code TaCZFabric#subscribeEvents} 注册。
 */
public class GunPegboardInteractionEvent {
   public static InteractionResult onLeftClickBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
      // 【1025 delta】调试棒不触发取枪交互
      if (player.getMainHandItem().is(Items.DEBUG_STICK)) {
         return InteractionResult.PASS;
      }
      BlockState state = level.getBlockState(pos);
      if (!(state.getBlock() instanceof GunPegboardBlock)) {
         return InteractionResult.PASS;
      }
      if (!(player.pick(6.0, 0.0F, false) instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) {
         return InteractionResult.PASS;
      }
      int slot = GunPegboardSlotHelper.getSlot(state, pos, hit.getLocation());
      if (slot < 0) {
         return InteractionResult.PASS;
      }
      if (!(level.getBlockEntity(pos) instanceof GunPegboardBlockEntity pegboard) || pegboard.getItem(slot).isEmpty()) {
         return InteractionResult.PASS;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      boolean shouldDrop = !player.isCreative() || player.isShiftKeyDown();
      if (shouldDrop ? pegboard.dropItem(slot) : pegboard.removeItem(slot)) {
         level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
      }
      return InteractionResult.SUCCESS;
   }
}
