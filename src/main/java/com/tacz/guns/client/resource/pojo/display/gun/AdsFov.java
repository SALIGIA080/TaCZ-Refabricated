package com.tacz.guns.client.resource.pojo.display.gun;

import javax.annotation.Nullable;

public class AdsFov {
   @Nullable
   private AdsFov.Channel model;
   @Nullable
   private AdsFov.Channel world;

   @Nullable
   public AdsFov.Channel getModel() {
      return this.model != null && this.model.isValid() ? this.model : null;
   }

   @Nullable
   public AdsFov.Channel getWorld() {
      return this.world != null && this.world.isValid() ? this.world : null;
   }

   public static class Channel extends AdsCurve {
      private boolean smooth = true;

      public boolean isSmooth() {
         return this.smooth;
      }
   }
}
