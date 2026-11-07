package com.tacz.guns.entity.shooter;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaError;

public class GunScriptGuard {
   private GunScriptGuard() {
   }

   public static void run(ShooterDataHolder data, LivingEntity shooter, @Nullable Identifier gunId, String operation, Runnable action) {
      run(data, shooter, gunId, operation, () -> {
         action.run();
         return null;
      }, null);
   }

   public static <T> T run(ShooterDataHolder data, LivingEntity shooter, @Nullable Identifier gunId, String operation, Supplier<T> action, T fallback) {
      if (data.gunScriptFailed) {
         return fallback;
      } else if (gunId != null && gunId.equals(data.currentGunId)) {
         ItemStack current = data.currentGunItem == null ? ItemStack.EMPTY : data.currentGunItem.get();
         if (hasGunId(current, gunId) && hasGunId(shooter.getMainHandItem(), gunId)) {
            try {
               T result = action.get();
               return data.gunScriptFailed ? fallback : result;
            } catch (LuaError var8) {
               data.cancelGunScriptState();
               data.gunScriptFailed = true;
               GunMod.LOGGER.error("Gun script failed for {} during {} (entity {}); paused until next draw", gunId, operation, shooter.getUUID(), var8);
               return fallback;
            }
         } else {
            data.cancelGunScriptState();
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private static boolean hasGunId(@Nullable ItemStack stack, Identifier gunId) {
      if (stack != null && !stack.isEmpty()) {
         IGun gun = IGun.getIGunOrNull(stack);
         return gun != null && gunId.equals(gun.getGunId(stack));
      } else {
         return false;
      }
   }
}
