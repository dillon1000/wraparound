package net.wraparound.mixin.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import net.wraparound.Wrap;
import net.wraparound.server.TopologyAware;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelSetupMixin {
	@Shadow
	@Final
	private LevelTicks<Block> blockTicks;

	@Shadow
	@Final
	private LevelTicks<Fluid> fluidTicks;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void wraparound$shareTopology(CallbackInfo ci) {
		var topology = Wrap.of((ServerLevel) (Object) this);
		((TopologyAware) this.blockTicks).wraparound$setTopology(topology);
		((TopologyAware) this.fluidTicks).wraparound$setTopology(topology);
	}
}
