package io.github.traelmyx.vaultfix.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.ICapabilityProvider;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import io.github.traelmyx.vaultfix.MissingPart;
import io.github.traelmyx.vaultfix.VaultItemLossFix;
import io.github.traelmyx.vaultfix.VaultRefresh;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The controller's combined inventory is rebuilt whenever one of the vault's parts loads or unloads, and while a part is
 * missing its slots refuse items instead of swallowing them. Until every part is present, the combined inventory is
 * rebuilt on the next lookup.
 */
@Mixin(value = ItemVaultBlockEntity.class, remap = false)
public abstract class ItemVaultBlockEntityMixin extends SmartBlockEntity implements VaultRefresh {
	@Shadow
	protected ICapabilityProvider<IItemHandler> itemCapability;
	@Shadow
	protected ItemStackHandler inventory;
	@Shadow
	protected BlockPos controller;

	@Shadow
	public abstract boolean isController();

	@Unique
	private boolean vault_item_loss_fix$incomplete;

	private ItemVaultBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void vault_item_loss_fix$rebuild() {
		if (itemCapability == null)
			return;
		itemCapability = null;
		vault_item_loss_fix$incomplete = false;
		invalidateCapabilities();
	}

	@Inject(method = "initCapability", at = @At("HEAD"), require = 0)
	private void vault_item_loss_fix$retryIncomplete(CallbackInfo ci) {
		if (VaultItemLossFix.enabled && vault_item_loss_fix$incomplete && itemCapability != null) {
			itemCapability = null;
			vault_item_loss_fix$incomplete = false;
		}
	}

	@ModifyExpressionValue(method = "initCapability", require = 0, at = @At(value = "NEW", target = "()Lnet/neoforged/neoforge/items/ItemStackHandler;"))
	private ItemStackHandler vault_item_loss_fix$refuseMissingPart(ItemStackHandler throwaway) {
		if (!VaultItemLossFix.enabled)
			return throwaway;
		vault_item_loss_fix$incomplete = true;
		return new MissingPart(inventory.getSlots());
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
