package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class EffectRenderingInventoryScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
   private static final ResourceLocation EFFECT_BACKGROUND_LARGE_SPRITE = new ResourceLocation("container/inventory/effect_background_large");
   private static final ResourceLocation EFFECT_BACKGROUND_SMALL_SPRITE = new ResourceLocation("container/inventory/effect_background_small");

   public EffectRenderingInventoryScreen(T p_98701_, Inventory p_98702_, Component p_98703_) {
      super(p_98701_, p_98702_, p_98703_);
   }

   public void render(GuiGraphics p_283027_, int p_281444_, int p_282953_, float p_281666_) {
      super.render(p_283027_, p_281444_, p_282953_, p_281666_);
      this.renderEffects(p_283027_, p_281444_, p_282953_);
   }

   public boolean canSeeEffects() {
      int i = this.leftPos + this.imageWidth + 2;
      int j = this.width - i;
      return j >= 32;
   }

   private void renderEffects(GuiGraphics p_281945_, int p_282601_, int p_282335_) {
      int i = this.leftPos + this.imageWidth + 2;
      int j = this.width - i;
      Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
      if (!collection.isEmpty() && j >= 32) {
         boolean flag = j >= 120;
         int k = 33;
         if (collection.size() > 5) {
            k = 132 / (collection.size() - 1);
         }

         Iterable<MobEffectInstance> iterable = Ordering.natural().sortedCopy(collection);
         this.renderBackgrounds(p_281945_, i, k, iterable, flag);
         this.renderIcons(p_281945_, i, k, iterable, flag);
         if (flag) {
            this.renderLabels(p_281945_, i, k, iterable);
         } else if (p_282601_ >= i && p_282601_ <= i + 33) {
            int l = this.topPos;
            MobEffectInstance mobeffectinstance = null;

            for(MobEffectInstance mobeffectinstance1 : iterable) {
               if (p_282335_ >= l && p_282335_ <= l + k) {
                  mobeffectinstance = mobeffectinstance1;
               }

               l += k;
            }

            if (mobeffectinstance != null) {
               List<Component> list = List.of(this.getEffectName(mobeffectinstance), MobEffectUtil.formatDuration(mobeffectinstance, 1.0F));
               p_281945_.renderTooltip(this.font, list, Optional.empty(), p_282601_, p_282335_);
            }
         }

      }
   }

   private void renderBackgrounds(GuiGraphics p_281540_, int p_282479_, int p_283680_, Iterable<MobEffectInstance> p_282013_, boolean p_283630_) {
      int i = this.topPos;

      for(MobEffectInstance mobeffectinstance : p_282013_) {
         if (p_283630_) {
            p_281540_.blitSprite(EFFECT_BACKGROUND_LARGE_SPRITE, p_282479_, i, 120, 32);
         } else {
            p_281540_.blitSprite(EFFECT_BACKGROUND_SMALL_SPRITE, p_282479_, i, 32, 32);
         }

         i += p_283680_;
      }

   }

   private void renderIcons(GuiGraphics p_282745_, int p_282521_, int p_282291_, Iterable<MobEffectInstance> p_282642_, boolean p_281536_) {
      MobEffectTextureManager mobeffecttexturemanager = this.minecraft.getMobEffectTextures();
      int i = this.topPos;

      for(MobEffectInstance mobeffectinstance : p_282642_) {
         MobEffect mobeffect = mobeffectinstance.getEffect();
         TextureAtlasSprite textureatlassprite = mobeffecttexturemanager.get(mobeffect);
         p_282745_.blit(p_282521_ + (p_281536_ ? 6 : 7), i + 7, 0, 18, 18, textureatlassprite);
         i += p_282291_;
      }

   }

   private void renderLabels(GuiGraphics p_281462_, int p_283484_, int p_282057_, Iterable<MobEffectInstance> p_281986_) {
      int i = this.topPos;

      for(MobEffectInstance mobeffectinstance : p_281986_) {
         Component component = this.getEffectName(mobeffectinstance);
         p_281462_.drawString(this.font, component, p_283484_ + 10 + 18, i + 6, 16777215);
         Component component1 = MobEffectUtil.formatDuration(mobeffectinstance, 1.0F);
         p_281462_.drawString(this.font, component1, p_283484_ + 10 + 18, i + 6 + 10, 8355711);
         i += p_282057_;
      }

   }

   private Component getEffectName(MobEffectInstance p_194001_) {
      MutableComponent mutablecomponent = p_194001_.getEffect().getDisplayName().copy();
      if (p_194001_.getAmplifier() >= 1 && p_194001_.getAmplifier() <= 9) {
         mutablecomponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (p_194001_.getAmplifier() + 1)));
      }

      return mutablecomponent;
   }
}