package org.ctdbase.converter;

import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXFactory;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;

import java.io.IOException;
import java.io.InputStream;

public abstract class Converter {

    private final BioPAXFactory bioPAXFactory = BioPAXLevel.L3.getDefaultFactory();
    private String XMLBase = Converter.sharedXMLBase;
    public static String sharedXMLBase = "http://www.ctdbase.org/#";

    public Model createNewModel() {
        Model model = bioPAXFactory.createModel();
        model.setXmlBase(getXMLBase());
        return model;
    }

    public <T extends BioPAXElement> T create(Class<T> aClass, String partialId) {
        return bioPAXFactory.create(aClass, absoluteUri(partialId));
    }

    public String getXMLBase() {
        return XMLBase;
    }

    public void setXMLBase(String sharedXMLBase) {
        XMLBase = sharedXMLBase;
    }

    protected String absoluteUri(String partialId) {
        return getXMLBase() + partialId;
    }

    // a public abstract method to be implemented:
    public abstract Model convert(InputStream inputStream) throws IOException;
}
