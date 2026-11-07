package com.tacz.guns.client.renderer.scope;

public enum ScopeVisionMode {
   NORMAL,
   NIGHT_VISION_GREEN,
   NIGHT_VISION_WHITE,
   THERMAL_WHITE_HOT,
   THERMAL_BLACK_HOT,
   THERMAL_IRONBOW;

   public boolean isThermal() {
      return this == THERMAL_WHITE_HOT || this == THERMAL_BLACK_HOT || this == THERMAL_IRONBOW;
   }
}
