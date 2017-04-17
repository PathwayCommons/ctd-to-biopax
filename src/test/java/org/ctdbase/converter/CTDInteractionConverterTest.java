package org.ctdbase.converter;

import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.trove.TProvider;
import org.biopax.paxtools.util.BPCollections;
import org.ctdbase.util.CtdUtil;
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


        TemplateReactionRegulation trr = (TemplateReactionRegulation) m.getByID(m.getXmlBase()+"EXP_4963086");
        assertNotNull(trr);
        assertEquals("PD 0325901 results in decreased expression of PEG3 mRNA",
                trr.getName().iterator().next());
        TemplateReaction tr = (TemplateReaction) trr.getControlled();
        assertNotNull(tr);
        assertTrue(tr.getName().contains("expression of PEG3 mRNA"));
        assertFalse(tr.getProduct().isEmpty());

        Control ctrl = (Control) m.getByID(m.getXmlBase()+"STA_4695456");
        assertNotNull(ctrl);
        assertEquals("INHIBITION", ctrl.getControlType().toString());
        assertTrue(ctrl.getName().contains("sodium arsenite results in increased stability of MAP3K8 mRNA"));
        Degradation deg = (Degradation) ctrl.getControlled().iterator().next();
        assertNotNull(deg);
        assertTrue(deg.getName().contains("stability of MAP3K8 mRNA"));

        Modulation modulation = (Modulation) m.getByID(m.getXmlBase()+"ACT_4463122");
        assertNotNull(modulation);
        assertTrue(modulation.getControlled().iterator().next() instanceof Catalysis);
        assertTrue(modulation.getController().iterator().next() instanceof SmallMolecule);


    }

}