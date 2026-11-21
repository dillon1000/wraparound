package net.wraparound.mixin.gen;

import net.minecraft.world.level.levelgen.RandomState;
import net.wraparound.gen.GenHooks;
import net.wraparound.gen.Periodizer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets density function leaves find the periodizer of the RandomState compiling them. */
@Mixin(targets = "net.minecraft.world.level.levelgen.RandomState$1")
public class RandomStateCompileContextMixin implements GenHooks.PeriodizerHolder {
	@Shadow
	@Final
	RandomState this$0;

	@Override
	public @Nullable Periodizer wraparound$periodizer() {
		return GenHooks.periodizer(this.this$0);
	}
}
