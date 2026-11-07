package com.tacz.guns.client.resource.pojo.display;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

public class SoundData {
   private final Identifier sound;
   @Nullable
   private final Float volume;
   @Nullable
   private final Float pitch;
   private final float volumeRandomRange;
   private final float pitchRandomRange;

   private SoundData(Identifier sound, @Nullable Float volume, @Nullable Float pitch, float volumeRandomRange, float pitchRandomRange) {
      this.sound = sound;
      this.volume = volume;
      this.pitch = pitch;
      this.volumeRandomRange = volumeRandomRange;
      this.pitchRandomRange = pitchRandomRange;
   }

   public static SoundData of(Identifier sound) {
      return new SoundData(sound, null, null, 0.0F, 0.0F);
   }

   public Identifier getSound() {
      return this.sound;
   }

   public boolean hasVolume() {
      return this.volume != null;
   }

   @Nullable
   public Float getVolume() {
      return this.volume;
   }

   public boolean hasPitch() {
      return this.pitch != null;
   }

   @Nullable
   public Float getPitch() {
      return this.pitch;
   }

   public float getVolumeRandomRange() {
      return this.volumeRandomRange;
   }

   public boolean hasVolumeRandomRange() {
      return this.volumeRandomRange > 0.0F;
   }

   public float getPitchRandomRange() {
      return this.pitchRandomRange;
   }

   public boolean hasPitchRandomRange() {
      return this.pitchRandomRange > 0.0F;
   }

   public float resolveVolume(RandomSource random, float fallback) {
      float base = this.hasVolume() ? this.volume : fallback;
      if (this.hasVolumeRandomRange()) {
         base += (random.nextFloat() * 2.0F - 1.0F) * this.volumeRandomRange;
      }

      return Math.max(0.0F, base);
   }

   public float resolvePitch(RandomSource random, float fallback) {
      float base = this.hasPitch() ? this.pitch : fallback;
      if (this.hasPitchRandomRange()) {
         base += (random.nextFloat() * 2.0F - 1.0F) * this.pitchRandomRange;
      }

      return Math.max(1.0E-4F, base);
   }

   @Override
   public String toString() {
      return "SoundData{sound="
         + this.sound
         + ", volume="
         + this.volume
         + ", pitch="
         + this.pitch
         + ", volumeRandomRange="
         + this.volumeRandomRange
         + ", pitchRandomRange="
         + this.pitchRandomRange
         + "}";
   }

   public static class Deserializer implements JsonDeserializer<SoundData> {
      public SoundData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
         if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (!primitive.isString()) {
               throw new JsonParseException("Sound must be a string id or an object, got: " + json);
            } else {
               Identifier sound = (Identifier)context.deserialize(primitive, Identifier.class);
               if (sound == null) {
                  throw new JsonParseException("Sound id must not be null, got: " + json);
               } else {
                  return SoundData.of(sound);
               }
            }
         } else if (!json.isJsonObject()) {
            throw new JsonParseException("Sound must be a string id or an object, got: " + json);
         } else {
            JsonObject object = json.getAsJsonObject();
            JsonElement idElement = object.get("id");
            if (idElement != null && idElement.isJsonPrimitive() && idElement.getAsJsonPrimitive().isString()) {
               Identifier sound = (Identifier)context.deserialize(idElement, Identifier.class);
               if (sound == null) {
                  throw new JsonParseException("Sound id must not be null, got: " + json);
               } else {
                  Float volume = null;
                  if (object.has("volume")) {
                     float v = object.get("volume").getAsFloat();
                     if (v < 0.0F) {
                        throw new JsonParseException("Sound volume must be >= 0, got: " + json);
                     }

                     volume = v;
                  }

                  Float pitch = null;
                  if (object.has("pitch")) {
                     float p = object.get("pitch").getAsFloat();
                     if (p <= 0.0F) {
                        throw new JsonParseException("Sound pitch must be > 0, got: " + json);
                     }

                     pitch = p;
                  }

                  float volumeRandomRange = 0.0F;
                  if (object.has("volume_random_range")) {
                     volumeRandomRange = object.get("volume_random_range").getAsFloat();
                     if (volumeRandomRange < 0.0F) {
                        throw new JsonParseException("Sound volume_random_range must be >= 0, got: " + json);
                     }
                  }

                  float pitchRandomRange = 0.0F;
                  if (object.has("pitch_random_range")) {
                     pitchRandomRange = object.get("pitch_random_range").getAsFloat();
                     if (pitchRandomRange < 0.0F) {
                        throw new JsonParseException("Sound pitch_random_range must be >= 0, got: " + json);
                     }
                  }

                  return new SoundData(sound, volume, pitch, volumeRandomRange, pitchRandomRange);
               }
            } else {
               throw new JsonParseException("Sound object requires a string \"id\" field, got: " + json);
            }
         }
      }
   }
}
