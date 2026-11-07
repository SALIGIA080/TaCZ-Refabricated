package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;

public class SlotAdapterIndexPOJO {
   @SerializedName("name")
   private String name;
   @SerializedName("tooltip")
   @Nullable
   private String tooltip;
   @SerializedName("display")
   private Identifier display;
   @SerializedName("data")
   private Identifier data;
   @SerializedName("slot")
   private AttachmentType slot;
   @SerializedName("allowed_attachments")
   private List<String> allowedAttachments = new ArrayList<>();
   @SerializedName("sort")
   private int sort;

   public String getName() {
      return this.name;
   }

   @Nullable
   public String getTooltip() {
      return this.tooltip;
   }

   public Identifier getDisplay() {
      return this.display;
   }

   public Identifier getData() {
      return this.data;
   }

   public AttachmentType getSlot() {
      return this.slot;
   }

   public List<String> getAllowedAttachments() {
      return this.allowedAttachments == null ? List.of() : this.allowedAttachments;
   }

   public int getSort() {
      return this.sort;
   }
}
