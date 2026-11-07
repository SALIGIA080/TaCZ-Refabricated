package com.tacz.guns.client.renderer.external;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.ExternalGunModel;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentVariantIndex;
import com.tacz.guns.client.resource.index.ClientSlotAdapterIndex;
import com.tacz.guns.util.SlotAdapterHelper;
import com.tacz.guns.util.VariantHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

public class ExternalGunAppearance {
   private ExternalGunAppearance() {
   }

   public static ExternalGunAppearance.Resolution resolve(ItemStack source) {
      return resolve(source, true);
   }

   public static ExternalGunAppearance.Resolution resolve(ItemStack source, boolean copyStack) {
      if (source.isEmpty()) {
         return ExternalGunAppearance.Resolution.UNSUPPORTED;
      } else {
         IGun gun = IGun.getIGunOrNull(source);
         if (gun == null) {
            return ExternalGunAppearance.Resolution.UNSUPPORTED;
         } else {
            GunDisplayInstance display = TimelessAPI.getGunDisplay(source).orElse(null);
            if (display == null) {
               return ExternalGunAppearance.Resolution.UNSUPPORTED;
            } else {
               List<ExternalGunAppearance.AttachmentModels> dependencies = attachmentModels(gun, source);
               if (dependencies == null) {
                  return ExternalGunAppearance.Resolution.UNSUPPORTED;
               } else {
                  boolean prepared = display.getExternalModels().prepare();

                  for (ExternalGunAppearance.AttachmentModels dependency : dependencies) {
                     prepared &= dependency.prepare();
                  }

                  if (!prepared) {
                     return ExternalGunAppearance.Resolution.PENDING;
                  } else {
                     ExternalGunModel model = display.getExternalGunModel();
                     if (model == null) {
                        return ExternalGunAppearance.Resolution.UNSUPPORTED;
                     } else {
                        Identifier texture = display.getModelTexture();
                        Pair<ExternalGunModel, Identifier> lod = display.getExternalLodModel();
                        if (texture != null && (display.getLodModel() == null || lod != null)) {
                           ItemStack stack = copyStack ? source.copy() : source;
                           List<ExternalGunAppearance.AttachmentKey> attachments = new ArrayList<>();
                           boolean lodAffectsGeometry = lod != null;

                           for (ExternalGunAppearance.AttachmentModels dependency : dependencies) {
                              if (!dependency.supported()) {
                                 return ExternalGunAppearance.Resolution.UNSUPPORTED;
                              }

                              lodAffectsGeometry |= dependency.index().getLodModel() != null;
                              attachments.add(dependency.key());
                           }

                           long revision = ClientIndexManager.resourceRevision();
                           List<ExternalGunAppearance.AttachmentKey> attachmentKeys = List.copyOf(attachments);
                           boolean bullet = gun.hasBulletInBarrel(stack);
                           boolean ammo = gun.getCurrentAmmoCount(stack) > 0;
                           ExternalGunAppearance.GeometryKey nearKey = new ExternalGunAppearance.GeometryKey(
                              revision, display.getDisplayId(), attachmentKeys, bullet, ammo, false
                           );
                           ExternalGunAppearance.Snapshot near = new ExternalGunAppearance.Snapshot(nearKey, stack, display, model, texture, false);
                           ExternalGunAppearance.Snapshot far = near;
                           if (lodAffectsGeometry) {
                              ExternalGunModel farModel = lod == null ? model : (ExternalGunModel)lod.getLeft();
                              Identifier farTexture = lod == null ? texture : (Identifier)lod.getRight();
                              if (farModel == null || farTexture == null) {
                                 return ExternalGunAppearance.Resolution.UNSUPPORTED;
                              }

                              ExternalGunAppearance.GeometryKey farKey = new ExternalGunAppearance.GeometryKey(
                                 revision, display.getDisplayId(), attachmentKeys, bullet, ammo, true
                              );
                              far = new ExternalGunAppearance.Snapshot(farKey, stack, display, farModel, farTexture, true);
                           }

                           return new ExternalGunAppearance.Resolution(ExternalGunAppearance.Status.READY, new ExternalGunAppearance.Variants(near, far));
                        } else {
                           return ExternalGunAppearance.Resolution.UNSUPPORTED;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Nullable
   public static List<ExternalGunAppearance.AttachmentModels> attachmentModels(IGun gun, ItemStack stack) {
      List<ExternalGunAppearance.AttachmentModels> models = new ArrayList<>();

      for (AttachmentType type : AttachmentType.values()) {
         if (type != AttachmentType.NONE) {
            ItemStack attached = gun.getAttachment(stack, type);
            if (attached.isEmpty()) {
               attached = gun.getBuiltinAttachment(stack, type);
            }

            IAttachment item = IAttachment.getIAttachmentOrNull(attached);
            if (item != null) {
               Identifier id = item.getAttachmentId(attached);
               ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
               if (index == null) {
                  return null;
               }

               ClientAttachmentVariantIndex variant = TimelessAPI.getClientAttachmentVariantIndex(attached).orElse(null);
               Identifier variantId = variant == null ? null : VariantHelper.getVariant(attached);
               Identifier selectedAdapter = SlotAdapterHelper.getEffectiveSlotAdapter(stack, type, id);
               ClientSlotAdapterIndex adapter = selectedAdapter == null ? null : TimelessAPI.getClientSlotAdapterIndex(selectedAdapter).orElse(null);
               ExternalGunAppearance.AttachmentKey key = new ExternalGunAppearance.AttachmentKey(type, id, variantId, adapter == null ? null : selectedAdapter);
               models.add(new ExternalGunAppearance.AttachmentModels(key, index, variant, adapter));
            }
         }
      }

      return models;
   }

   public record AttachmentKey(AttachmentType type, Identifier id, @Nullable Identifier variantId, @Nullable Identifier slotAdapterId) {
   }

   public record AttachmentModels(
      ExternalGunAppearance.AttachmentKey key,
      ClientAttachmentIndex index,
      @Nullable ClientAttachmentVariantIndex variant,
      @Nullable ClientSlotAdapterIndex adapter
   ) {
      public boolean prepare() {
         boolean prepared = this.index.getExternalModels().prepare();
         if (this.variant != null) {
            prepared &= this.variant.getExternalModels().prepare();
         }

         if (this.adapter != null) {
            prepared &= this.adapter.getExternalModels().prepare();
         }

         return prepared;
      }

      public boolean supported() {
         boolean replacesBase = this.variant != null
            && this.variant.isReplaceMode()
            && this.variant.getAttachmentModel() != null
            && this.variant.getModelTexture() != null;
         if (!replacesBase && this.index.getAttachmentModel() != null && this.index.getModelTexture() != null && this.index.getExternalModel() == null) {
            return false;
         } else if (this.variant != null
            && this.variant.getAttachmentModel() != null
            && this.variant.getModelTexture() != null
            && this.variant.getExternalModel() == null) {
            return false;
         } else {
            return this.index.getLodModel() != null && this.index.getExternalLodModel() == null
               ? false
               : this.adapter == null
                  || this.adapter.getAdapterModel() == null
                  || this.adapter.getModelTexture() == null
                  || this.adapter.getExternalModel() != null;
         }
      }
   }

   public record GeometryKey(
      long resourceRevision, Identifier displayId, List<ExternalGunAppearance.AttachmentKey> attachments, boolean bullet, boolean ammo, boolean useLod
   ) {
   }

   public record Resolution(ExternalGunAppearance.Status status, @Nullable ExternalGunAppearance.Variants variants) {
      public static final ExternalGunAppearance.Resolution PENDING = new ExternalGunAppearance.Resolution(ExternalGunAppearance.Status.PENDING, null);
      public static final ExternalGunAppearance.Resolution UNSUPPORTED = new ExternalGunAppearance.Resolution(ExternalGunAppearance.Status.UNSUPPORTED, null);

      public boolean pending() {
         return this.status == ExternalGunAppearance.Status.PENDING;
      }
   }

   public record Snapshot(
      ExternalGunAppearance.GeometryKey key, ItemStack stack, GunDisplayInstance display, ExternalGunModel model, Identifier texture, boolean useLod
   ) {
   }

   public static enum Status {
      READY,
      PENDING,
      UNSUPPORTED;
   }

   public record Variants(ExternalGunAppearance.Snapshot near, ExternalGunAppearance.Snapshot far) {
      public ExternalGunAppearance.Snapshot select(boolean useLod) {
         return useLod ? this.far : this.near;
      }
   }
}