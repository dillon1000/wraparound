package net.wraparound.mixin.gen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.Entity;
import net.wraparound.Topology;
import net.wraparound.Wrap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chunks outside the fundamental domain only exist so their neighbours can generate. Mobs spawned
 * into them would be stranded copies, so drop them.
 */
@Mixin(WorldGenRegion.class)
public class WorldGenRegionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void wraparound$skipOutsideDomain(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		Topology topology = Wrap.of(this.level);
		if (topology.active() && !topology.inDomain(entity.getX(), entity.getZ())) {
			cir.setReturnValue(true);
		}
	}
}
