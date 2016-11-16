# ctd-to-biopax
Originated from https://bitbucket.org/armish/gsoc14 and will continue here (ToDo).

## Comparative Toxicogenomics Database (CTD) to BioPAX Level3 data converter

Unlike many other drug-target databases, this data resource has a controlled vocabulary that can be mapped to BioPAX, for example: 'nutlin 3 results in increased expression of BAX'. Therefore implementation of a converter first requires a manual mapping from CTD terms to BioPAX ontology. Once the mapping is done, then the actual conversion requires parsing and integrating multiple CSV files that are distributed by the provider.

### Data source
- **Home page**: [http://ctdbase.org/](http://ctdbase.org/)
- **Type**: Drug activity
- **Format**: XML/CSV
- **License**: Free for academic use

### Implementation details
The converter is structured as a maven project, where the only major dependencies are *Paxtools* and *JAXB* libraries.
The project can be compiled into an executable JAR file that can be used as a command line utility (described in the next section).

For the conversion, the utility uses three different input files:

1. [Chemical-Gene Interactions](http://ctdbase.org/downloads/#cg) (XML)
2. [Gene Vocabulary](http://ctdbase.org/downloads/#allgenes) (CSV)
3. [Chemical Vocabulary](http://ctdbase.org/downloads/#allchems) (CSV)

all of which can be downloaded from the [CTD Downloads](http://ctdbase.org/downloads/) page.
User can provide any of these files as input and get a BioPAX file as the result of the conversion.
If user provides more than one input, then the converted models are merged and a single BioPAX file is provided as output.

The gene/chemical vocabulary converters produce BioPAX file with only `EntityReference`s in them.
Each entity reference in this converted models includes all the external referneces provided within the vocabulary file.
From the chemical vocabulary, `SmallMoleculeReference`s are produced;
and from the gene vocabulary, various types of references are produced for corresponding CTD gene forms: `ProteinReference`, `DnaReference`, `RnaReference`, `DnaRegionReference` and `RnaRegionReference`.

The interactions file contains all detailed interactions between chemicals and genes, but no background information on the chemical/gene entities.
Therefore it is necessary to convert all these files and merge these models into one in order to get a properly annotated BioPAX model.
The converter exactly does that by making sure that the entity references from the vocabulary files match with the ones produced from the interactions file.
This allows filling in the gaps and annotations of the entities in the final converted model.

The CTD data sets have nested interactions that are captured by their structured XML file and their XML schema: 
[CTD_chem_gene_ixns_structured.xml.gz](http://ctdbase.org/reports/CTD_chem_gene_ixns_structured.xml.gz) and [CTD_chem_gene_ixns_structured.xsd](http://ctdbase.org/reports/CTD_chem_gene_ixns_structured.xsd).
The converter takes advantage of `JAXB` library to handle this structured data set.
The automatically generated Java classes that correspond to this schema can be found under `src/main/java/org/ctdbase/model`.
The simple flow that show how the conversion happens is available as the main executable class: `CtdToBiopaxConverter.java`.

### Usage
Check out (clone) and change the project directory:

	$ cd ctd-to-biopax

build with Maven:

	$ mvn clean package

This will create an executable JAR file `ctd-to-biopax.jar` under the `target/` directory.
Once you have the single JAR file, you can try to run without any command line options to see the help text:

	$ java -jar ctd-to-biopax.jar
	usage: CtdToBiopaxConverter
	 -c,--chemical <arg>      CTD chemical vocabulary (CSV) [optional]
	 -g,--gene <arg>          CTD gene vocabulary (CSV) [optional]
	 -o,--output <arg>        Output (BioPAX file) [required]
	 -r,--remove-tangling     Remove tangling entities for clean-up [optional]
	 -t,--taxonomy <arg>      Taxonomy (e.g. '9606' for human) [optional]
	 -x,--interaction <arg>   structured chemical-gene interaction file (XML)
	                          [optional]

All input files (chemicals/genes/interactions) can be downloaded from the [CTD Downloads page](http://ctdbase.org/downloads/).
If you want to test the converter though, you can download smallish examples for all these files from the downloads page: [goal2_ctd_smallSampleInputFiles-20140702.zip](https://bitbucket.org/armish/gsoc14/downloads/goal2_ctd_smallSampleInputFiles-20140702.zip).
To convert these sample files into a single BioPAX file, run the following command:

	$ java -jar ctd-to-biopax.jar -x ctd_small.xml -c CTD_chemicals_small.csv -g CTD_genes_small.csv -r -o ctd.owl

which will create the `ctd.owl` file for you.
You can find a sample result BioPAX file at: [goal2_ctd_smallSampleConverted-20140703.owl.gz](https://bitbucket.org/armish/gsoc14/downloads/goal2_ctd_smallSampleConverted-20140703.owl.gz).
Once you have the file, you can then visualize this small sample file with [ChiBE](https://code.google.com/p/chibe/), which will list all available pathways in the model first.

![CTD Pathway List](https://bitbucket.org/armish/gsoc14/downloads/goal2_ctd_small_screenshot_pathwaysList.png)

And you can, for example, load the `Homo sapiens` pathway and this is what you will get:

![CTD Homo Sapiens Sample](https://bitbucket.org/armish/gsoc14/downloads/goal2_ctd_small_pathwayView.jpg)

The tool will also print log information to the console.

### Validation results (olde)
Since the fully converted CTD model is huge (> 4 Gb), I only validated the small sample data set, which is representative of the full one: [goal2_ctd_validationResults_20140703.zip](https://bitbucket.org/armish/gsoc14/downloads/goal2_ctd_validationResults_20140703.zip).
In the validation reports, we have a single type of `ERROR` that reports the lack of external references for some of the `EntityReference`s.
These are mostly due to lack of information in the sample chemical/gene vocabularies and are not valid for the full CTD data set -- which has all the necessary background information on all entities.

Original CTD model assumes all entities are forms of `Gene`s, hence provides unification xrefs to the *NCBI Gene* database for all entities.
This creates a problem in the converted BioPAX file, where we add gene xrefs to the protein entities for some of the CTD reactions.
This also causes some of the unification xrefs to be shared across entities (e.g. `DNA` and `Protein`).
The options to get rid of these problems will be discussed with the mentors/curators.

