package com.google.gsoc14.ctd2biopax.converter;

import au.com.bytecode.opencsv.CSVReader;
import com.google.gsoc14.ctd2biopax.util.CTDUtil;
import com.google.gsoc14.ctd2biopax.util.model.GeneForm;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CTDGeneConverter extends Converter {
    private static Log log = LogFactory.getLog(CTDGeneConverter.class);
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
                log.warn("Description for '" + nextLine[0] + "' does not have enough columns to it. Skipping entity creation");
                continue;
            }


            for (GeneForm geneForm : GeneForm.values()) {
                generateReference(model, geneForm.getReferenceClass(), geneForm, nextLine);
            }
        }

        log.info("Done with the gene conversion. "
                + "Added "
                + model.getObjects(EntityReference.class).size()
                + " entity references.");

        return model;
    }

    private EntityReference generateReference(
            Model model,
            Class<? extends EntityReference> aClass,
            GeneForm geneForm,
            String[] tokens)
    {
        assert tokens.length == 8;
        /*
            0 - GeneSymbol
            1 - GeneName
            2 - GeneID
            3 - AltGeneIDs
            4 - Synonyms
            5- BioGRIDIds
            6 - PharmGKBs
            7 - UniProtIds
         */

        String geneSymbol = tokens[0];
        String geneName = tokens[1];
        String geneID = tokens[2];
        String[] altGeneIds = tokens[3].split(INTRA_FIELD_SEPARATOR);
        String[] synonyms = tokens[4].split(INTRA_FIELD_SEPARATOR);
        String[] biogridIds = tokens[5].split(INTRA_FIELD_SEPARATOR);
        String[] pharmGKBIds = tokens[6].split(INTRA_FIELD_SEPARATOR);
        String[] uniprotIds = tokens[7].split(INTRA_FIELD_SEPARATOR);

        String uri = CTDUtil.createRefRDFId(geneForm.toString(), "GENE:" + geneID);
        EntityReference entityReference = (EntityReference) model.getByID(uri);
        if(entityReference != null) {
            log.warn("Already had the gene " + geneID + ". Skipping it.");
            return null;
        }
        entityReference = create(aClass, uri);

        entityReference.setStandardName(geneSymbol);
        entityReference.setDisplayName(geneSymbol);
        entityReference.addName(geneSymbol);
        for (String synonym : synonyms) {
            if(!synonym.isEmpty()) { entityReference.addName(synonym); }
        }

        if(!geneName.isEmpty()) { entityReference.addComment(geneName); }
        entityReference.addXref(createXref(model, UnificationXref.class, "NCBI Gene", geneID));
        // Let's skip other NCBI gene references, they inflate the model
        //addXrefsFromArray(model, entityReference, RelationshipXref.class, "NCBI Gene", altGeneIds);
        addXrefsFromArray(model, entityReference, RelationshipXref.class, "BioGRID", biogridIds);
        addXrefsFromArray(model, entityReference, RelationshipXref.class, "PharmGKB Gene", pharmGKBIds);
        addXrefsFromArray(model, entityReference, RelationshipXref.class, "UniProt", uniprotIds);

        model.add(entityReference);
        return entityReference;
    }

    private void addXrefsFromArray(Model model, EntityReference entityReference, Class<? extends Xref> xrefClass, String db, String[] ids) {
        for (String id : ids) {
            if(!id.isEmpty()) {
                entityReference.addXref(createXref(model, xrefClass, db, id));
            }
        }
    }

    private Xref createXref(Model model, Class<? extends Xref> xrefClass, String db, String id) {
        String rdfId = CTDUtil.sanitizeId(xrefClass.getSimpleName().toLowerCase() + "_" + db + ":" + id );
        Xref xref = (Xref) model.getByID(rdfId);
        if(xref == null) {
            xref = create(xrefClass, rdfId);
            xref.setDb(db);
            xref.setId(id);
            model.add(xref);
        }

        return xref;
    }
}
