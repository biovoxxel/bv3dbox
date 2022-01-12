package test.plugins;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

@Plugin(type = Command.class, menuPath = "Plugins>Test>Dynamic Command")
public class TestDynamicCommand extends DynamicCommand {
	
	CLIJ2 clij2 = CLIJ2.getInstance();
	
	@Parameter(label = "Choices", initializer = "initializeImage", persist = false)
	String dropdown;
	
	@Parameter(label = "Switch choices", callback = "callbackCheckbox", persist = false)
	Boolean checkbox = false;
	
	@Parameter(label = "Radius", callback = "previewTest", persist = true)
	double radius = 1.0;
	
	@Parameter(label = "Preview", callback = "previewTest", persist = false)
	Boolean preview = false;
	
	public void run() {
		preview = true;
		previewTest();
	}
	
	public void initializeImage() {
		String[] allImageNames = WindowManager.getImageTitles();
		String[] imageNames = new String[allImageNames.length + 1];
		imageNames[0] = "None";
		for (int w = 0; w < allImageNames.length; w++) {
			imageNames[w+1] = allImageNames[w];
		}
		
		List<String> imagePlusList = Arrays.asList(imageNames);
		final MutableModuleItem<String> dropdown = getInfo().getMutableInput("dropdown", String.class);
		dropdown.setChoices(imagePlusList);
	}
	
	public void callbackCheckbox() {
		if (checkbox) {
			String[] animalNames = {"Cat", "Dog", "Lion", "Opossum"};
			List<String> animalNameList = Arrays.asList(animalNames);
			final MutableModuleItem<String> dropdown = getInfo().getMutableInput("dropdown", String.class);
			dropdown.setChoices(animalNameList);
		} else {
			initializeImage();
		}
	}
	
	public void previewTest() {
		if (preview) {
			ImagePlus output = WindowManager.getImage("filtered_" + dropdown);
			boolean openNew = false;
			if (output == null) {
				openNew = true;
				output = new ImagePlus();
				output.setTitle("filtered_" + dropdown);
			}
			
			ImagePlus input = WindowManager.getImage(dropdown);
			
			ClearCLBuffer input_gpu = clij2.push(input);
			ClearCLBuffer output_gpu = clij2.create(input_gpu);
			clij2.gaussianBlur2D(input_gpu, output_gpu, radius, radius);
			ImagePlus temp = clij2.pull(output_gpu);
			
			output.setProcessor(temp.getProcessor());
			
			if (openNew) {
				output.show();
			} else {
				output.updateAndDraw();
			}
		} else {
			ImagePlus output = WindowManager.getImage("filtered_" + dropdown);
			if (output != null) {
				output.close();
			}
		}
	}
}