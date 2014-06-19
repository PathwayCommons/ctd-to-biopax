package com.google.gsoc14.ctd2biopax.util.model;

public enum AxnCode {

    ABU("abundance", "The abundance of a chemical (if chemical synthesis is not known).", null),
    ACT("activity", "An elemental function of a molecule.", null),
    B("binding", "A molecular interaction.", null),
    W("cotreatment", "Involving the use of two or more chemicals simultaneously.", null),
    EXP("expression", "The expression of a gene product.", null),
    FOL("folding", "The bending and positioning of a molecule to achieve conformational integrity.", null),
    LOC("localization", "Part of the cell where a molecule resides.", null),
    MET("metabolic processing", "The biochemical alteration of a molecule's structure (does not include changes in expression, stability, folding, localization, splicing, or transport).", null),
    ACE("acetylation", "The addition of an acetyl group.", MET),
    ACY("acylation", "The addition of an acyl group.", MET),
    ALK("alkylation", "The addition of an alkyl group.", MET),
    AMI("amination", "The addition of an amine group.", MET),
    CAR("carbamoylation", "The addition of a carbamoyl group.", MET),
    COX("carboxylation", "The addition of a carboxyl group.", MET),
    CSY("chemical synthesis", "A biochemical event resulting in a new chemical product.", MET),
    DEG("degradation", "Catabolism or breakdown.", MET),
    CLV("cleavage", "The processing or splitting of a molecule, not necessarily leading to the destruction of the molecule.", DEG),
    HYD("hydrolysis", "The splitting of a molecule via the specific use of water.", CLV),
    ETH("ethylation", "The addition of an ethyl group.", MET),
    GLT("glutathionylation", "The addition of a glutathione group.", MET),
    GYC("glycation", "The non-enzymatic addition of a sugar.", MET),
    GLY("glycosylation", "The addition of a sugar group.", MET),
    GLC("glucuronidation", "The addition of a sugar group to form a glucuronide, typically part of an inactivating or detoxifying reaction.", GLY),
    NGL("N-linked glycosylation", "The addition of a sugar group to an amide nitrogen.", GLY),
    OGL("O-linked glycosylation", "The addition of a sugar group to a hydroxyl group.", GLY),
    HDX("hydroxylation", "The addition of a hydroxy group.", MET),
    LIP("lipidation", "The addition of a lipid group.", MET),
    FAR("farnesylation", "The addition of a farnesyl group.", LIP),
    GER("geranoylation", "The addition of a geranoyl group.", LIP),
    MYR("myristoylation", "The addition of a myristoyl group.", LIP),
    PAL("palmitoylation", "The addition of a palmitoyl group.", LIP),
    PRE("prenylation", "The addition of a prenyl group.", LIP),
    MYL("methylation", "The addition of a methyl group.", MET),
    NIT("nitrosation", "The addition of a nitroso or nitrosyl group.", MET),
    NUC("nucleotidylation", "The addition of a nucleotidyl group.", MET),
    OXD("oxidation", "The loss of electrons.", MET),
    PHO("phosphorylation", "The addition of a phosphate group.", MET),
    RED("reduction", "The gain of electrons.", MET),
    RIB("ribosylation", "The addition of a ribosyl group.", MET),
    ARB("ADP-ribosylation", "The addition of a ADP-ribosyl group.", RIB),
    SUL("sulfation", "The addition of a sulfate group.", MET),
    SUM("sumoylation", "The addition of a SUMO group.", MET),
    UBQ("ubiquitination", "The addition of an ubiquitin group.", MET),
    MUT("mutagenesis", "The genetic alteration of a gene product.", null),
    RXN("reaction", "Any general biochemical or molecular event.", null),
    REC("response to substance", "Resistance or sensitivity to a substance.", null),
    SPL("splicing", "The removal of introns to generate mRNA.", null),
    STA("stability", "Overall molecular integrity.", null),
    TRT("transport", "The movement of a molecule into or out of a cell.", null),
    SEC("secretion", "The movement of a molecule out of a cell (by less specific means than export).", TRT),
    EXT("export", "The movement of a molecule out of a cell (by more specific means than secretion).", SEC),
    UPT("uptake", "The movement of a molecule into a cell (by less specific means than import).", TRT),
    IMT("import", "The movement of a molecule into a cell (by more specific means than uptake).", UPT)
    ;

    private String typeName;
    private String description;
    private AxnCode parentAxnCode;

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AxnCode getParentAxnCode() {
        return parentAxnCode;
    }

    public void setParentAxnCode(AxnCode parentAxnCode) {
        this.parentAxnCode = parentAxnCode;
    }

    AxnCode(String typeName, String description, AxnCode parentAxnCode) {
        this.typeName = typeName;
        this.description = description;
        this.parentAxnCode = parentAxnCode;
    }

    public boolean hasParent() {
        return getParentAxnCode() != null;
    }
}
