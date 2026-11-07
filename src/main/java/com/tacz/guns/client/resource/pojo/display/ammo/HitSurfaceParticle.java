package com.tacz.guns.client.resource.pojo.display.ammo;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import javax.annotation.Nullable;

public class HitSurfaceParticle {
   private static final int MAX_COUNT = 32;
   private static final int MAX_LIFE_TIME = 200;
   private static final float MAX_OFFSET = 1.0F;
   private static final float MAX_SPEED = 2.0F;
   private static final float MAX_SPREAD_ANGLE = 89.0F;
   @SerializedName("name")
   private String name;
   @SerializedName("count")
   private int count = 1;
   @Nullable
   @SerializedName("count_min")
   private Integer countMin;
   @Nullable
   @SerializedName("count_max")
   private Integer countMax;
   @Nullable
   @SerializedName("count_range")
   private List<Integer> countRange;
   @SerializedName("life_time")
   private int lifeTime = 20;
   @Nullable
   @SerializedName("color")
   private String color;
   @Nullable
   @SerializedName("alpha")
   private Float alpha;
   @Nullable
   @SerializedName("scale")
   private Float scale;
   @SerializedName("surface_offset")
   private float surfaceOffset = 0.03F;
   @SerializedName("random_offset")
   private float randomOffset = 0.0F;
   @SerializedName("normal_speed")
   private float normalSpeed = 0.08F;
   @SerializedName("random_speed")
   private float randomSpeed = 0.02F;
   @SerializedName("spread_angle")
   private float spreadAngle = 30.0F;

   public String getName() {
      return this.name;
   }

   public int getCount() {
      return clamp(this.count, 0, 32);
   }

   public int getCountMin() {
      if (this.countRange != null && !this.countRange.isEmpty() && this.countRange.get(0) != null) {
         return clamp(this.countRange.get(0), 0, 32);
      } else {
         return this.countMin == null ? this.getCount() : clamp(this.countMin, 0, 32);
      }
   }

   public int getCountMax() {
      if (this.countRange != null && this.countRange.size() > 1 && this.countRange.get(1) != null) {
         return clamp(this.countRange.get(1), 0, 32);
      } else {
         return this.countMax == null ? this.getCount() : clamp(this.countMax, 0, 32);
      }
   }

   public int getLifeTime() {
      return clamp(this.lifeTime, 1, 200);
   }

   @Nullable
   public String getColor() {
      return this.color;
   }

   @Nullable
   public Float getAlpha() {
      return this.alpha;
   }

   @Nullable
   public Float getScale() {
      return this.scale;
   }

   public float getSurfaceOffset() {
      return clamp(this.surfaceOffset, 0.0F, 1.0F);
   }

   public float getRandomOffset() {
      return clamp(this.randomOffset, 0.0F, 1.0F);
   }

   public float getNormalSpeed() {
      return clamp(this.normalSpeed, 0.0F, 2.0F);
   }

   public float getRandomSpeed() {
      return clamp(this.randomSpeed, 0.0F, 2.0F);
   }

   public float getSpreadAngle() {
      return clamp(this.spreadAngle, 0.0F, 89.0F);
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(value, max));
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(value, max));
   }
}
