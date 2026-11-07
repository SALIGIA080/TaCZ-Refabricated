package com.tacz.guns.compat.jei.category;

import com.tacz.guns.compat.jei.entry.GunAmmoQueryEntry;
import com.tacz.guns.init.ModCreativeTabs;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class GunAmmoQueryCategory implements IRecipeCategory<GunAmmoQueryEntry> {
   public static final RecipeType<GunAmmoQueryEntry> GUN_AMMO_QUERY = RecipeType.create("tacz", "gun_ammo_query", GunAmmoQueryEntry.class);
   private static final Component TITLE = Component.translatable("jei.tacz.gun_ammo_query.title");
   private static final Component AMMO_LABEL = Component.translatable("jei.tacz.gun_ammo_query.ammo");
   private static final int WIDTH = 162;
   private final IDrawableStatic bgDraw;
   private final IDrawable slotDraw;
   private final IDrawable iconDraw;

   public GunAmmoQueryCategory(IGuiHelper guiHelper) {
      this.bgDraw = guiHelper.createBlankDrawable(162, 58);
      this.slotDraw = guiHelper.getSlotDrawable();
      this.iconDraw = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, ModCreativeTabs.GUN_RIFLE_TAB.getIconItem());
   }

   public void draw(GunAmmoQueryEntry entry, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
      Font font = Minecraft.getInstance().font;
      guiGraphics.text(font, AMMO_LABEL, (162 - font.width(AMMO_LABEL)) / 2, 24, 5592405, false);
   }

   public void setRecipe(IRecipeLayoutBuilder builder, GunAmmoQueryEntry entry, IFocusGroup focuses) {
      ((IRecipeSlotBuilder)builder.addSlot(RecipeIngredientRole.OUTPUT, 73, 1).addItemStack(entry.getGunStack())).setBackground(this.slotDraw, -1, -1);
      ((IRecipeSlotBuilder)builder.addSlot(RecipeIngredientRole.INPUT, 73, 39).addItemStack(entry.getAmmoStack())).setBackground(this.slotDraw, -1, -1);
   }

   public RecipeType<GunAmmoQueryEntry> getRecipeType() {
      return GUN_AMMO_QUERY;
   }

   public Component getTitle() {
      return TITLE;
   }

   public int getWidth() {
      return WIDTH;
   }

   public int getHeight() {
      return 58;
   }

   public IDrawable getIcon() {
      return this.iconDraw;
   }
}
