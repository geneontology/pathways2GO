package org.geneontology.garage;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;
import java.util.Map;

public class Ascii {

	static CharsetEncoder asciiEncoder = Charset.forName("US-ASCII").newEncoder(); 
	 // or "ISO-8859-1" for ISO Latin 1

	public Ascii() {		
	}

	public static void main(String[] args) throws IOException {
//		String input = "CYP27A1 27-hydroxylates 5β-CHOL3α,7α,24(s)-triol";
//		System.out.println(isPureAscii(input));
//		System.out.println(stripNonAscii(input));
		String input_file = "/Users/bgood/Documents/GitHub/go-ontology/src/ontology/external2go/Reactions2GoTerms_human.txt";
		BufferedReader reader = new BufferedReader(new FileReader(input_file));
		String line = reader.readLine();
		int n = 1;
		while(line!=null) {
			if(!isPureAscii(line)) {
				System.out.println("line "+n+" text:"+ line);
			}
			line = reader.readLine();
			n++;
		}
	}
	
	public static boolean isPureAscii(String v) {
		return asciiEncoder.canEncode(v);
	}

	public static String stripNonAscii(String input) {
	    String output = input.replaceAll("[^\\p{ASCII}]", "");
	    return output;
	}
	
	public static String simpleGreekMap(String input) {
		Map<String, String> ascii_english = new HashMap<String, String>();
		ascii_english.put("α", "alpha");
		ascii_english.put("β", "beta");
		String output = input;
		for(String a : ascii_english.keySet()) {
			output = output.replaceAll(a, ascii_english.get(a));
		}
		return output;
	}
	
}
