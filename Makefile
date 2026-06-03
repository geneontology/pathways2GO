# Makefile for Reactome GO-CAM generation pipeline
#
# Usage:
#   make all                  # Full pipeline: download, build, convert, validate
#   make readable-shex-report # Generate human-readable ShEx report
#
# Optional targets (not included in 'all'):
#   make reason               # Load ontology into BG journal and run Arachne reasoning
#   make manuscript           # Run Manuscript causal comparison analysis (requires reason)
#
# Configuration (override on command line or in environment):
#   make all PATHWAYS2GO_PATH=/my/path MINERVA_PATH=/my/minerva

# --- Configuration ---
PATHWAYS2GO_PATH ?= $(shell pwd)
MINERVA_PATH     ?= $(shell pwd)/../minerva
TODAYS_DATE      := $(shell date +"%Y%m%d")
TARGET_PATH      := $(PATHWAYS2GO_PATH)/reactome_gen_$(TODAYS_DATE)
BIOPAX_PATH      := $(TARGET_PATH)/biopax
REACTO_OUT       := $(TARGET_PATH)/reacto-out
REPORTS_PATH     := $(TARGET_PATH)/reports
EXCHANGE_DIR     := $(PATHWAYS2GO_PATH)/exchange
BIOPAX2GO_JAR    := $(EXCHANGE_DIR)/bin/biopax2go.jar
JAVA_HEAP        ?= 24G
MINERVA_CLI_MEMORY ?= 12G
BG_RUNNER_PATH   ?= $(shell pwd)/../blazegraph-runner-1.6
BG_PARALLELISM   ?= 8

# --- Top-level targets ---
.PHONY: all download build convert extract-reports reacto validate \
	readable-shex-report reason manuscript clean

all: download build convert extract-reports reacto validate readable-shex-report

# --- Download BioPAX and ontology ---
$(TARGET_PATH)/biopax.zip:
	mkdir -p $(BIOPAX_PATH) $(REACTO_OUT) $(REPORTS_PATH)
	wget -P $(TARGET_PATH) https://reactome.org/download/current/biopax.zip

$(BIOPAX_PATH)/Homo_sapiens.owl: $(TARGET_PATH)/biopax.zip
	unzip $(TARGET_PATH)/biopax.zip -d $(BIOPAX_PATH)/
	touch $@

$(TARGET_PATH)/go-lego-reacto.owl:
	mkdir -p $(TARGET_PATH)
	wget -P $(TARGET_PATH) http://current.geneontology.org/ontology/extensions/go-lego-reacto.owl

download: $(BIOPAX_PATH)/Homo_sapiens.owl $(TARGET_PATH)/go-lego-reacto.owl

# --- Build biopax2go JAR ---
$(BIOPAX2GO_JAR): $(shell find $(EXCHANGE_DIR)/src -type f -name '*.java' 2>/dev/null)
	cd $(EXCHANGE_DIR) && mvn package

build: $(BIOPAX2GO_JAR)

# --- Convert BioPAX to GO-CAMs ---
$(REPORTS_PATH)/biopax2go.log: $(BIOPAX2GO_JAR) $(BIOPAX_PATH)/Homo_sapiens.owl $(TARGET_PATH)/go-lego-reacto.owl
	mkdir -p $(REACTO_OUT) $(REPORTS_PATH)
	time java -jar -Xmx$(JAVA_HEAP) $(BIOPAX2GO_JAR) \
		-b $(BIOPAX_PATH)/Homo_sapiens.owl \
		-bg $(REACTO_OUT)/blazegraph.jnl \
		-o $(REACTO_OUT)/ \
		-e REACTO \
		-dc GOC:reactome_curators \
		-dp https://reactome.org \
		-lego $(TARGET_PATH)/go-lego-reacto.owl \
		> $(REPORTS_PATH)/biopax2go.log

convert: $(REPORTS_PATH)/biopax2go.log

# --- Extract reports from conversion log ---
$(REPORTS_PATH)/deleted_regulators.tsv: $(REPORTS_PATH)/biopax2go.log
	grep DELETING_NON_SMALL_MOL_REGULATOR $(REPORTS_PATH)/biopax2go.log | cut -f2- > $@

$(REPORTS_PATH)/complex_active_unit_is_specified.tsv: $(REPORTS_PATH)/biopax2go.log
	grep COMPLEX_ACTIVE_UNIT_IS_SPECIFIED $(REPORTS_PATH)/biopax2go.log | cut -f2- | sort | uniq > $@

$(REPORTS_PATH)/complex_has_protein_no_active_unit.tsv: $(REPORTS_PATH)/biopax2go.log
	grep COMPLEX_HAS_PROTEIN_NO_ACTIVE_UNIT $(REPORTS_PATH)/biopax2go.log | cut -f2- | sort | uniq > $@

$(REPORTS_PATH)/complex_reduced_to_single_protein.tsv: $(REPORTS_PATH)/biopax2go.log
	grep COMPLEX_REDUCED_TO_SINGLE_PROTEIN $(REPORTS_PATH)/biopax2go.log | cut -f2- | sort | uniq > $@

$(REPORTS_PATH)/complex_cant_be_reduced_to_protein.tsv: $(REPORTS_PATH)/biopax2go.log
	grep COMPLEX_CANT_BE_REDUCED_TO_PROTEIN $(REPORTS_PATH)/biopax2go.log | cut -f2- | sort | uniq > $@

