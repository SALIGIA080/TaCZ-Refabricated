package com.tacz.guns.client.resource.serialize;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.guns.client.resource.pojo.animation.bedrock.ParticleEffectKeyframes;
import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map.Entry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

public class ParticleEffectKeyframesSerializer implements JsonDeserializer<ParticleEffectKeyframes> {
   public ParticleEffectKeyframes deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
      Double2ObjectRBTreeMap<ParticleEffectData> keyframes = new Double2ObjectRBTreeMap();
      if (json.isJsonObject()) {
         JsonObject jsonObject = json.getAsJsonObject();
         Iterator var6 = jsonObject.entrySet().iterator();

         while (true) {
            Entry<String, JsonElement> entry;
            double time;
            while (true) {
               if (!var6.hasNext()) {
                  return new ParticleEffectKeyframes(keyframes);
               }

               entry = (Entry<String, JsonElement>)var6.next();

               try {
                  time = Double.parseDouble(entry.getKey());
                  break;
               } catch (NumberFormatException var15) {
               }
            }

            JsonElement value = entry.getValue();
            Identifier effect = null;
            String locator = null;
            String preEffectScript = null;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
               effect = Identifier.tryParse(value.getAsString());
            } else if (value.isJsonObject()) {
               JsonObject obj = value.getAsJsonObject();
               effect = Identifier.tryParse(GsonHelper.getAsString(obj, "effect"));
               if (obj.has("locator")) {
                  locator = GsonHelper.getAsString(obj, "locator");
               }

               if (obj.has("pre_effect_script")) {
                  preEffectScript = GsonHelper.getAsString(obj, "pre_effect_script");
               }
            }

            if (effect != null) {
               keyframes.put(time, new ParticleEffectData(effect, locator, preEffectScript));
            }
         }
      } else {
         return new ParticleEffectKeyframes(keyframes);
      }
   }
}