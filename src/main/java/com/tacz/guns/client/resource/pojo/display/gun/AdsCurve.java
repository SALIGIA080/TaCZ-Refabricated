package com.tacz.guns.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.util.math.Easing;
import net.minecraft.util.Mth;

public class AdsCurve {
   private float start = 0.0F;
   private float end = 1.0F;
   private AdsCurve.Ease ease = AdsCurve.Ease.LINEAR;
   private boolean clamp = true;

   public boolean isValid() {
      return Float.isFinite(this.start) && Float.isFinite(this.end) && this.start >= 0.0F && this.end <= 1.0F && this.start < this.end && this.ease != null;
   }

   public float evaluate(float progress) {
      float t = (progress - this.start) / (this.end - this.start);
      if (this.clamp) {
         t = Mth.clamp(t, 0.0F, 1.0F);
      }
      return switch (this.ease) {
         case LINEAR -> t;
         case EASE_IN_QUAD -> t * t;
         case EASE_OUT_QUAD -> 1.0F - (1.0F - t) * (1.0F - t);
         case EASE_IN_OUT_QUAD -> t < 0.5F ? 2.0F * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 2.0) / 2.0F;
         case EASE_IN_CUBIC -> t * t * t;
         case EASE_OUT_CUBIC -> 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
         case EASE_IN_OUT_CUBIC -> t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
         case EASE_IN_QUART -> (float)Math.pow(t, 4.0);
         case EASE_OUT_QUART -> 1.0F - (float)Math.pow(1.0F - t, 4.0);
         case EASE_IN_OUT_QUART -> t < 0.5F ? 8.0F * (float)Math.pow(t, 4.0) : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 4.0) / 2.0F;
         case EASE_IN_QUINT -> (float)Math.pow(t, 5.0);
         case EASE_OUT_QUINT -> 1.0F - (float)Math.pow(1.0F - t, 5.0);
         case EASE_IN_OUT_QUINT -> t < 0.5F ? 16.0F * (float)Math.pow(t, 5.0) : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 5.0) / 2.0F;
         case EASE_IN_SINE -> (float)(1.0 - Math.cos(t * Math.PI / 2.0));
         case EASE_IN_EXPO -> (float)Easing.easeInExpo(t);
         case EASE_OUT_EXPO -> t <= 0.0F ? 0.0F : (t >= 1.0F ? 1.0F : (float)(1.0 - Math.pow(2.0, -10.0 * t)));
         case EASE_IN_OUT_EXPO -> t <= 0.0F
            ? 0.0F
            : (t >= 1.0F ? 1.0F : (t < 0.5F ? (float)Math.pow(2.0, 20.0 * t - 10.0) / 2.0F : (float)(2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0F));
         case EASE_OUT_SINE -> (float)Math.sin(t * Math.PI / 2.0);
         case EASE_IN_OUT_SINE -> (float)((1.0 - Math.cos(t * Math.PI)) / 2.0);
         case EASE_IN_CIRC -> 1.0F - (float)Math.sqrt(1.0 - Math.pow(Mth.clamp(t, 0.0F, 1.0F), 2.0));
         case EASE_OUT_CIRC -> (float)Math.sqrt(1.0 - Math.pow(Mth.clamp(t, 0.0F, 1.0F) - 1.0F, 2.0));
         case EASE_IN_OUT_CIRC -> {
            float u = Mth.clamp(t, 0.0F, 1.0F);
            yield u < 0.5F
               ? (1.0F - (float)Math.sqrt(1.0 - Math.pow(2.0F * u, 2.0))) / 2.0F
               : ((float)Math.sqrt(1.0 - Math.pow(-2.0F * u + 2.0F, 2.0)) + 1.0F) / 2.0F;
         }
         case EASE_IN_BACK -> (float)(2.70158 * t * t * t - 1.70158 * t * t);
         case EASE_OUT_BACK -> (float)(1.0 + 2.70158 * Math.pow(t - 1.0F, 3.0) + 1.70158 * Math.pow(t - 1.0F, 2.0));
         case EASE_IN_OUT_BACK -> t < 0.5F
            ? (float)(Math.pow(2.0F * t, 2.0) * (7.189819 * t - 2.5949095) / 2.0)
            : (float)(Math.pow(2.0F * t - 2.0F, 2.0) * (3.5949095 * (2.0F * t - 2.0F) + 2.5949095) / 2.0 + 1.0);
         case SMOOTHSTEP -> t * t * (3.0F - 2.0F * t);
      };
   }

   private static enum Ease {
      @SerializedName("linear")
      LINEAR,
      @SerializedName(
         value = "ease_in_sine",
         alternate = {"easeInSine"}
      )
      EASE_IN_SINE,
      @SerializedName(
         value = "ease_out_sine",
         alternate = {"easeOutSine"}
      )
      EASE_OUT_SINE,
      @SerializedName(
         value = "ease_in_out_sine",
         alternate = {"easeInOutSine"}
      )
      EASE_IN_OUT_SINE,
      @SerializedName(
         value = "ease_in_quad",
         alternate = {"easeInQuad"}
      )
      EASE_IN_QUAD,
      @SerializedName(
         value = "ease_out_quad",
         alternate = {"easeOutQuad"}
      )
      EASE_OUT_QUAD,
      @SerializedName(
         value = "ease_in_out_quad",
         alternate = {"easeInOutQuad"}
      )
      EASE_IN_OUT_QUAD,
      @SerializedName(
         value = "ease_in_cubic",
         alternate = {"easeInCubic"}
      )
      EASE_IN_CUBIC,
      @SerializedName(
         value = "ease_out_cubic",
         alternate = {"easeOutCubic"}
      )
      EASE_OUT_CUBIC,
      @SerializedName(
         value = "ease_in_out_cubic",
         alternate = {"easeInOutCubic"}
      )
      EASE_IN_OUT_CUBIC,
      @SerializedName(
         value = "ease_in_quart",
         alternate = {"easeInQuart"}
      )
      EASE_IN_QUART,
      @SerializedName(
         value = "ease_out_quart",
         alternate = {"easeOutQuart"}
      )
      EASE_OUT_QUART,
      @SerializedName(
         value = "ease_in_out_quart",
         alternate = {"easeInOutQuart"}
      )
      EASE_IN_OUT_QUART,
      @SerializedName(
         value = "ease_in_quint",
         alternate = {"easeInQuint"}
      )
      EASE_IN_QUINT,
      @SerializedName(
         value = "ease_out_quint",
         alternate = {"easeOutQuint"}
      )
      EASE_OUT_QUINT,
      @SerializedName(
         value = "ease_in_out_quint",
         alternate = {"easeInOutQuint"}
      )
      EASE_IN_OUT_QUINT,
      @SerializedName(
         value = "ease_in_expo",
         alternate = {"easeInExpo"}
      )
      EASE_IN_EXPO,
      @SerializedName(
         value = "ease_out_expo",
         alternate = {"easeOutExpo"}
      )
      EASE_OUT_EXPO,
      @SerializedName(
         value = "ease_in_out_expo",
         alternate = {"easeInOutExpo"}
      )
      EASE_IN_OUT_EXPO,
      @SerializedName(
         value = "ease_in_circ",
         alternate = {"easeInCirc"}
      )
      EASE_IN_CIRC,
      @SerializedName(
         value = "ease_out_circ",
         alternate = {"easeOutCirc"}
      )
      EASE_OUT_CIRC,
      @SerializedName(
         value = "ease_in_out_circ",
         alternate = {"easeInOutCirc"}
      )
      EASE_IN_OUT_CIRC,
      @SerializedName(
         value = "ease_in_back",
         alternate = {"easeInBack"}
      )
      EASE_IN_BACK,
      @SerializedName(
         value = "ease_out_back",
         alternate = {"easeOutBack"}
      )
      EASE_OUT_BACK,
      @SerializedName(
         value = "ease_in_out_back",
         alternate = {"easeInOutBack"}
      )
      EASE_IN_OUT_BACK,
      @SerializedName("smoothstep")
      SMOOTHSTEP;
   }
}
