/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import org.scijava.command.DynamicCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;
import org.scijava.prefs.DefaultPrefService;
import org.scijava.prefs.PrefService;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import ij.ImagePlus;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;

/**
 * @author BioVoxxel
 *
 */


public class BVPostProcessor extends DynamicCommand {

	private final LogService log = new StderrLogService();
	private final PrefService prefs = new DefaultPrefService();
	private static CLIJ2 clij2;
	private ClearCLBuffer input_image;
	
	/**
	 * 
	 */
	public BVPostProcessor(ImagePlus inputImagePlus) {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
	
		log.debug("input_image = " + inputImagePlus);
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		setInputImage(inputImagePlus);
		
		
	}
	
	
	public BVPostProcessor() {		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
	}

	
	public void setInputImage(ImagePlus inputImagePlus) {
		ClearCLBuffer temp_input_image = clij2.push(inputImagePlus);
		log.debug("temp_input_image = " + temp_input_image);
		this.input_image = clij2.create(temp_input_image.getDimensions(), NativeTypeEnum.Float);

		if (inputImagePlus.getProcessor().isBinary()) {
			clij2.connectedComponentsLabelingDiamond(temp_input_image, input_image);
		} else {
			
			clij2.copy(temp_input_image, input_image);
		}
		temp_input_image.close();
	}
	
	
	public ClearCLBuffer getInputBuffer() {
		return input_image;
	}
	
