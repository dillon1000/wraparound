package net.wraparound.mixin.server;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Wrap;
import net.wraparound.server.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On the server, distances between entities are measured through the seams. This covers despawning,
 * target selection, follow ranges, leashes, fishing lines and most other "is it close" checks.
 */
@Mixin(Entity.class)
public abstract class EntityDistanceMixin {
	private Entity wraparound$self() {
		return (Entity) (Object) this;
	}

	@Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
	private void wraparound$distanceToPoint(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
		Entity self = wraparound$self();
		if (Wrap.server(self.level())) {
			cir.setReturnValue(Relative.distanceSqr(self, x, y, z));
		}
	}

	@Inject(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", at = @At("HEAD"), cancellable = true)
	private void wraparound$distanceToVec(Vec3 pos, CallbackInfoReturnable<Double> cir) {
		Entity self = wraparound$self();
		if (Wrap.server(self.level())) {
			cir.setReturnValue(Relative.distanceSqr(self, pos.x, pos.y, pos.z));
		}
	}

	@Inject(method = "distanceTo", at = @At("HEAD"), cancellable = true)
	private void wraparound$distanceTo(Entity other, CallbackInfoReturnable<Float> cir) {
		Entity self = wraparound$self();
		if (Wrap.server(self.level())) {
			cir.setReturnValue((float) Math.sqrt(Relative.distanceSqr(self, other.getX(), other.getY(), other.getZ())));
		}
	}

	@Inject(method = "closerThan(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
	private void wraparound$closerThan(Entity other, double distance, CallbackInfoReturnable<Boolean> cir) {
		Entity self = wraparound$self();
		if (Wrap.server(self.level())) {
			cir.setReturnValue(Relative.distanceSqr(self, other.getX(), other.getY(), other.getZ()) < distance * distance);
		}
	}

	@Inject(method = "closerThan(Lnet/minecraft/world/entity/Entity;DD)Z", at = @At("HEAD"), cancellable = true)
	private void wraparound$closerThanXZ(Entity other, double distanceXZ, double distanceY, CallbackInfoReturnable<Boolean> cir) {
		Entity self = wraparound$self();
		if (Wrap.server(self.level())) {
			double dx = Relative.x(other, self) - self.getX();
			double dz = Relative.z(other, self) - self.getZ();
			double dy = other.getY() - self.getY();
			cir.setReturnValue(Mth.lengthSquared(dx, dz) < Mth.square(distanceXZ) && Mth.square(dy) < Mth.square(distanceY));
		}
	}
}
