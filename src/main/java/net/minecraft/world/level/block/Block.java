package net.minecraft.world.level.block;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

public class Block extends BlockBehaviour implements ItemLike {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Holder.Reference<Block> builtInRegistryHolder = BuiltInRegistries.BLOCK.createIntrusiveHolder(this);
   public static final IdMapper<BlockState> BLOCK_STATE_REGISTRY = new IdMapper<>();
   private static final LoadingCache<VoxelShape, Boolean> SHAPE_FULL_BLOCK_CACHE = CacheBuilder.newBuilder().maximumSize(512L).weakKeys().build(new CacheLoader<VoxelShape, Boolean>() {
      public Boolean load(VoxelShape p_49972_) {
         return !Shapes.joinIsNotEmpty(Shapes.block(), p_49972_, BooleanOp.NOT_SAME);
      }
   });
   public static final int UPDATE_NEIGHBORS = 1;
   public static final int UPDATE_CLIENTS = 2;
   public static final int UPDATE_INVISIBLE = 4;
   public static final int UPDATE_IMMEDIATE = 8;
   public static final int UPDATE_KNOWN_SHAPE = 16;
   public static final int UPDATE_SUPPRESS_DROPS = 32;
   public static final int UPDATE_MOVE_BY_PISTON = 64;
   public static final int UPDATE_NONE = 4;
   public static final int UPDATE_ALL = 3;
   public static final int UPDATE_ALL_IMMEDIATE = 11;
   public static final float INDESTRUCTIBLE = -1.0F;
   public static final float INSTANT = 0.0F;
   public static final int UPDATE_LIMIT = 512;
   protected final StateDefinition<Block, BlockState> stateDefinition;
   private BlockState defaultBlockState;
   @Nullable
   private String descriptionId;
   @Nullable
   private Item item;
   private static final int CACHE_SIZE = 2048;
   private static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
      Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap = new Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>(2048, 0.25F) {
         protected void rehash(int p_49979_) {
         }
      };
      object2bytelinkedopenhashmap.defaultReturnValue((byte)127);
      return object2bytelinkedopenhashmap;
   });

   public static int getId(@Nullable BlockState p_49957_) {
      if (p_49957_ == null) {
         return 0;
      } else {
         int i = BLOCK_STATE_REGISTRY.getId(p_49957_);
         return i == -1 ? 0 : i;
      }
   }

   public static BlockState stateById(int p_49804_) {
      BlockState blockstate = BLOCK_STATE_REGISTRY.byId(p_49804_);
      return blockstate == null ? Blocks.AIR.defaultBlockState() : blockstate;
   }

   public static Block byItem(@Nullable Item p_49815_) {
      return p_49815_ instanceof BlockItem ? ((BlockItem)p_49815_).getBlock() : Blocks.AIR;
   }

   public static BlockState pushEntitiesUp(BlockState p_49898_, BlockState p_49899_, LevelAccessor p_238252_, BlockPos p_49901_) {
      VoxelShape voxelshape = Shapes.joinUnoptimized(p_49898_.getCollisionShape(p_238252_, p_49901_), p_49899_.getCollisionShape(p_238252_, p_49901_), BooleanOp.ONLY_SECOND).move((double)p_49901_.getX(), (double)p_49901_.getY(), (double)p_49901_.getZ());
      if (voxelshape.isEmpty()) {
         return p_49899_;
      } else {
         for(Entity entity : p_238252_.getEntities((Entity)null, voxelshape.bounds())) {
            double d0 = Shapes.collide(Direction.Axis.Y, entity.getBoundingBox().move(0.0D, 1.0D, 0.0D), List.of(voxelshape), -1.0D);
            entity.teleportRelative(0.0D, 1.0D + d0, 0.0D);
         }

         return p_49899_;
      }
   }

   public static VoxelShape box(double p_49797_, double p_49798_, double p_49799_, double p_49800_, double p_49801_, double p_49802_) {
      return Shapes.box(p_49797_ / 16.0D, p_49798_ / 16.0D, p_49799_ / 16.0D, p_49800_ / 16.0D, p_49801_ / 16.0D, p_49802_ / 16.0D);
   }

   public static BlockState updateFromNeighbourShapes(BlockState p_49932_, LevelAccessor p_49933_, BlockPos p_49934_) {
      BlockState blockstate = p_49932_;
      BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

      for(Direction direction : UPDATE_SHAPE_ORDER) {
         blockpos$mutableblockpos.setWithOffset(p_49934_, direction);
         blockstate = blockstate.updateShape(direction, p_49933_.getBlockState(blockpos$mutableblockpos), p_49933_, p_49934_, blockpos$mutableblockpos);
      }

      return blockstate;
   }

   public static void updateOrDestroy(BlockState p_49903_, BlockState p_49904_, LevelAccessor p_49905_, BlockPos p_49906_, int p_49907_) {
      updateOrDestroy(p_49903_, p_49904_, p_49905_, p_49906_, p_49907_, 512);
   }

   public static void updateOrDestroy(BlockState p_49909_, BlockState p_49910_, LevelAccessor p_49911_, BlockPos p_49912_, int p_49913_, int p_49914_) {
      if (p_49910_ != p_49909_) {
         if (p_49910_.isAir()) {
            if (!p_49911_.isClientSide()) {
               p_49911_.destroyBlock(p_49912_, (p_49913_ & 32) == 0, (Entity)null, p_49914_);
            }
         } else {
            p_49911_.setBlock(p_49912_, p_49910_, p_49913_ & -33, p_49914_);
         }
      }

   }

   public Block(BlockBehaviour.Properties p_49795_) {
      super(p_49795_);
      StateDefinition.Builder<Block, BlockState> builder = new StateDefinition.Builder<>(this);
      this.createBlockStateDefinition(builder);
      this.stateDefinition = builder.create(Block::defaultBlockState, BlockState::new);
      this.registerDefaultState(this.stateDefinition.any());
      if (SharedConstants.IS_RUNNING_IN_IDE) {
         String s = this.getClass().getSimpleName();
         if (!s.endsWith("Block")) {
            LOGGER.error("Block classes should end with Block and {} doesn't.", (Object)s);
         }
      }

   }

   public static boolean isExceptionForConnection(BlockState p_152464_) {
      return p_152464_.getBlock() instanceof LeavesBlock || p_152464_.is(Blocks.BARRIER) || p_152464_.is(Blocks.CARVED_PUMPKIN) || p_152464_.is(Blocks.JACK_O_LANTERN) || p_152464_.is(Blocks.MELON) || p_152464_.is(Blocks.PUMPKIN) || p_152464_.is(BlockTags.SHULKER_BOXES);
   }

   public boolean isRandomlyTicking(BlockState p_49921_) {
      return this.isRandomlyTicking;
   }

   public static boolean shouldRenderFace(BlockState p_152445_, BlockGetter p_152446_, BlockPos p_152447_, Direction p_152448_, BlockPos p_152449_) {
      BlockState blockstate = p_152446_.getBlockState(p_152449_);
      if (p_152445_.skipRendering(blockstate, p_152448_)) {
         return false;
      } else if (blockstate.canOcclude()) {
         Block.BlockStatePairKey block$blockstatepairkey = new Block.BlockStatePairKey(p_152445_, blockstate, p_152448_);
         Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap = OCCLUSION_CACHE.get();
         byte b0 = object2bytelinkedopenhashmap.getAndMoveToFirst(block$blockstatepairkey);
         if (b0 != 127) {
            return b0 != 0;
         } else {
            VoxelShape voxelshape = p_152445_.getFaceOcclusionShape(p_152446_, p_152447_, p_152448_);
            if (voxelshape.isEmpty()) {
               return true;
            } else {
               VoxelShape voxelshape1 = blockstate.getFaceOcclusionShape(p_152446_, p_152449_, p_152448_.getOpposite());
               boolean flag = Shapes.joinIsNotEmpty(voxelshape, voxelshape1, BooleanOp.ONLY_FIRST);
               if (object2bytelinkedopenhashmap.size() == 2048) {
                  object2bytelinkedopenhashmap.removeLastByte();
               }

               object2bytelinkedopenhashmap.putAndMoveToFirst(block$blockstatepairkey, (byte)(flag ? 1 : 0));
               return flag;
            }
         }
      } else {
         return true;
      }
   }

   public static boolean canSupportRigidBlock(BlockGetter p_49937_, BlockPos p_49938_) {
      return p_49937_.getBlockState(p_49938_).isFaceSturdy(p_49937_, p_49938_, Direction.UP, SupportType.RIGID);
   }

   public static boolean canSupportCenter(LevelReader p_49864_, BlockPos p_49865_, Direction p_49866_) {
      BlockState blockstate = p_49864_.getBlockState(p_49865_);
      return p_49866_ == Direction.DOWN && blockstate.is(BlockTags.UNSTABLE_BOTTOM_CENTER) ? false : blockstate.isFaceSturdy(p_49864_, p_49865_, p_49866_, SupportType.CENTER);
   }

   public static boolean isFaceFull(VoxelShape p_49919_, Direction p_49920_) {
      VoxelShape voxelshape = p_49919_.getFaceShape(p_49920_);
      return isShapeFullBlock(voxelshape);
   }

   public static boolean isShapeFullBlock(VoxelShape p_49917_) {
      return SHAPE_FULL_BLOCK_CACHE.getUnchecked(p_49917_);
   }

   public boolean propagatesSkylightDown(BlockState p_49928_, BlockGetter p_49929_, BlockPos p_49930_) {
      return !isShapeFullBlock(p_49928_.getShape(p_49929_, p_49930_)) && p_49928_.getFluidState().isEmpty();
   }

   public void animateTick(BlockState p_220827_, Level p_220828_, BlockPos p_220829_, RandomSource p_220830_) {
   }

   public void destroy(LevelAccessor p_49860_, BlockPos p_49861_, BlockState p_49862_) {
   }

   public static List<ItemStack> getDrops(BlockState p_49870_, ServerLevel p_49871_, BlockPos p_49872_, @Nullable BlockEntity p_49873_) {
      LootParams.Builder lootparams$builder = (new LootParams.Builder(p_49871_)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(p_49872_)).withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withOptionalParameter(LootContextParams.BLOCK_ENTITY, p_49873_);
      return p_49870_.getDrops(lootparams$builder);
   }

   public static List<ItemStack> getDrops(BlockState p_49875_, ServerLevel p_49876_, BlockPos p_49877_, @Nullable BlockEntity p_49878_, @Nullable Entity p_49879_, ItemStack p_49880_) {
      LootParams.Builder lootparams$builder = (new LootParams.Builder(p_49876_)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(p_49877_)).withParameter(LootContextParams.TOOL, p_49880_).withOptionalParameter(LootContextParams.THIS_ENTITY, p_49879_).withOptionalParameter(LootContextParams.BLOCK_ENTITY, p_49878_);
      return p_49875_.getDrops(lootparams$builder);
   }

   public static void dropResources(BlockState p_49951_, Level p_49952_, BlockPos p_49953_) {
      if (p_49952_ instanceof ServerLevel) {
         getDrops(p_49951_, (ServerLevel)p_49952_, p_49953_, (BlockEntity)null).forEach((p_152406_) -> {
            popResource(p_49952_, p_49953_, p_152406_);
         });
         p_49951_.spawnAfterBreak((ServerLevel)p_49952_, p_49953_, ItemStack.EMPTY, true);
      }

   }

   public static void dropResources(BlockState p_49893_, LevelAccessor p_49894_, BlockPos p_49895_, @Nullable BlockEntity p_49896_) {
      if (p_49894_ instanceof ServerLevel) {
         getDrops(p_49893_, (ServerLevel)p_49894_, p_49895_, p_49896_).forEach((p_49859_) -> {
            popResource((ServerLevel)p_49894_, p_49895_, p_49859_);
         });
         p_49893_.spawnAfterBreak((ServerLevel)p_49894_, p_49895_, ItemStack.EMPTY, true);
      }

   }

   public static void dropResources(BlockState p_49882_, Level p_49883_, BlockPos p_49884_, @Nullable BlockEntity p_49885_, @Nullable Entity p_49886_, ItemStack p_49887_) {
      if (p_49883_ instanceof ServerLevel) {
         getDrops(p_49882_, (ServerLevel)p_49883_, p_49884_, p_49885_, p_49886_, p_49887_).forEach((p_49944_) -> {
            popResource(p_49883_, p_49884_, p_49944_);
         });
         p_49882_.spawnAfterBreak((ServerLevel)p_49883_, p_49884_, p_49887_, true);
      }

   }

   public static void popResource(Level p_49841_, BlockPos p_49842_, ItemStack p_49843_) {
      double d0 = (double)EntityType.ITEM.getHeight() / 2.0D;
      double d1 = (double)p_49842_.getX() + 0.5D + Mth.nextDouble(p_49841_.random, -0.25D, 0.25D);
      double d2 = (double)p_49842_.getY() + 0.5D + Mth.nextDouble(p_49841_.random, -0.25D, 0.25D) - d0;
      double d3 = (double)p_49842_.getZ() + 0.5D + Mth.nextDouble(p_49841_.random, -0.25D, 0.25D);
      popResource(p_49841_, () -> {
         return new ItemEntity(p_49841_, d1, d2, d3, p_49843_);
      }, p_49843_);
   }

   public static void popResourceFromFace(Level p_152436_, BlockPos p_152437_, Direction p_152438_, ItemStack p_152439_) {
      int i = p_152438_.getStepX();
      int j = p_152438_.getStepY();
      int k = p_152438_.getStepZ();
      double d0 = (double)EntityType.ITEM.getWidth() / 2.0D;
      double d1 = (double)EntityType.ITEM.getHeight() / 2.0D;
      double d2 = (double)p_152437_.getX() + 0.5D + (i == 0 ? Mth.nextDouble(p_152436_.random, -0.25D, 0.25D) : (double)i * (0.5D + d0));
      double d3 = (double)p_152437_.getY() + 0.5D + (j == 0 ? Mth.nextDouble(p_152436_.random, -0.25D, 0.25D) : (double)j * (0.5D + d1)) - d1;
      double d4 = (double)p_152437_.getZ() + 0.5D + (k == 0 ? Mth.nextDouble(p_152436_.random, -0.25D, 0.25D) : (double)k * (0.5D + d0));
      double d5 = i == 0 ? Mth.nextDouble(p_152436_.random, -0.1D, 0.1D) : (double)i * 0.1D;
      double d6 = j == 0 ? Mth.nextDouble(p_152436_.random, 0.0D, 0.1D) : (double)j * 0.1D + 0.1D;
      double d7 = k == 0 ? Mth.nextDouble(p_152436_.random, -0.1D, 0.1D) : (double)k * 0.1D;
      popResource(p_152436_, () -> {
         return new ItemEntity(p_152436_, d2, d3, d4, p_152439_, d5, d6, d7);
      }, p_152439_);
   }

   private static void popResource(Level p_152441_, Supplier<ItemEntity> p_152442_, ItemStack p_152443_) {
      if (!p_152441_.isClientSide && !p_152443_.isEmpty() && p_152441_.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
         ItemEntity itementity = p_152442_.get();
         itementity.setDefaultPickUpDelay();
         p_152441_.addFreshEntity(itementity);
      }
   }

   protected void popExperience(ServerLevel p_49806_, BlockPos p_49807_, int p_49808_) {
      if (p_49806_.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
         ExperienceOrb.award(p_49806_, Vec3.atCenterOf(p_49807_), p_49808_);
      }

   }

   public float getExplosionResistance() {
      return this.explosionResistance;
   }

   public void wasExploded(Level p_49844_, BlockPos p_49845_, Explosion p_49846_) { //типо из за взрыва сам взрывается тнт например
   }

   public void stepOn(Level p_152431_, BlockPos p_152432_, BlockState p_152433_, Entity p_152434_) {
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext p_49820_) {
      return this.defaultBlockState();
   }

   public void playerDestroy(Level p_49827_, Player p_49828_, BlockPos p_49829_, BlockState p_49830_, @Nullable BlockEntity p_49831_, ItemStack p_49832_) {
      p_49828_.awardStat(Stats.BLOCK_MINED.get(this));
      p_49828_.causeFoodExhaustion(0.005F);
      dropResources(p_49830_, p_49827_, p_49829_, p_49831_, p_49828_, p_49832_);
   }

   public void setPlacedBy(Level p_49847_, BlockPos p_49848_, BlockState p_49849_, @Nullable LivingEntity p_49850_, ItemStack p_49851_) {
   }

   public boolean isPossibleToRespawnInThis(BlockState p_279289_) {
      return !p_279289_.isSolid() && !p_279289_.liquid();
   }

   public MutableComponent getName() {
      return Component.translatable(this.getDescriptionId());
   }

   public String getDescriptionId() {
      if (this.descriptionId == null) {
         this.descriptionId = Util.makeDescriptionId("block", BuiltInRegistries.BLOCK.getKey(this));
      }

      return this.descriptionId;
   }

   public void fallOn(Level p_152426_, BlockState p_152427_, BlockPos p_152428_, Entity p_152429_, float p_152430_) {
      p_152429_.causeFallDamage(p_152430_, 1.0F, p_152429_.damageSources().fall());
   }

   public void updateEntityAfterFallOn(BlockGetter p_49821_, Entity p_49822_) {
      p_49822_.setDeltaMovement(p_49822_.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
   }

   public ItemStack getCloneItemStack(BlockGetter p_49823_, BlockPos p_49824_, BlockState p_49825_) {
      return new ItemStack(this);
   }

   public float getFriction() {
      return this.friction;
   }

   public float getSpeedFactor() {
      return this.speedFactor;
   }

   public float getJumpFactor() {
      return this.jumpFactor;
   }

   protected void spawnDestroyParticles(Level p_152422_, Player p_152423_, BlockPos p_152424_, BlockState p_152425_) {
      p_152422_.levelEvent(p_152423_, 2001, p_152424_, getId(p_152425_));
   }

   public void playerWillDestroy(Level p_49852_, BlockPos p_49853_, BlockState p_49854_, Player p_49855_) {
      this.spawnDestroyParticles(p_49852_, p_49855_, p_49853_, p_49854_);
      if (p_49854_.is(BlockTags.GUARDED_BY_PIGLINS)) {
         PiglinAi.angerNearbyPiglins(p_49855_, false);
      }

      p_49852_.gameEvent(GameEvent.BLOCK_DESTROY, p_49853_, GameEvent.Context.of(p_49855_, p_49854_));
   }

   public void handlePrecipitation(BlockState p_152450_, Level p_152451_, BlockPos p_152452_, Biome.Precipitation p_152453_) {
   }

   public boolean dropFromExplosion(Explosion p_49826_) {
      return true;
   }

   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_49915_) {
   }

   public StateDefinition<Block, BlockState> getStateDefinition() {
      return this.stateDefinition;
   }

   protected final void registerDefaultState(BlockState p_49960_) {
      this.defaultBlockState = p_49960_;
   }

   public final BlockState defaultBlockState() {
      return this.defaultBlockState;
   }

   public final BlockState withPropertiesOf(BlockState p_152466_) {
      BlockState blockstate = this.defaultBlockState();

      for(Property<?> property : p_152466_.getBlock().getStateDefinition().getProperties()) {
         if (blockstate.hasProperty(property)) {
            blockstate = copyProperty(p_152466_, blockstate, property);
         }
      }

      return blockstate;
   }

   private static <T extends Comparable<T>> BlockState copyProperty(BlockState p_152455_, BlockState p_152456_, Property<T> p_152457_) {
      return p_152456_.setValue(p_152457_, p_152455_.getValue(p_152457_));
   }

   public SoundType getSoundType(BlockState p_49963_) {
      return this.soundType;
   }

   public Item asItem() {
      if (this.item == null) {
         this.item = Item.byBlock(this);
      }

      return this.item;
   }

   public boolean hasDynamicShape() {
      return this.dynamicShape;
   }

   public String toString() {
      return "Block{" + BuiltInRegistries.BLOCK.getKey(this) + "}";
   }

   public void appendHoverText(ItemStack p_49816_, @Nullable BlockGetter p_49817_, List<Component> p_49818_, TooltipFlag p_49819_) {
   }

   protected Block asBlock() {
      return this;
   }

   protected ImmutableMap<BlockState, VoxelShape> getShapeForEachState(Function<BlockState, VoxelShape> p_152459_) {
      return this.stateDefinition.getPossibleStates().stream().collect(ImmutableMap.toImmutableMap(Function.identity(), p_152459_));
   }

   /** @deprecated */
   @Deprecated
   public Holder.Reference<Block> builtInRegistryHolder() {
      return this.builtInRegistryHolder;
   }

   protected void tryDropExperience(ServerLevel p_220823_, BlockPos p_220824_, ItemStack p_220825_, IntProvider p_220826_) {
      if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, p_220825_) == 0) {
         int i = p_220826_.sample(p_220823_.random);
         if (i > 0) {
            this.popExperience(p_220823_, p_220824_, i);
         }
      }

   }

   public static final class BlockStatePairKey {
      private final BlockState first;
      private final BlockState second;
      private final Direction direction;

      public BlockStatePairKey(BlockState p_49984_, BlockState p_49985_, Direction p_49986_) {
         this.first = p_49984_;
         this.second = p_49985_;
         this.direction = p_49986_;
      }

      public boolean equals(Object p_49988_) {
         if (this == p_49988_) {
            return true;
         } else if (!(p_49988_ instanceof Block.BlockStatePairKey)) {
            return false;
         } else {
            Block.BlockStatePairKey block$blockstatepairkey = (Block.BlockStatePairKey)p_49988_;
            return this.first == block$blockstatepairkey.first && this.second == block$blockstatepairkey.second && this.direction == block$blockstatepairkey.direction;
         }
      }

      public int hashCode() {
         int i = this.first.hashCode();
         i = 31 * i + this.second.hashCode();
         return 31 * i + this.direction.hashCode();
      }
   }
}