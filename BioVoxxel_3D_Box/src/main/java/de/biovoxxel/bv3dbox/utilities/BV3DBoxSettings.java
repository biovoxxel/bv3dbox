package de.biovoxxel.bv3dbox.utilities;

import org.scijava.command.Command;
import org.scijava.log.LogLevel;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Settings")
public class BV3DBoxSettings implements Command {

	@Parameter
	PrefService prefs;
	

	@Parameter(label = "Activate debug logging")
	private Boolean debugMode = false; 
	
	@Parameter(label = "Display debug images")
	private Boolean displayDebugImages = false; 

	@Override
	public void run() {
			
		if (debugMode) {
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.DEBUG);			
		} else {
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO);			
		}
		
		if (displayDebugImages) {
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", true);			
		} else {
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", false);			
		}
		
	}	
}
