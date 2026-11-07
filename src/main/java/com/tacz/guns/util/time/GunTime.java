package com.tacz.guns.util.time;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/**
 * 单调时钟（纳秒），带纪元计数。
 *
 * <p>26.2 移植说明：上游 0929 依赖 SBM 的 AnimationClock（双端可用）；
 * SBM 无 Fabric 版，这里直接用 {@link System#nanoTime()} 实现等价语义。
 * 注意：本类【不能】标 @Environment(CLIENT) —— 时间戳同步链路
 * （ClientMessageSyncBaseTimestamp 服务端处理、LivingEntityMixin.shoot 等）
 * 在专用服务器上也会调用 nowMillis()。只有 {@link #shouldTick()} 触碰客户端
 * 单例，内部已用 FabricLoader 环境判断隔离。
 * <ul>
 *   <li>时钟回拨（nanoTime 不回拨，但保留防御）时纪元 +1；</li>
 *   <li>{@link #shouldTick()} 表示「游戏未暂停」，用于暂停时冻结后台计时任务。</li>
 * </ul>
 */
public final class GunTime {
   private static final long EPOCH_OFFSET_NANOS = 100000000000L;
   private static volatile long originNanos = System.nanoTime();
   private static volatile long epoch;

   private GunTime() {
   }

   public static long nowNanos() {
      long raw = System.nanoTime();
      long origin = originNanos;
      if (raw < origin) {
         synchronized (GunTime.class) {
            if (raw < originNanos) {
               originNanos = raw;
               epoch++;
            }

            origin = originNanos;
         }
      }

      return EPOCH_OFFSET_NANOS + Math.max(0L, raw - origin);
   }

   public static long nowMillis() {
      return nowNanos() / 1000000L;
   }

   public static boolean shouldTick() {
      if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
         return true;
      }
      Minecraft mc = Minecraft.getInstance();
      return mc == null || !mc.isPaused();
   }

   public static long epoch() {
      nowNanos();
      return epoch;
   }

   public static synchronized void resetEpoch() {
      originNanos = System.nanoTime();
      epoch++;
   }

   public static boolean isWithin(long timestamp, long durationMillis) {
      if (timestamp >= 0L && durationMillis > 0L) {
         long elapsed = nowMillis() - timestamp;
         return elapsed >= 0L && elapsed < durationMillis;
      } else {
         return false;
      }
   }

   public static boolean hasElapsed(long timestamp, long durationMillis) {
      if (timestamp < 0L) {
         return false;
      } else {
         long elapsed = nowMillis() - timestamp;
         return elapsed >= durationMillis;
      }
   }
}
