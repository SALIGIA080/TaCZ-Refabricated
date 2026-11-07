package com.tacz.guns.client.resource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ClientAssetLoadTask<T> {
   private final Executor executor;
   private final Supplier<T> loader;
   private final BooleanSupplier current;
   private final Consumer<Throwable> failed;
   private volatile CompletableFuture<T> task;

   ClientAssetLoadTask(Executor executor, Supplier<T> loader, BooleanSupplier current, Consumer<Throwable> failed) {
      this.executor = executor;
      this.loader = loader;
      this.current = current;
      this.failed = failed;
   }

   public CompletableFuture<T> warmUp() {
      if (!this.current.getAsBoolean()) {
         return CompletableFuture.completedFuture(null);
      } else {
         CompletableFuture<T> existing = this.task;
         if (existing != null) {
            return existing;
         } else {
            synchronized (this) {
               if (this.task == null) {
                  CompletableFuture<T> loading = CompletableFuture.supplyAsync(() -> !this.current.getAsBoolean() ? null : this.loader.get(), this.executor);
                  this.task = loading.handle((value, failure) -> {
                     if (!this.current.getAsBoolean()) {
                        return null;
                     } else if (failure != null) {
                        Throwable cause = failure.getCause();
                        this.failed.accept(cause == null ? failure : cause);
                        return null;
                     } else {
                        return (T)value;
                     }
                  });
               }

               return this.task;
            }
         }
      }
   }

   public T getIfReady() {
      CompletableFuture<T> future = this.warmUp();
      return this.current.getAsBoolean() && future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled() ? future.getNow(null) : null;
   }

   public boolean isPending() {
      CompletableFuture<T> future = this.task;
      return this.current.getAsBoolean() && (future == null || !future.isDone());
   }
}
