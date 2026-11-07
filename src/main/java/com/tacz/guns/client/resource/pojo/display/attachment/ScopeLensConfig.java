package com.tacz.guns.client.resource.pojo.display.attachment;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.renderer.scope.ScopeVisionMode;
import com.tacz.guns.util.ColorHex;

public class ScopeLensConfig {
   @SerializedName("distortion")
   private float distortion = 0.05F;
   @SerializedName("aberration")
   private float aberration = 0.007F;
   @SerializedName("edge_width")
   private float edgeWidth = 0.35F;
   @SerializedName("vignette_strength")
   private float vignetteStrength = 1.0F;
   @SerializedName("vignette_width")
   private float vignetteWidth = 0.35F;
   @SerializedName("tint")
   private String tint = "#70AEE8";
   @SerializedName("tint_strength")
   private float tintStrength = 0.2F;
   @SerializedName("vision_mode")
   private ScopeVisionMode visionMode = ScopeVisionMode.NORMAL;
   @SerializedName("vision_gain")
   private float visionGain = 3.0F;
   @SerializedName("vision_noise")
   private float visionNoise = 0.025F;
   private transient int tintColor = 7384808;

   public void init() {
      this.distortion = clamp(this.distortion, 0.05F, 0.0F, 0.2F);
      this.aberration = clamp(this.aberration, 0.007F, 0.0F, 0.03F);
      this.edgeWidth = clamp(this.edgeWidth, 0.35F, 0.0F, 2.0F);
      this.vignetteStrength = clamp(this.vignetteStrength, 1.0F, 0.0F, 1.0F);
      this.vignetteWidth = clamp(this.vignetteWidth, 0.35F, 0.0F, 2.0F);
      this.tintColor = ColorHex.colorTextToRbgInt(this.tint == null ? "#70AEE8" : this.tint);
      this.tintStrength = clamp(this.tintStrength, 0.2F, 0.0F, 1.0F);
      this.visionGain = clamp(this.visionGain, 3.0F, 1.0F, 12.0F);
      this.visionNoise = clamp(this.visionNoise, 0.025F, 0.0F, 0.2F);
      if (this.visionMode == null) {
         this.visionMode = ScopeVisionMode.NORMAL;
      }
   }

   private static float clamp(float value, float fallback, float min, float max) {
      return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
   }

   public float getDistortion() {
      return this.distortion;
   }

   public float getAberration() {
      return this.aberration;
   }

   public float getEdgeWidth() {
      return this.edgeWidth;
   }

   public float getVignetteStrength() {
      return this.vignetteStrength;
   }

   public float getVignetteWidth() {
      return this.vignetteWidth;
   }

   public boolean hasEdgeEffects() {
      return this.edgeWidth > 0.0F && (this.distortion > 0.0F || this.aberration > 0.0F) || this.vignetteStrength > 0.0F && this.vignetteWidth > 0.0F;
   }

   public int getTint() {
      return this.tintColor;
   }

   public float getTintStrength() {
      return this.tintStrength;
   }

   public ScopeVisionMode getVisionMode() {
      return this.visionMode;
   }

   public float getVisionGain() {
      return this.visionGain;
   }

   public float getVisionNoise() {
      return this.visionNoise;
   }
}
