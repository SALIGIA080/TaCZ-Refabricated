package com.tacz.guns.resource.manager;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.network.DataType;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public class GunIndexManager extends CommonDataManager<CommonGunIndex> {
   public GunIndexManager() {
      super(DataType.GUN_INDEX, CommonGunIndex.class, CommonAssetsManager.GSON, "index/guns", "GunIndexLoader");
   }

   @Override
   protected void apply(Map<Identifier, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
      super.apply(objects, resourceManager, profiler);
      Builder<Identifier, String> builder = ImmutableMap.builder();
      objects.forEach((id, element) -> {
         CommonGunIndex index = this.getData(id);
         if (index == null) {
            builder.put(id, element.toString());
         } else {
            JsonObject synced = element.getAsJsonObject().deepCopy();
            synced.addProperty("has_unload_ammo_script", index.hasUnloadAmmoScript());
            builder.put(id, synced.toString());
         }
      });
      this.networkCache = builder.build();
   }
}
