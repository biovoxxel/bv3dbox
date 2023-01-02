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
	

	@Parameter(label = "Logging level", choices = {"NONE", "INFO", "DEBUG"}, description = "bv3dbox specific logging level, default = INFO")
	private String loggingLevel = "INFO"; 
	
	@Parameter(label = "Fiji log level", choices = {"NONE", "ERROR", "WARN", "INFO", "DEBUG", "TRACE"}, description = "global Fiji logging level, default = WARN, "
																													+ "if set to 'NONE', there will be no console output anymore!")
	private String scijavaLogLevel = "WARN"; 
	
//	@Parameter(label = "Display debug images")
//	private Boolean displayDebugImages = false; 

	@Override
	public void run() {
		
		switch (loggingLevel) {
		case "NONE":
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.NONE);
			log.setLevel(LogLevel.NONE);
			System.out.println("Log level = LogLevel.NONE");
			break;
		case "INFO":
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO);
			log.setLevel(LogLevel.INFO);
			System.out.println("Log level = LogLevel.INFO");
			break;
		case "DEBUG":
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.DEBUG);
			log.setLevel(LogLevel.DEBUG);
			System.out.println("Log level = LogLevel.DEBUG");
			break;

		default:
			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO);
			log.setLevel(LogLevel.INFO);
			System.out.println("Log level = LogLevel.INFO");
			break;
		}
		
		
		
		switch (scijavaLogLevel) {
		case "NONE":
			System.setProperty("scijava.log.level", "none");
			System.out.println("Fiji log level = NONE");
			break;
			
		case "ERROR":
			System.setProperty("scijava.log.level", "error");
			System.out.println("Fiji log level = ERROR");
			break;
			
		case "WARN":
			System.setProperty("scijava.log.level", "warn");
			System.out.println("Fiji log level = WARN");
			break;
	
		case "INFO":
			System.setProperty("scijava.log.level", "info");
			System.out.println("Fiji log level = INFO");
			break;
			
		case "DEBUG":
			System.setProperty("scijava.log.level", "debug");
			System.out.println("Fiji log level = DEBUG");
			break;
			
		case "TRACE":
			System.setProperty("scijava.log.level", "trace");
			System.out.println("Fiji log level = TRACE");
			break;

		default:
			System.setProperty("scijava.log.level", "warn");
			System.out.println("Default Fiji log level = WARN");
			break;
		}
		
		
		
//		if (displayDebugImages) {
//			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", true);
//		} else {
//			prefs.put(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", false);			
//		}
//		System.out.println("Display debug images = " + displayDebugImages);
		
	}	
}
