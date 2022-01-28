package de.biovoxxel.bv3dbox.plugins;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ij.ImagePlus;

class BVObjectInspectorTest {

	ImagePlus image1;
	ImagePlus image2;
	
	@BeforeAll
	void readImages() {
		image1 = new ImagePlus("C:\\Users\\broch\\Desktop\\Binary Nuclei.tif");
		image2 = new ImagePlus("C:\\Users\\broch\\Desktop\\Binary Speckles.tif");
	}
	
	@BeforeEach
	void setup() {
		BVObjectInspector bvoi = new BVObjectInspector(image1, image2);
		bvoi.inspect();
	}
	
	
	@Test
	void test() {
		fail("Not yet implemented");
	}

}
