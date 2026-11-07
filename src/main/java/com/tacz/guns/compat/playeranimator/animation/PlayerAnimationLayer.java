package com.tacz.guns.compat.playeranimator.animation;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerAnimationLayer extends ModifierLayer<IAnimation> {
   private static final int MAX_FADES = 8;
   private final BooleanSupplier suppressTransforms;

   public PlayerAnimationLayer(BooleanSupplier suppressTransforms) {
      this.suppressTransforms = suppressTransforms;
   }

   public void replaceAnimationWithFade(@NotNull AbstractFadeModifier fade, @Nullable IAnimation animation, boolean fadeFromNothing) {
      if (this.getAnimation() != animation) {
         while (this.size() >= 8) {
            this.removeModifier(0);
         }

         super.replaceAnimationWithFade(fade, animation, fadeFromNothing);
      }
   }

   public void setAnimationImmediately(@Nullable IAnimation animation) {
      while (this.size() > 0) {
         this.removeModifier(0);
      }

      this.setAnimation(animation);
   }

   public void clearAnimation() {
      this.setAnimationImmediately(null);
   }

   @NotNull
   public Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
      return this.suppressTransforms.getAsBoolean() ? value0 : super.get3DTransform(modelName, type, tickDelta, value0);
   }

   @NotNull
   public FirstPersonMode getFirstPersonMode(float tickDelta) {
      return FirstPersonMode.DISABLED;
   }
}
