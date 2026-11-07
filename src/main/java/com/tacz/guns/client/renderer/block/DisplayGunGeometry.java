package com.tacz.guns.client.renderer.block;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.PrimitiveTopology;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.DisplayGunModelInstance;
import com.tacz.guns.client.model.ExternalGunModel;
import com.tacz.guns.client.renderer.external.ExternalGunAppearance;
import com.tacz.guns.client.renderer.item.SbmGunTransforms;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientSlotAdapterIndex;
import com.tacz.guns.client.resource.pojo.TransformScale;
import com.tacz.guns.util.SlotAdapterHelper;
import java.util.EnumMap;
import java.util.Map.Entry;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3f;

public class DisplayGunGeometry {
   private DisplayGunGeometry() {
   }

   public static EnumMap<AttachmentType, ClientAttachmentIndex> resolveAttachments(IGun gun, ItemStack stack) {
      EnumMap<AttachmentType, ClientAttachmentIndex> indexes = new EnumMap<>(AttachmentType.class);

      for (AttachmentType type : AttachmentType.values()) {
         if (type != AttachmentType.NONE) {
            ItemStack attachment = effectiveAttachment(gun, stack, type);
            if (!attachment.isEmpty()) {
               IAttachment item = IAttachment.getIAttachmentOrNull(attachment);
               if (item != null) {
                  Identifier id = item.getAttachmentId(attachment);
                  ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
                  if (index == null) {
                     return null;
                  }

                  indexes.put(type, index);
               }
            }
         }
      }

      return indexes;
   }

   public static ItemStack effectiveAttachment(IGun gun, ItemStack stack, AttachmentType type) {
      ItemStack attached = gun.getAttachment(stack, type);
      return attached.isEmpty() ? gun.getBuiltinAttachment(stack, type) : attached;
   }

   public static boolean capture(ExternalGunAppearance.Snapshot snapshot, ItemDisplayContext context, GeometryCollector collector) {
      return capture(snapshot, context, false, collector);
   }

   public static boolean capture(
      ExternalGunAppearance.Snapshot snapshot, ItemDisplayContext context, boolean compensateItemRenderer, GeometryCollector collector
   ) {
      ItemStack stack = snapshot.stack();
      IGun gun = IGun.getIGunOrNull(stack);
      if (gun == null) {
         return false;
      } else {
         EnumMap<AttachmentType, ClientAttachmentIndex> attachments = resolveAttachments(gun, stack);
         if (attachments == null) {
            return false;
         } else {
            ExternalGunModel external = snapshot.model();
            DisplayGunModelInstance instance = external.displayInstance();
            instance.configureGun(gun, stack, attachments);
            PoseStack pose = new PoseStack();
            if (!applyTransform(snapshot, context, compensateItemRenderer, pose)) {
               return false;
            } else {
               renderModel(external, instance, pose, snapshot.texture(), collector);
               renderAttachments(instance, pose, snapshot.useLod(), gun, stack, attachments, collector);
               return true;
            }
         }
      }
   }

   public static boolean applyTransform(ExternalGunAppearance.Snapshot snapshot, ItemDisplayContext context, boolean compensateItemRenderer, PoseStack pose) {
      ExternalGunModel external = snapshot.model();
      TransformScale scales = snapshot.display().getTransform().getScale();
      switch (context) {
         case FIXED:
            if (compensateItemRenderer) {
               pose.translate(0.5, 0.5, 0.5);
            }

            SbmGunTransforms.applyFixed(pose, external, scales == null ? null : scales.getFixed());
            break;
         case GROUND:
            pose.translate(0.5, 0.5, 0.5);
            SbmGunTransforms.applyGround(pose, external, scales == null ? null : scales.getGround());
            break;
         case THIRD_PERSON_RIGHT_HAND:
            pose.translate(0.5, 0.5, 0.5);
            SbmGunTransforms.applyThirdPerson(pose, external, scales == null ? null : scales.getThirdPerson());
            break;
         default:
            return false;
      }

      return true;
   }

