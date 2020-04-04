/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

/**
 * Convert biopax pathways into GO-CAMs from the command line.  
 * Example parameters: -b "/test/biopax/Homo_sapiens_sept9_2019.owl" -o "/test/go_cams/reactome/reactome-homosapiens-" -bg "/blazegraph.jnl" -tag "unexpanded" -dc "https://orcid.org/0000-0002-7334-7852" -dp "https://reactome.org" -go "/gocam_ontology/go-plus.owl" -lego "/test/go-lego-test.owl" -tp "Glycolysis"
 * @author bgood
 *
 */
public class Biopax2GOCmdLine {

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
		String default_contributor = "https://orcid.org/0000-0002-7334-7852"; //
		String default_provider = "https://reactome.org";//"https://www.wikipathways.org/";//"https://www.pathwaycommons.org/";	
		String test_pathway_name = null;
		String catalog = null;
		String reacto_out = null;
		// create Options object
		Options options = new Options();
		options.addOption("b", true, "biopax pathway file to convert");
		options.addOption("reacto", true, "if reacto, generate an ontology of all physical entities in the input biopax file. ");

		options.addOption("o", true, "output directory");
		options.addOption("bg", true, "blazegraph output journal"); 
		options.addOption("tag", true, "a tag to be added to the title's of generated go-cams");
		options.addOption("dc", true, "ORCID id of default contributor for attribution, e.g. https://orcid.org/0000-0002-7334-7852");
		options.addOption("dp", true, "URL of default provider for attribution, e.g. https://reactome.org");
		options.addOption("lego", true, "Location of go-lego ontology file.  This is an ontology that serves to import other ontologies important for GO validation and operation.");
		options.addOption("go", true, "Location of primary GO file. Use GOPlus for inference. ");
		options.addOption("tp", true, "Exact name of a specific pathway to test - e.g. \"Signaling by MP\".  Other pathways in the biopax input file will be ignored. Default is that all pathways are processed");
		options.addOption("c", true, "Catalog file for tbox");


		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse( options, args);

		if(cmd.hasOption("b")) {
			input_biopax = cmd.getOptionValue("b");
		}
		else {
			System.out.println("please provide a biopax file to validate to convert.");
			System.exit(0);}
		if(cmd.hasOption("reacto")) {
			reacto_out = cmd.getOptionValue("reacto");
		}		


		if(reacto_out!=null) {
			PhysicalEntityOntologyBuilder.buildReacto(input_biopax, reacto_out, null);
		}
		//could chain them together if desired, but simple for now.  
		else {
			if(cmd.hasOption("o")) {
				output_file_stub = cmd.getOptionValue("o");
			}
			else {
				System.out.println("please specify an output directory, with optional file prefix, e.g. /test/go_cams/reactome/reactome-homosapiens-");
			}
			Blazer blaze = null;
			if(cmd.hasOption("bg")) {
				output_blazegraph_journal = cmd.getOptionValue("bg");
				bp2g.blazegraph_output_journal = output_blazegraph_journal;
				
				String journal = bp2g.blazegraph_output_journal;	
				//clean out any prior data in triple store
				FileWriter clean = new FileWriter(journal, false);
				clean.write("");
				clean.close();
				blaze = new Blazer(journal);
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

			Set<String> test_pathways = null;
			if(cmd.hasOption("tp")) {
				test_pathways = new HashSet<String>();
				test_pathway_name = cmd.getOptionValue("tp");
				test_pathways.add(test_pathway_name);
			}
			
			OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();	
			if(cmd.hasOption("c")) {
				catalog = cmd.getOptionValue("c");
				if(catalog!=null) {
					ontman.setIRIMappers(Collections.singleton(new CatalogXmlIRIMapper(catalog)));
				}
			}
			else {
				System.out.println("Warning, no catalog file provided for for go-lego.   Specify one with -c catalog.xml");
			}
			
			//initialize the rules for inference		
			OWLOntology tbox = ontman.loadOntologyFromOntologyDocument(new File(bp2g.go_lego_file));
			bp2g.golego = new GOLego(tbox);
			QRunner tbox_qrunner = new QRunner(Collections.singleton(tbox), null, bp2g.golego.golego_reasoner, true, false, false);
			bp2g.tbox_qrunner = tbox_qrunner;
			
			File dir = new File(input_biopax);
			File[] directoryListing = dir.listFiles();
			//TODO generalize this!  
			Set<String> taxa = new HashSet<String>();
			taxa.add("http://purl.obolibrary.org/obo/NCBITaxon_9606");
			
			//run through all files
			if (directoryListing != null) {
				for (File biopax : directoryListing) {
					String name = biopax.getName();
					if(name.contains(".owl")||name.contains(".xml")) { 
						name = name.replaceAll(".owl", "-");
						name = name.replaceAll(".xml", "-");
						String this_output_file_stub = output_file_stub+name;
						bp2g.convert(biopax.getAbsolutePath(), this_output_file_stub, base_title, default_contributor, default_provider, tag, test_pathways, blaze, taxa);
					}
				}
			}else {
				bp2g.convert(input_biopax, output_file_stub, base_title, default_contributor, default_provider, tag, test_pathways, blaze, taxa);
			}
		}
	}

}
