var filemenu = newMenu("BioVoxxel 3D Box Menu Tool", newArray("Make Voxel Isotropic", "Make 3D Image Isotropic", "-", "Flat Field Correction (2D/3D)", "Pseudo Flat Field Correction (2D/3D)", "Difference of Gaussian (2D/3D)", "Recursive Filter (2D/3D)", "Convoluted Background Subtraction (2D/3D)", "-", "Threshold Check (2D/3D)", "-", "Voronoi Threshold Labler (2D/3D)", "-", "Label Splitter (2D/3D)", "Separate Labels (2D/3D)", "Post Processor (2D/3D)", "-", "Object Inspector (2D/3D)", "Overlap Extractor (2D/3D)", "Neighbor Analysis (2D/3D)", "-", "Labels to 2D Roi Manager", "Labels to 3D Roi Manager", "-", "Settings", "-", "About"));

macro "BioVoxxel 3D Box Menu Tool - icon:bv3dbox-logo.png"{
	
	run("Settings", "logginglevel=NONE scijavaloglevel=ERROR");

	bv3dvox_cmd = getArgument();
	if (bv3dvox_cmd == "About") {
		run("About ");
	} else if (bv3dvox_cmd == "Voronoi Threshold Labler (2D/3D)" || bv3dvox_cmd == "Convoluted Background Subtraction (2D/3D)") {
 		run(bv3dvox_cmd, "previewbutton=null");
	} else if (bv3dvox_cmd == "Threshold Check (2D/3D)") {
		run(bv3dvox_cmd, "invertimage=null");
	} else if (bv3dvox_cmd!="-") {
		run(bv3dvox_cmd);
	}
}