   public static void renderAttachments(
      DisplayGunModelInstance instance,
      PoseStack pose,
      boolean useLod,
      IGun gun,
      ItemStack gunStack,
      EnumMap<AttachmentType, ClientAttachmentIndex> attachments,
      GeometryCollector collector
   ) {
      forEachAttachmentModel(
         instance,
         pose,
         useLod,
         gun,
         gunStack,
         attachments,
         (model, texture, stack, modelPose) -> renderModel(model, model.displayInstance(), modelPose, texture, collector)
      );
   }

   public static void forEachAttachmentModel(
      DisplayGunModelInstance instance,
      PoseStack pose,
      boolean useLod,
      IGun gun,
      ItemStack gunStack,
      EnumMap<AttachmentType, ClientAttachmentIndex> attachments,
      DisplayGunGeometry.AttachmentModelVisitor visitor
   ) {
      for (Entry<AttachmentType, ClientAttachmentIndex> entry : attachments.entrySet()) {
         AttachmentType type = entry.getKey();
         ClientAttachmentIndex index = entry.getValue();
         ItemStack attached = effectiveAttachment(gun, gunStack, type);
         IAttachment attachmentItem = IAttachment.getIAttachmentOrNull(attached);
         if (attachmentItem != null) {
            Identifier attachmentId = attachmentItem.getAttachmentId(attached);
            int position = instance.attachmentPosition(type);
            if (position >= 0) {
               PoseStack attachmentPose = new PoseStack();
               attachmentPose.last().pose().mul(pose.last().pose());
               attachmentPose.last().normal().mul(pose.last().normal());
               SbmGunTransforms.applyBonePivot(attachmentPose, instance, position);
               Identifier adapterId = SlotAdapterHelper.getEffectiveSlotAdapter(gunStack, type, attachmentId);
               ClientSlotAdapterIndex adapter = adapterId == null ? null : TimelessAPI.getClientSlotAdapterIndex(adapterId).orElse(null);
               if (adapter != null) {
                  visitOptionalModel(adapter.getExternalModel(), adapter.getModelTexture(), ItemStack.EMPTY, attachmentPose, visitor);
                  applyMountOffset(attachmentPose, adapter.getMountOffset());
               }

               ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(attached).orElse(null);
               if (variant != null && variant.isOverlayMode()) {
                  visitOptionalModel(variant.getExternalModel(), variant.getModelTexture(), attached, attachmentPose, visitor);
                  applyMountOffset(attachmentPose, variant.getMountOffset());
               }

               ExternalGunModel attachment = index.getExternalModel();
               Identifier attachmentTexture = index.getModelTexture();
               if (variant != null && variant.isReplaceMode() && variant.getAttachmentModel() != null && variant.getModelTexture() != null) {
                  attachment = variant.getExternalModel();
                  attachmentTexture = variant.getModelTexture();
               }

               Pair<ExternalGunModel, Identifier> lod = index.getExternalLodModel();
               if (useLod && lod != null) {
                  attachment = (ExternalGunModel)lod.getLeft();
                  attachmentTexture = (Identifier)lod.getRight();
               }

               visitOptionalModel(attachment, attachmentTexture, attached, attachmentPose, visitor);
            }
         }
      }
   }

   private static void visitOptionalModel(
      ExternalGunModel model, Identifier texture, ItemStack stack, PoseStack pose, DisplayGunGeometry.AttachmentModelVisitor visitor
   ) {
      if (model != null && texture != null) {
         visitor.accept(model, texture, stack, pose);
      }
   }

   private static void applyMountOffset(PoseStack pose, Vector3f offset) {
      if (offset != null) {
         pose.translate(-offset.x() / 16.0F, offset.y() / 16.0F, offset.z() / 16.0F);
      }
   }

   public static void renderModel(
      ExternalGunModel external, DisplayGunModelInstance instance, PoseStack pose, Identifier texture, GeometryCollector collector
   ) {
      VertexConsumer buffer = collector.buffer(RenderTypes.entityCutout(texture), DisplayGunRenderType.emissive(texture), PrimitiveTopology.QUADS);
      external.model().renderBoneTree(instance, pose, buffer, 0, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F, true, true);
   }

   @FunctionalInterface
   public interface AttachmentModelVisitor {
      void accept(ExternalGunModel var1, Identifier var2, ItemStack var3, PoseStack var4);
   }
}