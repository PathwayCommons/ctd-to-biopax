package com.google.gsoc14.ctd2biopax.util.model;

public enum GeneForm {
    THREE_UTR("3' UTR"),
    FIVE_UTR("3' UTR"),
    MRNA("mRNA"),
    PROTEIN("protein"),
    GENE("gene"),
    PROMOTER("promoter"),
    MUTANT_FORM("mutant form"),
    ENHANCER("enhancer"),
    POLYMORPHISM("polymorphism"),
    EXON("exon"),
    SNP("SNP"),
    INTRON("intron"),
    MODIFIED_FORM("modified form"),
    ALTERNATIVE_FORM("alternative form"),
    POLYA_TAIL("polyA tail");

    private final String description;

    GeneForm(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
