package com.tacz.guns.resource.pojo.data.gun;

import com.google.gson.annotations.SerializedName;
import net.minecraft.util.Mth;

public class GunRecoilPushData {
   private static final double MAX_RECOIL_PUSH_STRENGTH = 4.0;
   @SerializedName("ground_strength")
   private double groundStrength = 0.0;
   @SerializedName("air_strength")
   private double airStrength = 0.0;
   @SerializedName("sneak_strength")
   private Double sneakStrength = null;
   @SerializedName("crawl_strength")
   private Double crawlStrength = null;
   @SerializedName("per_projectile")
   private boolean perProjectile = false;
   @SerializedName("projectile_multiplier")
   private double projectileMultiplier = 1.0;

   public GunRecoilPushData() {
   }

   public GunRecoilPushData(double groundStrength, double airStrength, boolean perProjectile, double projectileMultiplier) {
      this(groundStrength, airStrength, null, null, perProjectile, projectileMultiplier);
   }

   public GunRecoilPushData(
      double groundStrength, double airStrength, Double sneakStrength, Double crawlStrength, boolean perProjectile, double projectileMultiplier
   ) {
      this.groundStrength = groundStrength;
      this.airStrength = airStrength;
      this.sneakStrength = sneakStrength;
      this.crawlStrength = crawlStrength;
      this.perProjectile = perProjectile;
      this.projectileMultiplier = projectileMultiplier;
   }

   public double getGroundStrength() {
      return this.clampStrength(this.groundStrength);
   }

   public double getAirStrength() {
      return this.clampStrength(this.airStrength);
   }

   public double getSneakStrength() {
      return this.clampStrength(this.sneakStrength != null ? this.sneakStrength : this.groundStrength);
   }

   public double getCrawlStrength() {
      return this.clampStrength(this.crawlStrength != null ? this.crawlStrength : this.groundStrength);
   }

   public boolean isPerProjectile() {
      return this.perProjectile;
   }

   public double getProjectileMultiplier() {
      return Math.max(this.projectileMultiplier, 0.0);
   }

   public double getFinalStrength(boolean onGround, int bulletAmount) {
      return this.getFinalStrength(onGround, false, false, bulletAmount);
   }

   public double getFinalStrength(boolean onGround, boolean sneaking, boolean crawling, int bulletAmount) {
      double strength;
      if (!onGround) {
         strength = this.getAirStrength();
      } else if (crawling) {
         strength = this.getCrawlStrength();
      } else if (sneaking) {
         strength = this.getSneakStrength();
      } else {
         strength = this.getGroundStrength();
      }

      if (this.perProjectile) {
         strength *= Math.max(bulletAmount, 1) * this.getProjectileMultiplier();
      }

      return this.clampStrength(strength);
   }

   private double clampStrength(double strength) {
      return Mth.clamp(strength, 0.0, 4.0);
   }
}
