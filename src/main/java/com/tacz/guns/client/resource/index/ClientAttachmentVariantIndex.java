package com.tacz.guns.client.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetLoadDispatcher;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.display.attachment.ScopeLensConfig;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.resource.index.CommonAttachmentVariantIndex;
import com.tacz.guns.resource.pojo.AttachmentVariantIndexPOJO;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class ClientAttachmentVariantIndex {
   private final Object modelLoadLock = new Object();
   private Component name;
   private AttachmentDisplay display;
   @Nullable
   private BedrockAttachmentModel attachmentModel;
   @Nullable
   private Identifier modelTexture;
   private float[] viewsFov;
   @Nullable
   private float[] zoom;
   private int[] views;
   private boolean scope;
   private boolean sight;
   private String mode;
   @Nullable
   private Vector3f mountOffset;
   private volatile boolean modelsLoaded = false;
   private volatile boolean modelsLoadFailed = false;
   private volatile CompletableFuture<Void> warmUpTask = null;

    /** SBM 澶栭儴妯″瀷璧勪骇缁勶紙1025 delta锛夈€?*/
    private final com.tacz.guns.client.resource.ExternalModelAssets externalModels = new com.tacz.guns.client.resource.ExternalModelAssets();
    private final com.tacz.guns.client.resource.ClientAssetLoadTask<com.tacz.guns.client.model.ExternalGunModel> externalModel = this.externalModels
            .add(
                    () -> this.display.getModel(),
                    this::bakeExternalModel,
                    failure -> com.tacz.guns.GunMod.LOGGER.warn("Failed to bake SBM external model {}", this.display.getModel(), failure)
            );

    @Nullable
    public com.tacz.guns.client.model.ExternalGunModel getExternalModel() {
        return this.externalModel.getIfReady();
    }

    public com.tacz.guns.client.resource.ExternalModelAssets getExternalModels() {
        return this.externalModels;
    }

    @Nullable
    private com.tacz.guns.client.model.ExternalGunModel bakeExternalModel() {
        if (this.modelsLoadFailed) {
            return null;
        }
        try {
            this.loadModelsIfNecessary();
        } catch (RuntimeException var3) {
            this.handleModelsLoadFailure(var3);
            return null;
        }
        if (this.attachmentModel != null && this.display.getModel() != null) {
            com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO source =
                    com.tacz.guns.client.resource.ClientAssetsManager.INSTANCE.getBedrockModelPOJO(this.display.getModel());
            if (source == null) {
                return null;
            }
            java.util.Set<String> hiddenTextBones = this.display.getTextShows() == null
                    ? java.util.Set.of() : java.util.Set.copyOf(this.display.getTextShows().keySet());
            return com.tacz.guns.client.model.ExternalGunModel.bake(source, hiddenTextBones, com.tacz.guns.client.model.DisplayGunModelInstance.Kind.ATTACHMENT);
        }
        return null;
    }

   private ClientAttachmentVariantIndex() {
   }

   public static ClientAttachmentVariantIndex getInstance(CommonAttachmentVariantIndex commonIndex) {
      ClientAttachmentVariantIndex index = new ClientAttachmentVariantIndex();
      AttachmentVariantIndexPOJO pojo = commonIndex.getPojo();
      checkIndex(pojo, index);
      checkDisplay(pojo, index);
      return index;
   }

   private static void checkIndex(AttachmentVariantIndexPOJO pojo, ClientAttachmentVariantIndex index) {
      Preconditions.checkArgument(pojo != null, "index object file is empty");
      index.name = pojo.getName() == null ? Component.empty() : Component.translatable(pojo.getName());
   }

   private static void checkDisplay(AttachmentVariantIndexPOJO pojo, ClientAttachmentVariantIndex index) {
      Identifier displayId = pojo.getDisplay();
      Preconditions.checkArgument(displayId != null, "index object missing display field");
      AttachmentDisplay display = ClientAssetsManager.INSTANCE.getAttachmentVariantDisplay(displayId);
      Preconditions.checkArgument(display != null, "there is no corresponding display file");
      index.display = display;
      index.viewsFov = display.getViewsFov();
      if (index.viewsFov == null) {
         Preconditions.checkArgument(display.getFov() > 0.0F, "fov must > 0");
         index.viewsFov = new float[]{display.getFov()};
      }

      index.zoom = display.getZoom();
      index.views = display.getViews() == null ? new int[]{1} : display.getViews();
      index.scope = display.isScope();
      index.sight = display.isSight();
      index.mode = display.getVariantMode();
      index.mountOffset = display.getMountOffset();
   }

   public void warmUp() {
      if (!this.modelsLoaded && !this.modelsLoadFailed && this.warmUpTask == null) {
         synchronized (this.modelLoadLock) {
            if (!this.modelsLoaded && !this.modelsLoadFailed && this.warmUpTask == null) {
               this.warmUpTask = CompletableFuture.runAsync(this::loadModelsIfNecessary, ClientAssetLoadDispatcher.executor());
               this.warmUpTask.whenComplete((unused, throwable) -> {
                  if (throwable != null) {
                     this.handleModelsLoadFailure(throwable);
                  }
               });
            }
         }
      }
   }

   private void ensureModelsLoaded() {
      if (!this.modelsLoaded && !this.modelsLoadFailed) {
         CompletableFuture<Void> task = this.warmUpTask;
         if (task == null) {
            try {
               this.loadModelsIfNecessary();
            } catch (Throwable var3) {
               this.handleModelsLoadFailure(var3);
            }
         } else {
            try {
               task.join();
            } catch (CompletionException var4) {
               this.handleModelsLoadFailure((Throwable)(var4.getCause() == null ? var4 : var4.getCause()));
            }
         }
      }
   }

   private void loadModelsIfNecessary() {
      if (!this.modelsLoaded) {
         synchronized (this.modelLoadLock) {
            if (!this.modelsLoaded) {
               this.attachmentModel = ClientAttachmentIndex.getOrLoadAttachmentModel(this.display.getModel());
               if (this.attachmentModel != null) {
                  this.attachmentModel.setIsScope(this.display.isScope());
                  this.attachmentModel.setIsSight(this.display.isSight());
                  this.attachmentModel.setScopeLens(this.display.getScopeLens());
               }

               this.modelTexture = this.display.getTexture();
               this.modelsLoaded = true;
            }
         }
      }
   }

   private void handleModelsLoadFailure(Throwable throwable) {
      boolean shouldLog = false;
      synchronized (this.modelLoadLock) {
         if (!this.modelsLoaded) {
            shouldLog = !this.modelsLoadFailed;
            this.warmUpTask = null;
            this.modelsLoadFailed = true;
         }
      }

      if (shouldLog) {
         GunMod.LOGGER.warn("Failed to load attachment variant model {}", this.display.getModel(), throwable);
      }
   }

   public Component getName() {
      return this.name;
   }

   @Nullable
   public BedrockAttachmentModel getAttachmentModel() {
      this.ensureModelsLoaded();
      return this.attachmentModel;
   }

   @Nullable
   public Identifier getModelTexture() {
      this.ensureModelsLoaded();
      return this.modelTexture;
   }

   public float[] getViewsFov() {
      return this.viewsFov;
   }

   @Nullable
   public float[] getZoom() {
      return this.zoom;
   }

   public int[] getViews() {
      return this.views;
   }

   public boolean isScope() {
      return this.scope;
   }

   public ScopeLensConfig getScopeLens() {
      return this.display.getScopeLens();
   }

   public boolean isSight() {
      return this.sight;
   }

   @Nullable
   public Vector3f getMountOffset() {
      return this.mountOffset;
   }

   public boolean isReplaceMode() {
      return !"overlay".equalsIgnoreCase(this.mode);
   }

   public boolean isOverlayMode() {
      return "overlay".equalsIgnoreCase(this.mode);
   }
}
