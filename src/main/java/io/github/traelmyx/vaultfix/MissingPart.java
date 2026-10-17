package io.github.traelmyx.vaultfix;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Stands in for a vault part that isn't loaded: holds nothing and accepts nothing, so no item can vanish into it. */
public class MissingPart extends ItemStackHandler {
	public MissingPart(int slots) {
		super(slots);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		return stack;
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		return false;
	}
}