REPORT_FILES := \
	$(REPORTS_PATH)/deleted_regulators.tsv \
	$(REPORTS_PATH)/complex_active_unit_is_specified.tsv \
	$(REPORTS_PATH)/complex_has_protein_no_active_unit.tsv \
	$(REPORTS_PATH)/complex_reduced_to_single_protein.tsv \
	$(REPORTS_PATH)/complex_cant_be_reduced_to_protein.tsv

extract-reports: $(REPORT_FILES)

# --- Generate reacto.owl and labels ---
$(TARGET_PATH)/reacto.ttl: $(BIOPAX2GO_JAR) $(BIOPAX_PATH)/Homo_sapiens.owl $(TARGET_PATH)/go-lego-reacto.owl
	time java -jar -Xmx$(JAVA_HEAP) $(BIOPAX2GO_JAR) \
		-b $(BIOPAX_PATH)/Homo_sapiens.owl \
		-reacto $(TARGET_PATH)/reacto \
		-chebi chebi.owl \
		-lego $(TARGET_PATH)/go-lego-reacto.owl

$(TARGET_PATH)/reacto_labels.tsv: $(TARGET_PATH)/reacto.ttl
	robot export \
		--input $(TARGET_PATH)/reacto.ttl \
		--header "IRI|LABEL" \
		--format tsv \
		--export $@

reacto: $(TARGET_PATH)/reacto_labels.tsv

# --- ShEx validation via Minerva CLI ---
$(REPORTS_PATH)/explanations.txt: $(REPORTS_PATH)/biopax2go.log
	cd $(MINERVA_PATH) && ./build-cli.sh
	cd $(MINERVA_PATH) && MINERVA_CLI_MEMORY=$(MINERVA_CLI_MEMORY) \
		minerva-cli/bin/minerva-cli.sh \
		--validate-go-cams --shex \
		--shexpath minerva-cli/go-cam-shapes-edited.shex \
		-i $(REACTO_OUT)/blazegraph.jnl \
		-r $(REPORTS_PATH)/ \
		-ontojournal $(TARGET_PATH)/blazegraph-lego.jnl

validate: $(REPORTS_PATH)/explanations.txt

# --- Optional: Blazegraph reasoning + Manuscript analysis ---
# These targets are not part of 'all'. Run manually:
#   make reason      # Load ontology into BG journal and run Arachne reasoning
#   make manuscript  # Run Manuscript causal comparison analysis (requires 'reason' first)

REACTO_MODELS_JNL := $(REACTO_OUT)/reacto-models-bg.jnl

$(REACTO_MODELS_JNL): $(TARGET_PATH)/go-lego-reacto.owl
	cp $(REACTO_OUT)/blazegraph.jnl $(REACTO_MODELS_JNL)
	$(BG_RUNNER_PATH)/bin/blazegraph-runner load \
		--journal=$(REACTO_MODELS_JNL) \
		--use-ontology-graph=true \
		--informat=rdfxml \
		$(TARGET_PATH)/go-lego-reacto.owl
	$(BG_RUNNER_PATH)/bin/blazegraph-runner reason \
		--journal=$(REACTO_MODELS_JNL) \
		--ontology="http://purl.obolibrary.org/obo/go/extensions/go-lego-reacto.owl" \
		--source-graphs-query=graphs.rq \
		--target-graph="http://model.geneontology.org/inferences" \
		--merge-sources=false \
		--parallelism=$(BG_PARALLELISM) \
		--reasoner=arachne

reason: $(REACTO_MODELS_JNL)

$(REPORTS_PATH)/manuscript.done: $(BIOPAX2GO_JAR) $(REACTO_MODELS_JNL) $(BIOPAX_PATH)/Homo_sapiens.owl
	java -Xmx$(JAVA_HEAP) -cp $(BIOPAX2GO_JAR) org.geneontology.garage.Manuscript \
		-b $(BIOPAX_PATH)/Homo_sapiens.owl \
		-j $(REACTO_MODELS_JNL) \
		-g $(REACTO_OUT)/ \
		-r $(REPORTS_PATH)/
	touch $@

manuscript: $(REPORTS_PATH)/manuscript.done

# --- Readable ShEx report ---
GO_LEGO_LABELS   ?= $(PATHWAYS2GO_PATH)/go-lego-labels.tsv
SHEX_REPORT_SCRIPT := $(PATHWAYS2GO_PATH)/scripts/readable_shex_report.py

$(GO_LEGO_LABELS): $(TARGET_PATH)/go-lego-reacto.owl
	robot export \
		--input $(TARGET_PATH)/go-lego-reacto.owl \
		--header "IRI|LABEL" \
		--entity-select "NAMED" \
		--entity-format "IRI" \
		--export $@

$(REPORTS_PATH)/readable_explanations.md: $(REPORTS_PATH)/explanations.txt $(GO_LEGO_LABELS) $(SHEX_REPORT_SCRIPT)
	python3 $(SHEX_REPORT_SCRIPT) \
		--explanations $(REPORTS_PATH)/explanations.txt \
		--labels $(GO_LEGO_LABELS) \
		--output-dir $(REPORTS_PATH)/

readable-shex-report: $(REPORTS_PATH)/readable_explanations.md

# --- Clean ---
clean:
	rm -rf $(TARGET_PATH)
