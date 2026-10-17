package io.github.traelmyx.vaultfix.mixin;

import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.ICapabilityProvider;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import io.github.traelmyx.vaultfix.VaultItemLossFix;
import io.github.traelmyx.vaultfix.VaultRefresh;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * The controller's combined inventory is rebuilt whenever one of the vault's parts loads or unloads, so it always uses the
 * inventories of the parts that are actually in the world.
 */
@Mixin(value = ItemVaultBlockEntity.class, remap = false)
public abstract class ItemVaultBlockEntityMixin extends SmartBlockEntity implements VaultRefresh {
	@Shadow
	protected ICapabilityProvider<IItemHandler> itemCapability;
	@Shadow
	protected BlockPos controller;

	@Shadow
	public abstract boolean isController();

	private ItemVaultBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void vault_item_loss_fix$rebuild() {
		if (itemCapability == null)
			return;
		itemCapability = null;
		invalidateCapabilities();
	}

	/** First tick of a freshly loaded part: the controller has to rebuild to see this part's inventory. */
	@Override
	public void initialize() {
		super.initialize();
		vault_item_loss_fix$tellController();
	}

	/** Unloaded or removed: the controller must stop using this part's inventory. */
	@Override
	public void invalidate() {
		super.invalidate();
		vault_item_loss_fix$tellController();
	}

	@Unique
	private void vault_item_loss_fix$tellController() {
		if (!VaultItemLossFix.enabled || level == null || level.isClientSide() || isController() || controller == null)
			return;
		// never load the controller's chunk just to tell it
		if (!level.isLoaded(controller))
			return;
		if (level.getBlockEntity(controller) instanceof VaultRefresh refresh && refresh != (Object) this)
			refresh.vault_item_loss_fix$rebuild();
	}
}
