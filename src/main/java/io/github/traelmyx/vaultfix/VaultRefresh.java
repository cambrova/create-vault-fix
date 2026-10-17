package io.github.traelmyx.vaultfix;

/** Lets one vault part tell the controller to rebuild its combined inventory. */
public interface VaultRefresh {
	void vault_item_loss_fix$rebuild();
}
