package com.tacz.guns.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.util.ColorHex;
import org.apache.commons.lang3.StringUtils;

public class Rangefinder {
   @SerializedName("max_distance")
   private float maxDistance = 512.0F;
   @SerializedName("refresh_tick")
   private int refreshTick = 2;
   @SerializedName("show_impact_prediction")
   private boolean showImpactPrediction = false;
   @SerializedName("impact_prediction_color")
   private String impactPredictionColor = "#FFFFFF";
   @SerializedName("impact_prediction_scale")
   private float impactPredictionScale = 1.0F;

   public float getMaxDistance() {
      return this.maxDistance < 0.0F ? 512.0F : this.maxDistance;
   }

   public int getRefreshTick() {
      return this.refreshTick <= 0 ? 2 : this.refreshTick;
   }

   public boolean isShowImpactPrediction() {
      return this.showImpactPrediction;
   }

   public int getImpactPredictionColor() {
      String colorText = StringUtils.trimToEmpty(this.impactPredictionColor);
      if (colorText.length() == 6) {
         colorText = "#" + colorText;
      }

      return 0xFF000000 | ColorHex.colorTextToRbgInt(colorText);
   }

   public float getImpactPredictionScale() {
      return this.impactPredictionScale <= 0.0F ? 1.0F : this.impactPredictionScale;
   }
}
