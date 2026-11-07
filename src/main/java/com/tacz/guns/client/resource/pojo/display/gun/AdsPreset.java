package com.tacz.guns.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nullable;

public class AdsPreset {
   @SerializedName("ads_path")
   @Nullable
   private AdsPath path;
   @SerializedName("ads_fov")
   @Nullable
   private AdsFov fov;

   @Nullable
   public AdsPath getPath() {
      return this.path;
   }

   @Nullable
   public AdsFov getFov() {
      return this.fov;
   }
}
