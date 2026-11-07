package com.tacz.guns.compat.jei.category;

import com.tacz.guns.compat.jei.entry.GunAttachmentQueryEntry;
import com.tacz.guns.init.ModCreativeTabs;
import java.util.Locale;
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

public class GunAttachmentQueryCategory implements IRecipeCategory<GunAttachmentQueryEntry> {
   public static final RecipeType<GunAttachmentQueryEntry> GUN_ATTACHMENT_QUERY = RecipeType.create(
      "tacz", "gun_attachment_query", GunAttachmentQueryEntry.class
   );
   private static final Component TITLE = Component.translatable("jei.tacz.gun_attachment_query.title");
   private static final int WIDTH = 162;
   private static final int COLUMNS = 9;
   private final IDrawableStatic bgDraw;
   private final IDrawable slotDraw;
   private final IDrawable iconDraw;

   public GunAttachmentQueryCategory(IGuiHelper guiHelper) {
      this.bgDraw = guiHelper.createBlankDrawable(162, 156);
      this.slotDraw = guiHelper.getSlotDrawable();
      this.iconDraw = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, ModCreativeTabs.GUN_RIFLE_TAB.getIconItem());
   }

   public void draw(GunAttachmentQueryEntry entry, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX, double mouseY) {
      Font font = Minecraft.getInstance().font;
      Component typeName = Component.translatable("tacz.type." + entry.getAttachmentType().name().toLowerCase(Locale.ROOT) + ".name");
      Component groupTitle = Component.translatable("jei.tacz.gun_attachment_query.group", new Object[]{typeName, entry.getTotalAttachmentCount()});
      guiGraphics.text(font, groupTitle, (162 - font.width(groupTitle)) / 2, 22, 5592405, false);
      if (entry.getPageCount() > 1) {
         Component page = Component.translatable("jei.tacz.gun_attachment_query.page", new Object[]{entry.getPageNumber(), entry.getPageCount()});
         guiGraphics.text(font, page, (162 - font.width(page)) / 2, 145, 5592405, false);
      }
   }

   public void setRecipe(IRecipeLayoutBuilder builder, GunAttachmentQueryEntry entry, IFocusGroup focuses) {
      ((IRecipeSlotBuilder)builder.addSlot(RecipeIngredientRole.OUTPUT, 73, 1).addItemStack(entry.getGunStack())).setBackground(this.slotDraw, -1, -1);

      for (int i = 0; i < entry.getAttachmentStacks().size(); i++) {
         int x = 1 + i % 9 * 18;
         int y = 35 + i / 9 * 18;
         ((IRecipeSlotBuilder)builder.addSlot(RecipeIngredientRole.INPUT, x, y).addItemStack(entry.getAttachmentStacks().get(i)))
            .setBackground(this.slotDraw, -1, -1);
      }
   }

   public RecipeType<GunAttachmentQueryEntry> getRecipeType() {
      return GUN_ATTACHMENT_QUERY;
   }

   public Component getTitle() {
      return TITLE;
   }

   public int getWidth() {
      return WIDTH;
   }

   public int getHeight() {
      return 156;
   }

   public IDrawable getIcon() {
      return this.iconDraw;
   }
}
