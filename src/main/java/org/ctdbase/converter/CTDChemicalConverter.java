package org.ctdbase.converter;

import au.com.bytecode.opencsv.CSVReader;
import org.ctdbase.util.CtdUtil;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.biopax.paxtools.model.level3.SmallMoleculeReference;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class CTDChemicalConverter extends Converter {
    private static Logger log = LoggerFactory.getLogger(CTDChemicalConverter.class);
    private static final String INTRA_FIELD_SEPARATOR = "\\|";

    @Override
    public Model convert(InputStream inputStream) throws IOException {
        CSVReader reader = new CSVReader(new InputStreamReader(inputStream));
        String[] nextLine;

        Model model = createNewModel();
        while((nextLine = reader.readNext()) != null) {
            // Skip commented lines
            if(nextLine[0].startsWith("#")) { continue; }

            if(nextLine.length < 9) {
                log.warn(nextLine[0] + "' does not have enough columns. Skipping.");
                continue;
            }
            /*
                0 - Chemical Name
                1 - ChemicalID (MESH:*)
                2 - CasRN
                3 - Definition
                4 - ParentIDs (MESH:*)
                5 - TreeNumber
                6 - ParentTreeNumber
                7 - Synonyms (sep w/ |)
                8 - DrugBank IDs
             */

            String chemName = nextLine[0];
            String chemicalId = nextLine[1];
            String casRN = nextLine[2];
            String definition = nextLine[3];
            String[] parentIDs = nextLine[4].split(INTRA_FIELD_SEPARATOR);
            String[] synonyms = nextLine[7].split(INTRA_FIELD_SEPARATOR);
            String[] dbIds = nextLine[8].split(INTRA_FIELD_SEPARATOR);

            String rdfId = CtdUtil.sanitizeId("ref_chemical_" + chemicalId.toLowerCase());

            SmallMoleculeReference smallMoleculeReference = (SmallMoleculeReference) model.getByID(absoluteUri(rdfId));
            if(smallMoleculeReference != null) {
                log.warn("We already added chemical " + chemicalId + ". Skipping it.");
                continue;
            }
            smallMoleculeReference = create(SmallMoleculeReference.class, rdfId);

            smallMoleculeReference.setDisplayName(chemName);
            smallMoleculeReference.setStandardName(chemName);
            smallMoleculeReference.addName(chemName);
            for (String synonym : synonyms) {
                smallMoleculeReference.addName(synonym);
            }

            smallMoleculeReference.addComment(definition);

            String[] tokens = chemicalId.split(":"); //length=2 always
            smallMoleculeReference.addXref(createXref(model, UnificationXref.class, tokens[0], tokens[1]));

            for (String dbId : dbIds) {
                if(dbId.isEmpty()) { continue; }
                smallMoleculeReference.addXref(createXref(model, RelationshipXref.class, "DrugBank", dbId));
            }

            for (String parentID : parentIDs) {
                if(parentID.isEmpty()) { continue; }
                tokens = parentID.split(":");
                smallMoleculeReference.addXref(createXref(model, RelationshipXref.class, "MeSH 2013", tokens[1]));
            }

            if(casRN != null && !casRN.isEmpty()) {
                smallMoleculeReference.addXref(createXref(model, RelationshipXref.class, "CAS", casRN));
            }

            model.add(smallMoleculeReference);
        }

        reader.close();

        log.info("Chemical conversion is complete. A total of "
                + model.getObjects(SmallMoleculeReference.class).size()
                + " chemicals were converted.");

        return model;
    }

}
