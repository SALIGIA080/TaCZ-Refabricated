package com.tacz.guns.resource.pojo.data.gun;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public class BuiltInAttachment {
   private final List<Identifier> attachments = Lists.newArrayList();
   @Nullable
   private Identifier defaultAttachment = null;
   @Nullable
   private Identifier displayAttachment = null;
   private boolean showIcon = true;

   public BuiltInAttachment(
      List<Identifier> attachments, @Nullable Identifier defaultAttachment, @Nullable Identifier displayAttachment, boolean showIcon
   ) {
      this.attachments.addAll(attachments);
      this.defaultAttachment = defaultAttachment;
      this.displayAttachment = displayAttachment;
      this.showIcon = showIcon;
   }

   public List<Identifier> getAttachments() {
      return this.attachments;
   }

   @Nullable
   public Identifier getDefaultAttachment() {
      return this.defaultAttachment;
   }

   @Nullable
   public Identifier getDisplayAttachment() {
      return this.displayAttachment;
   }

   public boolean isShowIcon() {
      return this.showIcon;
   }
}
