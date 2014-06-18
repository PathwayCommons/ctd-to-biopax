package com.google.gsoc14.ctd2biopax.converter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.Model;
import org.ctdbase.model.IxnSetType;
import org.ctdbase.model.IxnType;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;

public class CTDInteractionConverter extends Converter {
    private static Log log = LogFactory.getLog(CTDInteractionConverter.class);

    @Override
    public Model convert(InputStream inputStream) {
        IxnSetType interactions;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance("org.ctdbase.model");
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            interactions = (IxnSetType) ((JAXBElement) unmarshaller.unmarshal(inputStream)).getValue();

            log.info("There are " + interactions.getIxn().size() + " in the model.");

            Model model = createNewModel();
            return model;

        } catch (JAXBException e) {
            log.error("Could not initialize the JAXB Reader (" + e.toString() + ").");
            return null;
        }
    }
}
