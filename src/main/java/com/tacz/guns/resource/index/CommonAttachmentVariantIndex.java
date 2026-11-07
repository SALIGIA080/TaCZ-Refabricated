package com.tacz.guns.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.AttachmentVariantIndexPOJO;
import com.tacz.guns.resource.pojo.data.attachment.VariantData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class CommonAttachmentVariantIndex {
   private VariantData data;
   private AttachmentVariantIndexPOJO pojo;
   private int sort;
   private List<Identifier> targets;
   private List<Identifier> targetTags;

   private CommonAttachmentVariantIndex() {
   }

   public static CommonAttachmentVariantIndex getInstance(AttachmentVariantIndexPOJO pojo) throws IllegalArgumentException {
      CommonAttachmentVariantIndex index = new CommonAttachmentVariantIndex();
      index.pojo = pojo;
      checkIndex(pojo, index);
      checkData(pojo, index);
      return index;
   }

   private static void checkIndex(AttachmentVariantIndexPOJO pojo, CommonAttachmentVariantIndex index) {
      Preconditions.checkArgument(pojo != null, "index object file is empty");
      Preconditions.checkArgument(pojo.getDisplay() != null, "attachment variant display must be nonnull.");
      index.sort = Mth.clamp(pojo.getSort(), 0, 65536);
      index.targets = List.copyOf(pojo.getTargets());
      index.targetTags = List.copyOf(pojo.getTargetTags());
   }

   private static void checkData(AttachmentVariantIndexPOJO pojo, CommonAttachmentVariantIndex index) {
      Identifier dataId = pojo.getData();
      Preconditions.checkArgument(dataId != null, "index object missing pojoData field");
      VariantData data = CommonAssetsManager.get().getAttachmentVariantData(dataId);
      Preconditions.checkArgument(data != null, "there is no corresponding data file");
      index.data = data;
   }

   public VariantData getData() {
      return this.data;
   }

   public AttachmentVariantIndexPOJO getPojo() {
      return this.pojo;
   }

   public int getSort() {
      return this.sort;
   }

   public List<Identifier> getTargets() {
      return this.targets;
   }

   public List<Identifier> getTargetTags() {
      return this.targetTags;
   }

   public boolean appliesTo(Identifier attachmentId) {
      if (attachmentId == null) {
         return false;
      } else {
         return this.targets.contains(attachmentId) ? true : this.targetTags.stream().anyMatch(tag -> AllowAttachmentTagMatcher.matchTag(tag, attachmentId));
      }
   }
}
