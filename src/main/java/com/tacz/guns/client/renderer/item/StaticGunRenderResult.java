package com.tacz.guns.client.renderer.item;

public record StaticGunRenderResult(StaticGunRenderResult.Status status, boolean selectedLod) {
   public static final StaticGunRenderResult PENDING = new StaticGunRenderResult(StaticGunRenderResult.Status.PENDING, false);
   public static final StaticGunRenderResult UNSUPPORTED = new StaticGunRenderResult(StaticGunRenderResult.Status.UNSUPPORTED, false);

   public boolean submitted() {
      return this.status == StaticGunRenderResult.Status.SUBMITTED;
   }

   public static enum Status {
      SUBMITTED,
      PENDING,
      UNSUPPORTED;
   }
}
