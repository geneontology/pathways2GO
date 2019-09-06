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
!gpa-version: 1.1
DB	objectid	qualifier	goid	dbreferences	evidence codes	withorfrom	interactingtaxonid	date	assignedby	annotationextension	annotationproperties
 */
public class GPAD {
	String gpa_version;
	public static class Annotation implements Comparable<Object>{
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

		public String getString() {
			String s = "";
			s+=DB+"\t";
			s+=DBObjectID+"\t";
			s+=Qualifier+"\t";
			s+=GOID+"\t";
			s+=DBReferences+"\t";
			s+=EvidenceCode+"\t";
			s+=WithorFrom+"\t";
			s+=InteractingTaxonID+"\t";
			s+=Date+"\t";
			s+=AssignedBy+"\t";
			s+=AnnotationExtension+"\t";
			s+=AnnotationProperties+"\t";
			return s;
		}

		public static Annotation clone(Annotation a) {
			Annotation b = new Annotation();
			b.DB = a.DB;
			b.DBObjectID = a.DBObjectID;
			b.Qualifier = a.Qualifier;
			b.GOID = a.GOID;
			b.DBReferences = a.DBReferences;
			b.EvidenceCode = a.EvidenceCode;
			b.WithorFrom = a.WithorFrom;
			b.InteractingTaxonID = a.InteractingTaxonID;
			b.Date = a.Date;
			b.AssignedBy = a.AssignedBy;
			b.AnnotationExtension = a.AnnotationExtension;
			b.AnnotationProperties = a.AnnotationProperties;
			return b;
		}

		/**
		 * Very simple comparison - annos = if their gene id and go acc are the same
		 * Change this to check for evidence, qualifiers, etc.
		 */
		@Override
		public int compareTo(Object o) {
			Annotation a = (Annotation)o;
			if(DBObjectID.equals(a.DBObjectID)&&GOID.equals(a.GOID)) {
				return 0;  }
			else {
				return DBObjectID.compareTo(a.DBObjectID);
			}
		}  
		
		@Override
		public boolean equals(Object o) {
			Annotation a = (Annotation)o;
			if(DBObjectID.equals(a.DBObjectID)&&GOID.equals(a.GOID)) {
				return true;  
			}
			else {
				return false;
			}
		}
		
		@Override
		public int hashCode() {
		      return DBObjectID.hashCode()+GOID.hashCode();
		  }
	
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
		f.write(a.getString()+"\n");
	}
	f.close();
}

public class AnnotationComparison {

}

}
