/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

/**
 * @author bgood
 *
 */
public class Biopax2GOCmdLine {

	/**
	 * 
	 */
	public Biopax2GOCmdLine() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param args
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws RDFHandlerException 
	 * @throws RDFParseException 
	 * @throws RepositoryException 
	 * @throws OWLOntologyStorageException 
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws ParseException, OWLOntologyCreationException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException, IOException {
		BioPaxtoGO bp2g = new BioPaxtoGO();
		//parameters to set
		String input_biopax = null; //"/Users/bgood/Desktop/test/biopax/Homo_sapiens_sept9_2019.owl";
		String output_file_stub = null; //"/Users/bgood/Desktop/test/go_cams/reactome/reactome-homosapiens-"; 
		String output_blazegraph_journal =  null; //"/Users/bgood/noctua-config/blazegraph.jnl";  
		String tag = ""; //unexpanded
		String base_title = "title here";//"Will be replaced if a title can be found for the pathway in its annotations
		String default_contributor = "";//"https://orcid.org/0000-0002-7334-7852"; //
		String default_provider = "";//"https://reactome.org";//"https://www.wikipathways.org/";//"https://www.pathwaycommons.org/";	

		// create Options object
		Options options = new Options();
		options.addOption("b", true, "biopax pathway file to convert");
		options.addOption("o", true, "output directory");
		options.addOption("bg", true, "blazegraph output journal"); 
		options.addOption("tag", true, "a tag to be added to the title's of generated go-cams");
		options.addOption("dc", true, "ORCID id of default contributor for attribution, e.g. https://orcid.org/0000-0002-7334-7852");
		options.addOption("dp", true, "URL of default provider for attribution, e.g. https://reactome.org");
		options.addOption("lego", true, "Location of go-lego ontology file.  This is an ontology that serves to import other ontologies important for GO validation and operation.");
		options.addOption("go", true, "Location of primary GO file. Use GOPlus for inference. ");
		
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse( options, args);

		if(cmd.hasOption("b")) {
			input_biopax = cmd.getOptionValue("b");}
		else {
			System.out.println("please provide a biopax file to validate to convert.");
			System.exit(0);}
		if(cmd.hasOption("o")) {
			output_file_stub = cmd.getOptionValue("o");}
		else {
			System.out.println("please specify an output directory, with optional file prefix, e.g. /test/go_cams/reactome/reactome-homosapiens-");
			System.exit(0);}
		if(cmd.hasOption("bg")) {
			output_blazegraph_journal = cmd.getOptionValue("bg");
			bp2g.blazegraph_output_journal = output_blazegraph_journal;
		}
		if(cmd.hasOption("tag")) {
			tag = cmd.getOptionValue("tag");
		}
		if(cmd.hasOption("dc")) {
			default_contributor = cmd.getOptionValue("dc");
		}
		if(cmd.hasOption("dp")) {
			default_provider = cmd.getOptionValue("dp");
		}
		if(cmd.hasOption("lego")) {
			bp2g.go_lego_file = cmd.getOptionValue("lego");}
		else {
			System.out.println("please provide a go-lego OWL file.");
			System.exit(0);}
		if(cmd.hasOption("go")) {
			bp2g.go_plus_file = cmd.getOptionValue("go");
			bp2g.goplus = new GOPlus(bp2g.go_plus_file);
			}
		else {
			System.out.println("please provide a go OWL file.");
			System.exit(0);}


		Set<String> test_pathways = new HashSet<String>();
		test_pathways.add("Signaling by BMP");
		test_pathways.add("Glycolysis");
		test_pathways.add("Disassembly of the destruction complex and recruitment of AXIN to the membrane");
		//	//set to null to do full run
		//	test_pathways = null;
		bp2g.convertReactomeFile(input_biopax, output_file_stub, base_title, default_contributor, default_provider, tag, test_pathways);

	}

}
