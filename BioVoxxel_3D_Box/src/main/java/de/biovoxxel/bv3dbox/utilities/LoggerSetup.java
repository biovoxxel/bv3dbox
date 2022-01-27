/**
 * 
 */
package de.biovoxxel.bv3dbox.utilities;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * @author BioVoxxel
 *
 */

public class LoggerSetup {
	
	//Logger parameters
	private static final String LOGGER_FORMAT = "[%1$tY-%1$tm-%1$td] [%1$tH:%1$tM:%1$tS] [%4$s] %5$s%6$s%n";
	private static Level LOGGER_LEVEL = Level.ALL;
	private static Level CONSOLE_LOGGER_LEVEL = Level.ALL;
	protected static final Logger log = Logger.getLogger(LoggerSetup.class.getName());
	
		
	
	public Logger getLogger() {
		setupLogger();
		return log;
	}

	
	public static void setupLogger() {
			
		System.setProperty("java.util.logging.SimpleFormatter.format", LOGGER_FORMAT);			
		
		if (LOGGER_LEVEL == null) {
			LOGGER_LEVEL = Level.OFF;
		}
		
		if (CONSOLE_LOGGER_LEVEL == null) {
			CONSOLE_LOGGER_LEVEL = Level.OFF;
		}
		
		log.setLevel(LOGGER_LEVEL);
		log.setUseParentHandlers(false);
		
		Handler[] existingHandlers = log.getHandlers();
		
		for (int handler = 0; handler <	existingHandlers.length; handler++) {
			log.removeHandler(existingHandlers[handler]);			
		}
		
		ConsoleHandler consoleHandler = new ConsoleHandler();
		consoleHandler.setLevel(CONSOLE_LOGGER_LEVEL);
		consoleHandler.setFormatter(new SimpleFormatter());
		
		log.addHandler(consoleHandler);	
	}

}
