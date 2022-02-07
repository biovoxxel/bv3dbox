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

/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2022, Jan Brocher (BioVoxxel)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * Please cite BioVoxxel according to the provided DOI related to this software.
 * 
 */

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Analysis>Neighbor Analysis (2D/3D)")
public class BV_NeighborAnalysisGUI implements Command {

	@Parameter
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Method", choices = {"Objects", "Distance" })
	String neighborDetectionMethod = "Objects";
	
	@Parameter(label = "Distance range")
	String distanceRange = "1-Infinity";
	
	@Parameter(label = "Exclude edges from visualization")
	Boolean excudeEdgeObjectsFromVisualization = false;
	
	@Parameter(label = "Plot neighbor counts")
	Boolean plotNeighborCount = false;
	
	@Parameter(label = "Plot neighbor distribution")
	Boolean plotNeighborDistribution = false;
	
	
	
	@Override
	public void run() {
		BV_NeighborAnalysis neighborAnalysis = new BV_NeighborAnalysis(inputImagePlus);
				
		ClearCLBuffer neighbor_image = neighborAnalysis.getNeighborCountMap(neighborAnalysis.getConnectedComponentInput(), neighborDetectionMethod.toLowerCase(), distanceRange, excudeEdgeObjectsFromVisualization);
		
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
