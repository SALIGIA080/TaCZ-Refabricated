package com.tacz.guns.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class VariantHelper {
   private static final String VARIANT_TAG = "Variant";

   private VariantHelper() {
   }

   public static void setVariant(ItemStack attachment, Identifier variantId) {
      if (!attachment.isEmpty() && variantId != null) {
         ItemNbtUtils.updateTag(attachment, tag -> tag.putString(VARIANT_TAG, variantId.toString()));
      }
   }

   @Nullable
   public static Identifier getVariant(ItemStack attachment) {
      if (attachment.isEmpty()) {
         return null;
      } else {
         CompoundTag tag = ItemNbtUtils.getTag(attachment);
         return tag.contains(VARIANT_TAG) ? Identifier.tryParse(tag.getStringOr(VARIANT_TAG, "")) : null;
      }
   }

   public static boolean hasVariant(ItemStack attachment) {
      return getVariant(attachment) != null;
   }

   public static void removeVariant(ItemStack attachment) {
      if (!attachment.isEmpty()) {
         ItemNbtUtils.updateTag(attachment, tag -> tag.remove(VARIANT_TAG));
      }
   }
}
