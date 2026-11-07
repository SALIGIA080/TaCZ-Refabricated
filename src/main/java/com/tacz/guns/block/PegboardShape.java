package com.tacz.guns.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

public enum PegboardShape implements StringRepresentable {
   STRAIGHT("straight"),
   CORNER_LEFT("corner_left"),
   CORNER_RIGHT("corner_right");

   private final String name;

   private PegboardShape(String name) {
      this.name = name;
   }

   public String getSerializedName() {
      return this.name;
   }

   public Direction getCornerFacing(Direction facing) {
      return switch (this) {
         case CORNER_LEFT -> facing.getCounterClockWise();
         case CORNER_RIGHT -> facing.getClockWise();
         case STRAIGHT -> facing;
      };
   }
}
