/*
 * This file is part of World Downloader: A mod to make backups of your multiplayer worlds.
 * https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2520465-world-downloader-mod-create-backups-of-your-builds
 *
 * Copyright (c) 2014 nairol, cubic72
 * Copyright (c) 2018-2020 Pokechu22, julialy
 *
 * This project is licensed under the MMPLv2.  The full text of the MMPL can be
 * found in LICENSE.md, or online at https://github.com/iopleke/MMPLv2/blob/master/LICENSE.md
 * For information about this the MMPLv2, see https://stopmodreposts.org/
 *
 * Do not redistribute (in modified or unmodified form) without prior permission.
 */
package wdl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.resources.LanguageManager;
import net.minecraft.resources.IResourcePack;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.SimpleReloadableResourceManager;
import net.minecraft.resources.VanillaPack;
import net.minecraft.util.registry.Bootstrap;

/**
 * This class initializes Minecraft's bootstrap and language files.
 */
final class TestBootstrap {
	private TestBootstrap() { throw new AssertionError(); }

	private static final Logger LOGGER = LogManager.getLogger();
	private static boolean ran = false;

	// No files are left open, and the objects have a longer lifespan in e.g. LanguageMap
	@SuppressWarnings("resource")
	static void init() {
		if (ran) {
			return;
		}
		ran = true;
		//SharedConstants.developmentMode = true;
		LOGGER.debug("Initializing bootstrap...");
		Bootstrap.register();
		LOGGER.debug("Initialized bootstrap.");

		LOGGER.debug("Setting up I18n...");
		// Prepare I18n by constructing a LanguageManager and preparing it...
		// (some tests depend on it)
		LanguageManager languageManager = new LanguageManager("en_us");
		SimpleReloadableResourceManager resourceManager = new SimpleReloadableResourceManager(ResourcePackType.CLIENT_RESOURCES, null);
		IResourcePack pack = new VanillaPack("minecraft", "realms", "wdl");
		resourceManager.addResourcePack(pack);
		languageManager.onResourceManagerReload(resourceManager);
		LOGGER.debug("Set up I18n.");
	}
}
