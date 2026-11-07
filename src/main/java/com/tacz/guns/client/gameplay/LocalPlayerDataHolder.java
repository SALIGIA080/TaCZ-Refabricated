package com.tacz.guns.client.gameplay;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.util.TaczThreads;
import net.minecraft.client.player.LocalPlayer;

import javax.annotation.Nullable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Predicate;

public class LocalPlayerDataHolder {
    /**
     * 枪械操作的定时任务池（拔枪音效延时、连发节拍）。
     *
     * <p><b>必须是 daemon 池</b>：这里面跑的是 {@code scheduleAtFixedRate} 的连发任务，
     * 而且全仓没有任何 {@code shutdown()} 调用。原先用的是无参重载，
     * 也就是 {@code Executors.defaultThreadFactory()} —— 它<b>无条件</b>
     * {@code setDaemon(false)}，于是这两个线程会一直卡住 JVM 退出，
     * 15 秒后 {@code ClientShutdownWatchdog} 发一份崩溃报告。
     * 详见 {@link com.tacz.guns.util.TaczThreads}。
     */
    public static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE =
            Executors.newScheduledThreadPool(2, TaczThreads.daemonFactory("tacz-gun-scheduler"));
    public long clientBaseTimestamp = -1L;
    /**
     * 上一个 tick 的瞄准进度，用于插值，范围 0 ~ 1
     */
    public static float oldAimingProgress = 0;
    /**
     * 点击按钮的时间戳，防止客户端点击按钮后误触开火
     */
    public static long clientClickButtonTimestamp = -1L;
    /**
     * 玩家对象
     */
    private final LocalPlayer player;
    /**
     * 与射击有关的几个变量
     */
    public volatile long clientShootTimestamp = -1L;
    public volatile long clientLastShootTimestamp = -1L;
    public volatile boolean isShootRecorded = true;
    public float chargeProgress = 0f;
    public boolean isCharging = false;
    /** 1025：卸弹期间抑制自动装填。 */
    public boolean autoReloadSuppressed = false;
    /**
     * 这个状态锁表示：任意时刻，正在进行的枪械操作只能为一个。
     * 主要用于防止客户端操作表现效果重复执行。
     */
    public volatile boolean clientStateLock = false;
    /** 0929：换弹正在启动（状态锁释放瞬间、换弹动画未完全展开时置位），用于强制退出瞄准。 */
    public volatile boolean clientReloadStarting = false;
    /**
     * 1025：定时空状态锁（无具体等待条件，仅按 250ms 超时释放）的哨兵 Predicate。
     * 通过引用相等判断是否为「定时空锁」，避免引入额外的枚举/标志位。
     */
    private static final Predicate<IGunOperator> TIMED_LOCKED_CONDITION = operator -> false;
    /**
     * 用于标记 bolt 是否已经执行完成，防止因为客户端、服务端异步产生的数据不同步而造成的重复 bolt
     */
    public boolean isBolting = false;
    /**
     * 瞄准的进度，范围 0 ~ 1
     */
    public float clientAimingProgress = 0;
    /**
     * 瞄准时间戳，单位 ms
     */
    public long clientAimingTimestamp = -1L;
    public boolean clientIsAiming = false;
    /**
     * 切枪时间戳，在切枪开始时更新，单位 ms。
     * 在客户端仅用于计算收枪动画的时长和过渡时长。
     */
    public long clientDrawTimestamp = -1L;
    /**
     * 异步切枪
     */
    @Nullable
    public ScheduledFuture<?> drawFuture = null;
    /**
     * 用于等待上锁的服务端响应
     */
    @Nullable
    public Predicate<IGunOperator> lockedCondition = null;
    /**
     * 计算上锁响应时间，不允许超过最大响应时间，避免死锁
     */
    public long lockTimestamp = -1;

    // ===== 1.20.0：弹药数缓存（HUD 每帧查询会扫全背包，0929 起改为 50ms 缓存） =====
    private net.minecraft.world.item.ItemStack cachedAmmoGun = null;
    private long cachedAmmoTimestamp = -1L;
    private long cachedAmmoEpoch = -1L;
    private int cachedAmmoCount = 0;

    public LocalPlayerDataHolder(LocalPlayer player) {
        this.player = player;
    }

