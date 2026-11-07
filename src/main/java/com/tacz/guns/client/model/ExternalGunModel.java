package com.tacz.guns.client.model;

import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializer;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BonesItem;
import com.tacz.guns.client.resource.pojo.model.CubesItem;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ExternalGunModel {
   private static final Gson SOURCE_GSON = new GsonBuilder().registerTypeAdapter(CubesItem.class, (JsonSerializer<CubesItem>)(cube, type, context) -> {
      JsonObject json = new JsonObject();
      if (cube.getUv() != null) {
         json.add("uv", context.serialize(cube.getUv()));
      } else if (cube.getFaceUv() != null) {
         json.add("uv", context.serialize(cube.getFaceUv()));
      }

      json.add("size", context.serialize(cube.getSize()));
      json.add("origin", context.serialize(cube.getOrigin()));
      if (cube.getRotation() != null) {
         json.add("rotation", context.serialize(cube.getRotation()));
      }

      if (cube.getPivot() != null) {
         json.add("pivot", context.serialize(cube.getPivot()));
      }

      json.addProperty("inflate", cube.getInflate());
      if (cube.isHasMirror()) {
         json.addProperty("mirror", cube.isMirror());
      }

      return json;
   }).create();
   private final BakedBedrockModel model;
   private final DisplayGunModelInstance displayInstance;

   public ExternalGunModel(BakedBedrockModel model, Set<String> hiddenTextBones, DisplayGunModelInstance.Kind kind) {
      this.model = model;
      this.displayInstance = new DisplayGunModelInstance(model, hiddenTextBones, kind);
   }

   public static ExternalGunModel bake(BedrockModelPOJO source, Set<String> hiddenTextBones, DisplayGunModelInstance.Kind kind) {
      Objects.requireNonNull(source, "source");
      com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO sbmSource = (com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO)GsonUtil.CLIENT_GSON
         .fromJson(SOURCE_GSON.toJsonTree(source), com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO.class);
      if (sbmSource == null) {
         throw new IllegalArgumentException("Missing Bedrock model");
      } else {
         Set<String> preserved = new LinkedHashSet<>();
         BonesItem[] bones;
         if (source.getGeometryModelNew() != null) {
            List<BonesItem> items = source.getGeometryModelNew().getBones();
            bones = items == null ? new BonesItem[0] : items.toArray(BonesItem[]::new);
         } else {
            if (source.getGeometryModelLegacy() == null) {
               throw new IllegalArgumentException("No Bedrock geometry in gun model");
            }

            List<BonesItem> items = source.getGeometryModelLegacy().getBones();
            bones = items == null ? new BonesItem[0] : items.toArray(BonesItem[]::new);
         }

         Arrays.stream(bones).map(BonesItem::getName).filter(Objects::nonNull).forEach(preserved::add);
         BakerOptions options = new BakerOptions(Set.of(), preserved, true, false);
         return new ExternalGunModel(BakedBedrockModel.bake(sbmSource, options), hiddenTextBones, kind);
      }
   }

   public BakedBedrockModel model() {
      return this.model;
   }

   public DisplayGunModelInstance displayInstance() {
      return this.displayInstance;
   }
}