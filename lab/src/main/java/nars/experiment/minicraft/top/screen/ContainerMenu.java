package nars.experiment.minicraft.top.screen;

import nars.experiment.minicraft.top.entity.Inventory;
import nars.experiment.minicraft.top.entity.Player;
import nars.experiment.minicraft.top.gfx.Font;
import nars.experiment.minicraft.top.gfx.Screen;

public class ContainerMenu extends Menu {
	private final Player player;
	private final Inventory container;
	private int selected;
	private final String title;
	private int oSelected;
	private int window;

	public ContainerMenu(Player player, String title, Inventory container) {
		this.player = player;
		this.title = title;
		this.container = container;
	}

	@Override
	public void tick() {
		if (input.menu.clicked) game.setMenu(null);

		if (input.left.clicked) {
			window = 0;
			int tmp = selected;
			selected = oSelected;
			oSelected = tmp;
		}
		if (input.right.clicked) {
			window = 1;
			int tmp = selected;
			selected = oSelected;
			oSelected = tmp;
		}

		Inventory i = window == 1 ? player.inventory : container;
		Inventory i2 = window == 0 ? player.inventory : container;

		int len = i.items.size();
		if (selected < 0) selected = 0;
		if (selected >= len) selected = len - 1;

		if (input.up.clicked) selected--;
		if (input.down.clicked) selected++;

		if (len == 0) selected = 0;
		if (selected < 0) selected += len;
		if (selected >= len) selected -= len;

		if (input.attack.clicked && len > 0) {
			i2.add(oSelected, i.items.remove(selected));
			if (selected >= i.items.size()) selected = i.items.size() - 1;
		}
	}

	@Override
	public void render(Screen screen) {
		if (window == 1) screen.setOffset(6 * 8, 0);
		Font.renderFrame(screen, title, 1, 1, 12, 11);
		renderItemList(screen, 1, 1, 12, 11, container.items, window == 0 ? selected : -oSelected - 1);

		Font.renderFrame(screen, "inventory", 13, 1, 13 + 11, 11);
		renderItemList(screen, 13, 1, 13 + 11, 11, player.inventory.items, window == 1 ? selected : -oSelected - 1);
		screen.setOffset(0, 0);
	}
}