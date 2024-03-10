package org.ctdbase.converter;

import au.com.bytecode.opencsv.CSVReader;
import org.ctdbase.util.CtdUtil;
import org.ctdbase.util.model.GeneForm;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CTDGeneConverter extends Converter {
    private static Logger log = LoggerFactory.getLogger(CTDGeneConverter.class);
    private static final String INTRA_FIELD_SEPARATOR = "\\|";

    @Override
    public Model convert(InputStream inputStream) throws IOException {
        CSVReader reader = new CSVReader(new InputStreamReader(inputStream));
        String[] nextLine;
        Model model = createNewModel();

        while((nextLine = reader.readNext()) != null) {
            // Skip commented lines
            if (nextLine[0].startsWith("#")) {
                continue;
            }
            if(nextLine.length < 8) {
                log.warn(nextLine[0] + "' does not have enough columns to it. Skipping.");
                continue;
            }
            // create an ER of different type for each gene form
            for (GeneForm geneForm : GeneForm.values()) {
                generateReference(model, geneForm, nextLine);
            }
        }
        reader.close();

        log.info("Done with the gene conversion. "
                + "Added "
                + model.getObjects(EntityReference.class).size()
                + " entity references.");

        return model;
    }

    private EntityReference generateReference(
            Model model,
            GeneForm geneForm,
            String[] tokens)
    {
        assert tokens.length >= 8;
        /*
            0 - GeneSymbol
            1 - GeneName
            2 - GeneID
            3 - AltGeneIDs
            4 - Synonyms
            5 - BioGRIDIds
            6 - PharmGKBs
            7 - UniProtIds
         */

        String geneSymbol = tokens[0];
        String geneName = tokens[1];
        String geneID = tokens[2];
//        String[] altGeneIds = tokens[3].split(INTRA_FIELD_SEPARATOR);
//        String[] synonyms = tokens[4].split(INTRA_FIELD_SEPARATOR);
//        String[] biogridIds = tokens[5].split(INTRA_FIELD_SEPARATOR);
//        String[] pharmGKBIds = tokens[6].split(INTRA_FIELD_SEPARATOR);
//        String[] uniprotIds = tokens[7].split(INTRA_FIELD_SEPARATOR); //often not relevant organism...

        String rdfId = CtdUtil.sanitizeId("ref_" +  geneForm.toString().toLowerCase()
                + "_gene_" + geneID.toLowerCase());

        EntityReference entityReference = (EntityReference) model.getByID(absoluteUri(rdfId));
        if(entityReference != null) {
            log.warn("Already had the gene " + geneID + ". Skipping it.");
            return null;
        }

        entityReference = create(geneForm.getReferenceClass(), rdfId);
        entityReference.addXref(createXref(model, RelationshipXref.class, "hgnc.symbol", geneSymbol));
        entityReference.addXref(createXref(model, RelationshipXref.class, "ncbigene", geneID));
        entityReference.setStandardName(geneSymbol);
        entityReference.setDisplayName(geneSymbol);
//        for (String synonym : synonyms) { //too many, can be found in other/external resources after all
//            if(!synonym.isEmpty()) {
//                entityReference.addName(synonym);
//            }
//        }

        if(!geneName.isEmpty()) {
            entityReference.addComment(geneName);
        }

        // Let's skip other NCBI gene references, for they inflate the model too much...
//        addXrefsFromArray(model, entityReference, RelationshipXref.class, "NCBI Gene", altGeneIds);
//        addXrefsFromArray(model, entityReference, RelationshipXref.class, "BioGRID", biogridIds);
//        addXrefsFromArray(model, entityReference, RelationshipXref.class, "PharmGKB Gene", pharmGKBIds);
//        addXrefsFromArray(model, entityReference, RelationshipXref.class, "UniProt", uniprotIds);

        model.add(entityReference);
        return entityReference;
    }

    private void addXrefsFromArray(Model model, EntityReference entityReference,
                                   Class<? extends Xref> xrefClass, String db, String[] ids) {
        for (String id : ids) {
            if(!id.isEmpty()) {
                entityReference.addXref(createXref(model, xrefClass, db, id));
            }
        }
    }
}
