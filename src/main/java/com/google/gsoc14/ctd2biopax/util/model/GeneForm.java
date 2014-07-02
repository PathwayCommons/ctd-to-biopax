package com.google.gsoc14.ctd2biopax.util.model;

import org.biopax.paxtools.model.level3.*;

public enum GeneForm {
    THREE_UTR("3' UTR", RnaRegionReference.class, RnaRegion.class),
    FIVE_UTR("5' UTR", RnaRegionReference.class, RnaRegion.class),
    MRNA("mRNA", RnaReference.class, Rna.class),
    PROTEIN("protein", ProteinReference.class, Protein.class),
    GENE("gene", RnaReference.class, Rna.class),
    PROMOTER("promoter", DnaRegionReference.class, DnaRegion.class),
    MUTANT_FORM("mutant form", RnaReference.class, Rna.class),
    ENHANCER("enhancer", DnaRegionReference.class, DnaRegion.class),
    POLYMORPHISM("polymorphism", DnaReference.class, Dna.class),
    EXON("exon", RnaRegionReference.class, RnaRegion.class),
    SNP("SNP", DnaReference.class, Dna.class),
    INTRON("intron", RnaRegionReference.class, RnaRegion.class),
    MODIFIED_FORM("modified form", ProteinReference.class, Protein.class),
    ALTERNATIVE_FORM("alternative form", ProteinReference.class, Protein.class),
    POLYA_TAIL("polyA tail", RnaRegionReference.class, RnaRegion.class);

    private final String description;
    private final Class<? extends EntityReference> referenceClass;
    private final Class<? extends SimplePhysicalEntity> entityClass;

    GeneForm(String description,
            Class<? extends EntityReference> referenceClass,
            Class<? extends SimplePhysicalEntity> entityClass)
    {

        this.description = description;
        this.referenceClass = referenceClass;
        this.entityClass = entityClass;
    }

    public String getDescription() {
        return description;
    }

    public Class<? extends EntityReference> getReferenceClass() {
        return referenceClass;
    }

    public Class<? extends SimplePhysicalEntity> getEntityClass() {
        return entityClass;
    }
}
