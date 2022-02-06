package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_NeighborAnalysis;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Plot;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Analysis>Neighbor Analysis (2D/3D)")
public class BV_NeighborAnalysisGUI implements Command {

	@Parameter
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Method", choices = {"Objects", "Distance" })
	String neighborDetectionMethod = "Objects";
	
	@Parameter(label = "Distance range")
	String distanceRange = "1-Infinity";
	
	@Parameter
	Boolean plotNeighborCount = false;
	
	@Parameter
	Boolean plotNeighborDistribution = false;
	
	
	
	@Override
	public void run() {
		BV_NeighborAnalysis neighborAnalysis = new BV_NeighborAnalysis(inputImagePlus);
				
		ClearCLBuffer neighbor_image = neighborAnalysis.getNeighborCountMap(neighborAnalysis.getConnectedComponentInput(), neighborDetectionMethod.toLowerCase(), distanceRange);
		
		ImagePlus neighborCountMapImp = BV3DBoxUtilities.pullImageFromGPU(neighborAnalysis.getCurrentCLIJ2Instance(), neighbor_image, false, LutNames.GEEN_FIRE_BLUE_LUT);
		neighborCountMapImp.setTitle(WindowManager.getUniqueName("NeighborCount_" + inputImagePlus.getTitle()));
		
		neighborCountMapImp.show();
		
		if (plotNeighborCount) {
			Plot plot = neighborAnalysis.getNeighborPlotFromCountMap(neighbor_image);
			plot.setStyle(0, "blue,#a0a0ff,0");
			plot.show();
		}
		
		if (plotNeighborDistribution) {
			Plot plot = neighborAnalysis.getNeighborDistribution(neighbor_image);
			plot.setStyle(0, "blue,#a0a0ff,0");
			plot.show();
		}
		
	}

}
