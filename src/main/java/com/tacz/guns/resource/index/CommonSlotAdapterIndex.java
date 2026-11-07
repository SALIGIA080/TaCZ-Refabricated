package com.tacz.guns.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.SlotAdapterIndexPOJO;
import com.tacz.guns.resource.pojo.data.attachment.SlotAdapterData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class CommonSlotAdapterIndex {
   private SlotAdapterData data;
   private SlotAdapterIndexPOJO pojo;
   private int sort;
   private AttachmentType slot;
   private List<String> allowedAttachments;

   private CommonSlotAdapterIndex() {
   }

   public static CommonSlotAdapterIndex getInstance(SlotAdapterIndexPOJO pojo) throws IllegalArgumentException {
      CommonSlotAdapterIndex index = new CommonSlotAdapterIndex();
      index.pojo = pojo;
      checkIndex(pojo, index);
      checkData(pojo, index);
      return index;
   }

   private static void checkIndex(SlotAdapterIndexPOJO pojo, CommonSlotAdapterIndex index) {
      Preconditions.checkArgument(pojo != null, "index object file is empty");
      Preconditions.checkArgument(pojo.getDisplay() != null, "slot adapter display must be nonnull.");
      Preconditions.checkArgument(pojo.getSlot() != null && pojo.getSlot() != AttachmentType.NONE, "slot adapter slot must be a valid attachment type.");
      index.sort = Mth.clamp(pojo.getSort(), 0, 65536);
      index.slot = pojo.getSlot();
      index.allowedAttachments = List.copyOf(pojo.getAllowedAttachments());
   }

   private static void checkData(SlotAdapterIndexPOJO pojo, CommonSlotAdapterIndex index) {
      Identifier dataId = pojo.getData();
      Preconditions.checkArgument(dataId != null, "index object missing pojoData field");
      SlotAdapterData data = CommonAssetsManager.get().getSlotAdapterData(dataId);
      Preconditions.checkArgument(data != null, "there is no corresponding data file");
      index.data = data;
   }

   public SlotAdapterData getData() {
      return this.data;
   }

   public SlotAdapterIndexPOJO getPojo() {
      return this.pojo;
   }

   public int getSort() {
      return this.sort;
   }

   public AttachmentType getSlot() {
      return this.slot;
   }

   public boolean allowsAttachment(Identifier attachmentId) {
      if (attachmentId == null) {
         return false;
      } else {
         for (String value : this.allowedAttachments) {
            if (value != null) {
               if (value.startsWith("#")) {
                  Identifier tagId = Identifier.tryParse(value.substring(1));
                  if (tagId != null && AllowAttachmentTagMatcher.matchTag(tagId, attachmentId)) {
                     return true;
                  }
               } else if (attachmentId.equals(Identifier.tryParse(value))) {
                  return true;
               }
            }
         }

         return false;
      }
   }
}
