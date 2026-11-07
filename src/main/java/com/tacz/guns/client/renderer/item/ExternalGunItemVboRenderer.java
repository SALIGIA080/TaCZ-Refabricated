package com.tacz.guns.client.renderer.item;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.MeshDrawResult;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.StaticMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.block.DisplayGunGeometry;
import com.tacz.guns.client.renderer.external.ExternalGunAppearance;
import com.tacz.guns.client.renderer.external.ExternalGunEffects;
import com.tacz.guns.config.client.RenderConfig;
import java.time.Duration;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ExternalGunItemVboRenderer {
   private static final Identifier OWNER = Identifier.fromNamespaceAndPath("tacz", "external_item_guns");
   private static boolean wasEnabled;
   private static final Duration IDLE_RETENTION = Duration.ofSeconds(300L);

   private ExternalGunItemVboRenderer() {
   }

   public static StaticGunRenderResult tryRender(
      ItemStack stack, ItemDisplayContext context, boolean useLod, PoseStack pose, SubmitNodeCollector buffers, int packedLight, int packedOverlay
   ) {
      if (!(Boolean)RenderConfig.STATIC_ITEM_GUN_VBO.get()) {
         if (wasEnabled) {
            StaticMeshRenderer.invalidateOwner(OWNER);
         }

         wasEnabled = false;
         return StaticGunRenderResult.UNSUPPORTED;
      } else {
         wasEnabled = true;
         if (!StaticMeshRenderer.isOwnerEnabled(OWNER)) {
            return StaticGunRenderResult.UNSUPPORTED;
         } else if (!ItemPreviewRenderContext.isPreview()
            && !DisplayItemRenderContext.isActive()
            && (
               context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                  || context == ItemDisplayContext.GROUND
                  || context == ItemDisplayContext.FIXED && !ScreenItemVboContext.isActive()
            )) {
            if (!StaticMeshRenderer.supportsInstanceAttributes(packedLight, packedOverlay)) {
               return StaticGunRenderResult.UNSUPPORTED;
            } else {
               ExternalGunAppearance.Resolution resolution = ExternalGunAppearance.resolve(stack, false);
               if (resolution.pending()) {
                  return StaticGunRenderResult.PENDING;
               } else {
                  ExternalGunAppearance.Variants appearances = resolution.variants();
                  if (appearances == null) {
                     return StaticGunRenderResult.UNSUPPORTED;
                  } else {
                     ExternalGunAppearance.Snapshot appearance = appearances.select(useLod);
                     ExternalGunAppearance.Snapshot previous = appearances.select(!useLod);
                     MeshDrawResult result = StaticMeshRenderer.tryDraw(
                        OWNER,
                        new ExternalGunItemVboRenderer.ItemGeometryKey(appearance.key(), context),
                        new ExternalGunItemVboRenderer.ItemGeometryKey(previous.key(), context),
                        collector -> DisplayGunGeometry.capture(appearance, context, true, collector),
                        pose,
                        packedLight,
                        packedOverlay,
                        IDLE_RETENTION
                     );
                     if (result.drawn()) {
                        ExternalGunAppearance.Snapshot selected = result.usedPrevious() ? previous : appearance;
                        if (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                           ExternalGunEffects.renderThirdPerson(selected, pose, buffers, packedLight, packedOverlay);
                        }

                        return new StaticGunRenderResult(StaticGunRenderResult.Status.SUBMITTED, selected.useLod());
                     } else {
                        return result == MeshDrawResult.UNSUPPORTED ? StaticGunRenderResult.UNSUPPORTED : StaticGunRenderResult.PENDING;
                     }
                  }
               }
            }
         } else {
            return StaticGunRenderResult.UNSUPPORTED;
         }
      }
   }

   private record ItemGeometryKey(ExternalGunAppearance.GeometryKey appearance, ItemDisplayContext context) {
   }
}