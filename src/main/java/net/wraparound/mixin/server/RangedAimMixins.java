package net.wraparound.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import net.wraparound.server.Relative;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ranged attacks aim at {@code target.getX() - shooter.getX()}. Here the target's coordinates are
 * read at its copy nearest the shooter, so arrows, potions, snowballs, tridents, spit and fireballs
 * fly across seams toward the target instead of away from it.
 */
public final class RangedAimMixins {
	private RangedAimMixins() {
	}

	private static final String LIVING_X = "Lnet/minecraft/world/entity/LivingEntity;getX()D";
	private static final String LIVING_Z = "Lnet/minecraft/world/entity/LivingEntity;getZ()D";

	@Mixin({AbstractSkeleton.class, Witch.class, SnowGolem.class, Drowned.class, Illusioner.class})
	public static abstract class Shooters {
		@WrapOperation(method = "performRangedAttack", at = @At(value = "INVOKE", target = LIVING_X), require = 0)
		private double wraparound$x(LivingEntity target, Operation<Double> original) {
			return Relative.x(target, (Entity) (Object) this);
		}

		@WrapOperation(method = "performRangedAttack", at = @At(value = "INVOKE", target = LIVING_Z), require = 0)
		private double wraparound$z(LivingEntity target, Operation<Double> original) {
			return Relative.z(target, (Entity) (Object) this);
		}
	}

	@Mixin(Llama.class)
	public static abstract class Spit {
		@WrapOperation(method = "spit", at = @At(value = "INVOKE", target = LIVING_X))
		private double wraparound$x(LivingEntity target, Operation<Double> original) {
			return Relative.x(target, (Entity) (Object) this);
		}

		@WrapOperation(method = "spit", at = @At(value = "INVOKE", target = LIVING_Z))
		private double wraparound$z(LivingEntity target, Operation<Double> original) {
			return Relative.z(target, (Entity) (Object) this);
		}
	}

	@Mixin(CrossbowItem.class)
	public static abstract class Crossbow {
		@WrapOperation(method = "shootProjectile", at = @At(value = "INVOKE", target = LIVING_X))
		private double wraparound$x(LivingEntity entity, Operation<Double> original, @Local(argsOnly = true, ordinal = 0) LivingEntity shooter) {
			return entity == shooter ? original.call(entity) : Relative.x(entity, shooter);
		}

		@WrapOperation(method = "shootProjectile", at = @At(value = "INVOKE", target = LIVING_Z))
		private double wraparound$z(LivingEntity entity, Operation<Double> original, @Local(argsOnly = true, ordinal = 0) LivingEntity shooter) {
			return entity == shooter ? original.call(entity) : Relative.z(entity, shooter);
		}
	}

	@Mixin(targets = "net.minecraft.world.entity.monster.Blaze$BlazeAttackGoal")
	public static abstract class BlazeGoal {
		@Shadow
		@Final
		private Blaze blaze;

		@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = LIVING_X))
		private double wraparound$x(LivingEntity target, Operation<Double> original) {
			return Relative.x(target, this.blaze);
		}

		@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = LIVING_Z))
		private double wraparound$z(LivingEntity target, Operation<Double> original) {
			return Relative.z(target, this.blaze);
		}
	}

	@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal")
	public static abstract class GhastGoal {
		@Shadow
		@Final
		private Ghast ghast;

		@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = LIVING_X))
		private double wraparound$x(LivingEntity target, Operation<Double> original) {
			return Relative.x(target, this.ghast);
		}

		@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = LIVING_Z))
		private double wraparound$z(LivingEntity target, Operation<Double> original) {
			return Relative.z(target, this.ghast);
		}
	}

	@Mixin(Ghast.class)
	public static abstract class GhastFacing {
		@WrapOperation(method = "faceMovementDirection", at = @At(value = "INVOKE", target = LIVING_X))
		private static double wraparound$x(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Mob ghast) {
			return Relative.x(target, ghast);
		}

		@WrapOperation(method = "faceMovementDirection", at = @At(value = "INVOKE", target = LIVING_Z))
		private static double wraparound$z(LivingEntity target, Operation<Double> original, @Local(argsOnly = true) Mob ghast) {
			return Relative.z(target, ghast);
		}
	}

	/** Knockback pushes away from the attacker's nearest copy. */
	@Mixin(LivingEntity.class)
	public static abstract class Knockback {
		@WrapOperation(method = "dealDefaultKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;getSourcePosition()Lnet/minecraft/world/phys/Vec3;"))
		private Vec3 wraparound$source(DamageSource source, Operation<Vec3> original) {
			Vec3 pos = original.call(source);
			return pos == null ? null : Relative.pos(pos, (Entity) (Object) this);
		}

		@WrapOperation(
			method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D")
		)
		private double wraparound$sightX(Entity target, Operation<Double> original) {
			return Relative.x(target, (Entity) (Object) this);
		}

		@WrapOperation(
			method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D")
		)
		private double wraparound$sightZ(Entity target, Operation<Double> original) {
			return Relative.z(target, (Entity) (Object) this);
		}

		@WrapOperation(method = "blockedByItem", at = @At(value = "INVOKE", target = LIVING_X))
		private double wraparound$blockX(LivingEntity defender, Operation<Double> original) {
			return Relative.x(defender, (Entity) (Object) this);
		}

		@WrapOperation(method = "blockedByItem", at = @At(value = "INVOKE", target = LIVING_Z))
		private double wraparound$blockZ(LivingEntity defender, Operation<Double> original) {
			return Relative.z(defender, (Entity) (Object) this);
		}
	}
}
