package org.ctdbase.converter;

import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;

import java.io.IOException;
import java.io.InputStream;

public abstract class Converter {

    private String XMLBase = Converter.sharedXMLBase;
    public static String sharedXMLBase = "http://www.ctdbase.org/#";

    public Model createNewModel() {
        Model model = BioPAXLevel.L3.getDefaultFactory().createModel();
        model.setXmlBase(getXMLBase());
        return model;
    }

    public <T extends BioPAXElement> T create(Class<T> aClass, String rdfId) {
        return BioPAXLevel.L3.getDefaultFactory().create(aClass, absoluteUri(rdfId));
    }

    public String getXMLBase() {
        return XMLBase;
    }

    public void setXMLBase(String sharedXMLBase) {
        XMLBase = sharedXMLBase;
    }

    protected String absoluteUri(String rdfId) {
        return (rdfId.startsWith("http:")) ? rdfId : getXMLBase() + rdfId;
    }

    // a public abstract method to be implemented:
    public abstract Model convert(InputStream inputStream) throws IOException;
}
