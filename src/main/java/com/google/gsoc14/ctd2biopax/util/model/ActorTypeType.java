package com.google.gsoc14.ctd2biopax.util.model;

public enum ActorTypeType {
    IXN("interaction"),
    GENE("gene"),
    CHEMICAL("chemical");

    private final String description;

    ActorTypeType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
