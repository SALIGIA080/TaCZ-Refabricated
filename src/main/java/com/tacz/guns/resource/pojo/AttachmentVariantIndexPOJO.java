package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;

public class AttachmentVariantIndexPOJO {
   @SerializedName("name")
   private String name;
   @SerializedName("tooltip")
   @Nullable
   private String tooltip;
   @SerializedName("display")
   private Identifier display;
   @SerializedName("data")
   private Identifier data;
   @SerializedName("targets")
   private List<Identifier> targets = new ArrayList<>();
   @SerializedName("target_tags")
   private List<Identifier> targetTags = new ArrayList<>();
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

   public List<Identifier> getTargets() {
      return this.targets == null ? List.of() : this.targets;
   }

   public List<Identifier> getTargetTags() {
      return this.targetTags == null ? List.of() : this.targetTags;
   }

   public int getSort() {
      return this.sort;
   }
}
