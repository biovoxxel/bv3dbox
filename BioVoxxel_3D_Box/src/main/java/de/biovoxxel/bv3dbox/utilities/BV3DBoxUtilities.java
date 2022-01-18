package de.biovoxxel.bv3dbox.utilities;

import org.scijava.plugin.Plugin;
import org.scijava.service.Service;

import de.biovoxxel.bv3dbox.plugins.BVVoronoiThresholdLabeling;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.LutLoader;
import ij.process.LUT;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

@Plugin(type = Service.class)
public class BV3DBoxUtilities {
	
		
	public static String[] extendImageTitleListWithNone() {
		String[] allImageNames = WindowManager.getImageTitles();
		String[] imageNames = new String[allImageNames.length + 1];
		imageNames[0] = "None";
		for (int w = 0; w < allImageNames.length; w++) {
			imageNames[w+1] = allImageNames[w];
		}
		return imageNames;
	}

	
	public static void pullAndDisplayImageFromGPU(CLIJ2 clij2, ClearCLBuffer imageToShow, boolean autoContrast, LutNames lutName) {
		
		ImagePlus imagePlusToBePulled = clij2.pull(imageToShow);
		imagePlusToBePulled.setTitle(imageToShow.getName());
		
		
		LUT outputLut;
		if (lutName.equals(LutNames.GRAY)) {
			outputLut = createGrayLUT();
		} else {
			outputLut = LutLoader.openLut(IJ.getDirectory("luts") + lutName.lutName + ".lut");			
		}
		imagePlusToBePulled.setLut(outputLut);

		if (autoContrast) {
			double max_int = clij2.maximumOfAllPixels(imageToShow);
			imagePlusToBePulled.setDisplayRange(0.0, max_int);			
		} else {
			imagePlusToBePulled.resetDisplayRange();			
		}
		imagePlusToBePulled.show();
		
	}
	
	public static ImagePlus pullImageFromGPU(CLIJ2 clij2, ClearCLBuffer imageToShow, boolean autoContrast, LutNames lutName) {
		
		ImagePlus imagePlusToBePulled = clij2.pull(imageToShow);
		imagePlusToBePulled.setTitle(imageToShow.getName());
		
		
		LUT outputLut;
		if (lutName.equals(LutNames.GRAY)) {
			outputLut = createGrayLUT();
		} else {
			outputLut = LutLoader.openLut(IJ.getDirectory("luts") + lutName.lutName + ".lut");			
		}
		imagePlusToBePulled.setLut(outputLut);

		if (autoContrast) {
			double max_int = clij2.maximumOfAllPixels(imageToShow);
			imagePlusToBePulled.setDisplayRange(0.0, max_int);			
		} else {
			imagePlusToBePulled.resetDisplayRange();			
		}
		
		return imagePlusToBePulled;
	}
	
	public static float getMinFromRange(String range) throws NumberFormatException {
		float min_value = Float.NaN;
		if (range.contains("-")) {
			String min_value_string = range.substring(0, range.indexOf("-"));
			if (min_value_string.equalsIgnoreCase("infinity")) {
				min_value = Float.POSITIVE_INFINITY;
			} else {
				min_value = Float.parseFloat(min_value_string);
			}
		}
		return min_value;
		
	}
	
	public static float getMaxFromRange(String range) throws NumberFormatException {
		float max_value = Float.NaN;
		if (range.contains("-")) {
			String max_value_string = range.substring(range.indexOf("-") + 1);
			if (max_value_string.equalsIgnoreCase("infinity")) {
				max_value = Float.POSITIVE_INFINITY;
			} else {
				max_value = Float.parseFloat(max_value_string);
			}
		}
		return max_value;
		
	}
	
	public enum LutNames {
		NONE("None"),
		FIRE_LUT("fire"),
		GRAY("Grays"),
		GLASBEY_LUT("glasbey_on_dark"),
		GEEN_FIRE_BLUE_LUT("Green Fire Blue"),
		PHYSICS("physics");

		public final String lutName;
		
		LutNames(String lutName) {
			 this.lutName = lutName;
		}
	}
	
	public static LUT createGrayLUT() {
		
		byte[] red = new byte[256];
		byte[] green = new byte[256];
		byte[] blue = new byte[256];
		
		for (int v = 0; v < 256; v++) {
			red[v] = (byte) v;
			green[v] = (byte) v;
			blue[v] = (byte) v;
		}
		
		LUT grayLUT = new LUT(red, green, blue);
		
		return grayLUT;
	}
	
	
	
}
