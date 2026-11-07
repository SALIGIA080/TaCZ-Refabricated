package com.tacz.guns.compat.jei.entry;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class AmmoQueryEntry {
   public static final int GUNS_PER_PAGE = 54;
   private final ItemStack ammoStack;
   private final List<ItemStack> gunStacks;
   private final int totalGunCount;
   private final int pageNumber;
   private final int pageCount;

   private AmmoQueryEntry(ItemStack ammoStack, List<ItemStack> gunStacks, int totalGunCount, int pageNumber, int pageCount) {
      this.ammoStack = ammoStack;
      this.gunStacks = List.copyOf(gunStacks);
      this.totalGunCount = totalGunCount;
      this.pageNumber = pageNumber;
      this.pageCount = pageCount;
   }

   public static List<AmmoQueryEntry> getAllAmmoQueryEntries(List<GunAmmoQueryEntry> gunAmmoEntries) {
      Map<Identifier, List<GunAmmoQueryEntry>> gunsByAmmo = new HashMap<>();

      for (GunAmmoQueryEntry entry : gunAmmoEntries) {
         IAmmo iAmmo = IAmmo.getIAmmoOrNull(entry.getAmmoStack());
         if (iAmmo != null) {
            Identifier ammoId = iAmmo.getAmmoId(entry.getAmmoStack());
            gunsByAmmo.computeIfAbsent(ammoId, id -> new ArrayList<>()).add(entry);
         }
      }

      List<AmmoQueryEntry> entries = new ArrayList<>();
      TimelessAPI.getAllCommonAmmoIndex()
         .stream()
         .sorted(
            Comparator.<Entry<Identifier, CommonAmmoIndex>>comparingInt(entryx -> ((CommonAmmoIndex)entryx.getValue()).getSort())
               .thenComparing(entryx -> ((Identifier)entryx.getKey()).toString())
         )
         .forEach(entryx -> {
            List<GunAmmoQueryEntry> matches = gunsByAmmo.get(entryx.getKey());
            if (matches != null && !matches.isEmpty()) {
               ItemStack ammo = matches.get(0).getAmmoStack();
               List<ItemStack> guns = matches.stream().map(GunAmmoQueryEntry::getGunStack).toList();
               int total = guns.size();
               int pageCount = (total + 54 - 1) / 54;

               for (int page = 0; page < pageCount; page++) {
                  int start = page * 54;
                  int end = Math.min(start + 54, total);
                  entries.add(new AmmoQueryEntry(ammo, guns.subList(start, end), total, page + 1, pageCount));
               }
            }
         });
      return entries;
   }

   public ItemStack getAmmoStack() {
      return this.ammoStack;
   }

   public List<ItemStack> getGunStacks() {
      return this.gunStacks;
   }

   public int getTotalGunCount() {
      return this.totalGunCount;
   }

   public int getPageNumber() {
      return this.pageNumber;
   }

   public int getPageCount() {
      return this.pageCount;
   }
}
