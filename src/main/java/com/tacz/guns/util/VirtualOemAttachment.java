package com.tacz.guns.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.pojo.data.gun.BuiltInAttachment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class VirtualOemAttachment {
   private static final String VIRTUAL_OEM_TAG = "VirtualOemAttachment";

   private VirtualOemAttachment() {
   }

   public static boolean isAllowedVirtualOem(Identifier gunId, Identifier attachmentId, AttachmentType type) {
      return TimelessAPI.getCommonGunIndex(gunId).map(index -> {
         BuiltInAttachment builtInAttachment = index.getGunData().getBuiltInAttachments().get(type);
         return builtInAttachment != null && builtInAttachment.getAttachments().contains(attachmentId);
      }).orElse(false);
   }

   public static boolean shouldShowIcon(Identifier gunId, Identifier attachmentId, AttachmentType type) {
      return TimelessAPI.getCommonGunIndex(gunId).map(index -> {
         BuiltInAttachment builtInAttachment = index.getGunData().getBuiltInAttachments().get(type);
         return builtInAttachment != null && builtInAttachment.getAttachments().contains(attachmentId) && builtInAttachment.isShowIcon();
      }).orElse(false);
   }

   public static void mark(ItemStack attachmentStack) {
      ItemNbtUtils.updateTag(attachmentStack, tag -> tag.putBoolean(VIRTUAL_OEM_TAG, true));
   }

   public static boolean isMarked(ItemStack attachmentStack) {
      CompoundTag tag = ItemNbtUtils.getTag(attachmentStack);
      return tag.getBooleanOr(VIRTUAL_OEM_TAG, false);
   }
}
