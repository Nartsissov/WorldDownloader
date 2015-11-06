package wdl.api;

import net.minecraft.block.Block;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;

/**
 * Represents a mod that listens to WDL's events.
 * <br/>
 * To use this, it MUST be added to the list of WDL mods via
 * {@link WDLApi#addWDLMod(IWDLMod)}.
 * <br/>
 * Also, implement the subinterfaces for this to be useful.
 * <br/>
 * It is recomended to implement {@link IWDLModDescripted} to provide
 * aditional information on the mod, but that is not required.
 */
public interface IWDLMod {
	/**
	 * Gets the name of the mod.
	 * 
	 * @return The name of the mod.
	 */
	public abstract String getName();
	
	/**
	 * Gets a version string for this mod.
	 * 
	 * @return the version of the mod.
	 */
	public abstract String getVersion();
}