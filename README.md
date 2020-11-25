[![Build Status](https://travis-ci.com/geneontology/pathways2GO.svg?branch=master)](https://travis-ci.com/geneontology/pathways2GO)
# Pathways to GO
Code for converting between biological pathways (e.g. Reactome, Wikipathways) expressed using the BioPAX standard and the Gene Ontology Causal Activity OWL Model (GO-CAM) structure.  

## Additional documentation
- GO-wiki, http://wiki.geneontology.org/index.php/Biological_Pathways_as_GO-CAMs
- [Slides describing rules for converting Reactome pathways (via BioPAX) to GO-CAMs.](https://docs.google.com/presentation/d/1_UAQN09WPCA5win5mbMs1ORMALNwiRwMBgZDPuyJEW8/edit#slide=id.g562cc2b479_0_0)

## Related Code Repositories 
- Web editor for GO-CAMs: https://github.com/geneontology/noctua
- Web server for hosting, reasoning over GO-CAMs https://github.com/geneontology/minerva
- Rule-based OWL reasoner optimized for large t-box, small a-box https://github.com/balhoff/arachne
- Work in progress OWL reasoner https://github.com/balhoff/whelk
- Current home of generated GO-CAM models - https://github.com/geneontology/noctua-models/tree/dev  

## Related Websites
- Development browser/editor with pathways converted to GO-CAMs http://noctua-dev.berkeleybop.org 

## Related Presentations
- Keynote at Rocky Bioinformatics https://www.slideshare.net/goodb/integrating-pathway-databases-with-gene-ontology-causal-activity-models

## Related Publications
- "Arachne: an OWL RL reasoner applied to Gene Ontology Causal Activity Models (and beyond)" ISWC, http://ceur-ws.org/Vol-2180/paper-05.pdf 
- "Gene Ontology Causal Activity Modeling (GO-CAM) moves beyond GO annotations to structured descriptions of biological functions and systems" Nature Genetics, https://www.nature.com/articles/s41588-019-0500-1 

## Building
- Clone repo, Maven build (developing with Eclipse and Maven plugin)

## How it works - ontologies
- GO-CAMs are "OWL instance graphs" aka little ontologies where instances are created and linked to one another to represent how we think gene products enable biology to function.  In semantic web terminology, these little ontologies define the "Abox" or "assertional component" of the GO knowledge base.  They depend on a "Tbox" or "terminological component".  The Tbox contains all of the definitions of the classes and properties that the Abox refers to.  In other words, the Tbox provides the language and the Abox provides the sentences.  For GO-CAMs, the Tbox is defined by a conglomerate ontology called go-lego http://purl.obolibrary.org/obo/go/extensions/go-lego.owl which contains GO, a number of species-specific anatomy ontologies, portions of CHEBI, parts of NCBI taxonomy, and various other bits and pieces needed to express GO-CAMs.  The import list for go-lego can be found here: https://github.com/geneontology/go-ontology/blob/master/src/ontology/extensions/go-lego-edit.ofn 
- Right now, go-lego does not contain class-level representations of all of the physical entities that are used in pathway databases (namely, gene products, complexes, and chemicals), Reactome in particular.  So, to generate semantically complete GO-CAMs, this base ontology needs to be extended with ontologies that capture the physical entities used.  
  - The NEO ontology is meant to capture all gene products that the GO consortium members may want to use in their GO-CAMs.  It is available here: http://purl.obolibrary.org/obo/go/noctua/neo.owl and built with code from https://github.com/geneontology/neo Note that its size (>3gb at last count) makes it challenging to work with in the context of standard semantic web tools.  
  - The 1.0.0 pathways2go framework is primarily oriented towards the conversion of pathways from the Reactome knowledge base (https://reactome.org).  The Reactome entity representations include knowledge that NEO does not including: the locations of the physical entity, modifications to proteins, and a large number of unique complexes.  To accomodate these entities with GO-CAM models, pathways2go produces a new ontology called 'reacto.owl' that captures all of this information and needs to be imported for Reactome-generated GO-CAMs to be terminologically (Tbox) complete.  It is generated automatically and can be accessed at http://purl.obolibrary.org/obo/go/extensions/reacto.owl Note that reacto contains references to chebi, GO, PRO, and MOD from the OBO collection.  For complete reasoning, these could be imported but are left out of the build because of size issues.  
- In 1.1.0 the pathways2go framework was extended to support conversion of biopax pathways from https://yeastgenome.org .  If the parameter -e YeastCyc is added to the command line execution, the conversion will not use the REACTO entity ontology pattern described above.  Instead, it will make direct reference to OWL Classes in the go-lego and neo ontologies and, where these are missing, use appropriate upper level classes as defaults.  In addition, this release introduces the -sssom parameter.  If a sssom (https://github.com/OBOFoundry/SSSOM/blob/master/SSSOM.md) mapping file is provided, the converter will use the mapping to add class assignments where they are missing from the biopax file.  (It uses the best match with confidence above 0.5).  

## Running
- Build using Maven install, or download a release jar file from https://github.com/geneontology/pathways2GO/releases 
- the biopax2go.jar executable has 2 purposes.  It can generate an OWL ontology containing the physical entities in a biopax file (e.g. reacto.owl) and it can convert a biopax file into a set of GO-CAMs corresponding to the pathways in the biopax.  
- For example, to generate a physical entity ontology called reacto.owl from a biopax file: 
  - java -jar biopax2go.jar -b some_biopax.owl -reacto reacto -lego go-lego.owl -chebi chebi.owl
  - Note that it needs a local copy of go-lego.owl and chebi.owl (http://purl.obolibrary.org/obo/chebi.owl).  
- To generate GO-CAMS from a biopax file, minimally:
  - java -jar biopax2go.jar -b some_biopax.owl -o ./output_dir/ -lego go-lego.owl -e REACTO
- To generate GO-CAMS from a YeastCyc biopax file, with a provided sssom mapping file 
  - java -jar biopax2go.jar -b some_biopax.owl -o ./output_dir/ -lego go-lego.owl -e YeastCyc -sssom ./yeastpathway.sssom.tsv

