/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.LutLoader;
import ij.process.LUT;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.AutoThresholderImageJ1;
import utilities.BV3DBoxUtilities;
import utilities.BV3DBoxUtilities.LutNames;

/**
 * @author BioVoxxel
 *
 */
@Plugin(type = Command.class, menuPath = "BV3DBox>Voronoi Threshold Labeling 2D/3D")
public class VoronoiThresholdLabeling3D extends DynamicCommand {

	
	@Parameter(required = true, initializer = "setupImage")
	private ImagePlus inputImagePlus;
	
	@Parameter(label = "Image Filter", choices = {"None", "Gaussian", "DoG", "Median", "Mean", "Open", "Close", "Variance"}, callback = "adaptFilterRadius")
	private String filterMethod = "Gaussian";
	
	@Parameter(label = "", min = "0f", max = "100f", callback = "processImage")
	private Float filterRadius = 1.0f;
	
	@Parameter(label = "Background Subtraction", choices = {"None", "DoG", "DoM", "TopHat", "BottomHat"}, callback = "adaptBackgroundRadius")
	private String backgroundSubtractionMethod;
	
	@Parameter(label = "", min = "0f", max = "100f", callback = "processImage")
	private Float backgroundRadius = 1.0f;
	
	@Parameter(label = "", initializer = "thresholdMethodList", callback = "processImage")
	private String thresholdMethod;
	
	@Parameter(label = "", min = "0f", callback = "processImage")
	private Float spotSigma;
	
	@Parameter(label = "", min = "0f", callback = "processImage")
	private Float maximaRadius;
	
	@Parameter(label = "Output type", choices = {"Labels", "Binary"}, style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, callback = "processImage")
	private String outputType;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	
	CLIJ2 clij2;
	private ClearCLBuffer input_image;
	private ClearCLBuffer filtered_image;
	private ClearCLBuffer tempMedian;
	private ClearCLBuffer background_image;
	private ClearCLBuffer thresholded_image;
	private ClearCLBuffer maxima_image;
	private ClearCLBuffer masked_spots;
	private ClearCLBuffer output_image;

	private double x_y_ratio;
	private double z_x_ratio;
	
	LUT grays = BV3DBoxUtilities.createGrayLUT();
	LUT glasbey = LutLoader.openLut(IJ.getDirectory("luts") + LutNames.GLASBEY_LUT.lutName + ".lut");

	private ImagePlus outputImagePlus;



	
	@Override
	public void run() {
		input_image.close();
		clij2.close();
		
	}
	
	
	public void processImage() {
		
		filterImage();
		IJ.showProgress(0.2);
		
		backgroundSubtraction();
		filtered_image.close();
		IJ.showProgress(0.4);
		
		thresholdImage();
		background_image.close();
		IJ.showProgress(0.6);
		
		detectMaxima();
		IJ.showProgress(0.8);
		
		createLabels();
		maxima_image.close();
		thresholded_image.close();
		IJ.showProgress(0.9);

//		masked_spots.close();
//		tempMedian.close();
		
		ImagePlus tempOutputImagePlus;
		
		if (outputType.equals("Binary")) {
			tempOutputImagePlus = clij2.pullBinary(output_image);
//		} else if (outputType.equals("Binary with Watershed")) {
//			ClearCLBuffer labelEdges = clij2.create(input_image);
//			clij2.reduceLabelsToLabelEdges(output_image, labelEdges);
//			ClearCLBuffer temp = clij2.create(input_image);
//			clij2.copy(output_image, temp);
//			clij2.binarySubtract(temp, labelEdges, output_image);
//			tempOutputImagePlus = clij2.pullBinary(output_image);
//			labelEdges.close();
//			temp.close();
		} else {
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, output_image, true, LutNames.GLASBEY_LUT);
		}
		
		output_image.close();
		
		outputImagePlus = WindowManager.getImage("VTL_" + inputImagePlus.getTitle());			
		System.out.println(outputImagePlus);
		
