package org.ctdbase.converter;

import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.BioSource;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.Xref;
import org.ctdbase.util.CtdUtil;

import java.io.IOException;
import java.io.InputStream;

public abstract class Converter {

    private String xmlBase = Converter.sharedXMLBase;
    public static String sharedXMLBase = "ctdbase:";

    public Model createNewModel() {
        Model model = BioPAXLevel.L3.getDefaultFactory().createModel();
        model.setXmlBase(getXmlBase());
        return model;
    }

    public <T extends BioPAXElement> T create(Class<T> aClass, String rdfId) {
        return BioPAXLevel.L3.getDefaultFactory().create(aClass, absoluteUri(rdfId));
    }

    public String getXmlBase() {
        return xmlBase;
    }

    public void setXmlBase(String sharedXMLBase) {
        xmlBase = sharedXMLBase;
    }

    protected String absoluteUri(String rdfId) {
        return getXmlBase() + rdfId;
    }

    // a public abstract method to be implemented:
    public abstract Model convert(InputStream inputStream) throws IOException;

    protected <T extends Xref>  Xref createXref(Model model, Class<T> xrefClass, String db, String id) {
        String pref = switch(xrefClass.getSimpleName()) {
            case "UnificationXref" -> "ux_";
            case "RelationshipXref" -> "rx";
            case "PublicationXref" -> "px";
            default -> "x";
        };
        String rdfId = CtdUtil.sanitizeId(pref + "_" + db + "_" + id );
        T xref = (T) model.getByID(absoluteUri(rdfId));
        if(xref == null) {
            xref = create(xrefClass, rdfId);
            xref.setDb(db);
            xref.setId(id);
            model.add(xref);
        }
        return xref;
    }

    //
    protected BioSource createBioSource(Model model, String taxonomyId, String name) {
        String uri = "bioregistry.io/ncbitaxon:" + taxonomyId;
        BioSource bioSource = (BioSource) model.getByID(uri);
        if(bioSource == null) {
            UnificationXref x = model.addNew(UnificationXref.class, "ncbitaxon:" + taxonomyId);
            bioSource = model.addNew(BioSource.class, uri);
            bioSource.setDisplayName(name);
            bioSource.addXref(x);
        }
        return bioSource;
    }
}
