package com.tacz.guns.client.gui.components;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.config.client.RenderConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.Type;

public class CreativePackFilters {
   private static final Identifier TAB_TEXTURE = Identifier.parse("textures/gui/recipe_book.png");
   private static final Identifier ALL_PACKS_ICON = Identifier.fromNamespaceAndPath("tacz", "textures/gui/creative_pack_all.png");
   private static final int VISIBLE_PACKS = 3;
   private static final int TAB_HEIGHT = 26;
   private static final int TAB_SPACING = 29;
   private final ItemPickerMenu menu;
   private final IntSupplier guiLeft;
   private final IntSupplier guiTop;
   private final Runnable resetScroll;
   private final Map<CreativeModeTab, String> selections = new IdentityHashMap<>();
   private final List<CreativePackFilters.PackFilterButton> buttons = new ArrayList<>();
   private final List<CreativePackFilters.FilterOption> options = new ArrayList<>();
   private Button previousButton;
   private Button nextButton;
   private CreativeModeTab currentTab;
   private Collection<ItemStack> sourceItems;
   private long packInfoVersion = -1L;
   private int packScroll;

   public CreativePackFilters(ItemPickerMenu menu, IntSupplier guiLeft, IntSupplier guiTop, Runnable resetScroll) {
      this.menu = menu;
      this.guiLeft = guiLeft;
      this.guiTop = guiTop;
      this.resetScroll = resetScroll;
   }

   public void init(Consumer<AbstractWidget> add, CreativeModeTab tab) {
      this.buttons.clear();

      for (int i = 0; i <= 3; i++) {
         CreativePackFilters.PackFilterButton button = new CreativePackFilters.PackFilterButton(
            this.guiLeft.getAsInt() - 28, this.guiTop.getAsInt() + 11 + 29 * i
         );
         this.buttons.add(button);
         add.accept(button);
      }

      this.previousButton = Button.builder(Component.literal("▲"), buttonx -> this.scrollPacks(-1))
         .bounds(this.guiLeft.getAsInt() - 24, this.guiTop.getAsInt() - 3, 20, 12)
         .tooltip(Tooltip.create(Component.translatable("gui.tacz.creative_pack_filter.previous")))
         .build();
      this.nextButton = Button.builder(Component.literal("▼"), buttonx -> this.scrollPacks(1))
         .bounds(this.guiLeft.getAsInt() - 24, this.guiTop.getAsInt() + 127, 20, 12)
         .tooltip(Tooltip.create(Component.translatable("gui.tacz.creative_pack_filter.next")))
         .build();
      add.accept(this.previousButton);
      add.accept(this.nextButton);
      this.updateTab(tab, true);
   }

   public void updateTab(CreativeModeTab tab, boolean force) {
      if (!(Boolean)RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR.get()) {
         if (this.currentTab == tab) {
            this.showItems(tab.getDisplayItems());
         }

         this.selections.clear();
         this.hideWidgets();
      } else {
         Identifier tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
         if (tabId != null && "tacz".equals(tabId.getNamespace()) && tab.getType() == Type.CATEGORY) {
            Collection<ItemStack> displayItems = tab.getDisplayItems();
            long reloadVersion = ClientAssetsManager.INSTANCE.getPackInfoReloadVersion();
            if (force || this.currentTab != tab || this.sourceItems != displayItems || this.packInfoVersion != reloadVersion) {
               if (this.currentTab != tab) {
                  this.packScroll = 0;
               }

               this.currentTab = tab;
               this.sourceItems = displayItems;
               this.packInfoVersion = reloadVersion;
               this.options.clear();
               List<ItemStack> allItems = displayItems.stream().<ItemStack>map(ItemStack::copy).toList();
               this.options
                  .add(
                     new CreativePackFilters.FilterOption(
                        null, Component.translatable("gui.tacz.creative_pack_filter.all"), ItemStack.EMPTY, ALL_PACKS_ICON, allItems
                     )
                  );
               Map<String, List<ItemStack>> groupedItems = groupItems(displayItems);
               groupedItems.forEach(
                  (namespace, items) -> {
                     PackInfo info = ClientAssetsManager.INSTANCE.getPackInfo(namespace);
                     Component name = info != null
                           && info.getName() != null
                           && !info.getName().isBlank()
                           && !"custom.tacz.error.no_name".equals(info.getName())
                        ? Component.translatable(info.getName())
                        : Component.literal(namespace);
                     Identifier logo = info == null ? null : info.getLogo(namespace);
                     if (logo != null && Minecraft.getInstance().getResourceManager().getResource(logo).isEmpty()) {
                        logo = null;
                     }

                     CreativePackFilters.FilterOption option = new CreativePackFilters.FilterOption(
                        namespace, name, items.get(0).copy(), logo, (List<ItemStack>)items
                     );
                     if ("tacz".equals(namespace)) {
                        this.options.add(1, option);
                     } else {
                        this.options.add(option);
                     }
                  }
               );
               String selected = this.selections.get(tab);
               if (selected != null && !groupedItems.containsKey(selected)) {
                  this.selections.remove(tab);
               }

               this.packScroll = Mth.clamp(this.packScroll, 0, this.maxScroll());
               this.updateButtons();
               this.applyFilter();
            }
         } else {
            this.hideWidgets();
         }
      }
   }

