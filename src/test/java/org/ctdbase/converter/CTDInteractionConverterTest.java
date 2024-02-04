package org.ctdbase.converter;

import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by igor on 04/04/17.
 */
public class CTDInteractionConverterTest {

    @Test
    public void convert() {
        CTDInteractionConverter converter = new CTDInteractionConverter(null);
        Model m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));

        TemplateReactionRegulation trr = (TemplateReactionRegulation) m.getByID(m.getXmlBase()+"EXP_4963086");
        assertNotNull(trr);
        assertEquals("PD 0325901 results in decreased expression of PEG3 mRNA",
                trr.getName().iterator().next());
        TemplateReaction tr = (TemplateReaction) trr.getControlled().iterator().next();
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

        ctrl = (Control) m.getByID(m.getXmlBase()+"ACT_4463122");
        assertNotNull(ctrl);
        assertTrue(ctrl.getControlled().iterator().next() instanceof Control);
        assertTrue(ctrl.getController().iterator().next() instanceof SmallMolecule);

        // an interesting special case, where the controller of the nested 'met' (metabolic) control+reaction
        // is also the controller for the 'csy' (synthesis) control+conversion process:
        ctrl = (Control) m.getByID(m.getXmlBase()+"CSY_3811415");
        assertNotNull(ctrl);
        assertTrue(ctrl.getController().size()==1);
        Conversion reaction = (Conversion) ctrl.getControlled().iterator().next();
        assertTrue(reaction instanceof Conversion);
        assertEquals("Acrylamide",reaction.getLeft().iterator().next().getDisplayName());
        assertEquals("N-acetyl-S-(2-carbamoylethyl)cysteine",reaction.getRight().iterator().next().getDisplayName());

        ctrl = (Control) m.getByID(m.getXmlBase()+"REC_2788357");
        Control w = (Control) ctrl.getControlled().iterator().next();
        assertTrue(w instanceof Control);
        assertEquals("Aspirin co-treated with clopidogrel, Epinephrine", w.getName().iterator().next());
        assertEquals(3,w.getController().size());

        reaction = (Conversion) m.getByID(m.getXmlBase()+"B_4071696");
        assertTrue(reaction instanceof ComplexAssembly);
        //a nested ComplexAssembly provides the complex to be used in the parent process,
        //but itself would not participate in another control.
        assertTrue(reaction.getControlledOf().isEmpty());
        ctrl = (Control) m.getByID(m.getXmlBase()+"ACT_4247152");
        assertTrue(ctrl instanceof Control);
        //a nested Modulation is about activity of NR1H4 (controller),
        //but the Modulation itself would not participate in another control.
        //And NR1H4 bind to FGF19 promoter, forming the "NR1H4 protein/FGF19 promoter complex"
        //via the corresponding ComplexAssembly process.
        assertTrue(ctrl.getControlledOf().isEmpty());

        reaction = (ComplexAssembly) m.getByID(m.getXmlBase()+"B_4247151");
        assertTrue(reaction instanceof ComplexAssembly);
        assertEquals(m.getXmlBase()+"complex_4247151",reaction.getRight().iterator().next().getUri());
        Complex c = (Complex) m.getByID(m.getXmlBase()+"complex_4247151");
        assertNotNull(c);
        assertEquals("NR1H4 protein/FGF19 promoter complex",c.getDisplayName());

        //similar
        c = (Complex) m.getByID(m.getXmlBase()+"complex_4247161");
        assertNotNull(c);
        assertEquals("NR1H4 protein/FGF19 promoter complex",c.getDisplayName());
        assertTrue(c.getControllerOf().size()==1);
        assertEquals(m.getByID(m.getXmlBase()+"EXP_4247160"), c.getControllerOf().iterator().next());

        //the most complicated case - both actors are of ixn type (sub-proc.), and the axn code is 'rec',
        //and the control's axn code is 'act', and the second actor is 'w', etc..
        ctrl = (Control) m.getByID(m.getXmlBase() + "REC_3727084");
        assertNotNull(ctrl); //chemical C093124 controls(inhibits) the susceptibility W_3727086
        w = (Control) m.getByID(m.getXmlBase() + "W_3727086");
        assertTrue(w instanceof Control);
        assertEquals(w, ctrl.getControlled().iterator().next());
        assertEquals(2, w.getControlledOf().size()); // is controlledOf both REC_3727084 and ACT_GENE_4843 controls
        assertTrue(w.getControlledOf().contains(ctrl));
        assertTrue(w.getControlledOf().contains(m.getByID(m.getXmlBase() + "ACT_GENE_4843")));
    }

    // test filtering by a taxonomy id which is not present in the data
    @Test
    public void convertYest() {
        CTDInteractionConverter converter = new CTDInteractionConverter("559292");
        Model m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));
        assertTrue(m.getObjects(Control.class).isEmpty());

        converter = new CTDInteractionConverter("undefined");
        m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));
//        (new SimpleIOHandler()).convertToOWL(m, System.out);
        assertEquals(8, m.getObjects(Control.class).size());
        converter = new CTDInteractionConverter("defined");
        m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));
        assertEquals(36, m.getObjects(Control.class).size());
        //see if no. controls generated with undefined + defined = all (null)
        converter = new CTDInteractionConverter(null); //convert everything - any species, and undefined too
        m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));
        assertEquals(44, m.getObjects(Control.class).size());

        // mouse
        converter = new CTDInteractionConverter("10090");
        m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));
        assertEquals(1, m.getObjects(Control.class).size());
        //human (ignoring records with no taxon defined)
        converter = new CTDInteractionConverter("9606");
        m = converter.convert(getClass().getResourceAsStream("/chem_gene_ixns_struct.xml"));
        assertEquals(35, m.getObjects(Control.class).size());
    }
}