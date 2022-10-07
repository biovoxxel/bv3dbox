package de.biovoxxel.bv3dbox.utilities;

import org.scijava.command.Command;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Settings")
public class BV3DBoxSettings implements Command {

	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	

	@Parameter(label = "Logging level", choices = {"NONE", "INFO", "DEBUG"})
	private String debugMode = "Info"; 
	
	@Parameter(label = "Display debug images")
	private Boolean displayDebugImages = false; 

	@Override
	public void run() {
		
		switch (debugMode) {
		case "NONE":
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.NONE);
			log.setLevel(LogLevel.NONE);
			System.out.println("Debug mode = LogLevel.NONE");
			break;
		case "INFO":
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO);
			log.setLevel(LogLevel.INFO);
			System.out.println("Debug mode = LogLevel.INFO");
			break;
		case "DEBUG":
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.DEBUG);
			log.setLevel(LogLevel.DEBUG);
			System.out.println("Debug mode = LogLevel.DEBUG");
			break;

		default:
			break;
		}
		
		
		
		
		if (displayDebugImages) {
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", true);
		} else {
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", false);			
		}
		System.out.println("Display debug images = " + displayDebugImages);
		
	}	
}
