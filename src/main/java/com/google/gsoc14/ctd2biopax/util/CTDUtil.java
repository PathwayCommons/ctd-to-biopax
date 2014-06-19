package com.google.gsoc14.ctd2biopax.util;

import com.google.gsoc14.ctd2biopax.util.model.GeneForm;
import org.ctdbase.model.ActorType;

import java.io.Serializable;
import java.util.List;

public class CTDUtil {
    public static String extractName(ActorType actor) {
        // TODO: Better name extraction
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
                : "complex interaction";

    }
}
