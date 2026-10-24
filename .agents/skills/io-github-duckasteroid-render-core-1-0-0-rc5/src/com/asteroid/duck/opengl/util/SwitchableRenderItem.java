package com.asteroid.duck.opengl.util;

/**
 * A {@link CompositeRenderItem} that selects one of its children to render
 */
public class SwitchableRenderItem extends CompositeRenderItem {
	private int selectedItem = 0;

	@Override
	public void doRender(RenderContext ctx) {
		if (!items().isEmpty()) {
			items().get(selectedItem).doRender(ctx);
		}
	}

	public int getSelectedItem() {
		return selectedItem;
	}

	public void setSelectedItem(int item) {
		selectedItem = item;
		if (selectedItem < 0) {
			selectedItem = items().size() -1;
		}
		if (selectedItem >= items().size()) {
			selectedItem = 0;
		}
	}

	public void next() {
		setSelectedItem(getSelectedItem() + 1);
	}

	public void previous() {
    	setSelectedItem(getSelectedItem() - 1);
  	}
}
