/**
 * 
 */
package org.geneontology.gpad;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author bgood
DB	objectid	qualifier	goid	dbreferences	evidence codes	withorfrom	interactingtaxonid	date	assignedby	annotationextension	annotationproperties
 */
public class GPAD {
	String gpa_version;
	public class Annotation{
		String DB = "";
		String DBObjectID = "";
		String Qualifier = "";
		String GOID = "";
		String DBReferences = "";
		String EvidenceCode = "";
		String WithorFrom = "";
		String InteractingTaxonID = "";
		String Date = "";
		String AssignedBy = "";
		String AnnotationExtension = "";
		String AnnotationProperties = "";
	}
	/**
	 * 
	 */
	public GPAD() {
		// TODO Auto-generated constructor stub
	}

	public Set<Annotation> parseFile(String input_file) throws IOException{
		Set<Annotation> annos = new HashSet<Annotation>();
		BufferedReader reader = new BufferedReader(new FileReader(input_file));
		String line = reader.readLine();
		if(line.startsWith("!")) {
			gpa_version = line.split(" ")[1];
			line = reader.readLine();
		}
		while(line!=null) {
			Annotation a = new Annotation();
			String[] cols = line.split("\t");
			a.DB = cols[0];
			a.DBObjectID = cols[1];
			a.Qualifier = cols[2];
			a.GOID = cols[3];
			a.DBReferences = cols[4];
			a.EvidenceCode = cols[5];
			a.WithorFrom = cols[6];
			a.InteractingTaxonID = cols[7];
			a.Date = cols[8];
			a.AssignedBy = cols[9]; //minimum n
			if(cols.length>10) {
				a.AnnotationExtension = cols[10];
			}
			if(cols.length>11) {
				a.AnnotationProperties = cols[11];
			}
			annos.add(a);
			line = reader.readLine();
		}
		reader.close();
		return annos;
	}
	
	public void writeFile(Set<Annotation> annos, String outfile) throws IOException {
		FileWriter f = new FileWriter(outfile);
		if(gpa_version!=null) {
			f.write("!gpa-version: "+gpa_version+"\n");
		}else {
			f.write("!gpa-version: 1.1\n");
		}
		for(Annotation a : annos) {
			f.write(a.DB+"\t");
			f.write(a.DBObjectID+"\t");
			f.write(a.Qualifier+"\t");
			f.write(a.GOID+"\t");
			f.write(a.DBReferences+"\t");
			f.write(a.EvidenceCode+"\t");
			f.write(a.WithorFrom+"\t");
			f.write(a.InteractingTaxonID+"\t");
			f.write(a.Date+"\t");
			f.write(a.AssignedBy+"\t");
			f.write(a.AnnotationExtension+"\t");
			f.write(a.AnnotationProperties+"\n");
		}
		f.close();
	}

}
