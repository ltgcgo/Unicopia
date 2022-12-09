package com.minelittlepony.unicopia.item;

import java.util.*;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableMultimap;
import com.minelittlepony.unicopia.USounds;
import com.minelittlepony.unicopia.entity.*;
import com.minelittlepony.unicopia.entity.player.*;
import com.minelittlepony.unicopia.util.MagicalDamageSource;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.*;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringHelper;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class AlicornAmuletItem extends AmuletItem implements ItemTracker.Trackable, ItemImpl.ClingyItem, ItemImpl.GroundTickCallback {
    private static final float EFFECT_UPDATE_FREQUENCY = 1000000;
    private static final UUID EFFECT_UUID = UUID.fromString("c0a870f5-99ef-4716-a23e-f320ee834b26");
    private static final Map<EntityAttribute, Float> EFFECT_SCALES = Map.of(
            EntityAttributes.GENERIC_ATTACK_DAMAGE, 0.2F,
            EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.05F,
            EntityAttributes.GENERIC_ATTACK_SPEED, 0.2F,
            EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 0.001F,
            EntityAttributes.GENERIC_ARMOR, 0.01F
    );

    public AlicornAmuletItem(FabricItemSettings settings) {
        super(settings, 0, ImmutableMultimap.of());
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext tooltipContext) {
        Pony iplayer = Pony.of(MinecraftClient.getInstance().player);

        if (iplayer != null) {
            long attachedTime = iplayer.getArmour().getTicks(this);
            if (attachedTime > 0) {
                tooltip.add(Text.translatable(getTranslationKey() + ".lore", StringHelper.formatTicks((int)attachedTime)));
            }
        }
    }

    @Override
    public ParticleEffect getParticleEffect(IItemEntity entity) {
        return ((ItemEntity)entity).world.random.nextBoolean() ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME;
    }

    @Override
    public boolean isClingy(ItemStack stack) {
        return true;
    }

    @Override
    public float getFollowDistance(IItemEntity entity) {
        return Math.max(20, ItemImpl.ClingyItem.super.getFollowDistance(entity));
    }

    @Override
    public float getFollowSpeed(IItemEntity entity) {
        return Math.max(0.12F, ItemImpl.ClingyItem.super.getFollowSpeed(entity));
    }

    @Override
    public void interactWithPlayer(IItemEntity item, PlayerEntity player) {
        ItemEntity entity = (ItemEntity)item;

        if (!player.world.isClient && !entity.isRemoved()) {
            if (player.getPos().distanceTo(entity.getPos()) < 0.5) {
               if (entity.world.random.nextInt(150) == 0) {
                   entity.setPickupDelay(0);
                   entity.onPlayerCollision(player);

                   if (player.getMainHandStack().getItem() == this) {
                       TypedActionResult<ItemStack> result = use(player.world, player, Hand.MAIN_HAND);

                       if (result.getResult() == ActionResult.SUCCESS) {
                           entity.setPickupDelay(1000);
                           entity.setRemoved(RemovalReason.DISCARDED);
                       }
                   }
               }
            }
        }
    }

    @Override
    public void onEquipped(Living<?> wearer) {
        wearer.getReferenceWorld().playSound(null, wearer.getOrigin(), USounds.ITEM_ALICORN_AMULET_CURSE, wearer.getEntity().getSoundCategory(), 3, 1);
    }

    @Override
    public void onUnequipped(Living<?> wearer, long timeWorn) {
        float attachedTime = timeWorn / 100F;

        LocalDifficulty difficulty = wearer.getReferenceWorld().getLocalDifficulty(wearer.getOrigin());
        float amount = attachedTime * (1 + difficulty.getClampedLocalDifficulty());

        amount = Math.min(amount, wearer.getMaster().getMaxHealth());

        if (wearer.getMaster() instanceof PlayerEntity) {
            ((PlayerEntity)wearer.getMaster()).getHungerManager().setFoodLevel(1);
        }
        wearer.getMaster().damage(MagicalDamageSource.ALICORN_AMULET, amount);
        wearer.getMaster().addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 1));

        if (attachedTime > 120) {
            wearer.getMaster().takeKnockback(1, 1, 1);
            wearer.updateVelocity();
        }

        EFFECT_SCALES.keySet().forEach(attribute -> {
            wearer.getMaster().getAttributeInstance(attribute).tryRemoveModifier(EFFECT_UUID);
        });
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {

        if (world.isClient) {
            return;
        }

        // if we're in the main hand, try to equip ourselves
        if (entity instanceof PlayerEntity player && selected && !isApplicable(player) && world.random.nextInt(320) == 0) {
            use(world, player, Hand.MAIN_HAND);
            return;
        }

        Living<?> living = Living.living(entity);

        if (!living.getArmour().contains(this)) {
            return;
        }

        float attachedTicks = living.getArmour().getTicks(this);
        float seconds = attachedTicks / EFFECT_UPDATE_FREQUENCY;

        if (entity instanceof PlayerEntity player) {
            // healing effect
            if (player.getHealth() < player.getMaxHealth()) {
                player.heal(0.5F);
            } else if (player.canConsume(false)) {
                player.getHungerManager().add(1, 0);
            } else {
                player.removeStatusEffect(StatusEffects.NAUSEA);
            }

            MagicReserves reserves = ((Pony)living).getMagicalReserves();

            // constantly increase exertion
            if (reserves.getExertion().get() < reserves.getExertion().getMax()) {
                reserves.getExertion().add(2);
            }

            if (reserves.getEnergy().get() < 0.005F + seconds) {
                reserves.getEnergy().add(2);
            }
        }

        // every 1 second, update modifiers
        if (attachedTicks % EFFECT_UPDATE_FREQUENCY == 0) {
            EFFECT_SCALES.entrySet().forEach(attribute -> {
                EntityAttributeInstance instance = living.getMaster().getAttributeInstance(attribute.getKey());
                EntityAttributeModifier modifier = instance.getModifier(EFFECT_UUID);
                float desiredValue = attribute.getValue() * seconds;
                if (!MathHelper.approximatelyEquals(desiredValue, modifier.getValue())) {
                    if (modifier != null) {
                        instance.removeModifier(modifier);
                    }
                    instance.addTemporaryModifier(new EntityAttributeModifier(EFFECT_UUID, "Alicorn Amulet Modifier", attribute.getValue() * seconds, EntityAttributeModifier.Operation.ADDITION));
                }
            });
        }
    }

    @Override
    public ActionResult onGroundTick(IItemEntity item) {
        ItemEntity entity = (ItemEntity)item;

        if (entity.world.random.nextInt(500) == 0) {
            entity.world.playSound(null, entity.getBlockPos(), USounds.ITEM_ALICORN_AMULET_AMBIENT, SoundCategory.HOSTILE, 0.5F, 1);
        }

        return ActionResult.PASS;
    }
}
