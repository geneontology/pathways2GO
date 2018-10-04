/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * @author bgood
 *
 */
public class NoctuaLayout {

	/**
	 * 
	 */
	public NoctuaLayout(GoCAM go_cam) {
		
	}

	/**
	 * Given knowledge of semantic structure of a GO-CAM, try to make a basic layout that is useful within the Noctua editor as it stands in Version1 (May 2018).
	 * In this implementation, all function node attributes should be fully 'folded' in the the UI. 
	 * Attempt to line the function nodes up in some reasonable order..
	 * @param go_cam
	 */
	GoCAM layoutForNoctuaVersion1(GoCAM go_cam) {
		Iterator<OWLIndividual> pathways = EntitySearcher.getIndividuals(GoCAM.bp_class, go_cam.go_cam_ont).iterator();
		int x_spacer = 450;
		int x = 200;
		//generally only one pathway represented with reactions - others just links off via part of
		//draw them in a line across the top 
		while(pathways.hasNext()) {
			OWLNamedIndividual pathway = (OWLNamedIndividual)pathways.next();

			//making Pathway basically just a label for what people are looking at
			//put it at top left
			int h = 20; 
			int k = 20; 

			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(h));
			go_cam.addLiteralAnnotations2Individual(pathway.getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(k));   			

			//find reactions that are part of this pathway
			Collection<OWLIndividual> reactions_and_subpathways = EntitySearcher.getObjectPropertyValues(pathway, GoCAM.has_part, go_cam.go_cam_ont);
			Set<OWLIndividual> reactions = new HashSet<OWLIndividual>();

//			for(OWLIndividual r : reactions_and_subpathways) {
//				for(OWLClassExpression type :EntitySearcher.getTypes(r, go_cam.go_cam_ont)) {
//					OWLClass c = type.asOWLClass();
//					if(c.equals(GoCAM.molecular_function)) {
//						reactions.add(r);
//						break;
//					}
//				}
//			}
			//classify reactions: root of causal chain, member of chain, island
			Set<OWLIndividual> islands = new HashSet<OWLIndividual>();
			Set<OWLIndividual> chain_roots = new HashSet<OWLIndividual>();
			Set<OWLIndividual> chain_members = new HashSet<OWLIndividual>();
			for(OWLIndividual r : reactions_and_subpathways) {
				int incoming = 0;
				int outgoing = 0;
				Collection<OWLObjectPropertyAssertionAxiom> axioms = getCausalReferencingOPAxioms((OWLEntity) r, go_cam);
				for(OWLObjectPropertyAssertionAxiom op : axioms) {
					if(op.getSubject().equals(r)) {
						outgoing++;
					}else if(op.getObject().equals(r)) {
						incoming++;
					}		
				}
				if(incoming==0&&outgoing==0) {
					islands.add(r);
				}
				else if(incoming==0&&outgoing>0) {
					chain_roots.add(r);
				}else if(incoming>0) {
					chain_members.add(r);
				}

			}

			//if there is a root or roots.. do a sideways horizontal line graph
			if(chain_roots.size()>0) {
				layoutChain(250, 20, 350, 500, chain_roots, chain_members, islands, go_cam);	
			}else if(chain_members.size()==0) {
				layoutChain(250, 20, 200, 500, chain_roots, chain_members, islands, go_cam);	
			}else{	
				// do circle layout 
				if(reactions!=null) {					
					layoutCircle(chain_members, islands, go_cam);		
				}
			}
			x = x+x_spacer;
		}
		return go_cam;
	}

	Set<OWLObjectPropertyAssertionAxiom> getCausalReferencingOPAxioms(OWLEntity e, GoCAM go_cam){
		Collection<OWLAxiom> axioms = EntitySearcher.getReferencingAxioms((OWLEntity) e, go_cam.go_cam_ont);
		Set<OWLObjectPropertyAssertionAxiom> causal_axioms = new HashSet<OWLObjectPropertyAssertionAxiom>();
		for(OWLAxiom axiom : axioms) {
			if(axiom.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)){
				OWLObjectPropertyAssertionAxiom op = (OWLObjectPropertyAssertionAxiom) axiom;
				//causal only
				//TODO would be fun to actually use the RO and a little inference to make this list..  
				if(op.getProperty().equals(GoCAM.directly_negatively_regulates)||
						op.getProperty().equals(GoCAM.directly_positively_regulates)||
						op.getProperty().equals(GoCAM.directly_negatively_regulated_by)||
						op.getProperty().equals(GoCAM.directly_positively_regulated_by)||
						op.getProperty().equals(GoCAM.provides_direct_input_for)||
						//kind of wonky but lets try it..
						op.getProperty().equals(GoCAM.has_output)||
						op.getProperty().equals(GoCAM.involved_in_negative_regulation_of)||
						op.getProperty().equals(GoCAM.involved_in_positive_regulation_of)
						) {
					causal_axioms.add(op);
				}
			}				
		}
		return causal_axioms;
	}

	void layoutChain(int x, int y, int x_spacer, int y_spacer, Set<OWLIndividual> chain_roots, Set<OWLIndividual> chain_members, Set<OWLIndividual> islands, GoCAM go_cam) {
		int max_y = 0;
		int r = 0;
		int r2_start = 75;
		for(OWLIndividual root : chain_roots) {
			if(r%2==0) {
				max_y = layoutHorizontalLine(x, y, x_spacer, y_spacer, root, go_cam);
			}else {
				max_y = layoutHorizontalLine(r2_start, y, x_spacer, y_spacer, root, go_cam);
			}
			y = max_y+y_spacer;
			r++;
		}	
		if(r%2!=0) {
			x = r2_start;
		}
		for(OWLIndividual island : islands) {
			go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) island).getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
			go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) island).getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));
			x+=x_spacer;
		}
	}

	int layoutHorizontalLine(int x, int y, int x_spacer, int y_spacer, OWLIndividual node, GoCAM go_cam) {
		//don't fly into infinity and beyond!
		if(mapHintPresent((OWLNamedIndividual) node, go_cam)) {		
			return y;
		}
	//	System.out.println("laying out "+go_cam.getaLabel((OWLEntity) node)+" "+node.toString()+x+" "+y+" "+x_spacer+" "+y_spacer);
		//layout the node
		go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) node).getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
		go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) node).getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));
		//recursively lay out children
		Collection<OWLIndividual> children = EntitySearcher.getObjectPropertyValues(node, GoCAM.provides_direct_input_for, go_cam.go_cam_ont);
		if(children.size()==0) {
			Collection<OWLObjectPropertyAssertionAxiom> axioms = getCausalReferencingOPAxioms((OWLEntity) node, go_cam);
			for(OWLObjectPropertyAssertionAxiom axiom : axioms) {
				if(node.equals(((OWLObjectPropertyAssertionAxiom) axiom).getSubject())){
					children.add(((OWLObjectPropertyAssertionAxiom) axiom).getObject());
				}				
			}
		}
		int nrows = 0;
		//typically there will only be one, but... 
		for(OWLIndividual child : children) {
			if(!mapHintPresent((OWLNamedIndividual) child, go_cam)) {
				layoutHorizontalLine(x+x_spacer, y, x_spacer, y_spacer, child, go_cam);
				nrows++;
				y = y+y_spacer;
			}
		}
		if(nrows==1) {
			return y-y_spacer;
		}
		return y;
	}

	void layoutCircle(Collection<OWLIndividual> chain_members, Collection<OWLIndividual> islands, GoCAM go_cam) {
		//layout any unconnected nodes on the top for this one
		int x = 250; int y = 20; int x_spacer = 75;
		for(OWLIndividual island : islands) {
			go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) island).getIRI(), GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
			go_cam.addLiteralAnnotations2Individual(((OWLNamedIndividual) island).getIRI(), GoCAM.y_prop, go_cam.df.getOWLLiteral(y));
			x+=x_spacer;
		}		

		LinkedHashSet<OWLIndividual> ordered_chain = causalSort(chain_members, null, null, go_cam);
		//add any stragglers.. give up on multiple loops for now. 
		if(chain_members.size()>ordered_chain.size()) {
			for(OWLIndividual member : chain_members) {
				ordered_chain.add(member);//hashset should ensure non-redundancy
			}
		}
		//TODO calculate based on n nodes
		//currently reasonable approximation for small number of nodes
		int h = 800; // x coordinate of circle center
		int k = 700; // y coordinate of circle center (y going down for web layout)
		int r = 600; //radius of circle 
		int n = chain_members.size(); //number nodes to draw 
		double step = 2*Math.PI/n; //radians to move about circle per step 
		double theta = 0;
		for(OWLIndividual reaction_node : ordered_chain) {
			x = Math.round((long)(h + r*Math.cos(theta)));
			y = Math.round((long)(k - r*Math.sin(theta))); 
			theta = theta + step;
			IRI node_iri = reaction_node.asOWLNamedIndividual().getIRI();
			go_cam.addLiteralAnnotations2Individual(node_iri, GoCAM.x_prop, go_cam.df.getOWLLiteral(x));
			go_cam.addLiteralAnnotations2Individual(node_iri, GoCAM.y_prop, go_cam.df.getOWLLiteral(y));
		}
	}

	private LinkedHashSet<OWLIndividual> causalSort(Collection<OWLIndividual> chain_members, LinkedHashSet<OWLIndividual> ordered_chain, OWLIndividual node, GoCAM go_cam){
		if(ordered_chain==null&&node==null) {
			//initialize
			ordered_chain = new LinkedHashSet<OWLIndividual>();
			node = chain_members.iterator().next();
			ordered_chain.add(node);
		}
		//get causal child 
		Collection<OWLIndividual> children = EntitySearcher.getObjectPropertyValues(node, GoCAM.provides_direct_input_for, go_cam.go_cam_ont);
		if(children.size()==0) {
			Collection<OWLObjectPropertyAssertionAxiom> axioms = getCausalReferencingOPAxioms((OWLEntity) node, go_cam);
			for(OWLObjectPropertyAssertionAxiom axiom : axioms) {
				if(node.equals(((OWLObjectPropertyAssertionAxiom) axiom).getSubject())){
					children.add(((OWLObjectPropertyAssertionAxiom) axiom).getObject());
				}				
			}
		}
		//add one
		if(children.size()>0) {
			OWLIndividual next = children.iterator().next();
			if(ordered_chain.add(next)) {
				ordered_chain = causalSort(chain_members, ordered_chain, next, go_cam);
			}
		}
		return ordered_chain;
	}

	/**
	 * Have we already given the node a location?
	 * @param node
	 * @param go_cam
	 * @return
	 */
	private boolean mapHintPresent(OWLNamedIndividual node, GoCAM go_cam){
		boolean x_present = false;
		//long nx = EntitySearcher.getAnnotationObjects(node, go_cam.go_cam_ont, GoCAM.x_prop).count();
		Collection<OWLAnnotation> xs = EntitySearcher.getAnnotationObjects(node, go_cam.go_cam_ont, GoCAM.x_prop);
		if(xs.size()>0) {
			x_present = true;
		}
		return x_present;
	}

}
