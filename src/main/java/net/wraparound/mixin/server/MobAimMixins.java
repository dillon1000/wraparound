package net.wraparound.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.AABB;
import net.wraparound.Transform;
import net.wraparound.Wrap;
import net.wraparound.server.Relative;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mobs look at, walk toward, path to and swing at the copy of their target that is closest. */
public final class MobAimMixins {
	private MobAimMixins() {
	}

	@Mixin(LookControl.class)
	public static abstract class Look {
		@Shadow
		@Final
		protected Mob mob;

		@Shadow
		public abstract void setLookAt(double x, double y, double z, float yMaxRotSpeed, float xMaxRotAngle);

		@Inject(method = "setLookAt(DDDFF)V", at = @At("HEAD"), cancellable = true)
		private void wraparound$lookAtNearest(double x, double y, double z, float yMax, float xMax, CallbackInfo ci) {
			if (!Wrap.server(this.mob.level())) {
				return;
			}

			Transform t = Relative.toward(this.mob, x, z);
			if (!t.isIdentity()) {
				setLookAt(t.x(x), y, t.z(z), yMax, xMax);
				ci.cancel();
			}
		}
	}

	@Mixin(MoveControl.class)
	public static abstract class Move {
		@Shadow
		@Final
		protected Mob mob;

		@Shadow
		public abstract void setWantedPosition(double x, double y, double z, double speedModifier);

		@Inject(method = "setWantedPosition", at = @At("HEAD"), cancellable = true)
		private void wraparound$wantNearest(double x, double y, double z, double speed, CallbackInfo ci) {
			if (!Wrap.server(this.mob.level())) {
				return;
			}

			Transform t = Relative.toward(this.mob, x, z);
			if (!t.isIdentity()) {
				setWantedPosition(t.x(x), y, t.z(z), speed);
				ci.cancel();
			}
		}
	}

	@Mixin(PathNavigation.class)
	public static abstract class Navigation {
		@Shadow
		@Final
		protected Mob mob;

		@ModifyVariable(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), argsOnly = true)
		private Set<BlockPos> wraparound$nearestTargets(Set<BlockPos> targets) {
			if (!Wrap.server(this.mob.level())) {
				return targets;
			}

			Set<BlockPos> mapped = new HashSet<>();
			for (BlockPos pos : targets) {
				mapped.add(Relative.toward(this.mob, pos.getX() + 0.5, pos.getZ() + 0.5).apply(pos));
			}

			return mapped;
		}
	}

	@Mixin(Mob.class)
	public static abstract class Melee {
		@WrapOperation(method = "isWithinMeleeAttackRange", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHitbox()Lnet/minecraft/world/phys/AABB;"))
		private AABB wraparound$nearestHitbox(LivingEntity target, Operation<AABB> original) {
			return Relative.box(original.call(target), (Mob) (Object) this);
		}
	}
}
