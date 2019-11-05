# see https://www.w3.org/2018/09/rdf-data-viz/
# consider converting to obographviz

%.dot: %.ttl config.json
	python3 ./ontology-visualization/ontology_viz.py -C config.json -o $@ $<

%.png: %.dot
	dot -Tpng -o $@ $<
