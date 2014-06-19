package com.google.gsoc14.ctd2biopax.converter;

import com.google.gsoc14.ctd2biopax.util.CTDUtil;
import com.google.gsoc14.ctd2biopax.util.model.ActorTypeType;
import com.google.gsoc14.ctd2biopax.util.model.GeneForm;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;
import org.ctdbase.model.ActorType;
import org.ctdbase.model.AxnType;
import org.ctdbase.model.IxnSetType;
import org.ctdbase.model.IxnType;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.util.List;

public class CTDInteractionConverter extends Converter {
    private static Log log = LogFactory.getLog(CTDInteractionConverter.class);

    @Override
    public Model convert(InputStream inputStream) {
        IxnSetType interactions;
        Model model = createNewModel();

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance("org.ctdbase.model");
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            interactions = (IxnSetType) ((JAXBElement) unmarshaller.unmarshal(inputStream)).getValue();

            int count = 0;
            for (IxnType ixn : interactions.getIxn()) {
                convertInteraction(model, ixn);
                count++;

                // TODO: remove this
                if(count > 20) break;
            }

            log.info("There are " + interactions.getIxn().size() + " in the model.");

        } catch (JAXBException e) {
            log.error("Could not initialize the JAXB Reader (" + e.toString() + ").");
        }

        return model;
    }

    private Control convertInteraction(Model model, IxnType ixn) {
        // We are going to use the first actor to create the controller
        List<ActorType> actors = ixn.getActor();
        Control control = createControllerFromActor(model, ixn, actors.get(0));
        Process process = createProcessFromAction(model, ixn, actors.get(1));
        control.addControlled(process);
        assignControlVocabulary(control, ixn.getAxn().iterator().next());

        return control;
    }

    private void assignControlVocabulary(Control control, AxnType action) {
        switch(action.getDegreecode().charAt(0)) {
            case '+':
                control.setControlType(ControlType.ACTIVATION);
                break;
            case '-':
                control.setControlType(ControlType.INHIBITION);
                break;
            default:
                // No vocab -- unknown
        }
    }

    private Process createProcessFromAction(Model model, IxnType ixn, ActorType actor) {
        List<AxnType> actions = ixn.getAxn();
        assert actions.size() == 1;
        AxnType action = actions.iterator().next();

        Pathway pathway = create(Pathway.class, "process_" + ixn.getId());
        transferNames(ixn, pathway);
        model.add(pathway);
        return pathway;
    }


    private Control createControllerFromActor(Model model, IxnType ixn, ActorType actor) {
        Control control = create(Control.class, "control_" + ixn.getId());
        Controller controller = createControllerFromActor(model, actor);
        control.addController(controller);
        model.add(control);
        return control;
    }

    // TODO: Some interactions, co-treatment, might create multiple controllers. Fix this.
    private Controller createControllerFromActor(Model model, ActorType actor) {
        ActorTypeType aType = ActorTypeType.valueOf(actor.getType().toUpperCase());
        Controller controller;
        switch (aType) {
            case GENE:
            default:
                String form = actor.getForm();
                GeneForm geneForm = form == null
                        ? GeneForm.PROTEIN
                        : GeneForm.valueOf(form.toUpperCase().replaceAll(" ", "_"));

                Class<? extends SimplePhysicalEntity> eClass;
                Class<? extends EntityReference> refClass;
                switch (geneForm) {
                    default:
                    case PROTEIN:
                        eClass = Protein.class;
                        refClass = ProteinReference.class;
                        break;
                    case GENE:
                        eClass = Rna.class;
                        refClass = RnaReference.class;
                        break;
                    case SNP:
                    case POLYMORPHISM:
                        eClass = Dna.class;
                        refClass = DnaReference.class;
                        break;
                    case PROMOTER:
                    case ENHANCER:
                    case EXON:
                    case INTRON:
                        eClass = DnaRegion.class;
                        refClass = DnaRegionReference.class;
                        break;
                    case POLYA_TAIL:
                        eClass = RnaRegion.class;
                        refClass = RnaRegionReference.class;
                        break;
                }

                controller = createEntityFromActor(model, actor, eClass, refClass);
                break;
            case CHEMICAL:
                controller = createEntityFromActor(model, actor, SmallMolecule.class, SmallMoleculeReference.class);
                break;
            case IXN:
                controller = createPathway(model, actor);
                break;
        }

        return controller;
    }

    private Controller createPathway(Model model, ActorType actor) {
        Pathway pathway = create(Pathway.class, actor.getId());
        transferNames(actor, pathway);
        model.add(pathway);
        // TODO: if this is the case, then actor represents a rxn
        // TODO: Create this reaction and add that to the pathway
        return pathway;
    }

    private SimplePhysicalEntity createEntityFromActor(
            Model model,
            ActorType actorType,
            Class<? extends SimplePhysicalEntity> entityClass,
            Class<? extends EntityReference> referenceClass
    ) {
        String actorTypeId = actorType.getId();
        String entityId = actorTypeId + "_" + actorType.getParentid();
        String refId = "ref_" + actorTypeId;

        EntityReference entityReference = (EntityReference) model.getByID(refId);
        if(entityReference == null) {
            entityReference = create(referenceClass, refId);
            transferNames(actorType, entityReference);
            model.add(entityReference);
        }

        SimplePhysicalEntity simplePhysicalEntity = create(entityClass, entityId);
        transferNames(actorType, simplePhysicalEntity);
        simplePhysicalEntity.setEntityReference(entityReference);
        model.add(simplePhysicalEntity);


        return simplePhysicalEntity;
    }


    private void assignName(String name, Named named) {
        named.setStandardName(name);
        named.setDisplayName(name);
        named.getName().add(name);
    }

    private void transferNames(ActorType actor, Named named) {
        String name = CTDUtil.extractName(actor);
        assignName(name, named);
    }

    private void transferNames(IxnType ixn, Named named) {
        String fName = CTDUtil.extractName(ixn.getActor().get(0));
        String sName = CTDUtil.extractName(ixn.getActor().get(1));
        String actionStr = ixn.getAxn().iterator().next().getValue();
        String completeName = fName + " " + actionStr + " " + sName;
        assignName(completeName, named);
    }


}
