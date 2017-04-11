package org.ctdbase.converter;

import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.trove.TProvider;
import org.biopax.paxtools.util.BPCollections;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by igor on 04/04/17.
 */
public class CTDInteractionConverterTest {

    @Test
    public void convert() throws Exception {
        // Memory efficiency fix for huge BioPAX models
        BPCollections.I.setProvider(new TProvider());

        CTDInteractionConverter converter = new CTDInteractionConverter("9606");
        Model m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));

        //TODO: replace printing to the stout with good assertions

        (new SimpleIOHandler()).convertToOWL(m,System.out);
    }

}