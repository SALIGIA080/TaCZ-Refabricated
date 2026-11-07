package com.tacz.guns.resource.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.Identifier;

public class GunDataManager extends CommonDataManager<GunData> {
   private static final String EXCLUSIVE_ATTACHMENTS = "exclusive_attachments";

   public GunDataManager() {
      super(DataType.GUN_DATA, GunData.class, CommonAssetsManager.GSON, "data/guns", "GunDataLoader");
   }

   protected GunData parseJson(JsonElement element) {
      GunData data = (GunData)this.getGson().fromJson(element, this.getDataClass());
      initExclusiveAttachmentModifiers(data, element);
      return data;
   }

   public static void initExclusiveAttachmentModifiers(GunData data, JsonElement element) {
      if (data != null && element.isJsonObject()) {
         JsonElement exclusiveElement = element.getAsJsonObject().get("exclusive_attachments");
         if (exclusiveElement != null && exclusiveElement.isJsonObject()) {
            JsonObject exclusiveAttachments = exclusiveElement.getAsJsonObject();
            exclusiveAttachments.entrySet().forEach(entry -> {
               Identifier attachmentId = Identifier.tryParse((String)entry.getKey());
               if (attachmentId != null) {
                  AttachmentData attachmentData = data.getExclusiveAttachments().get(attachmentId);
                  if (attachmentData != null) {
                     AttachmentDataManager.initModifiers(attachmentData, (JsonElement)entry.getValue(), CommonAssetsManager.GSON);
                  }
               }
            });
         }
      }
   }
}
