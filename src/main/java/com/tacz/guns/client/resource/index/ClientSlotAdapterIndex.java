package com.tacz.guns.client.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetLoadDispatcher;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.attachment.AttachmentDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;
import com.tacz.guns.resource.pojo.SlotAdapterIndexPOJO;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class ClientSlotAdapterIndex {
   private final Object modelLoadLock = new Object();
   private Component name;
   private AttachmentDisplay display;
   @Nullable
   private BedrockAttachmentModel adapterModel;
   @Nullable
   private Identifier modelTexture;
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
        if (this.adapterModel != null && this.display.getModel() != null) {
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

   private ClientSlotAdapterIndex() {
   }

   public static ClientSlotAdapterIndex getInstance(CommonSlotAdapterIndex commonIndex) {
      ClientSlotAdapterIndex index = new ClientSlotAdapterIndex();
      SlotAdapterIndexPOJO pojo = commonIndex.getPojo();
      checkIndex(pojo, index);
      checkDisplay(pojo, index);
      return index;
   }

   private static void checkIndex(SlotAdapterIndexPOJO pojo, ClientSlotAdapterIndex index) {
      Preconditions.checkArgument(pojo != null, "index object file is empty");
      index.name = pojo.getName() == null ? Component.empty() : Component.translatable(pojo.getName());
   }

   private static void checkDisplay(SlotAdapterIndexPOJO pojo, ClientSlotAdapterIndex index) {
      Identifier displayId = pojo.getDisplay();
      Preconditions.checkArgument(displayId != null, "index object missing display field");
      AttachmentDisplay display = ClientAssetsManager.INSTANCE.getSlotAdapterDisplay(displayId);
      Preconditions.checkArgument(display != null, "there is no corresponding display file");
      index.display = display;
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
               this.adapterModel = ClientAttachmentIndex.getOrLoadAttachmentModel(this.display.getModel());
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
         GunMod.LOGGER.warn("Failed to load slot adapter model {}", this.display.getModel(), throwable);
      }
   }

   public Component getName() {
      return this.name;
   }

   @Nullable
   public BedrockAttachmentModel getAdapterModel() {
      this.ensureModelsLoaded();
      return this.adapterModel;
   }

   @Nullable
   public Identifier getModelTexture() {
      this.ensureModelsLoaded();
      return this.modelTexture;
   }

   @Nullable
   public Vector3f getMountOffset() {
      return this.mountOffset;
   }
}
