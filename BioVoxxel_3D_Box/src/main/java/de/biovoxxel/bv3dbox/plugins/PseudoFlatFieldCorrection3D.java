package de.biovoxxel.bv3dbox.plugins;

import java.awt.Point;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.NumberWidget;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;
import utilities.BV3DBoxSettings;
import utilities.BV3DBoxUtilities;
import utilities.BV3DBoxUtilities.LutNames;

@Plugin(type = Command.class, menuPath = "BV3DBox>Pseudo Flat Field Correction")
public class PseudoFlatFieldCorrection3D extends DynamicCommand {

//	public PseudoFlatFieldCorrection3D() {
//		// TODO Auto-generated constructor stub
//	}
	
	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	
	@Parameter(required = true, initializer = "setupImage")
	ImagePlus inputImagePlus;
	
	@Parameter(persist = false, label = "Blurring radius (sigma)", min = "0f", callback = "pseudoFlatFieldCorrection")
	private Float filterRadius = 0.0f;
	
	@Parameter(label = "Force 2D filter (saves memory)")
	Boolean force2DFilter = true;
	
	@Parameter(label = "Show background image", callback = "pseudoFlatFieldCorrection")
	Boolean showBackgroundImage = true;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	
	CLIJ2 clij2;
	ClearCLBuffer inputImage;
	
	private ImagePlus outputImagePlus;
	private double x_y_ratio;
	private double z_x_ratio;

	public void run() {
		clij2.close();
	}
	
	public void pseudoFlatFieldCorrection() {
		ClearCLBuffer backgound = clij2.create(inputImage.getDimensions(), NativeTypeEnum.Float);
		clij2.copy(inputImage, backgound);
		ClearCLBuffer blurredBackground = clij2.create(backgound);
		
		double y_filter_radius = filterRadius * x_y_ratio;
		
		log.debug("filterRadius=" + filterRadius);
		log.debug("y_filter_radius=" + y_filter_radius);
		
						
		if (inputImagePlus.isStack()) {
			int frames = inputImagePlus.getNFrames();
			int z_slices = inputImagePlus.getNSlices();
			log.debug("frames=" + frames);
			log.debug("z_slices=" + z_slices);
			
			double z_filter_radius = 0; 
			if (z_slices > 1 && frames == 1 && !force2DFilter) {
				z_filter_radius = filterRadius / z_x_ratio;				
			} 
			log.debug("z_filter_radius=" + z_filter_radius);
			
			clij2.gaussianBlur3D(backgound, blurredBackground, filterRadius, y_filter_radius, z_filter_radius);
			log.debug("3D filtering for background creation");
		} else {
			clij2.gaussianBlur2D(backgound, blurredBackground, filterRadius, y_filter_radius);
			log.debug("2D filtering for background creation");
		}
		
		backgound.close();
		
		ImagePlus tempOutputImagePlus;
		if (showBackgroundImage) {
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, blurredBackground, false, LutNames.PHYSICS);
			
		} else {
			
			double meanBackgroundIntensity = clij2.meanOfAllPixels(blurredBackground);
			log.debug("meanBackgroundIntensity = " + meanBackgroundIntensity);
			
			ClearCLBuffer dividedImage = clij2.create(blurredBackground);
			clij2.divideImages(inputImage, blurredBackground, dividedImage);
			log.debug("Image devided by background");
			
			ClearCLBuffer outputImage = clij2.create(dividedImage);
			clij2.multiplyImageAndScalar(dividedImage, outputImage, meanBackgroundIntensity);
			dividedImage.close();
			
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, outputImage, true, LutNames.GRAY);
			outputImage.close();
		}
		
		blurredBackground.close();
		
		
		outputImagePlus = WindowManager.getImage("PFFC_" + inputImagePlus.getTitle());
		
		if (outputImagePlus == null) {
			outputImagePlus = new ImagePlus();
		}
		
		outputImagePlus.setImage(tempOutputImagePlus);
		outputImagePlus.setTitle("PFFC_" + inputImagePlus.getTitle());
		ImageWindow inputImageWindow = inputImagePlus.getWindow();
		Point inputImageLocation = inputImageWindow.getLocationOnScreen();
		outputImagePlus.show();
		outputImagePlus.getWindow().setLocation(inputImageLocation.x + inputImageWindow.getWidth() + 10, inputImageLocation.y);
		
	}
	
	
	@SuppressWarnings("unused")
	private void setupImage() {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "debug_level", LogLevel.INFO));
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		readCalibration();
		
		if (inputImagePlus.getRoi() != null) {
			inputImage = clij2.pushCurrentSelection(inputImagePlus);
		} else {
			inputImage = clij2.push(inputImagePlus);			
		}
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
			
		}
	}
	
	@SuppressWarnings("unused")
	private void slideSlices() {
	
		outputImagePlus.setSlice(stackSlice);
		
	}
	
	
	private void readCalibration() {
		Calibration cal = inputImagePlus.getCalibration();
		x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		z_x_ratio = cal.pixelDepth / cal.pixelWidth;
			
	}
	
	public void cancel() {
		ImagePlus outputImagePlus = WindowManager.getImage("PFFC_" + inputImagePlus.getTitle());
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		clij2.close();
	}
	
}







