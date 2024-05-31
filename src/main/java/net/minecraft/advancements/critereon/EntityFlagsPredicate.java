package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record EntityFlagsPredicate(Optional<Boolean> isOnFire, Optional<Boolean> isCrouching, Optional<Boolean> isSprinting, Optional<Boolean> isSwimming, Optional<Boolean> isBaby) {
   public static final Codec<EntityFlagsPredicate> CODEC = RecordCodecBuilder.create((p_299555_) -> {
      return p_299555_.group(ExtraCodecs.strictOptionalField(Codec.BOOL, "is_on_fire").forGetter(EntityFlagsPredicate::isOnFire), ExtraCodecs.strictOptionalField(Codec.BOOL, "is_sneaking").forGetter(EntityFlagsPredicate::isCrouching), ExtraCodecs.strictOptionalField(Codec.BOOL, "is_sprinting").forGetter(EntityFlagsPredicate::isSprinting), ExtraCodecs.strictOptionalField(Codec.BOOL, "is_swimming").forGetter(EntityFlagsPredicate::isSwimming), ExtraCodecs.strictOptionalField(Codec.BOOL, "is_baby").forGetter(EntityFlagsPredicate::isBaby)).apply(p_299555_, EntityFlagsPredicate::new);
   });

   public boolean matches(Entity p_33697_) {
      if (this.isOnFire.isPresent() && p_33697_.isOnFire() != this.isOnFire.get()) {
         return false;
      } else if (this.isCrouching.isPresent() && p_33697_.isCrouching() != this.isCrouching.get()) {
         return false;
      } else if (this.isSprinting.isPresent() && p_33697_.isSprinting() != this.isSprinting.get()) {
         return false;
      } else if (this.isSwimming.isPresent() && p_33697_.isSwimming() != this.isSwimming.get()) {
         return false;
      } else {
         if (this.isBaby.isPresent() && p_33697_ instanceof LivingEntity) {
            LivingEntity livingentity = (LivingEntity)p_33697_;
            if (livingentity.isBaby() != this.isBaby.get()) {
               return false;
            }
         }

         return true;
      }
   }

   public static class Builder {
      private Optional<Boolean> isOnFire = Optional.empty();
      private Optional<Boolean> isCrouching = Optional.empty();
      private Optional<Boolean> isSprinting = Optional.empty();
      private Optional<Boolean> isSwimming = Optional.empty();
      private Optional<Boolean> isBaby = Optional.empty();

      public static EntityFlagsPredicate.Builder flags() {
         return new EntityFlagsPredicate.Builder();
      }

      public EntityFlagsPredicate.Builder setOnFire(Boolean p_33715_) {
         this.isOnFire = Optional.of(p_33715_);
         return this;
      }

      public EntityFlagsPredicate.Builder setCrouching(Boolean p_150058_) {
         this.isCrouching = Optional.of(p_150058_);
         return this;
      }

      public EntityFlagsPredicate.Builder setSprinting(Boolean p_150060_) {
         this.isSprinting = Optional.of(p_150060_);
         return this;
      }

      public EntityFlagsPredicate.Builder setSwimming(Boolean p_150062_) {
         this.isSwimming = Optional.of(p_150062_);
         return this;
      }

      public EntityFlagsPredicate.Builder setIsBaby(Boolean p_33718_) {
         this.isBaby = Optional.of(p_33718_);
         return this;
      }

      public EntityFlagsPredicate build() {
         return new EntityFlagsPredicate(this.isOnFire, this.isCrouching, this.isSprinting, this.isSwimming, this.isBaby);
      }
   }
}