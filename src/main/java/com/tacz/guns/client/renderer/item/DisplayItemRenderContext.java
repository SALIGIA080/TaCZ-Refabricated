package com.tacz.guns.client.renderer.item;

public final class DisplayItemRenderContext {
   private static int depth;

   private DisplayItemRenderContext() {
   }

   public static boolean isActive() {
      return depth > 0;
   }

   public static void render(Runnable action) {
      depth++;

      try {
         action.run();
      } catch (RuntimeException var2) {
         depth--;
         throw var2;
      }

      depth--;
   }
}
