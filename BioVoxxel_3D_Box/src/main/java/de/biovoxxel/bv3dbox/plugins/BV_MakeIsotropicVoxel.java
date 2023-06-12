package de.biovoxxel.bv3dbox.plugins;

import ij.ImagePlus;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

public class BV_MakeIsotropicVoxel {
	
	ImagePlus inputImagePlus;
	ClearCLBuffer input_image;
	float[] calibration = new float[3];
	CLIJ2 clij2;
	
	public BV_MakeIsotropicVoxel(CLIJ2 clij2, ImagePlus inputImagePlus) {
		
		if (clij2 == null) {
			clij2 = CLIJ2.getInstance();
		} else {
			this.clij2 = clij2;
		}
		clij2.clear();
		
		this.inputImagePlus = inputImagePlus;
		input_image = clij2.push(inputImagePlus);
	}
	
	
	public ClearCLBuffer makeIsotropic(CLIJ2 clij2, ImagePlus image, float finalVoxelSize) {
				
		Calibration cal = image.getCalibration();
		
		calibration[0] = (float) cal.pixelWidth;
		calibration[1] = (float) cal.pixelHeight;
		calibration[2] = (float) cal.pixelDepth;
		
		
		return makeIsotropic(clij2, input_image, calibration, finalVoxelSize);
		
	}
	
	public ClearCLBuffer makeIsotropic(CLIJ2 clij2, ClearCLBuffer input_image, float[] calibration, float finalVoxelSize) {
		
		ClearCLBuffer output_image = createOutputBufferFromSource(input_image, finalVoxelSize);
		clij2.makeIsotropic(input_image, output_image, calibration[0], calibration[1], calibration[2], finalVoxelSize);
		
		input_image.close();
		
		return output_image;
	}	
	
	
	
	public ClearCLBuffer createOutputBufferFromSource(ClearCLBuffer input, float finalVoxelSize) {

        float scale1X = (float) (calibration[0] / finalVoxelSize);
        float scale1Y = (float) (calibration[1] / finalVoxelSize);
        float scale1Z = (float) (calibration[2] / finalVoxelSize);

        return clij2.create(
                (long) (input.getWidth() * scale1X),
                (long) (input.getHeight() * scale1Y),
                (long) (input.getDepth() * scale1Z));
	}
	
}