   private void hideWidgets() {
      this.currentTab = null;
      this.sourceItems = null;
      this.buttons.forEach(button -> button.setOption(null));
      this.previousButton.visible = this.nextButton.visible = false;
      this.previousButton.active = this.nextButton.active = false;
   }

   static Map<String, List<ItemStack>> groupItems(Collection<ItemStack> items) {
      Map<String, List<ItemStack>> groups = new LinkedHashMap<>();

      for (ItemStack stack : items) {
         if (!stack.isEmpty()) {
            ItemStack copy = stack.copy();
            groups.computeIfAbsent(getContentId(copy).getNamespace(), key -> new ArrayList<>()).add(copy);
         }
      }

      return groups;
   }

   static Identifier getContentId(ItemStack stack) {
      if (stack.getItem() instanceof IGun gun) {
         return gun.getGunId(stack);
      } else if (stack.getItem() instanceof IAmmo ammo) {
         return ammo.getAmmoId(stack);
      } else if (stack.getItem() instanceof IAttachment attachment) {
         return attachment.getAttachmentId(stack);
      } else if (stack.getItem() instanceof IBlock block) {
         return block.getBlockId(stack);
      } else {
         if (stack.getItem() instanceof IAmmoBox box) {
            Identifier ammoId = box.getAmmoId(stack);
            if (!DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)) {
               return ammoId;
            }
         }

         return BuiltInRegistries.ITEM.getKey(stack.getItem());
      }
   }

   private void select(CreativePackFilters.FilterOption option) {
      if (option.namespace() == null) {
         this.selections.remove(this.currentTab);
      } else {
         this.selections.put(this.currentTab, option.namespace());
      }

      this.applyFilter();
   }

   private void applyFilter() {
      String selected = this.selections.get(this.currentTab);
      CreativePackFilters.FilterOption option = this.options
         .stream()
         .filter(entry -> Objects.equals(entry.namespace(), selected))
         .findFirst()
         .orElse(this.options.get(0));
      this.showItems(option.items());
   }

   private void showItems(Collection<ItemStack> items) {
      this.menu.items.clear();
      items.forEach(stack -> this.menu.items.add(stack.copy()));
      this.resetScroll.run();
      this.menu.scrollTo(0.0F);
   }

   private int maxScroll() {
      return Math.max(0, this.options.size() - 1 - 3);
   }

   private void scrollPacks(int delta) {
      this.packScroll = Mth.clamp(this.packScroll + delta, 0, this.maxScroll());
      this.updateButtons();
   }

   private void updateButtons() {
      this.buttons.get(0).setOption(this.options.get(0));

      for (int i = 1; i < this.buttons.size(); i++) {
         int optionIndex = this.packScroll + i;
         this.buttons.get(i).setOption(optionIndex < this.options.size() ? this.options.get(optionIndex) : null);
      }

      this.previousButton.visible = this.nextButton.visible = this.maxScroll() > 0;
      this.previousButton.active = this.packScroll > 0;
      this.nextButton.active = this.packScroll < this.maxScroll();
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if ((Boolean)RenderConfig.RENDER_CREATIVE_PACK_SIDEBAR.get() && this.currentTab != null && delta != 0.0) {
         int x = this.guiLeft.getAsInt() - 28;
         int y = this.guiTop.getAsInt() + 11;
         if (mouseX >= x && mouseX < x + 32 && mouseY >= y && mouseY < y + 87 + 26) {
            this.scrollPacks(delta > 0.0 ? -1 : 1);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private record FilterOption(@Nullable String namespace, Component name, ItemStack icon, @Nullable Identifier logo, List<ItemStack> items) {
   }

   private class PackFilterButton extends Button {
      @Nullable
      private CreativePackFilters.FilterOption option;

      private PackFilterButton(int x, int y) {
         super(x, y, 32, 26, Component.empty(), button -> {
            CreativePackFilters.FilterOption selected = ((CreativePackFilters.PackFilterButton)button).option;
            if (selected != null) {
               CreativePackFilters.this.select(selected);
            }
         }, DEFAULT_NARRATION);
      }

      @Override
      public void onPress(InputWithModifiers input) {
         this.onPress.onPress(this);
      }

      private void setOption(@Nullable CreativePackFilters.FilterOption option) {
         this.option = option;
         this.visible = this.active = option != null;
         this.setMessage((Component)(option == null ? Component.empty() : option.name()));
         this.setTooltip(option == null ? null : Tooltip.create(option.name()));
      }

      @Override
      protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
         if (this.option != null) {
            boolean selected = Objects.equals(this.option.namespace(), CreativePackFilters.this.selections.get(CreativePackFilters.this.currentTab));
            // 26.2：blitNineSliced 已移除，改用 blit 直接采样九宫格源块（圆角边框 3px 差异可忽略）
            graphics.blit(
               RenderPipelines.GUI_TEXTURED, CreativePackFilters.TAB_TEXTURE, this.getX(), this.getY(),
               (float) (selected ? 188 : 153), 2.0F, selected ? 31 : 28, 26, 256, 256
            );
            if (this.option.logo() != null) {
               graphics.blit(RenderPipelines.GUI_TEXTURED, this.option.logo(), this.getX() + 8, this.getY() + 5, 0.0F, 0.0F, 16, 16, 16, 16);
            } else {
               graphics.item(this.option.icon(), this.getX() + 8, this.getY() + 5);
            }

            if (this.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()) {
               graphics.outline(this.getX() + 5, this.getY() + 3, 22, 20, -1);
            }
         }
      }
   }
}
