# Create: Vault Item Loss Fix

Fixes [Create issue #4370](https://github.com/Creators-of-Create/Create/issues/4370): item vaults that cross a chunk border lose items, and can duplicate them. Not affiliated with the Create team.

The controller block of a vault keeps one combined inventory over all its blocks. When blocks in another chunk unload and load again, they get fresh inventories from the save, but the controller keeps using the old ones: items put into those slots are lost, items taken out stay in the saved block. This addon rebuilds the controller's inventory whenever one of its blocks loads or unloads.

Config: `config/vault_item_loss_fix.properties` (`enabled`).

## Tests

A two-block vault; one block is unloaded and loaded again from its saved data, as a chunk reload does. Without the fix, 64 diamonds put in are lost and 64 taken out are duplicated (both controls). With it, nothing is lost or duplicated.

MIT.
