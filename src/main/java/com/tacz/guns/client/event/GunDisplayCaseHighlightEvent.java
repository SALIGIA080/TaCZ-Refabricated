package com.tacz.guns.client.event;

import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 展示柜槽位高亮（锁定时不显示）。同 GunPegboardHighlightEvent，走 Gizmo 体系。
 */
@Environment(EnvType.CLIENT)
public class GunDisplayCaseHighlightEvent {
   private static final int COLOR_FREE = 0xFF00FF00;
   private static final int COLOR_OCCUPIED = 0xFFFF0000;

   public static void beforeGizmos(LevelRenderContext context) {
      Minecraft minecraft = Minecraft.getInstance();
      if (!(minecraft.hitResult instanceof BlockHitResult target) || target.getType() != HitResult.Type.BLOCK) {
         return;
      }
      if (minecraft.level == null || minecraft.player == null) {
         return;
      }
      BlockPos pos = target.getBlockPos();
      Level level = minecraft.level;
      BlockState state = level.getBlockState(pos);
      if (!(state.getBlock() instanceof GunDisplayCaseBlock) || state.getValue(GunDisplayCaseBlock.LOCKED)) {
         return;
      }
      GunDisplayCaseBlockEntity displayCase = level.getBlockEntity(pos) instanceof GunDisplayCaseBlockEntity value ? value : null;
      if (minecraft.player.isShiftKeyDown()) {
         if (displayCase != null) {
            for (int slot = 0; slot < 16; slot++) {
               if (!displayCase.getItem(slot).isEmpty()) {
                  renderSlotBox(state, pos, slot, COLOR_OCCUPIED);
               }
            }
         }
      } else {
         int slot = GunDisplayCaseSlotHelper.getSlot(state, pos, target.getLocation());
         if (slot >= 0) {
            boolean occupied = displayCase != null && !displayCase.getItem(slot).isEmpty();
            renderSlotBox(state, pos, slot, occupied ? COLOR_OCCUPIED : COLOR_FREE);
         }
      }
   }

   private static void renderSlotBox(BlockState state, BlockPos pos, int slot, int argb) {
      AABB aabb = GunDisplayCaseSlotHelper.getSlotAabb(state, pos, slot);
      Gizmos.cuboid(aabb, GizmoStyle.stroke(argb));
   }
}
