package com.tacz.guns.client.renderer.block;

import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.cache.MeshCachePolicy;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.capture.GeometryCollector;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.MeshRenderableAdapter;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshGroup;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.client.mesh.world.WorldMeshStrategy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.DisplayGunSlot;
import com.tacz.guns.block.entity.GunDisplayBlockEntity;
import com.tacz.guns.client.renderer.external.ExternalGunAppearance;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.config.client.RenderConfig;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class DisplayGunMeshGroup {
   /** 26.2：LightTexture 已移除，pack(15,15) 即满亮光照 0xF000F0。 */
   private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
   private static final double ITEM_DEPTH = 0.25;
   private static WorldMeshGroup<DisplayGunSlot> group;
   private static boolean configWasEnabled;
   private static final Map<DisplayGunSlot, DisplayGunMeshGroup.CachedAppearance> APPEARANCES = new WeakHashMap<>();
   public static final MeshCachePolicy TTL_300 = new MeshCachePolicy(Duration.ofSeconds(300L), 128, 536870912L);
   private static final MeshRenderableAdapter<DisplayGunSlot> ADAPTER = new MeshRenderableAdapter<DisplayGunSlot>() {
      public boolean isValid(DisplayGunSlot slot) {
         GunDisplayBlockEntity owner = slot.owner();
         BlockEntity blockEntity = owner.getBlockEntity();
         ItemStack stack = owner.getItem(slot.index());
         boolean valid = !blockEntity.isRemoved()
            && blockEntity.getLevel() == Minecraft.getInstance().level
            && !stack.isEmpty()
            && IGun.getIGunOrNull(stack) != null;
         if (!valid) {
            DisplayGunMeshGroup.APPEARANCES.remove(slot);
         }

         return valid;
      }

      public boolean isVisible(DisplayGunSlot slot) {
         return DisplayGunMeshGroup.snapshot(slot) != null;
      }

      public Vec3 origin(DisplayGunSlot slot) {
         return Vec3.atLowerCornerOf(slot.owner().getBlockEntity().getBlockPos());
      }

      public Matrix4f localTransform(DisplayGunSlot slot) {
         GunDisplayBlockEntity owner = slot.owner();
         int index = slot.index();
         int gridSize = owner.getGridSize();
         int localIndex = index % (gridSize * gridSize);
         double x = (localIndex % gridSize + 0.5) / gridSize - 0.5;
         double y = (localIndex / gridSize + 0.5) / gridSize - 0.5;
         PoseStack pose = new PoseStack();
         DisplayItemTransforms.apply(pose, owner.getSlotFacing(index), x, y, 0.25, owner.getRotation(index));
         return new Matrix4f(pose.last().pose());
      }

      public int packedLight(DisplayGunSlot slot) {
         GunDisplayBlockEntity owner = slot.owner();
         if (owner.isDisplayGlowing()) {
            return FULL_BRIGHT_LIGHT;
         } else {
            BlockEntity blockEntity = owner.getBlockEntity();
            // 26.2：LevelRenderer.getLightColor 已移除，按经典 pack 公式 (sky<<20)|(block<<4) 现算。
            int block = blockEntity.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, blockEntity.getBlockPos());
            int sky = blockEntity.getLevel().getBrightness(net.minecraft.world.level.LightLayer.SKY, blockEntity.getBlockPos());
            return (sky << 20) | (block << 4);
         }
      }

      public Object geometryKey(DisplayGunSlot slot) {
         ExternalGunAppearance.Snapshot current = DisplayGunMeshGroup.snapshot(slot);
         return current == null ? slot : current.key();
      }

      public boolean collectGeometry(DisplayGunSlot slot, GeometryCollector collector) {
         ExternalGunAppearance.Snapshot current = DisplayGunMeshGroup.snapshot(slot);
         return current != null && DisplayGunGeometry.capture(current, ItemDisplayContext.FIXED, collector);
      }
   };

   private DisplayGunMeshGroup() {
   }

   public static boolean tryRender(GunDisplayBlockEntity owner, int index, int overlay) {
      if (!(Boolean)RenderConfig.DISPLAY_GUN_VBO.get()) {
         if (configWasEnabled) {
            if (group != null) {
               group.clear();
            }

            APPEARANCES.clear();
         }

         configWasEnabled = false;
         return false;
      } else {
         configWasEnabled = true;
         if (overlay == OverlayTexture.NO_OVERLAY && owner.getBlockEntity().getLevel() == Minecraft.getInstance().level) {
            ItemStack stack = owner.getItem(index);
            if (!stack.isEmpty() && IGun.getIGunOrNull(stack) != null) {
               DisplayGunSlot slot = owner.getDisplaySlot(index);
               ExternalGunAppearance.Snapshot current = snapshot(slot);
               DisplayGunMeshGroup.CachedAppearance cached = APPEARANCES.get(slot);
               if (current != null) {
                  if (group == null) {
                     group = WorldMeshRenderer.createGroup("tacz:display_guns", WorldMeshStrategy.INSTANCE, Set.of(WorldMeshStrategy.INSTANCE), TTL_300);
                  }

                  group.track(slot, ADAPTER);
                  if (slot.consumeChange()) {
                     group.markDirty(slot);
                  }

                  ExternalGunAppearance.Snapshot previous = cached.resolution().variants().select(!current.useLod());
                  group.claimForWorldDraw(slot, current.key(), previous.key());
                  return true;
               } else {
                  return cached != null && cached.resolution().pending();
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   public static ExternalGunAppearance.Snapshot snapshot(DisplayGunSlot slot) {
      GunDisplayBlockEntity owner = slot.owner();
      BlockEntity blockEntity = owner.getBlockEntity();
      if (blockEntity.isRemoved()) {
         return null;
      } else {
         long appearanceRevision = slot.appearanceRevision();
         long resourceRevision = ClientIndexManager.resourceRevision();
         DisplayGunMeshGroup.CachedAppearance cached = APPEARANCES.get(slot);
         if (cached == null
            || cached.appearanceRevision() != appearanceRevision
            || cached.resourceRevision() != resourceRevision
            || cached.resolution().pending()) {
            cached = new DisplayGunMeshGroup.CachedAppearance(appearanceRevision, resourceRevision, ExternalGunAppearance.resolve(owner.getItem(slot.index())));
            APPEARANCES.put(slot, cached);
         }

         ExternalGunAppearance.Variants variants = cached.resolution().variants();
         if (variants == null) {
            return null;
         } else if (variants.near() == variants.far()) {
            return variants.near();
         } else {
            int range = (Integer)RenderConfig.GUN_LOD_RENDER_DISTANCE.get();
            boolean useLod = range == 0 || blockEntity.getBlockPos().distToCenterSqr(Minecraft.getInstance().gameRenderer.mainCamera().position()) >= (double)range * range;
            return variants.select(useLod);
         }
      }
   }

   private record CachedAppearance(long appearanceRevision, long resourceRevision, ExternalGunAppearance.Resolution resolution) {
   }
}