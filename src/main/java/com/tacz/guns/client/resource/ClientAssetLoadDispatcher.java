package com.tacz.guns.client.resource;

import com.tacz.guns.config.client.ResourceConfig;
import com.tacz.guns.util.TaczThreads;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.resources.Identifier;

/**
 * 资源后台预热器。
 *
 * <p>这个池本来就是 daemon（唯一一个），改用统一工厂只为口径一致 ——
 * 三个池各写各的 daemon 逻辑正是上一轮漏掉两个的原因。
 * 为什么必须 daemon 见 {@link TaczThreads}。
 *
 * <p>1025 引入「代际（generation）」机制：资源重新加载时 {@link #invalidate()} 递增代际，
 * 使旧代际下尚未完成的预热任务在 {@link ClientAssetLoadTask#warmUp()} 中被判定为过期而返回 null。
 */
public final class ClientAssetLoadDispatcher {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(TaczThreads.daemonFactory("tacz-client-asset-preload"));

    private ClientAssetLoadDispatcher() {
    }

    public static ExecutorService executor() {
        return EXECUTOR;
    }

    static <T> ClientAssetLoadTask<T> task(Supplier<T> loader, Consumer<Throwable> failed) {
        long generation = GENERATION.get();
        return new ClientAssetLoadTask<>(EXECUTOR, loader, () -> GENERATION.get() == generation, failed);
    }

    public static void invalidate() {
        GENERATION.incrementAndGet();
    }

    static CompletableFuture<?> preload(ClientAssetLoadTask<?> task, Identifier modelId) {
        return modelId == null || ResourceConfig.ENABLE_LAZY_CLIENT_ASSET_LOAD.get() && !"tacz".equals(modelId.getNamespace())
                ? CompletableFuture.completedFuture(null)
                : task.warmUp();
    }
}
