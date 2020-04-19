/**
 * 
 */
package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.geneontology.gocam.exchange.PhysicalEntityOntologyBuilder.ReasonerImplementation;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import com.google.common.collect.Sets;

/**
 * @author benjamingood
 *
 */
public class PhysicalEntityOntologyBuilderTest {

	static String input_biopax_file = "/tmp/reactome_biopax/Homo_sapiens.owl";
	static String reacto_out = "/tmp/REACTO";
	static String chebi_location = null;
	
	/**
	* @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		//download the latest reactome release
		String reactome = "https://reactome.org/download/current/biopax.zip";
		URL reactome_url = new URL(reactome);
		File biopax = new File("/tmp/biopax.zip");
		org.apache.commons.io.FileUtils.copyURLToFile(reactome_url, biopax);
		if(reactome.endsWith(".zip")) {
			//unGunzipFile();
			unzip(biopax.getAbsolutePath(), "/tmp/reactome_biopax");
		}
		
	}
	
	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	//This test uses more resources than travis has available - need to run it manually.  
	//@Test
	public void reasonerTest() {
		try {
			OWLOntologyManager	ontman = OWLManager.createOWLOntologyManager();		
			System.out.println(" downloading go plus ");
			OWLOntology go_lego_tbox = ontman.loadOntology(IRI.create("http://purl.obolibrary.org/obo/go/extensions/go-plus.owl"));			
			System.out.println(" making reacto ");
			boolean add_imports = false;
			OWLOntology chebi = null;
			if(chebi_location!=null) {
				chebi = ontman.loadOntologyFromOntologyDocument(new File(chebi_location));
			}
			OWLOntology reacto = PhysicalEntityOntologyBuilder.buildReacto(input_biopax_file, reacto_out, go_lego_tbox, add_imports, chebi);			
			ontman.addAxioms(reacto, go_lego_tbox.getAxioms());
			OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			System.out.println(" creating reacto reasoner ");
			OWLReasoner reasoner = reasonerFactory.createReasoner(reacto);
			reasoner.flush();
			boolean consistent = reasoner.isConsistent();
			boolean coherent = true;
			Set<OWLClass> u = reasoner.getUnsatisfiableClasses().getEntities();
			if(u.size()>1) {
				coherent = false;
				for(OWLClass broken : u) {
					System.out.println("unsatisfiable class: "+broken);
				}
			}else {
				System.out.println("reacto is coherent");
			}
			assertTrue("reacto plus go-plus is not consistent ", consistent);
			assertTrue("reacto plus go-plus is not coherent (has an unsatisfiable class) ", coherent);
		} catch (OWLOntologyCreationException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OWLOntologyStorageException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RepositoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RDFParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RDFHandlerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void unzip(String zipFilePath, String destDir) {
        File dir = new File(destDir);
        // create output directory if it doesn't exist
        if(!dir.exists()) dir.mkdirs();
        FileInputStream fis;
        //buffer for read and write data to file
        byte[] buffer = new byte[1024];
        try {
            fis = new FileInputStream(zipFilePath);
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();
            while(ze != null){
                String fileName = ze.getName();
                File newFile = new File(destDir + File.separator + fileName);
                System.out.println("Unzipping to "+newFile.getAbsolutePath());
                //create directories for sub directories in zip
                new File(newFile.getParent()).mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
                }
                fos.close();
                //close this ZipEntry
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
            //close last ZipEntry
            zis.closeEntry();
            zis.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
	
}
