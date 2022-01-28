/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import ij.ImagePlus;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

/**
 * @author BioVoxxel
 *
 */
//@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Neighbor Analysis (2D/3D)")
public class NeighborAnalysis3D extends DynamicCommand {

	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	
	@Parameter
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Analysis type", choices = {"Distance", "Voronoi", "Density"}, persist = true)
	public String analysisType;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	
	private ClearCLBuffer inputImage;
	private CLIJ2 clij2;

	
	public void run() {
		
	}

	public void neighborAnalysis() {
		
		ClearCLBuffer voronoi = clij2.create(inputImage);
		ClearCLBuffer distanceMap = clij2.create(inputImage);
		
		clij2.voronoiLabeling(inputImage, voronoi);
		
		clij2.distanceMap(voronoi, distanceMap);
	
		
	}
	

	@SuppressWarnings("unused")
	private void imageSetup() {
				
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
			
		inputImage = clij2.push(inputImagePlus);
		log.debug(inputImagePlus.getTitle() + "pushed to GPU");
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		
		if(inputImagePlus.isStack()) {
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			log.debug("inputImagePlus is a stack");
		} else {
			stackSlice.setMaximumValue(1);
			log.debug("inputImagePlus is a single image");
		}
	
		
	}
	
	@SuppressWarnings("unused")
	private void slideSlices() {
	
		inputImagePlus.setSlice(stackSlice);
		
	}
}
