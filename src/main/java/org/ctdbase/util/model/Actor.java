package org.ctdbase.util.model;

public enum Actor {
    IXN("interaction"),
    GENE("gene"),
    CHEMICAL("chemical");

    private final String description;

    Actor(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
