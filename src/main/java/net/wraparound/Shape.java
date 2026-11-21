package net.wraparound;

import net.minecraft.util.StringRepresentable;

/**
 * How the edges of the square world are glued together.
 *
 * <ul>
 *   <li>{@link #CYLINDER}: east and west edges are glued. North and south go on forever.</li>
 *   <li>{@link #TORUS}: east-west and north-south edges are both glued.</li>
 *   <li>{@link #KLEIN_BOTTLE}: east-west glued, north-south glued with a left-right flip.</li>
 * </ul>
 */
public enum Shape implements StringRepresentable {
	FLAT("flat"),
	CYLINDER("cylinder"),
	TORUS("torus"),
	KLEIN_BOTTLE("klein_bottle");

	private final String id;

	Shape(String id) {
		this.id = id;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public boolean wrapsX() {
		return this != FLAT;
	}

	public boolean wrapsZ() {
		return this == TORUS || this == KLEIN_BOTTLE;
	}

	/** Whether crossing the north-south seam mirrors east and west. */
	public boolean mirrorsAcrossZ() {
		return this == KLEIN_BOTTLE;
	}
}
