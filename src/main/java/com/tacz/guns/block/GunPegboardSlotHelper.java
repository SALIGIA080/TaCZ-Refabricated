package com.tacz.guns.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GunPegboardSlotHelper {
   private static final double PLATE_THICKNESS = 0.1875;
   private static final double LINE_OFFSET = 0.004;
   private static final double LINE_DEPTH = 0.0125;

   private GunPegboardSlotHelper() {
   }

   public static boolean hasCorner(BlockState state) {
      return state.getBlock() instanceof GunPegboardBlock && state.getValue(GunPegboardBlock.SHAPE) != PegboardShape.STRAIGHT;
   }

   public static Direction getSlotFacing(BlockState state, int slot) {
      Direction facing = (Direction)state.getValue(GunPegboardBlock.FACING);
      return slot >= 16 && hasCorner(state) ? ((PegboardShape)state.getValue(GunPegboardBlock.SHAPE)).getCornerFacing(facing) : facing;
   }

   public static boolean isSlotActive(BlockState state, int slot) {
      if (slot >= 0 && slot < 32) {
         boolean secondFace = slot >= 16;
         if (!hasCorner(state)) {
            return !secondFace;
         } else {
            int column = slot % 4;
            boolean left = state.getValue(GunPegboardBlock.SHAPE) == PegboardShape.CORNER_LEFT;
            int blockedColumn = left == secondFace ? 0 : 3;
            return column != blockedColumn;
         }
      } else {
         return false;
      }
   }

   public static int getSlot(BlockState state, BlockPos pos, Vec3 hitLocation) {
      Direction facing = (Direction)state.getValue(GunPegboardBlock.FACING);
      double x = hitLocation.x - pos.getX();
      double y = hitLocation.y - pos.getY();
      double z = hitLocation.z - pos.getZ();
      int faceOffset = 0;
      if (hasCorner(state) && !onDisplayFace(facing, x, z)) {
         facing = getSlotFacing(state, 16);
         if (!onDisplayFace(facing, x, z)) {
            return -1;
         }

         faceOffset = 16;
      }
      double u = switch (facing) {
         case SOUTH -> 1.0 - x;
         case EAST -> z;
         case WEST -> 1.0 - z;
         default -> x;
      };
      if (!(u < 0.0) && !(u >= 1.0) && !(y < 0.0) && !(y >= 1.0)) {
         int col = Math.min((int)(u * 4.0), 3);
         int row = Math.min((int)(y * 4.0), 3);
         int slot = faceOffset + row * 4 + col;
         return isSlotActive(state, slot) ? slot : -1;
      } else {
         return -1;
      }
   }

   private static boolean onDisplayFace(Direction facing, double x, double z) {
      double depth = switch (facing) {
         case SOUTH -> z;
         case EAST -> x;
         case WEST -> 1.0 - x;
         default -> 1.0 - z;
      };
      return Math.abs(depth - 0.1875) < 0.001;
   }

   public static AABB getSlotAabb(BlockState state, BlockPos pos, int slot) {
      int row = slot % 16 / 4;
      int col = slot % 4;
      double minU = col / 4.0;
      double maxU = (col + 1) / 4.0;
      double minY = row / 4.0;
      double maxY = (row + 1) / 4.0;
      Direction facing = getSlotFacing(state, slot);
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
