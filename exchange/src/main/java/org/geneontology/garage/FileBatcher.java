package org.geneontology.garage;

import java.io.File;

public class FileBatcher {

	public FileBatcher() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		String input_dir = "/Users/bgood/Desktop/test/go_cams/reactome/";
		File dir = new File(input_dir);
		File[] directoryListing = dir.listFiles();
		if (directoryListing != null) {
			for (File input : directoryListing) {
				input.renameTo(new File(input.getParent()+"/reactome-homosapiens-"+input.getName()));
			}
		}

	}

}
