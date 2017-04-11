package org.ctdbase.util;

import org.ctdbase.util.model.Actor;
import org.ctdbase.util.model.AxnCode;
import org.ctdbase.model.ActorType;
import org.ctdbase.model.AxnType;
import org.ctdbase.model.IxnType;
import org.ctdbase.model.ObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBElement;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class CtdUtil {
    private static Logger log = LoggerFactory.getLogger(CtdUtil.class);
    private static ObjectFactory ctdFactory = new ObjectFactory();

    public static String extractName(IxnType ixn) {
        return extractName(ixn, false);
    }

    public static String extractName(IxnType ixn, boolean skipControl)
    {
        AxnCode axnCode = extractAxnCode(ixn);

        String actionStr = (skipControl)
            ? axnCode.getTypeName() + " of"
            : ( ixn.getAxn().isEmpty() ? "n/a" : ixn.getAxn().iterator().next().getValue() );
        //- only the first axn is used here...

        String completeName = "";
        switch (axnCode) {
            case B:
            case W:
                Iterator<ActorType> iterator = ixn.getActor().iterator();
                assert iterator.hasNext();
                String fName = extractName(iterator.next());
                completeName = ((skipControl)?"":fName) + " " + actionStr;
                while(iterator.hasNext()) {
                    completeName += " " + extractName(iterator.next()) + ",";
                }
                completeName = completeName.substring(0, completeName.length()-1);
                break;
            default:
                if(ixn.getActor().size() > 0) {
                    fName = CtdUtil.extractName(ixn.getActor().get(0));
                    String sName = CtdUtil.extractName(ixn.getActor().get(1));
                    completeName = ((skipControl)? "" : fName + " ") + actionStr + " " + sName;
                } else {
                    completeName = "n/a";
                }
                break;
        }

        return completeName;
    }

    public static String extractName(ActorType actor)
    {
        List<Serializable> serializableList = actor.getContent();

        String formStr = actor.getForm();
        if(formStr != null) {
            formStr = " " + formStr;
        } else {
            formStr = "";
        }

        String name = "";
        if(serializableList.isEmpty()) {
            name = actor.getId();
        } else if(serializableList.size() == 1)
            name = serializableList.iterator().next().toString() + formStr;
        else
            name = "[" + extractName(convertActorToIxn(actor)) + "]";

        return name;
    }

    public static AxnCode extractAxnCode(IxnType ixn) {
        List<AxnType> axns = ixn.getAxn();
        if(axns.isEmpty()) {
            return AxnCode.RXN;
        } else {
            if(axns.size() > 1) {
                log.warn(String.format("IXN #%d has %d axn elements",ixn.getId(),axns.size()));
            }
            //TODO: shall we consider converting not only for the first axn code?
            AxnCode axnCode = AxnCode.valueOf(axns.get(0).getCode().toUpperCase());
            return axnCode;
        }
    }

    public static IxnType convertActorToIxn(ActorType actor)
    {
        if(CtdUtil.extractActor(actor) != Actor.IXN)
            throw new IllegalArgumentException("Actor is not IXN type; id: " + actor.getId());

        IxnType ixnType = ctdFactory.createIxnType();

        for (Serializable item : actor.getContent())
        {
            if(!(item instanceof JAXBElement))
                continue;

            Object value = ((JAXBElement) item).getValue();
            if(value instanceof ActorType) {
                ixnType.getActor().add((ActorType) value);
            } else if(value instanceof AxnType) {
                ixnType.getAxn().add((AxnType) value);
            } else {
                log.error("Nested item with unexpected class: " + value.getClass().getSimpleName());
            }
        }

        ixnType.setId(Long.parseLong(actor.getId()));

        return ixnType;
    }


    public static Actor extractActor(ActorType actor) {
        return Actor.valueOf(actor.getType().toUpperCase());
    }

    public static String createProcessId(IxnType ixnType, ActorType actor) {
//        return sanitizeId("process_" + extractAxnCode(ixnType) + "_" + extractName(actor));
        return sanitizeId("process_" + actor.getId());
    }

    public static String locationToId(String location) {
        return "location_" + sanitizeId(location);
    }

    public static String sanitizeId(String str) {
            return str.replaceAll("[^-\\w]", "_");  //removes '+',':', spaces, etc.
    }

    public static String sanitizeGeneForm(String form) {
        return form
                .replaceAll(" ", "_")
                .replaceAll("'", "")
                .replaceAll("3", "THREE")
                .replaceAll("5", "FIVE")
                ;
    }
}
