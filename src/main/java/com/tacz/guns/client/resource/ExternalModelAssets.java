package com.tacz.guns.client.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

public final class ExternalModelAssets {
   private final List<ExternalModelAssets.Model> models = new ArrayList<>();

   public <T> ClientAssetLoadTask<T> add(Supplier<Identifier> resourceId, Supplier<T> loader, Consumer<Throwable> failed) {
      ClientAssetLoadTask<T> task = ClientAssetLoadDispatcher.task(loader, failed);
      this.models.add(new ExternalModelAssets.Model(resourceId, task));
      return task;
   }

   public boolean prepare() {
      boolean pending = false;

      for (ExternalModelAssets.Model model : this.models) {
         model.task().warmUp();
         pending |= model.task().isPending();
      }

      return !pending;
   }

   public CompletableFuture<Void> preload() {
      List<CompletableFuture<?>> tasks = new ArrayList<>();

      for (ExternalModelAssets.Model model : this.models) {
         tasks.add(ClientAssetLoadDispatcher.preload(model.task(), model.resourceId().get()));
      }

      return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
   }

   private record Model(Supplier<Identifier> resourceId, ClientAssetLoadTask<?> task) {
   }
}
