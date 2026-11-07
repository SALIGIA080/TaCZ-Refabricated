package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.client.renderer.item.ItemPreviewRenderContext;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.config.util.GunPegboardItemConfigRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GunPegboardRenderer implements BlockEntityRenderer<GunPegboardBlockEntity, GunPegboardRenderer.PegboardRenderState> {
   private static final int SLOT_COUNT = 16;
   private static final float PREVIEW_ALPHA = 0.65F;

   public GunPegboardRenderer(Context context) {
   }

   public static class PegboardRenderState extends BlockEntityRenderState {
      public Direction facing = Direction.NORTH;
      public boolean glowing;
      public final ItemStackRenderState[] slots = new ItemStackRenderState[SLOT_COUNT];
      public final int[] rotations = new int[SLOT_COUNT];
      public final boolean[] hasItem = new boolean[SLOT_COUNT];
      // 放置预览
      public boolean hasPreview;
      public int previewSlot = -1;
      public final ItemStackRenderState previewItem = new ItemStackRenderState();

      public PegboardRenderState() {
         for (int i = 0; i < SLOT_COUNT; i++) {
            this.slots[i] = new ItemStackRenderState();
         }
      }
   }

   @Override
   public PegboardRenderState createRenderState() {
      return new PegboardRenderState();
   }

   @Override
   public void extractRenderState(GunPegboardBlockEntity blockEntity, PegboardRenderState state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
      BlockEntityRenderState.extractBase(blockEntity, state, crumblingOverlay);
      state.facing = blockEntity.getBlockState().getValue(GunPegboardBlock.FACING);
      state.glowing = blockEntity.getBlockState().getBlock() instanceof GunPegboardBlock pegboard && pegboard.isGlowing();

      ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
      Level level = blockEntity.getLevel();

      for (int slot = 0; slot < SLOT_COUNT; slot++) {
         ItemStack stack = blockEntity.getItem(slot);
         state.slots[slot].clear();
         state.hasItem[slot] = !stack.isEmpty();
         state.rotations[slot] = blockEntity.getRotation(slot);
         if (!stack.isEmpty() && resolver != null && level != null) {
            resolver.updateForTopItem(state.slots[slot], stack, ItemDisplayContext.FIXED, level, null, 0);
         }
      }

      // 放置预览
      state.hasPreview = false;
      state.previewSlot = -1;
      state.previewItem.clear();
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player != null && level != null && resolver != null && !player.isShiftKeyDown()
            && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
         BlockPos pos = blockEntity.getBlockPos();
         if (hit.getBlockPos().equals(pos)) {
            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!held.isEmpty() && GunPegboardItemConfigRead.canPlace(held)) {
               int slot = GunPegboardSlotHelper.getSlot(blockEntity.getBlockState(), pos, hit.getLocation());
               if (slot >= 0 && blockEntity.getItem(slot).isEmpty()) {
                  ItemStack preview = held.copy();
                  preview.setCount(1);
                  resolver.updateForTopItem(state.previewItem, preview, ItemDisplayContext.FIXED, level, null, 0);
                  state.hasPreview = true;
                  state.previewSlot = slot;
               }
            }
         }
      }
   }

   @Override
   public void submit(PegboardRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
      int itemLight = state.glowing ? 15728880 /* full bright */ : state.lightCoords;

      for (int slot = 0; slot < SLOT_COUNT; slot++) {
         if (!state.hasItem[slot]) {
            continue;
         }
         int row = slot / 4;
         int col = slot % 4;
         double x = (col + 0.5) / 4.0 - 0.5;
         double y = (row + 0.5) / 4.0 - 0.5;
         poseStack.pushPose();
         DisplayItemTransforms.apply(poseStack, state.facing, x, y, 0.25, state.rotations[slot]);
         state.slots[slot].submit(poseStack, collector, itemLight, OverlayTexture.NO_OVERLAY, 0);
         poseStack.popPose();
      }

      if (state.hasPreview) {
         int row = state.previewSlot / 4;
         int col = state.previewSlot % 4;
         double x = (col + 0.5) / 4.0 - 0.5;
         double y = (row + 0.5) / 4.0 - 0.5;
         poseStack.pushPose();
         DisplayItemTransforms.apply(poseStack, state.facing, x, y, 0.25, 0);
         ItemPreviewRenderContext.setAlpha(PREVIEW_ALPHA);
         state.previewItem.submit(poseStack, collector, itemLight, OverlayTexture.NO_OVERLAY, 0);
         ItemPreviewRenderContext.setAlpha(1.0F);
         poseStack.popPose();
      }
   }

   @Override
   public int getViewDistance() {
      return RenderConfig.TARGET_RENDER_DISTANCE.get();
   }

   @Override
   public boolean shouldRenderOffScreen() {
      return true;
   }

   @Override
   public boolean shouldRender(GunPegboardBlockEntity blockEntity, Vec3 cameraPos) {
      return Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPos, this.getViewDistance());
   }
}
