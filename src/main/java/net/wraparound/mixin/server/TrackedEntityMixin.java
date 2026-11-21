package net.wraparound.mixin.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wraparound.Topology;
import net.wraparound.Wrap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Entities across a seam are sent to players who can see them through it. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class TrackedEntityMixin {
	@Shadow
	@Final
	Entity entity;

	@Redirect(
		method = "updatePlayer",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
	)
	private Vec3 wraparound$wrappedDelta(Vec3 playerPos, Vec3 entityPos) {
		Topology topology = Wrap.of(this.entity.level());
		if (topology.active()) {
			return playerPos.subtract(topology.nearestCopy(entityPos, playerPos));
		}

		return playerPos.subtract(entityPos);
	}
}
