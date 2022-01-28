package de.biovoxxel.bv3dbox.plugins;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;


//@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Extended Particle Analyzer (2D/3D)")
public class ExtendedParticleAnalyzer3D extends DynamicCommand {

		
	@Parameter
	ImagePlus binaryImagePlus;
	
	@Parameter(label = "", initializer = "initializeOriginalImageChoices")
	String originalImagePlus;
	
	@Parameter
	String areaRange = "0.00-infinity";
	
	@Parameter
	String circularityRange = "0.00-1.00";
	
	@Parameter
	String perimeterRange = "0.00-infinity";
	
	@Parameter
	String roundnessRange = "0.00-1.00";
	
	@Parameter
	String solidityRange = "0.00-1.00";
	
	@Parameter
	String aspectRatioRange = "0.00-infinity";
	
	@Parameter(label = "Preview", callback = "preview")
	Boolean preview = false;
	
	
	ResultsTable initialResultsTable = null;
	
	public ExtendedParticleAnalyzer3D() {
		
	}
	
	
	
	public void run() {
		
	}
	
	public void extendedParticleAnalysis() {
		
		if (!binaryImagePlus.getProcessor().isBinary()) {
			cancel("8-bit binary image needed!");
			//return;
		}
		
		
		if (initialResultsTable == null) {
			initialResultsTable = initialAnalysis();
		}
		
		
		CLIJ2 clij2 = CLIJ2.getInstance();
		
				
		double minSize = (double) BV3DBoxUtilities.getMinFromRange(areaRange);
		double maxSize = (double) BV3DBoxUtilities.getMaxFromRange(areaRange);
		double minCirc = (double) BV3DBoxUtilities.getMinFromRange(circularityRange);
		double maxCirc = (double) BV3DBoxUtilities.getMaxFromRange(circularityRange);
		
//		int rowCount = initialResults.getCounter();
//		String[] headings = initialResults.getHeadings();
//		
//		ClearCLBuffer initialResultsVector = clij2.create(rowCount, headings.length);
//		clij2.pushResultsTable(initialResultsVector, initialResults);
				
		ClearCLBuffer temp = clij2.push(binaryImagePlus);
		ClearCLBuffer input_image = clij2.create(temp);
		if (binaryImagePlus.getProcessor().isBinary()) {
			clij2.connectedComponentsLabelingBox(temp, input_image);
		} else {
			clij2.copy(temp, input_image);
		}
		temp.close();
		
//		ClearCLBuffer exclusionVector = clij2.create(rowCount, 1);
//		
//		clij2.pushResultsTableColumn(exclusionVector, initialResults, circRange)
		
	}

	private ResultsTable initialAnalysis() {
		
		int options = ParticleAnalyzer.SHOW_OVERLAY_MASKS | ParticleAnalyzer.CLEAR_WORKSHEET;
		
		ResultsTable initialResults = new ResultsTable();		
		
		ParticleAnalyzer pa = new ParticleAnalyzer(options, ParticleAnalyzer.ALL_STATS, initialResults, 0.0, Double.POSITIVE_INFINITY);
		pa.analyze(binaryImagePlus);
		
		return initialResults;
		
	}
	
	
	public List<String> imageListWithNoneOption() {
		
		String[] imageNames = BV3DBoxUtilities.extendImageTitleListWithNone();
		
		List<String> extendedImageList = Arrays.asList(imageNames);
		
		return extendedImageList;
	}
	
	
	public void initializeOriginalImageChoices() {
		
		List<String> extendedImageList = imageListWithNoneOption();
		
		final MutableModuleItem<String> originalImagePlus = getInfo().getMutableInput("originalImagePlus", String.class);
		originalImagePlus.setChoices(extendedImageList);
	
	}
	
	
	public void preview() {
		
	}
}
