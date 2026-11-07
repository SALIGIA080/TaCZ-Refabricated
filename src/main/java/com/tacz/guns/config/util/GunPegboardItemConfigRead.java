package com.tacz.guns.config.util;

import com.google.common.collect.Lists;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.sync.SyncConfig;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

public final class GunPegboardItemConfigRead {
   private static final List<Identifier> WHITELIST = Lists.newArrayList();
   private static final List<Identifier> BLACKLIST = Lists.newArrayList();
   private static final TagKey<Item> WHITELIST_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("tacz", "gun_pegboard/whitelist"));
   private static final TagKey<Item> BLACKLIST_ITEMS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("tacz", "gun_pegboard/blacklist"));

   private GunPegboardItemConfigRead() {
   }

   public static void init() {
      WHITELIST.clear();
      BLACKLIST.clear();
      handleConfigData(SyncConfig.GUN_PEGBOARD_WHITELIST_ITEMS.get(), WHITELIST);
      handleConfigData(SyncConfig.GUN_PEGBOARD_BLACKLIST_ITEMS.get(), BLACKLIST);
   }

   public static boolean canPlace(ItemStack stack) {
      Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
      if (itemId == null) {
         return false;
      } else if (BLACKLIST.contains(itemId) || stack.is(BLACKLIST_ITEMS)) {
         return false;
      } else if (!WHITELIST.contains(itemId) && !stack.is(WHITELIST_ITEMS)) {
         Item item = stack.getItem();
         return item instanceof IGun || item instanceof IAttachment || item instanceof IAmmo || item instanceof IAmmoBox;
      } else {
         return true;
      }
   }

   private static void handleConfigData(List<String> configData, List<Identifier> storeList) {
      configData.forEach(data -> {
         if (!StringUtils.isBlank(data)) {
            storeList.add(Identifier.parse(data));
         }
      });
   }
}
