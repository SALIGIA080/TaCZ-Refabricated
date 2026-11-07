package com.tacz.guns.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModRecipe;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 弹药库升级配方（3×3 环绕：中心旧弹药库 + 8 个材料）。
 *
 * <p>26.2 移植说明：
 * <ul>
 *   <li>配方对象拿不到自己的 id，0929 按 id 区分金/钻两档的做法改为
 *       JSON 里的 {@code "tier"} 字段（两个配方 JSON 已补上）；</li>
 *   <li>Forge 的 IShapedRecipe 移除，配方书位置信息走 {@link #placementInfo()}；</li>
 *   <li>方块实体数据复制改用 DataComponents.BLOCK_ENTITY_DATA（TypedEntityData）。</li>
 * </ul>
 */
public class AmmoStorageUpgradeRecipe extends CustomRecipe {
   private final String tier;
   private final Item from;
   private final Item material;
   private final Item to;

   public AmmoStorageUpgradeRecipe(String tier) {
      this.tier = tier;
      if ("diamond".equals(tier)) {
         this.from = ModItems.AMMO_STORAGE_GOLD;
         this.material = Items.DIAMOND;
         this.to = ModItems.AMMO_STORAGE_DIAMOND;
      } else {
         this.from = ModItems.AMMO_STORAGE;
         this.material = Items.GOLD_INGOT;
         this.to = ModItems.AMMO_STORAGE_GOLD;
      }
   }

   public String tier() {
      return this.tier;
   }

   @Override
   public boolean matches(CraftingInput input, Level level) {
      if (input.width() != 3 || input.height() != 3) {
         return false;
      }
      for (int slot = 0; slot < input.size(); slot++) {
         ItemStack stack = input.getItem(slot);
         if (slot == 4) {
            if (!stack.is(this.from)) {
               return false;
            }
         } else if (!stack.is(this.material)) {
            return false;
         }
      }
      return true;
   }

   @Override
   public ItemStack assemble(CraftingInput input) {
      ItemStack result = new ItemStack(this.to);
      // 保留旧弹药库存储的弹药数据
      var data = input.getItem(4).get(DataComponents.BLOCK_ENTITY_DATA);
      if (data != null) {
         result.set(DataComponents.BLOCK_ENTITY_DATA, data);
      }
      return result;
   }

   @Override
   public PlacementInfo placementInfo() {
      Ingredient center = Ingredient.of(this.from);
      Ingredient materialIngredient = Ingredient.of(this.material);
      List<Ingredient> ingredients = new java.util.ArrayList<>(9);
      for (int slot = 0; slot < 9; slot++) {
         ingredients.add(slot == 4 ? center : materialIngredient);
      }
      return PlacementInfo.create(ingredients);
   }

   @Override
   public CraftingBookCategory category() {
      return CraftingBookCategory.MISC;
   }

   @Override
   public RecipeSerializer<? extends CustomRecipe> getSerializer() {
      return ModRecipe.AMMO_STORAGE_UPGRADE_RECIPE_SERIALIZER;
   }

   public static final MapCodec<AmmoStorageUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
         com.mojang.serialization.Codec.STRING.fieldOf("tier").forGetter(AmmoStorageUpgradeRecipe::tier)
      ).apply(instance, AmmoStorageUpgradeRecipe::new));

   public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, AmmoStorageUpgradeRecipe> STREAM_CODEC =
         StreamCodec.of(
               (buf, recipe) -> buf.writeUtf(recipe.tier),
               buf -> new AmmoStorageUpgradeRecipe(buf.readUtf())
         );
}
