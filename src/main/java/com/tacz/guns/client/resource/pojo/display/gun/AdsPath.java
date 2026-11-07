package com.tacz.guns.client.resource.pojo.display.gun;

public class AdsPath {
   private AdsCurve x = new AdsCurve();
   private AdsCurve y = new AdsCurve();
   private AdsCurve z = new AdsCurve();
   private AdsCurve rotation = new AdsCurve();

   public float x(float progress) {
      return this.x.evaluate(progress);
   }

   public float y(float progress) {
      return this.y.evaluate(progress);
   }

   public float z(float progress) {
      return this.z.evaluate(progress);
   }

   public float rotation(float progress) {
      return this.rotation.evaluate(progress);
   }

   public boolean isValid() {
      return this.x != null
         && this.x.isValid()
         && this.y != null
         && this.y.isValid()
         && this.z != null
         && this.z.isValid()
         && this.rotation != null
         && this.rotation.isValid();
   }
}
