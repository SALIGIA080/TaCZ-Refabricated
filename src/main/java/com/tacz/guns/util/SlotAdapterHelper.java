package com.tacz.guns.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class SlotAdapterHelper {
   private static final String SLOT_ADAPTERS_TAG = "SlotAdapters";
   private static final String TAG_PREFIX = "#";
   private static final String ADAPTER_TAG_PATH_PREFIX = "adapter/";

   private SlotAdapterHelper() {
   }

   public static void setSlotAdapter(ItemStack holder, AttachmentType type, Identifier adapterId) {
      if (!holder.isEmpty() && type != null && adapterId != null) {
         ItemNbtUtils.updateTag(holder, root -> {
            CompoundTag adapters = root.getCompoundOrEmpty(SLOT_ADAPTERS_TAG);
            adapters.putString(slotKey(type), adapterId.toString());
            root.put(SLOT_ADAPTERS_TAG, adapters);
         });
      }
   }

   @Nullable
   public static Identifier getSlotAdapter(ItemStack holder, AttachmentType type) {
      if (!holder.isEmpty() && type != null) {
         CompoundTag root = ItemNbtUtils.getTag(holder);
         if (root.contains(SLOT_ADAPTERS_TAG)) {
            CompoundTag adapters = root.getCompoundOrEmpty(SLOT_ADAPTERS_TAG);
            String key = slotKey(type);
            return !adapters.contains(key) ? null : Identifier.tryParse(adapters.getStringOr(key, ""));
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static void removeSlotAdapter(ItemStack holder, AttachmentType type) {
      if (!holder.isEmpty() && type != null) {
         ItemNbtUtils.updateTag(holder, root -> {
            if (root.contains(SLOT_ADAPTERS_TAG)) {
               CompoundTag adapters = root.getCompoundOrEmpty(SLOT_ADAPTERS_TAG);
               adapters.remove(slotKey(type));
               if (adapters.isEmpty()) {
                  root.remove(SLOT_ADAPTERS_TAG);
               } else {
                  root.put(SLOT_ADAPTERS_TAG, adapters);
               }
            }
         });
      }
   }

   public static boolean allowsDirectAttachment(ItemStack gun, Identifier attachmentId) {
      Identifier hostGunId = getHostGunId(gun);
      return hostGunId != null && attachmentId != null && AllowAttachmentTagMatcher.match(hostGunId, attachmentId);
   }

   public static List<Entry<Identifier, CommonSlotAdapterIndex>> getAvailableSlotAdapters(ItemStack gun, AttachmentType attachmentType) {
      Identifier hostGunId = getHostGunId(gun);
      if (hostGunId != null && attachmentType != null && attachmentType != AttachmentType.NONE) {
         Set<String> allowList = CommonAssetsManager.get().getAllowAttachmentTags(hostGunId);
         if (allowList != null && !allowList.isEmpty()) {
            LinkedHashMap<Identifier, CommonSlotAdapterIndex> candidates = new LinkedHashMap<>();
            Set<Identifier> visitedTags = new HashSet<>();

            for (String value : allowList) {
               Identifier tagId = parseTag(value);
               if (tagId != null && tagId.getPath().startsWith("adapter/")) {
                  expandAdapterTag(tagId, attachmentType, visitedTags, candidates);
               }
            }

            return candidates.entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList();
         } else {
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   public static boolean isSlotAdapterAvailable(ItemStack gun, AttachmentType attachmentType, Identifier adapterId) {
      return adapterId == null ? false : getAvailableSlotAdapters(gun, attachmentType).stream().anyMatch(entry -> entry.getKey().equals(adapterId));
   }

   @Nullable
   public static Identifier getSelectedAvailableSlotAdapter(ItemStack gun, AttachmentType attachmentType) {
      Identifier selected = getSlotAdapter(gun, attachmentType);
      if (selected == null) {
         return null;
      } else {
         return isSlotAdapterAvailable(gun, attachmentType, selected) ? selected : null;
      }
   }

   public static boolean allowsAttachment(ItemStack gun, AttachmentType attachmentType, Identifier attachmentId) {
      if (attachmentType != null && attachmentType != AttachmentType.NONE && attachmentId != null) {
         Identifier selected = getSelectedAvailableSlotAdapter(gun, attachmentType);
         return selected == null
            ? allowsDirectAttachment(gun, attachmentId)
            : TimelessAPI.getCommonSlotAdapterIndex(selected).map(index -> index.allowsAttachment(attachmentId)).orElse(false);
      } else {
         return false;
      }
   }

   @Nullable
   public static Identifier getEffectiveSlotAdapter(ItemStack gun, AttachmentType attachmentType, Identifier attachmentId) {
      Identifier selected = getSelectedAvailableSlotAdapter(gun, attachmentType);
      return selected != null && attachmentId != null
         ? TimelessAPI.getCommonSlotAdapterIndex(selected).filter(index -> index.allowsAttachment(attachmentId)).map(index -> selected).orElse(null)
         : null;
   }

   public static boolean allowsSlotAdapters(ItemStack holder) {
      return getHostGunId(holder) != null;
   }

   private static void expandAdapterTag(
      Identifier tagId,
      AttachmentType attachmentType,
      Set<Identifier> visitedTags,
      LinkedHashMap<Identifier, CommonSlotAdapterIndex> candidates
   ) {
      if (visitedTags.add(tagId)) {
         Set<String> content = CommonAssetsManager.get().getAttachmentTags(tagId);
         if (content != null && !content.isEmpty()) {
            for (String value : content) {
               Identifier nestedTag = parseTag(value);
               if (nestedTag != null) {
                  expandAdapterTag(nestedTag, attachmentType, visitedTags, candidates);
               } else {
                  Identifier adapterId = Identifier.tryParse(value);
                  if (adapterId != null && !candidates.containsKey(adapterId)) {
                     TimelessAPI.getCommonSlotAdapterIndex(adapterId)
                        .filter(index -> index.getSlot() == attachmentType)
                        .ifPresent(index -> candidates.put(adapterId, index));
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static Identifier parseTag(String value) {
      return value != null && value.startsWith("#") ? Identifier.tryParse(value.substring(TAG_PREFIX.length())) : null;
   }

   private static String slotKey(AttachmentType type) {
      return type.name();
   }

   @Nullable
   private static Identifier getHostGunId(ItemStack holder) {
      IGun gun = IGun.getIGunOrNull(holder);
      return gun == null ? null : gun.getGunId(holder);
   }
}
