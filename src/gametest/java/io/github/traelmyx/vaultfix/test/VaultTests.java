package io.github.traelmyx.vaultfix.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import io.github.traelmyx.vaultfix.VaultItemLossFix;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Create #4370: a two-block item vault. In a world the two blocks sit in different chunks; here the second block's
 * block entity is unloaded and loaded again from its saved data, which is what a chunk reload does to it, while the
 * controller block stays loaded.
 */
@GameTestHolder(VaultTests.MOD_ID)
@PrefixGameTestTemplate(false)
public class VaultTests {
	public static final String MOD_ID = "vault_item_loss_fix_gametest";

	private static final BlockPos FIRST = new BlockPos(2, 1, 2), SECOND = new BlockPos(3, 1, 2);

	private static void build(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		var state = AllBlocks.ITEM_VAULT.getDefaultState().setValue(ItemVaultBlock.HORIZONTAL_AXIS, Direction.Axis.X);
		helper.setBlock(FIRST, state);
		helper.setBlock(SECOND, state);
	}

	private static IItemHandler vault(GameTestHelper helper) {
		return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(FIRST), null);
	}

	private static CompoundTag unload(GameTestHelper helper, BlockPos pos) {
		var level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		BlockEntity be = level.getBlockEntity(abs);
		CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
		be.onChunkUnloaded();
		level.removeBlockEntity(abs);
		return saved;
	}

	private static void load(GameTestHelper helper, BlockPos pos, CompoundTag saved) {
		var level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		level.setBlockEntity(BlockEntity.loadStatic(abs, level.getBlockState(abs), saved, level.registryAccess()));
	}

	/** Diamonds stored in the second block's own inventory, which is what gets saved for it. */
	private static int diamondsInSecond(GameTestHelper helper) {
		ItemVaultBlockEntity second = (ItemVaultBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(SECOND));
		int n = 0;
		var inv = second.getInventoryOfBlock();
		for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(Items.DIAMOND)) n += inv.getStackInSlot(i).getCount();
		return n;
	}

	private static int secondSlot(IItemHandler vault) {
		return vault.getSlots() - 1;
	}

	// The second block reloads while the vault is in use: items put into its slots afterwards must end up in it

	private static void partReload(GameTestHelper helper, boolean fixed, Check check) {
		VaultItemLossFix.enabled = fixed;
		build(helper);
		helper.runAtTickTime(10, () -> {
			IItemHandler vault = vault(helper);
			helper.assertTrue(vault != null && vault.getSlots() >= 2, "the two blocks did not form one vault");
			load(helper, SECOND, unload(helper, SECOND));
		});
		helper.runAtTickTime(12, () -> {
			IItemHandler vault = vault(helper);
			ItemStack left = vault.insertItem(secondSlot(vault), new ItemStack(Items.DIAMOND, 64), false);
			int stored = 64 - left.getCount();
			int saved = diamondsInSecond(helper);
			VaultItemLossFix.enabled = true;
			System.out.printf("[vault_item_loss_fix] part reloaded, fix %s: vault took %d diamonds, %d are in the block that gets saved%n", fixed ? "on" : "off", stored, saved);
			check.check(stored, saved);
			helper.succeed();
		});
	}

	// Items stored in the second block before it reloads: taking them out afterwards must take them out of that block

	private static void takeAfterReload(GameTestHelper helper, boolean fixed, Check check) {
		VaultItemLossFix.enabled = fixed;
		build(helper);
		helper.runAtTickTime(10, () -> {
			IItemHandler vault = vault(helper);
			helper.assertTrue(vault != null && vault.getSlots() >= 2, "the two blocks did not form one vault");
			helper.assertTrue(vault.insertItem(secondSlot(vault), new ItemStack(Items.DIAMOND, 64), false).isEmpty(), "the vault did not take the diamonds");
			load(helper, SECOND, unload(helper, SECOND));
		});
		helper.runAtTickTime(12, () -> {
			IItemHandler vault = vault(helper);
			int taken = vault.extractItem(secondSlot(vault), 64, false).getCount();
			int left = diamondsInSecond(helper);
			VaultItemLossFix.enabled = true;
			System.out.printf("[vault_item_loss_fix] take after reload, fix %s: took %d diamonds out, %d still in the block that gets saved%n", fixed ? "on" : "off", taken, left);
			check.check(taken, left);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = "take_fixed")
	public static void takingAfterReloadEmptiesTheBlock(GameTestHelper helper) {
		takeAfterReload(helper, true, (taken, left) ->
			helper.assertTrue(taken == 64 && left == 0, "took " + taken + ", " + left + " still in the block"));
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = "take_control")
	public static void takeControlDuplicates(GameTestHelper helper) {
		// without the fix the diamonds come out of a stale copy and stay in the block too
		takeAfterReload(helper, false, (taken, left) ->
			helper.assertTrue(taken + left > 64, "nothing was duplicated without the fix (took " + taken + ", " + left + " left): the bug did not reproduce"));
	}

	private interface Check {
		void check(int taken, int inBlock);
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = "reload_fixed")
	public static void reloadedPartKeepsItems(GameTestHelper helper) {
		partReload(helper, true, (taken, inBlock) -> {
			helper.assertTrue(taken == 64, "the vault took " + taken + " of 64 diamonds");
			helper.assertTrue(inBlock == 64, inBlock + " of 64 diamonds are in the block that gets saved");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = "reload_control")
	public static void reloadControlReproducesBug(GameTestHelper helper) {
		partReload(helper, false, (taken, inBlock) ->
			helper.assertTrue(taken > inBlock, "no items were lost without the fix (" + taken + " taken, " + inBlock + " saved): the bug did not reproduce"));
	}
}
