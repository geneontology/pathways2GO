/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.openrdf.model.BNode;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.Rio;
import org.openrdf.rio.helpers.RDFHandlerBase;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import com.bigdata.journal.Options;
import com.bigdata.rdf.sail.BigdataSail;
import com.bigdata.rdf.sail.BigdataSailRepository;
import com.bigdata.rdf.sail.BigdataSailRepositoryConnection;

/**
 * This class mostly copied from BlazegraphMolecularModelManager in Minerva by @balhoff 
 * @author bgood
 *
 */
public class Blazer {

	private final BigdataSailRepository repo;
	public BigdataSailRepository getRepo() {
		return repo;
	}

	/**
	 * 
	 */
	public Blazer(String pathToJournal) {
		// TODO Auto-generated constructor stub
		this.repo = initializeRepository(pathToJournal);
	}

	private BigdataSailRepository initializeRepository(String pathToJournal) {
		try {
			Properties properties = new Properties();
			properties.load(this.getClass().getResourceAsStream("blazegraph.properties"));
			properties.setProperty(Options.FILE, pathToJournal);
			BigdataSail sail = new BigdataSail(properties);
			BigdataSailRepository repository = new BigdataSailRepository(sail);
			repository.initialize();
			return repository;
		} catch (RepositoryException e) {
			//LOG.fatal("Could not create Blazegraph sail", e);
			return null;		
		} catch (IOException e) {
			//LOG.fatal("Could not create Blazegraph sail", e);
			return null;
		}
	}

	/**
	 * Imports ontology RDF directly to database. No OWL checks are performed. 
	 * @param file
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 * @throws RepositoryException 
	 */
	public void importModelToDatabase(File file) throws OWLOntologyCreationException, RepositoryException, IOException, RDFParseException, RDFHandlerException {
		synchronized(getRepo()) {
			final BigdataSailRepositoryConnection connection = getRepo().getUnisolatedConnection();
			try {
				connection.begin();
				try {
					java.util.Optional<URI> ontIRIOpt = scanForOntologyIRI(file).map(id -> new URIImpl(id));
					if (ontIRIOpt.isPresent()) {
						URI graph = ontIRIOpt.get();
						connection.clear(graph);
						//FIXME Turtle format is hard-coded here
						connection.add(file, "", RDFFormat.TURTLE, graph);
						connection.commit();
					} else {
						System.out.println("blazegraph iri exception");
						throw new OWLOntologyCreationException("Detected anonymous ontology; must have IRI");
					}
				} catch (Exception e) {
					System.out.println("Random exception");
					connection.rollback();
					throw e;
				}
			} finally {
				connection.close();
			}
		}
	}

	/**
	 * Tries to efficiently find the ontology IRI triple without loading the whole file.
	 * @throws IOException 
	 * @throws RDFHandlerException 
	 * @throws RDFParseException 
	 */
	private java.util.Optional<String> scanForOntologyIRI(File file) throws RDFParseException, RDFHandlerException, IOException {
		RDFHandlerBase handler = new RDFHandlerBase() {
			public void handleStatement(Statement statement) { 
				if (statement.getObject().stringValue().equals("http://www.w3.org/2002/07/owl#Ontology") &&
						statement.getPredicate().stringValue().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) throw new FoundTripleException(statement);
			}
		};
		InputStream inputStream = new FileInputStream(file);
		try {
			//FIXME Turtle format is hard-coded here
			RDFParser parser = Rio.createParser(RDFFormat.TURTLE);
			parser.setRDFHandler(handler);
			parser.parse(inputStream, "");
			// If an ontology IRI triple is found, it will be thrown out
			// in an exception. Otherwise, return empty.
			return java.util.Optional.empty();
		} catch (FoundTripleException fte) {
			Statement statement = fte.getStatement();
			if (statement.getSubject() instanceof BNode ) {
				System.out.println("Blank node subject for ontology triple: " + statement);
				return java.util.Optional.empty();
			} else {
				return java.util.Optional.of(statement.getSubject().stringValue());
			}
		} finally {
			inputStream.close();
		}
	}

	private static class FoundTripleException extends RuntimeException {

		private static final long serialVersionUID = 8366509854229115430L;
		private final Statement statement;

		public FoundTripleException(Statement statement) {
			this.statement = statement;
		}

		public Statement getStatement() {
			return this.statement;
		}
	}

	public TupleQueryResult runSparqlQuery(String query) {
		// open connection
		try {
			BigdataSailRepositoryConnection cxn = ((BigdataSailRepository) repo).getReadOnlyConnection();
			// evaluate sparql query
			try {
				final TupleQuery tupleQuery = cxn.prepareTupleQuery(QueryLanguage.SPARQL,query);
				TupleQueryResult result = tupleQuery.evaluate();
				return result;
			} catch (MalformedQueryException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				// close the repository connection
				cxn.close();
			}
		} catch (RepositoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return null; 
	}

	/**
	 * @param args
	 * @throws RepositoryException 
	 * @throws QueryEvaluationException 
	 */
	public static void main(String[] args) throws QueryEvaluationException {
		Blazer b = new Blazer("./src/test/resources/gocam/blazegraph.jnl"); 
		TupleQueryResult result = null;
		try {
			result = b.runSparqlQuery("select ?s ?p ?o where {?s ?p ?o } limit 3");
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				System.out.println(bindingSet);
			}
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			result.close();
		}
	}

}

