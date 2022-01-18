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

	@Override
	public void run() {
			
		if (debugMode) {
			prefs.put(BV3DBoxSettings.class, "debug_level", LogLevel.DEBUG);			
		} else {
			prefs.put(BV3DBoxSettings.class, "debug_level", LogLevel.INFO);			
		}
		
	}	
}
