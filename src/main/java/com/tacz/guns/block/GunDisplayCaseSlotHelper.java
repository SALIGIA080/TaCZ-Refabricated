package com.tacz.guns.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GunDisplayCaseSlotHelper {
   private static final double BACK_PANEL_THICKNESS = 0.1875;
   private static final double LINE_OFFSET = 0.004;
   private static final double LINE_DEPTH = 0.0125;

   private GunDisplayCaseSlotHelper() {
   }

   public static int getSlot(BlockState state, BlockPos pos, Vec3 hitLocation) {
      Direction facing = (Direction)state.getValue(GunDisplayCaseBlock.FACING);
      double x = hitLocation.x - pos.getX();
      double y = hitLocation.y - pos.getY();
      double z = hitLocation.z - pos.getZ();

      double u = switch (facing) {
         case SOUTH -> 1.0 - x;
         case EAST -> z;
         case WEST -> 1.0 - z;
         default -> x;
      };
      if (!(u < 0.0) && !(u >= 1.0) && !(y < 0.0) && !(y >= 1.0)) {
         int col = Math.min((int)(u * 4.0), 3);
         int row = Math.min((int)(y * 4.0), 3);
         return row * 4 + col;
      } else {
         return -1;
      }
   }

   public static AABB getSlotAabb(BlockState state, BlockPos pos, int slot) {
      int row = slot / 4;
      int col = slot % 4;
      double minU = col / 4.0;
      double maxU = (col + 1) / 4.0;
      double minY = row / 4.0;
      double maxY = (row + 1) / 4.0;
      Direction facing = (Direction)state.getValue(GunDisplayCaseBlock.FACING);
      double x0;
      double x1;
      double z0;
      double z1;
      switch (facing) {
         case SOUTH:
            x0 = 1.0 - maxU;
            x1 = 1.0 - minU;
            z0 = 0.1915;
            z1 = 0.20400000000000001;
            break;
         case EAST:
            x0 = 0.1915;
            x1 = 0.20400000000000001;
            z0 = minU;
            z1 = maxU;
            break;
         case WEST:
            x0 = 0.796;
            x1 = 0.8085;
            z0 = 1.0 - maxU;
            z1 = 1.0 - minU;
            break;
         default:
            x0 = minU;
            x1 = maxU;
            z0 = 0.796;
            z1 = 0.8085;
      }

      return new AABB(pos.getX() + x0, pos.getY() + minY, pos.getZ() + z0, pos.getX() + x1, pos.getY() + maxY, pos.getZ() + z1)
         .inflate(0.002);
   }
}
