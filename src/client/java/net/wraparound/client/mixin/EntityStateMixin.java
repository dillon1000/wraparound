package net.wraparound.client.mixin;

import net.minecraft.world.entity.Entity;
import net.wraparound.Transform;
import net.wraparound.client.ClientWrap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public class EntityStateMixin implements ClientWrap.EntityState {
	@Unique
	private @Nullable Transform wraparound$transform;

	@Override
	public @Nullable Transform wraparound$transform() {
		return this.wraparound$transform;
	}

	@Override
	public void wraparound$setTransform(@Nullable Transform transform) {
		this.wraparound$transform = transform;
	}
}
