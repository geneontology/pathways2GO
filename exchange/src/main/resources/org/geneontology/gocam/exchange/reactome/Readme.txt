reactome_graph_active_units.csv

Can be generated from the neo4j version of the Reactome database with the query:

MATCH (reaction:Reaction {speciesName:"Homo sapiens"})-[:catalystActivity]->(control_event:CatalystActivity)-[:activeUnit]->(active_unit:PhysicalEntity)
RETURN reaction.stId, control_event.dbId, active_unit.stId

The file tracks relations between control events and the active units of the controller for those events.  
Often a complex is associated with the control event, and one sub unit (e.g. a protein) of the complex is identified as the key player.  
This information is not tracked in the BioPAX file. 