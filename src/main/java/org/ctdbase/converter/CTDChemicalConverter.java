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
import java.util.UUID;

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

            assert nextLine.length == 9;
            /*
                0 - Chemical Name
                1 - ChemicalID (MESH:*)
                2 - CasRN
                3 - Definition
                4 - ParentIDs (MESH:*)
                5- TreeNumber
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

            String rdfId = CtdUtil.createRefRDFId("CHEMICAL", chemicalId);
            SmallMoleculeReference smallMolecule = (SmallMoleculeReference) model.getByID(absoluteUri(rdfId));
            if(smallMolecule != null) {
                log.warn("We already added chemical " + chemicalId + ". Skipping it.");
                continue;
            }
            smallMolecule = create(SmallMoleculeReference.class, rdfId);

            smallMolecule.setDisplayName(chemName);
            smallMolecule.setStandardName(chemName);
            smallMolecule.addName(chemName);
            for (String synonym : synonyms) {
                smallMolecule.addName(synonym);
            }

            smallMolecule.addComment(definition);

            smallMolecule.addXref(createUnificationXrefFromId(model, chemicalId));
            for (String dbId : dbIds) {
                if(dbId.isEmpty()) { continue; }
                smallMolecule.addXref(createDrugBankXref(model, dbId));
            }

            for (String parentID : parentIDs) {
                if(parentID.isEmpty()) { continue; }
                smallMolecule.addXref(createMeshXref(model, parentID));
            }

            if(casRN != null && !casRN.isEmpty()) {
                smallMolecule.addXref(createCASXref(model, casRN));
            }

            model.add(smallMolecule);
        }

        log.info("Chemical conversion is complete. A total of "
                + model.getObjects(SmallMoleculeReference.class).size()
                + " chemicals were converted.");
        return model;
    }

    private RelationshipXref createMeshXref(Model model, String parentID) {
        String[] tokens = parentID.split(":");
        String uri = CtdUtil.sanitizeId("rxref_" + parentID + "_" + UUID.randomUUID());
        RelationshipXref rxref = create(RelationshipXref.class, uri);
        rxref.setDb("MeSH 2013");
        rxref.setId(tokens[1]);
        model.add(rxref);
        return null;
    }

    private RelationshipXref createCASXref(Model model, String casRN) {
        String uri = CtdUtil.sanitizeId("rxref_" + casRN + "_" + UUID.randomUUID());
        RelationshipXref rxref = create(RelationshipXref.class, uri);
        rxref.setDb("CAS");
        rxref.setId(casRN);
        model.add(rxref);
        return null;
    }

    private RelationshipXref createDrugBankXref(Model model, String dbId) {
        String uri = CtdUtil.sanitizeId("rxref_" + dbId + "_" + UUID.randomUUID());
        RelationshipXref rxref = create(RelationshipXref.class, uri);
        rxref.setDb("DrugBank");
        rxref.setId(dbId);
        model.add(rxref);
        return null;
    }

    private UnificationXref createUnificationXrefFromId(Model model, String chemicalId) {
        String[] tokens = chemicalId.split(":");
        String uri = CtdUtil.sanitizeId("rxref_" + chemicalId + "_" + UUID.randomUUID());
        UnificationXref xref = create(UnificationXref.class, uri);
        xref.setDb("MeSH 2013");
        xref.setId(tokens[1]);
        model.add(xref);
        return xref;
    }

}
