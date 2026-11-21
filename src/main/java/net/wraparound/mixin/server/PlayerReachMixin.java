package net.wraparound.mixin.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.wraparound.Topology;
import net.wraparound.Wrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A player can reach an entity standing just across a seam. */
@Mixin(Player.class)
public class PlayerReachMixin {
	@ModifyVariable(method = {"isWithinEntityInteractionRange(Lnet/minecraft/world/phys/AABB;D)Z", "isWithinAttackRange"}, at = @At("HEAD"), argsOnly = true)
	private AABB wraparound$nearestCopy(AABB box) {
		Player self = (Player) (Object) this;
		if (!Wrap.server(self.level())) {
			return box;
		}

		Topology topology = Wrap.of(self.level());
		var center = box.getCenter();
		return topology.nearest(center.x, center.z, self.getX(), self.getZ()).apply(box);
	}
}
