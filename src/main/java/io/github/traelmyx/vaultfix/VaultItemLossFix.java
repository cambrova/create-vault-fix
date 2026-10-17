package io.github.traelmyx.vaultfix;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Create issue #4370: item vaults that span chunks lose items.
 * <p>
 * A vault's controller block builds one combined inventory out of every part's own inventory and keeps it. When a part
 * in another chunk unloads and loads again, the part gets a new inventory, but the controller keeps pointing at the old
 * one, so items put into those slots go nowhere.
 */
@Mod(VaultItemLossFix.MOD_ID)
public class VaultItemLossFix {
	public static final String MOD_ID = "vault_item_loss_fix";
	public static final Logger LOGGER = LoggerFactory.getLogger("Vault Item Loss Fix");

	public static volatile boolean enabled = true;

	public VaultItemLossFix() {
		loadConfig(FMLPaths.CONFIGDIR.get());
		try {
			Class.forName("com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity", false, VaultItemLossFix.class.getClassLoader())
				.getDeclaredField("itemCapability");
		} catch (ReflectiveOperationException | LinkageError e) {
			LOGGER.warn("This Create version changed how vaults build their inventory; the fix may be inactive ({})", e.toString());
		}
	}

	static void loadConfig(Path configDir) {
		Path file = configDir.resolve(MOD_ID + ".properties");
		Properties props = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				props.load(reader);
			} catch (IOException e) {
				LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
		props.setProperty("enabled", Boolean.toString(enabled));
		try {
			Files.createDirectories(configDir);
			try (Writer writer = Files.newBufferedWriter(file)) {
				props.store(writer, "Vault Item Loss Fix. enabled: vaults rebuild their combined inventory when one of their parts loads or unloads.");
			}
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", file, e);
		}
	}
}
