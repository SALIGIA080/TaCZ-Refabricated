package com.tacz.guns.compat.jei.transfer;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import java.util.Optional;
import javax.annotation.Nullable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public class GunSmithTableTransferHandler implements IRecipeTransferHandler<GunSmithTableMenu, GunSmithTableRecipe> {
   private final IRecipeType<GunSmithTableRecipe> recipeType;
   private final IRecipeTransferHandlerHelper transferHelper;

   public GunSmithTableTransferHandler(IRecipeType<GunSmithTableRecipe> recipeType, IRecipeTransferHandlerHelper transferHelper) {
      this.recipeType = recipeType;
      this.transferHelper = transferHelper;
   }

   public Class<GunSmithTableMenu> getContainerClass() {
      return GunSmithTableMenu.class;
   }

   public Optional<MenuType<GunSmithTableMenu>> getMenuType() {
      return Optional.of(GunSmithTableMenu.TYPE);
   }

   public IRecipeType<GunSmithTableRecipe> getRecipeType() {
      return this.recipeType;
   }

   @Nullable
   public IRecipeTransferError transferRecipe(
      GunSmithTableMenu container, GunSmithTableRecipe recipe, IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer
   ) {
      if (!container.canSelectRecipe(recipe.getId())) {
         return this.transferHelper.createUserErrorWithTooltip(Component.translatable("jei.tacz.gun_smith_table.recipe_unavailable"));
      } else {
         if (doTransfer) {
            container.requestRecipeSelection(recipe.getId());
         }

         return null;
      }
   }
}
