package com.tacz.guns.util;

import com.google.common.collect.Maps;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.EnumMap;
import net.minecraft.resources.Identifier;

public final class DefaultGunAttachments {
   private DefaultGunAttachments() {
   }

   public static EnumMap<AttachmentType, Identifier> defaults(Identifier gunId) {
      return TimelessAPI.getCommonGunIndex(gunId).map(index -> {
         EnumMap<AttachmentType, Identifier> defaults = Maps.newEnumMap(AttachmentType.class);
         index.getGunData().getBuiltInAttachments().forEach((type, builtInAttachment) -> {
            Identifier defaultAttachment = builtInAttachment.getDefaultAttachment();
            if (defaultAttachment != null) {
               defaults.put(type, defaultAttachment);
            }
         });
         return defaults;
      }).orElseGet(() -> Maps.newEnumMap(AttachmentType.class));
   }
}
