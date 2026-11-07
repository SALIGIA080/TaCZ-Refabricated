package com.tacz.guns.resource.pojo.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import javax.annotation.Nullable;

public final class SmokeDirectionData {
   public static final int MAX_DIRECTIONS = 4;
   public static final float DEFAULT_SPREAD = 0.35F;
   public static final float DEFAULT_SPREAD_ANGLE = 20.0F;

   private SmokeDirectionData() {
   }

   @Nullable
   public static SmokeDirectionData.Config parse(
      @Nullable JsonElement angles, @Nullable Float spreadAngle, @Nullable JsonElement vectors, @Nullable Float spread
   ) {
      float[][] angleDirections = parseAngles(angles);
      if (angleDirections != null) {
         return new SmokeDirectionData.Config(angleDirections, parseSpreadAngle(spreadAngle), true);
      } else {
         float[][] legacyDirections = parseDirections(vectors);
         return legacyDirections == null ? null : new SmokeDirectionData.Config(legacyDirections, parseSpread(spread), false);
      }
   }

   @Nullable
   private static float[][] parseAngles(@Nullable JsonElement element) {
      if (element != null && (element.isJsonArray() || element.isJsonObject())) {
         JsonArray array;
         if (element.isJsonArray()) {
            array = element.getAsJsonArray();
         } else {
            array = new JsonArray();
            array.add(element);
         }

         if (array.size() == 0) {
            return null;
         } else {
            int count = Math.min(array.size(), 4);
            float[][] result = new float[count][];
            int i = 0;

            while (i < count) {
               JsonElement direction = array.get(i);
               if (!direction.isJsonObject()) {
                  return null;
               }

               JsonElement plane = direction.getAsJsonObject().get("plane_angle");
               JsonElement out = direction.getAsJsonObject().get("out_angle");
               if (isFiniteNumber(plane) && isFiniteNumber(out)) {
                  float planeDegrees = plane.getAsFloat();
                  float outDegrees = out.getAsFloat();
                  if (!(planeDegrees < -180.0F) && !(planeDegrees > 180.0F) && !(outDegrees < -90.0F) && !(outDegrees > 90.0F)) {
                     double planeRadians = Math.toRadians(planeDegrees);
                     double outRadians = Math.toRadians(outDegrees);
                     result[i] = new float[]{
                        (float)(Math.cos(outRadians) * Math.cos(planeRadians)),
                        (float)(Math.cos(outRadians) * Math.sin(planeRadians)),
                        (float)(-Math.sin(outRadians))
                     };
                     i++;
                     continue;
                  }

                  return null;
               }

               return null;
            }

            return result;
         }
      } else {
         return null;
      }
   }

   private static boolean isFiniteNumber(@Nullable JsonElement value) {
      return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && Float.isFinite(value.getAsFloat());
   }

   public static float parseSpreadAngle(@Nullable Float angle) {
      return angle != null && Float.isFinite(angle) && angle >= 0.0F && angle <= 180.0F ? angle : 20.0F;
   }

   @Nullable
   public static float[][] parseDirections(@Nullable JsonElement element) {
      if (element != null && element.isJsonArray()) {
         JsonArray array = element.getAsJsonArray();
         if (array.size() == 0) {
            return null;
         } else {
            boolean multiple = array.get(0) != null && array.get(0).isJsonArray();
            int count = multiple ? Math.min(array.size(), 4) : 1;
            float[][] result = new float[count][];

            for (int i = 0; i < count; i++) {
               float[] dir = parseVector((JsonElement)(multiple ? array.get(i) : array));
               if (dir == null) {
                  return null;
               }

               result[i] = dir;
            }

            return result;
         }
      } else {
         return null;
      }
   }

   public static float parseSpread(@Nullable Float spread) {
      return spread != null && Float.isFinite(spread) && spread >= 0.0F ? spread : 0.35F;
   }

   @Nullable
   private static float[] parseVector(@Nullable JsonElement element) {
      if (element != null && element.isJsonArray()) {
         JsonArray array = element.getAsJsonArray();
         if (array.size() != 3) {
            return null;
         } else {
            float[] dir = new float[3];

            for (int i = 0; i < 3; i++) {
               JsonElement component = array.get(i);
               if (component == null || !component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) {
                  return null;
               }

               dir[i] = component.getAsFloat();
               if (!Float.isFinite(dir[i])) {
                  return null;
               }
            }

            float length = (float)Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            return length <= 1.0E-6F ? null : new float[]{dir[0] / length, dir[1] / length, dir[2] / length};
         }
      } else {
         return null;
      }
   }

   public record Config(float[][] directions, float spread, boolean angleMode) {
   }
}