    public int getCachedAmmoCount(net.minecraft.world.item.ItemStack gun, com.tacz.guns.api.item.IGun iGun) {
        long epoch = com.tacz.guns.util.time.GunTime.epoch();
        long now = com.tacz.guns.util.time.GunTime.nowMillis();
        long elapsed = now - this.cachedAmmoTimestamp;
        if (gun != this.cachedAmmoGun || epoch != this.cachedAmmoEpoch || this.cachedAmmoTimestamp < 0L || elapsed < 0L || elapsed >= 50L) {
            this.cachedAmmoCount = ClientAmmoCount.get(this.player, gun, iGun);
            this.cachedAmmoGun = gun;
            this.cachedAmmoTimestamp = now;
            this.cachedAmmoEpoch = epoch;
        }
        return this.cachedAmmoCount;
    }

    public void resetAmmoCountCache() {
        this.cachedAmmoGun = null;
        this.cachedAmmoTimestamp = -1L;
        this.cachedAmmoEpoch = -1L;
        this.cachedAmmoCount = 0;
    }

    /**
     * 锁上状态锁
     */
    public void lockState(@Nullable Predicate<IGunOperator> lockedCondition) {
        clientStateLock = true;
        lockTimestamp = com.tacz.guns.util.time.GunTime.nowMillis();
        this.lockedCondition = lockedCondition;
    }

    /**
     * 1025：无参上锁 —— 上「定时空锁」，仅按 250ms 超时释放，不等待具体条件。
     */
    public void lockState() {
        this.lockState(TIMED_LOCKED_CONDITION);
    }

    /**
     * 1025：是否处于「定时空锁」状态（区别于等待具体条件的条件锁）。
     */
    public boolean isTimedStateLocked() {
        return this.clientStateLock && this.lockedCondition == TIMED_LOCKED_CONDITION;
    }

    /**
     * 此方法每 tick 执行一次，判断是否应当释放状态锁。
     */
    public void tickStateLock() {
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        ReloadState reloadState = gunOperator.getSynReloadState();
        // 如果还没完成上锁，则不能释放状态锁
        // 上锁允许的最大响应时间，毫秒
        long maxLockTime = 250;
        long lockTime = com.tacz.guns.util.time.GunTime.nowMillis() - lockTimestamp;
        if (lockTime < maxLockTime && lockedCondition != null && !lockedCondition.test(gunOperator)) {
            return;
        }
        lockedCondition = null;
        // 0929：换弹中置位 clientReloadStarting（供 LocalPlayerAim 强制退出瞄准），否则所有冷却归零才释放锁
        if (reloadState.getStateType().isReloading()) {
            clientReloadStarting = true;
            return;
        }
        long shootCoolDown = gunOperator.getSynShootCoolDown();
        if (shootCoolDown > 0) {
            return;
        }
        if (gunOperator.getSynDrawCoolDown() > 0) {
            return;
        }
        if (gunOperator.getSynIsBolting()) {
            return;
        }
        if (gunOperator.getSynMeleeCoolDown() > 0) {
            return;
        }
        // 释放状态锁
        clientStateLock = false;
        clientReloadStarting = false;
    }

    /**
     * 重生后各种参数的重置
     */
    public void reset() {
        cancelShootFutures();
        resetAmmoCountCache();
        // 重置客户端的 shoot 时间戳
        isShootRecorded = true;
        clientShootTimestamp = -1;
        chargeProgress = 0f;
        isCharging = false;
        autoReloadSuppressed = false;
        // 重置客户端瞄准状态
        clientIsAiming = false;
        clientAimingProgress = 0;
        oldAimingProgress = 0;
        // 重置拉栓状态
        isBolting = false;
        // 打开状态锁
        clientStateLock = false;
        clientReloadStarting = false;
    }

    // ===== 1.20.0：射击定时任务登记，退出世界/重生时统一取消 =====
    private final java.util.Set<ScheduledFuture<?>> shootFutures = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void registerShootFuture(ScheduledFuture<?> future) {
        this.shootFutures.add(future);
    }

    public void unregisterShootFuture(ScheduledFuture<?> future) {
        this.shootFutures.remove(future);
    }

    public void cancelShootFutures() {
        this.shootFutures.forEach(future -> future.cancel(false));
        this.shootFutures.clear();
    }

    public boolean hasPendingShoot() {
        this.shootFutures.removeIf(java.util.concurrent.Future::isDone);
        return !this.shootFutures.isEmpty();
    }
}
