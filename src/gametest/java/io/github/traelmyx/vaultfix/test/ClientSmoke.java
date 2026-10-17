package io.github.traelmyx.vaultfix.test;

import com.simibubi.create.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.nio.file.Files;

// Run with -Dsmoketest=true: creates a flat world with Create loaded, builds a chest + tank + steam engine in front of
// the player, takes a screenshot and quits. Exit code 1 on failure.
public final class ClientSmoke {

	public static final boolean ENABLED = Boolean.getBoolean("smoketest");
	private static final long TIMEOUT_MS = 5 * 60 * 1000;

	private static int stage;
	private static int ticks;
	private static volatile net.minecraft.core.BlockPos enginePos;

	private ClientSmoke() {
	}

	public static boolean start() {
		if (!ENABLED) {
			return false;
		}
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(TIMEOUT_MS);
				fail("timed out");
			} catch (InterruptedException ignored) {
			}
		}, "smoketest-watchdog");
		watchdog.setDaemon(true);
		watchdog.start();
		return true;
	}

	public static void tick(Minecraft mc) {
		try {
			switch (stage) {
				case 0 -> {
					Screen screen = SmokeCompat.screen(mc);
					if (screen != null && SmokeCompat.overlayHidden(mc) && ++ticks >= 20) {
						if (screen.getClass().getSimpleName().equals("LoadingErrorScreen")) {
							fail("mod loading errors");
						}
						ticks = 0;
						mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
						SmokeCompat.createFlatWorld(mc, GameType.CREATIVE);
						stage = 1;
					}
				}
				case 1 -> {
					if (mc.level != null && mc.player != null && SmokeCompat.screen(mc) == null) {
						stage = 2;
					}
				}
				case 2 -> {
					ticks++;
					if (ticks == 40) {
						build(mc);
					}
					if (ticks == 100) {
						SmokeCompat.screenshot(mc, "fan.png");
					}
					if (ticks >= 120) {
						var pos = enginePos;
						if (pos == null || !AllBlocks.ENCASED_FAN.has(mc.level.getBlockState(pos))) {
							fail("client does not see the fan at " + pos);
						}
						Files.writeString(mc.gameDirectory.toPath().resolve("smoke-ok"), "ok\n");
						stage = 3;
						mc.stop();
					}
				}
				default -> {
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
			fail(t.toString());
		}
	}

	private static void build(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		server.execute(() -> {
			var player = server.getPlayerList().getPlayers().get(0);
			var level = server.overworld();
			var base = player.blockPosition().offset(0, 0, 3);
			// a creative motor driving a fan through a shaft
			level.setBlockAndUpdate(base.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
			level.setBlockAndUpdate(base, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
			level.setBlockAndUpdate(base.east(), AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
			enginePos = base.east();
			player.teleportTo(level, base.getX() + 4.5, base.getY() + 1, base.getZ() - 0.5, 90, 20);
		});
	}

	public static void fail(String why) {
		System.err.println("Smoke test failed: " + why);
		System.out.println("[clientsmoke] FAILED: " + why);
		System.err.flush();
		Runtime.getRuntime().halt(1);
	}
}
