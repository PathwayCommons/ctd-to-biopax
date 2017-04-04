package org.ctdbase.converter;

import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by igor on 04/04/17.
 */
public class CTDInteractionConverterTest {

    @Test
    public void convert() throws Exception {
        CTDInteractionConverter converter = new CTDInteractionConverter("9606");
        Model m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));

        (new SimpleIOHandler()).convertToOWL(m,System.out);
    }

}