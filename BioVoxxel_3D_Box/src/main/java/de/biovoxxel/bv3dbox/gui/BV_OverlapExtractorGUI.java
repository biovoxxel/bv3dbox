package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BVOverlapExtractor;
import ij.ImagePlus;


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Overlap Extractor (2D/3D)")
public class BVOverlapExtractorGUI implements Command {

	
	@Parameter(label = "Primary objects", description = "", persist = true)
	private ImagePlus image_plus_1;
	
	@Parameter(label = "Secondary objects", description = "", persist = true)
	private ImagePlus image_plus_2;
	
	@Parameter(label = "%-Volume range", description = "", min = "0", max = "100")
	private String volume_range = "0.0-100.0";
	
	@Parameter(label = "Exclude primary on edges", description = "")
	private Boolean exclude_edge_objects = false;
	
	@Parameter(label = "Show original primary statistics", description = "")
	private Boolean show_original_primary_statistics = false;
	
	@Parameter(label = "Show extracted objects", description = "")
	private Boolean show_extracted_objects = false;
	
	@Parameter(label = "Show count statistics", description = "")
	private Boolean show_count_statistics = false;
	
	@Parameter(label = "Show volume statistics", description = "")
	private Boolean show_volume_statistics = false;
		
	@Parameter(label = "Show %-volume map", description = "")
	private Boolean show_percent_volume_map = false;

	@Override
	public void run() {
		BVOverlapExtractor bvolex = new BVOverlapExtractor(image_plus_1, image_plus_2);
		
		bvolex.setVolumeRange(volume_range);
		bvolex.setOutputFlags(exclude_edge_objects, show_original_primary_statistics, show_extracted_objects, show_count_statistics, show_volume_statistics, show_percent_volume_map);
		
		bvolex.extract();
		
	}	
}
