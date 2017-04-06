package org.ctdbase.converter;

import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Xref;
import org.ctdbase.util.CtdUtil;

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

    protected <T extends Xref>  Xref createXref(Model model, Class<T> xrefClass, String db, String id) {
        String rdfId = CtdUtil.sanitizeId(xrefClass.getSimpleName().toLowerCase() + "_" + db + "_" + id );
        T xref = (T) model.getByID(absoluteUri(rdfId));
        if(xref == null) {
            xref = create(xrefClass, rdfId);
            xref.setDb(db);
            xref.setId(id);
            model.add(xref);
        }
        return xref;
    }
}