	public ClearCLBuffer postProcessor(String method, int iteration) {

		ClearCLBuffer output_image = clij2.create(input_image);
		
			
		boolean is3D = input_image.getDimension() > 2 ? true : false;
		
		
		//System.out.println("is3D = " + is3D);
	
		
		switch (method) {
			case "Median (sphere, max r=15)":
				iteration = iteration > 15 ? 15 : iteration;
				if (is3D) {
					clij2.median3DSliceBySliceSphere(input_image, output_image, iteration, iteration);
				} else {
					clij2.median2DSphere(input_image, output_image, iteration, iteration);
				}
				break;
			case "Median (box, max r=15)":
				iteration = iteration > 15 ? 15 : iteration;
				if (is3D) {
					clij2.median3DSliceBySliceBox(input_image, output_image, iteration, iteration);
				} else {
					clij2.median2DBox(input_image, output_image, iteration, iteration);
				}
				break;
			case "Erode (sphere)":
				if (is3D) {
					clij2.minimum3DSphere(input_image, output_image, iteration, iteration, iteration);					
				} else {
					clij2.minimum2DSphere(input_image, output_image, iteration, iteration);
				}
				break;
				
			case "Erode (box)":
				if (is3D) {
					clij2.minimum3DBox(input_image, output_image, iteration, iteration, iteration);					
				} else {
					clij2.minimum2DBox(input_image, output_image, iteration, iteration);
				}
				break;
				
			case "Dilate (sphere)":
				if (is3D) {
					clij2.maximum3DSphere(input_image, output_image, iteration, iteration, iteration);					
				} else {
					clij2.maximum2DSphere(input_image, output_image, iteration, iteration);
				}
				break;
				
			case "Dilate (box)":
				if (is3D) {
					clij2.maximum3DBox(input_image, output_image, iteration, iteration, iteration);					
				} else {
					clij2.maximum2DBox(input_image, output_image, iteration, iteration);
				}
				break;
				
			case "Open (sphere)":
				ClearCLBuffer temp_eroded_sphere = clij2.create(input_image);
				if (is3D) {
//					clij2.greyscaleOpeningSphere(input_image, output_image, iteration, iteration, iteration);
					clij2.minimum3DSphere(input_image, temp_eroded_sphere, iteration, iteration, iteration);	
					clij2.maximum3DSphere(temp_eroded_sphere, output_image, iteration, iteration, iteration);		
				} else {
//					clij2.greyscaleOpeningSphere(input_image, output_image, iteration, iteration, 0);
					clij2.minimum2DSphere(input_image, temp_eroded_sphere, iteration, iteration);
					clij2.maximum2DSphere(temp_eroded_sphere, output_image, iteration, iteration);
				}
				temp_eroded_sphere.close();
				break;
				
			case "Open (box)":
				ClearCLBuffer temp_eroded_box = clij2.create(input_image);
				if (is3D) {
//					clij2.greyscaleOpeningSphere(input_image, output_image, iteration, iteration, iteration);
					clij2.minimum3DBox(input_image, temp_eroded_box, iteration, iteration, iteration);	
					clij2.maximum3DBox(temp_eroded_box, output_image, iteration, iteration, iteration);		
				} else {
//					clij2.greyscaleOpeningSphere(input_image, output_image, iteration, iteration, 0);
					clij2.minimum2DBox(input_image, temp_eroded_box, iteration, iteration);
					clij2.maximum2DBox(temp_eroded_box, output_image, iteration, iteration);
				}
				temp_eroded_box.close();
				break;
				
			case "Close (sphere)":
				ClearCLBuffer temp_dilated_sphere = clij2.create(input_image);
				if (is3D) {
//					clij2.greyscaleClosingSphere(input_image, output_image, iteration, iteration, iteration);
					clij2.maximum3DSphere(input_image, temp_dilated_sphere, iteration, iteration, iteration);		
					clij2.minimum3DSphere(temp_dilated_sphere, output_image, iteration, iteration, iteration);	
				} else {
//					clij2.greyscaleClosingSphere(input_image, output_image, iteration, iteration, 0);
					clij2.maximum2DSphere(input_image, temp_dilated_sphere, iteration, iteration);
					clij2.minimum2DSphere(temp_dilated_sphere, output_image, iteration, iteration);
				}
				temp_dilated_sphere.close();
				break;
				
			case "Close (box)":
				ClearCLBuffer temp_dilated_box = clij2.create(input_image);
				if (is3D) {
//					clij2.greyscaleClosingSphere(input_image, output_image, iteration, iteration, iteration);
					clij2.maximum3DBox(input_image, temp_dilated_box, iteration, iteration, iteration);		
					clij2.minimum3DBox(temp_dilated_box, output_image, iteration, iteration, iteration);	
				} else {
//					clij2.greyscaleClosingSphere(input_image, output_image, iteration, iteration, 0);
					clij2.maximum2DBox(input_image, temp_dilated_box, iteration, iteration);
					clij2.minimum2DBox(temp_dilated_box, output_image, iteration, iteration);
				}
				temp_dilated_box.close();
				break;
				
			case "Fill holes (labels)":
				clij2.binaryFillHoles(input_image, output_image);
				break;
			
			case "Variance (sphere)":
				if (is3D) {
					clij2.varianceSphere(input_image, output_image, iteration, iteration, iteration);
				} else {
					clij2.varianceSphere(input_image, output_image, iteration, iteration, 0);
				}
				break;
			
			case "Variance (box)":
				if (is3D) {
					clij2.varianceBox(input_image, output_image, iteration, iteration, iteration);
				} else {
					clij2.varianceBox(input_image, output_image, iteration, iteration, 0);
				}
				break;
				
			default:
				break;
			}
		
		return output_image;
		
	}
	
	
	public ImagePlus getImagePlus(ClearCLBuffer buffer) {
		return clij2.pull(buffer);
	}
	
	public CLIJ2 getCLIJ2Instance() {
		return clij2;
	}
	
	public static void main(String[] args) {
		ImagePlus inputImage = new ImagePlus("C:\\Users\\broch\\Desktop\\Binary Nuclei.tif"); 
		BVPostProcessor bvpp = new BVPostProcessor(inputImage);
		
		ClearCLBuffer output = bvpp.postProcessor("Erode", 3);
		clij2.pull(output).show();
	}
	
}
