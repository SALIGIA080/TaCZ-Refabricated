package com.tacz.guns.client.event;

import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
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
 * 挂板槽位高亮。26.2 没有 RenderHighlightEvent，线框渲染也改走 Gizmo 体系：
 * 挂在 Fabric LevelRenderEvents.BEFORE_GIZMOS，用 Gizmos.cuboid 画世界坐标线框。
 * （挂板方块本身的轮廓仍由原版负责，这里只叠加槽位框。）
 */
@Environment(EnvType.CLIENT)
public class GunPegboardHighlightEvent {
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
      if (!(state.getBlock() instanceof GunPegboardBlock)) {
         return;
      }
      GunPegboardBlockEntity pegboard = level.getBlockEntity(pos) instanceof GunPegboardBlockEntity value ? value : null;
      if (minecraft.player.isShiftKeyDown()) {
         // 潜行时高亮所有已占用槽位
         if (pegboard != null) {
            for (int slot = 0; slot < 16; slot++) {
               if (!pegboard.getItem(slot).isEmpty()) {
                  renderSlotBox(state, pos, slot, COLOR_OCCUPIED);
               }
            }
         }
      } else {
         int slot = GunPegboardSlotHelper.getSlot(state, pos, target.getLocation());
         if (slot >= 0) {
            boolean occupied = pegboard != null && !pegboard.getItem(slot).isEmpty();
            renderSlotBox(state, pos, slot, occupied ? COLOR_OCCUPIED : COLOR_FREE);
         }
      }
   }

   private static void renderSlotBox(BlockState state, BlockPos pos, int slot, int argb) {
      AABB aabb = GunPegboardSlotHelper.getSlotAabb(state, pos, slot);
      Gizmos.cuboid(aabb, GizmoStyle.stroke(argb));
   }
}
