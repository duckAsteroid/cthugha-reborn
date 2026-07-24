package com.asteroid.duck.opengl.util.toggle;

import com.asteroid.duck.opengl.util.RenderContext;
import com.asteroid.duck.opengl.util.keys.KeyCombination;

public class SimpleToggle implements Toggle {
	private final char toggleKey;
	private final boolean latched;
	private final String description;
	private boolean enabled;

	public SimpleToggle(boolean latched, char toggleKey, String description) {
		this.toggleKey = toggleKey;
		this.latched = latched;
		this.description = description;
	}

	@Override
	public void init(RenderContext ctx) {
		ctx.getKeyRegistry().registerKeyAction(KeyCombination.simple(toggleKey), this::toggle, description);
	}

	private void toggle() {
		this.enabled = !enabled;
	}

	@Override
	public boolean isRenderEnabled(RenderContext ctx) {
		if (!latched) {
			// an unlatched toggle just returns current state of enabled
			return enabled;
		}
		if (enabled) {
			// latched toggle will return enabled once each time it is toggled
			enabled = false;
      return true;
		}
		return false;
	}
}
