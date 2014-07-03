package com.google.gsoc14.ctd2biopax.util;

import com.google.gsoc14.ctd2biopax.util.model.ActorTypeType;
import com.google.gsoc14.ctd2biopax.util.model.AxnCode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ctdbase.model.ActorType;
import org.ctdbase.model.AxnType;
import org.ctdbase.model.IxnType;
import org.ctdbase.model.ObjectFactory;

import javax.xml.bind.JAXBElement;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class CTDUtil {
    private static Log log = LogFactory.getLog(CTDUtil.class);
    private static ObjectFactory ctdFactory = new ObjectFactory();

    public static String extractName(IxnType ixn) {
        String actionStr = ixn.getAxn().iterator().next().getValue();

        String completeName = "";
        switch (extractAxnCode(ixn)) {
            case B:
            case W:
                Iterator<ActorType> iterator = ixn.getActor().iterator();
                assert iterator.hasNext();
                completeName = extractName(iterator.next()) + " " + actionStr + " ";
                while(iterator.hasNext()) {
                    completeName += extractName(iterator.next()) + ", ";
                }
                completeName = completeName.substring(0, completeName.length()-2);
                break;
            default:
                String fName = CTDUtil.extractName(ixn.getActor().get(0));
                String sName = CTDUtil.extractName(ixn.getActor().get(1));
                completeName = fName + " " + actionStr + " " + sName;
        }

        return completeName;
    }

    public static String extractName(ActorType actor) {
        List<Serializable> serializableList = actor.getContent();
        assert !serializableList.isEmpty();

        String formStr = actor.getForm();
        if(formStr != null) {
            formStr = " " + formStr;
        } else {
            formStr = "";
        }

        return serializableList.size() == 1
                ? serializableList.iterator().next().toString() + formStr
                : "[" + extractName(convertActorToIxn(actor)) + "]";

    }
    public static AxnType extractAxn(IxnType ixn) {
        List<AxnType> actions = ixn.getAxn();
        assert actions.size() > 0;
        return actions.iterator().next();
    }

    public static AxnCode extractAxnCode(AxnType axnType) {
        return AxnCode.valueOf(axnType.getCode().toUpperCase());
    }

    public static AxnCode extractAxnCode(IxnType ixn) {
        return extractAxnCode(extractAxn(ixn));
    }

    public static IxnType convertActorToIxn(ActorType actor) {
        IxnType ixnType = ctdFactory.createIxnType();

        for (Serializable item : actor.getContent()) {
            if(!(item instanceof JAXBElement)) { continue; }

            Object value = ((JAXBElement) item).getValue();
            if(value instanceof ActorType) {
                ixnType.getActor().add((ActorType) value);
            } else if(value instanceof AxnType) {
                ixnType.getAxn().add((AxnType) value);
            } else {
                log.error("Nested item with unexpected class: " + value.getClass().getSimpleName());
            }
        }

        ixnType.setId(actor.getId().hashCode());
        return ixnType;
    }


    public static ActorTypeType extractActorTypeType(ActorType actor) {
        return ActorTypeType.valueOf(actor.getType().toUpperCase());
    }

    public static String createProcessId(IxnType ixnType, ActorType actor) {
        return sanitizeId("process_" + extractAxnCode(extractAxn(ixnType)) + "_" + extractName(actor));
    }

    public static String locationToId(String location) {
        return "location_" + sanitizeId(location);
    }

    public static String sanitizeId(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("Problem encoding the ID: " + e.getMessage());
            return str;
        }
    }

    public static String sanitizeGeneForm(String form) {
        return form
                .replaceAll(" ", "_")
                .replaceAll("'", "")
                .replaceAll("3", "THREE")
                .replaceAll("5", "FIVE")
                ;
    }

    public static String createRefRDFId(String form, String actorId) {
        return CTDUtil.sanitizeId("ref_" + form + "_" + actorId);
    }
}