		if (outputImagePlus != null) {
			outputImagePlus.setImage(tempOutputImagePlus);
			outputImagePlus.setTitle("VTL_" + inputImagePlus.getTitle());
			
			if (outputType.equals("Binary")) {
				outputImagePlus.setLut(grays);
			} else {
				outputImagePlus.setLut(glasbey);
			}
			
			outputImagePlus.updateAndDraw();
		} else {
			outputImagePlus = new ImagePlus();
			outputImagePlus.setImage(tempOutputImagePlus);
			
			if (outputType.equals("Binary")) {
				outputImagePlus.setLut(grays);
			} else {
				outputImagePlus.setLut(glasbey);
			}
			outputImagePlus.setTitle("VTL_" + inputImagePlus.getTitle());
			outputImagePlus.show();
		}
		
	}
	
	

	private void filterImage() {
		
		filtered_image = clij2.create(input_image);
		
		double y_filter_radius = filterRadius * x_y_ratio;
		double z_filter_radius = filterRadius / z_x_ratio;
		
		long zSlices = input_image.getDepth();
		
		if (zSlices == 1) {
			z_filter_radius = 0;
		}
		
		if (filterMethod.equals("None")) {
			clij2.copy(input_image, filtered_image);
		}
		
		if (filterMethod.equals("Gaussian")) {
			clij2.gaussianBlur3D(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
		}
		
		if (filterMethod.equals("DoG")) {
			double dogFilterRadius = filterRadius + 2d;
			clij2.differenceOfGaussian3D(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius, dogFilterRadius, (dogFilterRadius * x_y_ratio), (dogFilterRadius / z_x_ratio));
		}
		
		if (filterMethod.equals("Median")) {
			if (zSlices == 1) {
				clij2.median2DSphere(input_image, filtered_image, filterRadius, y_filter_radius);	
			} else {
				clij2.median3DSliceBySliceSphere(input_image, filtered_image, filterRadius, y_filter_radius);				
			}
		}
		
		if (filterMethod.equals("Mean")) {
			if (zSlices == 1) {
				clij2.mean2DSphere(input_image, filtered_image, filterRadius, y_filter_radius);	
			} else {
				clij2.mean3DSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);				
			}
		}
		
		if (filterMethod.equals("Open")) {
			clij2.greyscaleOpeningSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
		}
		
		if (filterMethod.equals("Close")) {
			clij2.greyscaleClosingSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
		}
		
		if (filterMethod.equals("Variance")) {
			clij2.varianceSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
		}
		
	}

	
	private void backgroundSubtraction() {
		
		background_image = clij2.create(input_image);
		
		double y_bckgr_radius = backgroundRadius * x_y_ratio;
		double z_bckgr_radius = backgroundRadius / z_x_ratio;
		
		long zSlices = input_image.getDepth();
		
		if (zSlices == 1) {
			z_bckgr_radius = 0;
		}
		
		
		if (backgroundSubtractionMethod.equals("None")) {
			clij2.copy(filtered_image, background_image);
		}
		
		if (backgroundSubtractionMethod.equals("DoG")) {
			clij2.differenceOfGaussian3D(filtered_image, background_image, 0, 0, 0, backgroundRadius, y_bckgr_radius, z_bckgr_radius);	
		}

		if (backgroundSubtractionMethod.equals("DoM")) {
			tempMedian = clij2.create(input_image);
			if (zSlices == 1) {
				clij2.median2DSphere(filtered_image, tempMedian, backgroundRadius, y_bckgr_radius);	
			} else {
				clij2.median3DSliceBySliceSphere(filtered_image, tempMedian, backgroundRadius, y_bckgr_radius);				
			}
			clij2.subtractImages(filtered_image, tempMedian, background_image);
			tempMedian.close();
		}
		
		if (backgroundSubtractionMethod.equals("TopHat")) {
			clij2.topHatSphere(filtered_image, background_image, backgroundRadius, y_bckgr_radius, z_bckgr_radius);
		}
		
		if (backgroundSubtractionMethod.equals("BottomHat")) {
			clij2.bottomHatSphere(filtered_image, background_image, backgroundRadius, y_bckgr_radius, z_bckgr_radius);
		}
	}


	private void readCalibration() {
		Calibration cal = inputImagePlus.getCalibration();
		x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		z_x_ratio = cal.pixelDepth / cal.pixelWidth;
			
	}
	
	
	
	private void thresholdImage() {
		thresholded_image = clij2.create(input_image);
		
		clij2.automaticThreshold(background_image, thresholded_image, thresholdMethod);
	}

	

	private void detectMaxima() {
		
		ClearCLBuffer temp = clij2.create(input_image);
		
		double y_filter_sigma = spotSigma * x_y_ratio;
		double z_filter_sigma = spotSigma / z_x_ratio;
		
		
		clij2.gaussianBlur3D(input_image, temp, spotSigma, y_filter_sigma, z_filter_sigma);
		//alternative
		//double offsetDoG = 2.0d;
		//clij2.differenceOfGaussian3D(input_image, temp, filterRadius, y_filter_sigma, z_filter_sigma, spotSigma + offsetDoG, ((spotSigma + offsetDoG) * x_y_ratio), ((spotSigma + offsetDoG) / z_x_ratio));
		
		maxima_image = clij2.create(input_image);
		
		double y_maxima_radius = maximaRadius * x_y_ratio;
		double z_maxima_radius = maximaRadius / z_x_ratio;
		
		clij2.detectMaxima3DBox(temp, maxima_image, maximaRadius, y_maxima_radius, z_maxima_radius);
		temp.close();
	}

	
	private void createLabels() {
		// mask spots
		masked_spots = clij2.create(input_image);
		clij2.binaryAnd(maxima_image, thresholded_image, masked_spots);
		
		output_image = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);
		clij2.maskedVoronoiLabeling(masked_spots, thresholded_image, output_image);
		
	
	}

	
	@SuppressWarnings("unused")
	private void setupImage() {
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		readCalibration();
		
		input_image = clij2.push(inputImagePlus);
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
			
		}
	}
	
	
	@SuppressWarnings("unused")
	private void thresholdMethodList() {
		String[] thresholdMethodString = AutoThresholderImageJ1.getMethods();
		
		List<String> thresholdMethodList = Arrays.asList(thresholdMethodString);
		
		final MutableModuleItem<String> thresholdMethod = getInfo().getMutableInput("thresholdMethod", String.class);
		thresholdMethod.setChoices(thresholdMethodList);
	}
	
	@SuppressWarnings("unused")
	private void adaptFilterRadius() {
		
		final MutableModuleItem<Float> filterRadius = getInfo().getMutableInput("filterRadius", Float.class);
		if (filterMethod.equals("Median")) {
			filterRadius.setValue(this, 1f);
			filterRadius.setMaximumValue(15f);
			
		} else {
			filterRadius.setMaximumValue(200f);
		}
		
		processImage();
	}
	
	
	@SuppressWarnings("unused")
	private void adaptBackgroundRadius() {
		
		final MutableModuleItem<Float> backgroundRadius = getInfo().getMutableInput("backgroundRadius", Float.class);
		if (backgroundSubtractionMethod.equals("DoM")) {
			backgroundRadius.setValue(this, 1f);
			backgroundRadius.setMaximumValue(15f);
		} else {
			backgroundRadius.setMaximumValue(200f);
		}
		
		processImage();
	}
	
	@SuppressWarnings("unused")
	private void slideSlices() {
	
		outputImagePlus.setSlice(stackSlice);
		
	}
	
	public void cancel() {
		ImagePlus outputImagePlus = WindowManager.getImage("VTL_" + inputImagePlus.getTitle());
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		clij2.close();
	}
	

}
