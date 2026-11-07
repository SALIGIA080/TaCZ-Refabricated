package com.tacz.guns.mixin.client;

import com.tacz.guns.client.gui.components.CreativePackFilters;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 创造模式枪包筛选器挂载点（1025 功能）。
 *
 * <p>26.2 相对 1.20.1 的变化：</p>
 * <ul>
 *   <li>{@code selectTab(CreativeModeTab)} 从 public 变为 private，仍可 mixin 注入；</li>
 *   <li>{@code refreshCurrentTabContents(Collection<ItemStack>)} 已删除（逻辑并入 selectTab），
 *       故移除对应注入点；</li>
 *   <li>{@code render(GuiGraphics,...)} 已删除，改为 {@code extractRenderState} 体系，
 *       这里改用每 tick 的 {@code containerTick()} 做「非强制刷新」；</li>
 *   <li>{@code mouseScrolled} 签名从 3 个 double 变为 4 个 double。</li>
 * </ul>
 */
@Mixin({CreativeModeInventoryScreen.class})
public abstract class CreativeModeInventoryScreenMixin extends AbstractContainerScreen<ItemPickerMenu> {
   @Shadow
   private static CreativeModeTab selectedTab;
   @Shadow
   private float scrollOffs;
   @Unique
   private CreativePackFilters tacz$packFilters;

   protected CreativeModeInventoryScreenMixin(ItemPickerMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void tacz$initPackFilters(CallbackInfo ci) {
      if (this.tacz$packFilters == null) {
         this.tacz$packFilters = new CreativePackFilters(
            this.getMenu(), () -> this.leftPos, () -> this.topPos, () -> this.scrollOffs = 0.0F
         );
      }

      this.tacz$packFilters.init(widget -> this.addRenderableWidget(widget), selectedTab);
   }

   @Inject(
      method = {"selectTab"},
      at = {@At("TAIL")}
   )
   private void tacz$selectPackFilterTab(CreativeModeTab tab, CallbackInfo ci) {
      if (this.tacz$packFilters != null) {
         this.tacz$packFilters.updateTab(tab, true);
      }
   }

   @Inject(
      method = {"containerTick"},
      at = {@At("TAIL")}
   )
   private void tacz$refreshPackFilterItems(CallbackInfo ci) {
      if (this.tacz$packFilters != null) {
         this.tacz$packFilters.updateTab(selectedTab, false);
      }
   }

   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void tacz$scrollPackFilters(double mouseX, double mouseY, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
      if (this.tacz$packFilters != null && this.tacz$packFilters.mouseScrolled(mouseX, mouseY, deltaY)) {
         cir.setReturnValue(true);
      }
   }
}
