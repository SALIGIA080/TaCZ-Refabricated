package com.tacz.guns.event;

import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.block.AmmoStorageBlock;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.util.AmmoStorageInteractionHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 弹药库交互事件：潜行 + 主手持弹药盒右键弹药库 → 把弹药盒里的子弹卸进弹药库。
 * 26.2 移植说明：Forge RightClickBlock → Fabric UseBlockCallback，
 * 在 {@code TaCZFabric#subscribeEvents} 注册。
 */
public class AmmoStorageInteractionEvent {
   public static InteractionResult onRightClickBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
      if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      }
      ItemStack held = player.getItemInHand(hand);
      if (!(held.getItem() instanceof IAmmoBox ammoBox)) {
         return InteractionResult.PASS;
      }
      BlockState state = level.getBlockState(hit.getBlockPos());
      if (!(state.getBlock() instanceof AmmoStorageBlock)) {
         return InteractionResult.PASS;
      }
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      if (level.getBlockEntity(hit.getBlockPos()) instanceof AmmoStorageBlockEntity storage) {
         return AmmoStorageInteractionHelper.unloadAmmoBox(storage, held, ammoBox, player);
      }
      return InteractionResult.CONSUME;
   }
}
