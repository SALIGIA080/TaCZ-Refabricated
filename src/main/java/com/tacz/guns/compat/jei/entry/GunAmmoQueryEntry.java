package com.tacz.guns.compat.jei.entry;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.index.CommonGunIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class GunAmmoQueryEntry {
   private final ItemStack gunStack;
   private final ItemStack ammoStack;

   private GunAmmoQueryEntry(ItemStack gunStack, ItemStack ammoStack) {
      this.gunStack = gunStack;
      this.ammoStack = ammoStack;
   }

   public static List<GunAmmoQueryEntry> getAllGunAmmoQueryEntries() {
      List<GunAmmoQueryEntry> entries = new ArrayList<>();
      Map<Identifier, ItemStack> ammoStacks = new HashMap<>();
      TimelessAPI.getAllCommonGunIndex()
         .stream()
         .sorted(
            Comparator.<Entry<Identifier, CommonGunIndex>>comparingInt(entry -> entry.getValue().getSort())
               .thenComparing(entry -> entry.getKey().toString())
         )
         .forEach(entry -> {
            Identifier ammoId = entry.getValue().getGunData().getAmmoId();
            if (ammoId != null && !TimelessAPI.getCommonAmmoIndex(ammoId).isEmpty()) {
               ItemStack gun = GunItemBuilder.create().setId(entry.getKey()).build();
               if (gun.getItem() instanceof IGun) {
                  ItemStack ammo = ammoStacks.computeIfAbsent(ammoId, id -> AmmoItemBuilder.create().setId(id).build());
                  if (ammo.getItem() instanceof IAmmo iAmmo && iAmmo.isAmmoOfGun(gun, ammo)) {
                     entries.add(new GunAmmoQueryEntry(gun, ammo));
                  }
               }
            }
         });
      return entries;
   }

   public ItemStack getGunStack() {
      return this.gunStack;
   }

   public ItemStack getAmmoStack() {
      return this.ammoStack;
   }
}
