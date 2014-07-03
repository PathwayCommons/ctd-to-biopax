package com.google.gsoc14.ctd2biopax.converter;

import com.google.gsoc14.ctd2biopax.util.CTDUtil;
import com.google.gsoc14.ctd2biopax.util.model.ActorTypeType;
import com.google.gsoc14.ctd2biopax.util.model.AxnCode;
import com.google.gsoc14.ctd2biopax.util.model.GeneForm;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.controller.PropertyEditor;
import org.biopax.paxtools.controller.SimpleEditorMap;
import org.biopax.paxtools.controller.Traverser;
import org.biopax.paxtools.controller.Visitor;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;
import org.ctdbase.model.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.util.*;

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

            for (IxnType ixn : interactions.getIxn()) {
                convertInteraction(model, ixn);
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
        if(actors.size() < 2) {
            log.warn("Ixn #" + ixn.getId() + " has less than two actors. Skipping conversion for this reaction. ");
            return null;
        }

        Process process = createProcessFromAction(model, ixn, actors.get(1));
        Control control = createControlFromActor(model, ixn, actors.get(0));
        control.addControlled(process);
        assignControlVocabulary(control, ixn.getAxn().iterator().next());

        // Now annotate with publications
        int count = 0;
        for (ReferenceType referenceType : ixn.getReference()) {
            PublicationXref publicationXref = create(PublicationXref.class, "pub_" + ixn.getId() + "_" + (++count));
            publicationXref.setDb("Pubmed");
            publicationXref.setId(referenceType.getPmid().toString());
            process.addXref(publicationXref);
            model.add(publicationXref);
        }

        Process topProcess;
        // Binding reactions are special, don't want controls for them -- so let's back roll
        if(CTDUtil.extractAxnCode(ixn).equals(AxnCode.B)) {
            control.removeControlled(process);
            model.remove(control);
            topProcess = process;
        } else {
            topProcess = control;
        }

        // Categorize according to the organism via pathways
        for (TaxonType taxonType : ixn.getTaxon()) {
            assignReactionToPathway(model, topProcess, taxonType);
        }

        // And viola!
        return control;
    }

    private void assignReactionToPathway(Model model, Process process, TaxonType taxonType) {
        String taxonName = taxonType.getValue();
        String orgTaxId = taxonType.getId();
        String taxonId = "taxon_pathway_" + orgTaxId;
        Pathway pathway = (Pathway) model.getByID(taxonId);
        if(pathway == null) {
            pathway = create(Pathway.class, taxonId);
            assignName(taxonName + " pathway", pathway);

            // Let's assign the biosource
            BioSource bioSource = create(BioSource.class, "src_" + orgTaxId);
            assignName(taxonName, bioSource);
            pathway.setOrganism(bioSource);

            UnificationXref unificationXref = create(UnificationXref.class, "xref_taxon_" + orgTaxId);
            unificationXref.setDb("taxonomy");
            unificationXref.setId(orgTaxId);
            bioSource.addXref(unificationXref);

            model.add(unificationXref);
            model.add(bioSource);
            model.add(pathway);
        }
        pathway.addPathwayComponent(process);
        addReactionToPathwayByTraversing(model, process, pathway);

    }

    private void addReactionToPathwayByTraversing(Model model, Process control, Pathway pathway) {
        // Propagate pathway assignments
        final Pathway finalPathway = pathway;
        Traverser traverser = new Traverser(SimpleEditorMap.get(BioPAXLevel.L3), new Visitor() {
            @Override
            public void visit(BioPAXElement domain, Object range, Model model, PropertyEditor<?, ?> editor) {
                if(range != null && range instanceof Process) {
                    finalPathway.addPathwayComponent((Process) range);
                }
            }
        });
        traverser.traverse(control, model);
    }

    private void assignControlVocabulary(Control control, AxnType action) {
        switch(action.getDegreecode().charAt(0)) {
            case '+':
                control.setControlType(ControlType.ACTIVATION);
                break;
            case '-':
                control.setControlType(ControlType.INHIBITION);
                break;
            case '1':
            default:
                // No vocab -- unknown
        }
    }

    private Process createProcessFromAction(Model model, IxnType ixn, ActorType actor) {
        AxnCode axnCode = CTDUtil.extractAxnCode(ixn);
        String processId = CTDUtil.createProcessId(ixn, actor);

        Process process = (Process) model.getByID(processId);
        if(process != null)
            return process;

        switch (axnCode) {
            case EXP:
                process = createExpressionReaction(model, ixn, actor, processId);
                break;
            case ACT:
                process = createModificationReaction(model, actor, processId, "inactive", "active");
                break;
            case MUT:
                process = createModificationReaction(model, actor, processId, "wildtype", "mutated");
                break;
            case SPL: // splicing
                process = createModificationReaction(model, actor, processId, null, "spliced");
                break;
            case STA: // stability
                process = createModificationReaction(model, actor, processId, "instable", "stable");
                break;
            // The following are all metabolic reactions identified by the PTM name
            // So we will just use the modifier name generically for all these
            case ACE:
            case ACY:
            case ALK:
            case AMI:
            case CAR:
            case COX:
            case ETH:
            case GLT:
            case GYC:
            case GLY:
            case GLC:
            case NGL:
            case OGL:
            case HDX:
            case LIP:
            case FAR:
            case GER:
            case MYR:
            case PAL:
            case PRE:
            case MYL:
            case NIT:
            case NUC:
            case OXD:
            case PHO:
            case RED:
            case RIB:
            case ARB:
            case SUL:
            case SUM:
            case UBQ:
            case CLV:
                process = createModificationReaction(model, actor, processId, null, axnCode.getTypeName());
                break;
            case DEG:
            case HYD:
                // general degredation
                process = createDegradationReaction(model, actor, processId);
                break;
            case RXN: // Reaction
                Pathway pathway = create(Pathway.class, processId);
                transferNames(ixn, pathway);
                model.add(pathway);
                IxnType newIxn = CTDUtil.convertActorToIxn(actor);
                addReactionToPathwayByTraversing(model, convertInteraction(model, newIxn), pathway);
                process = pathway;
                break;
            case EXT:
            case SEC:
                process = createTransportReaction(model, actor, processId, null, "extracellular matrix");
                break;
            case UPT:
            case IMT:
                process = createTransportReaction(model, actor, processId, "extracellular matrix", null);
                break;
            case CSY:
                process = createSynthesisReaction(model, actor, processId);
                break;
            case B:
                process = createBindingReaction(model, ixn);
                break;
            case TRT: // transport
            case MET: // metabolism
            case ABU: // abundance
            case FOL: // folding
            case LOC: // localization
            case REC: // Response to substance
            default:
                log.warn("We don't have a proper representation of " + axnCode.getTypeName() + " reactions. " +
                        "Creating a black-box pathway for interaction #" + ixn.getId());
                Pathway blackbox = create(Pathway.class, processId);
                transferNames(ixn, blackbox);
                model.add(blackbox);
                process = blackbox;
        }

        // Add action description as a comment
        process.addComment(axnCode.getDescription());

        return process;
    }

    private Process createBindingReaction(Model model, IxnType ixn) {
        List<ActorType> actors = ixn.getActor();
        assert actors.size() >= 2;

        Complex complex = createComplex(model, ixn);
        SimplePhysicalEntity spe1 = createSPEFromActor(model, actors.get(0), false);
        SimplePhysicalEntity spe2 = createSPEFromActor(model, actors.get(1), false);

        String processId = CTDUtil.createProcessId(ixn, actors.get(0));
        ComplexAssembly complexAssembly = (ComplexAssembly) model.getByID(processId);
        if(complexAssembly == null) {
            complexAssembly = create(ComplexAssembly.class, processId);
            transferNames(ixn, complexAssembly);
            complexAssembly.addLeft(spe1);
            complexAssembly.addLeft(spe2);
            complexAssembly.addRight(complex);
            complexAssembly.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            model.add(complexAssembly);
        }

        return complexAssembly;
    }

    private Process createDegradationReaction(Model model, ActorType actor, String processId) {
        Degradation degradation = create(Degradation.class, processId);
        SimplePhysicalEntity par = createSPEFromActor(model, actor, false);
        degradation.addLeft(par);
        degradation.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);

        model.add(degradation);

        return degradation;
    }

    private Process createSynthesisReaction(Model model, ActorType actor, String processId) {
        BiochemicalReaction biochemicalReaction = create(BiochemicalReaction.class, processId);
        SimplePhysicalEntity rightPar = createSPEFromActor(model, actor, false);

        biochemicalReaction.addRight(rightPar);
        biochemicalReaction.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
        model.add(biochemicalReaction);

        return biochemicalReaction;
    }

    private Process createTransportReaction(Model model, ActorType actor, String processId, String leftLoc, String rightLoc) {
        Transport transport = create(Transport.class, processId);
        SimplePhysicalEntity leftPar = createSPEFromActor(model, actor, false);
        SimplePhysicalEntity rightPar = createSPEFromActor(model, actor, true);
        transport.addLeft(leftPar);
        transport.addRight(rightPar);
        transport.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);

        if(leftLoc != null) {
            leftPar.setCellularLocation(createCellularLocation(model, leftLoc));
        }

        if(rightLoc != null) {
            rightPar.setCellularLocation(createCellularLocation(model, rightLoc));
        }

        model.add(transport);

        return transport;
    }

    private CellularLocationVocabulary createCellularLocation(Model model, String location) {
        String locId = CTDUtil.locationToId(location);

        CellularLocationVocabulary cellularLocationVocabulary = (CellularLocationVocabulary) model.getByID(locId);
        if(cellularLocationVocabulary == null) {
            cellularLocationVocabulary = create(CellularLocationVocabulary.class, locId);
            cellularLocationVocabulary.addTerm(location);
            model.add(cellularLocationVocabulary);
        }

        return cellularLocationVocabulary;
    }


    private Process createModificationReaction(Model model, ActorType actor, String processId, String leftTerm, String rightTerm) {
        BiochemicalReaction biochemicalReaction = create(BiochemicalReaction.class, processId);
        SimplePhysicalEntity leftPar = createSPEFromActor(model, actor, leftTerm != null);
        SimplePhysicalEntity rightPar = createSPEFromActor(model, actor, rightTerm != null);
        biochemicalReaction.addLeft(leftPar);
        biochemicalReaction.addRight(rightPar);
        biochemicalReaction.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);

        if(CTDUtil.extractActorTypeType(actor).equals(ActorTypeType.CHEMICAL)) {
            if(leftTerm != null) {
                leftPar.setDisplayName(leftPar.getDisplayName() + " (" + leftTerm + ")");
            }

            if(rightTerm != null) {
                rightPar.setDisplayName(rightPar.getDisplayName() + " (" + rightTerm + ")");
            }
        } else {
            if(leftTerm != null) {
                leftPar.addFeature(createModFeature(model, "modl_" + processId, leftTerm));
            }

            if(rightTerm != null) {
                rightPar.addFeature(createModFeature(model, "modr_" + processId, rightTerm));
            }
        }


        model.add(biochemicalReaction);

        return biochemicalReaction;
    }

    private ModificationFeature createModFeature(Model model, String id, String term) {
        ModificationFeature feature = create(ModificationFeature.class, id);
        SequenceModificationVocabulary modificationVocabulary = create(SequenceModificationVocabulary.class, "seqmod_" + id);
        modificationVocabulary.addTerm(term);
        feature.setModificationType(modificationVocabulary);
        model.add(feature);
        model.add(modificationVocabulary);

        return feature;
    }

    private Process createExpressionReaction(Model model, IxnType ixn, ActorType actor, String processId) {
        TemplateReaction templateReaction = create(TemplateReaction.class, processId);
        templateReaction.setTemplateDirection(TemplateDirectionType.FORWARD); // Expression is always forward
        ActorTypeType actorTypeType = CTDUtil.extractActorTypeType(actor);
        switch (actorTypeType) {
            case IXN:
                log.error("Found an expression reaction with the second " +
                        "actor also a reaction, which is ambigous: #"
                        + ixn.getId() + ". Created an incomplete template reaction.");
                break;
            default:
                SimplePhysicalEntity actorEntity = createSPEFromActor(model, actor, false);
                templateReaction.addProduct(actorEntity);
                model.add(templateReaction);
                transferNames(ixn, templateReaction);
        }
        return templateReaction;
    }

    private Control createControlFromActor(Model model, IxnType ixn, ActorType actor) {
        Control control = create(Control.class, "control_" + ixn.getId());
        for (Controller controller : createControlEntityFromActor(model, actor)) {
            control.addController(controller);
        }
        model.add(control);

        return control;
    }

    private Collection<Controller> createControlEntityFromActor(Model model, ActorType actor) {
        HashSet<Controller> controllers = new HashSet<Controller>();
        ActorTypeType aType = CTDUtil.extractActorTypeType(actor);
        switch (aType) {
            case IXN:
                IxnType ixnType = CTDUtil.convertActorToIxn(actor);
                switch(CTDUtil.extractAxnCode(ixnType)) {
                    case B:
                        controllers.add(createComplex(model, ixnType));
                        break;
                    case W:
                        controllers.addAll(createMultipleControllers(model, ixnType));
                        break;
                    default:
                        Control control = convertInteraction(model, ixnType);
                        Pathway pathway = createPathway(model, actor);
                        pathway.addPathwayComponent(control);
                        controllers.add(pathway);
                }
                break;
            default: // If not an IXN, then it is a physical entity
                controllers.add(createSPEFromActor(model, actor, false));
                break;
        }

        return controllers;
    }

    private SimplePhysicalEntity createSPEFromActor(Model model, ActorType actor, boolean createNewInstance) {
        SimplePhysicalEntity spe;
        ActorTypeType aType = CTDUtil.extractActorTypeType(actor);

        switch (aType) {
            case GENE:
            default:
                String form = actor.getForm();
                GeneForm geneForm =
                        form == null
                                ? GeneForm.PROTEIN
                                : GeneForm.valueOf(CTDUtil.sanitizeGeneForm(form).toUpperCase());

                Class<? extends SimplePhysicalEntity> eClass = geneForm.getEntityClass();
                Class<? extends EntityReference> refClass = geneForm.getReferenceClass();
                spe = createEntityFromActor(model, actor, eClass, refClass, createNewInstance);
                break;
            case CHEMICAL:
                spe = createEntityFromActor(model, actor, SmallMolecule.class, SmallMoleculeReference.class, createNewInstance);
                break;
        }

        return spe;
    }

    private Collection<? extends Controller> createMultipleControllers(Model model, IxnType ixnType) {
        HashSet<Controller> controllers = new HashSet<Controller>();
        for (ActorType actorType : ixnType.getActor()) {
            controllers.addAll(createControlEntityFromActor(model, actorType));
        }

        return controllers;
    }

    private Complex createComplex(Model model, IxnType ixnType) {
        Complex complex = create(Complex.class, "complex_" + ixnType.getId());
        transferNames(ixnType, complex);

        String cName = "";
        for (ActorType actorType : ixnType.getActor()) {
            SimplePhysicalEntity speFromActor = createSPEFromActor(model, actorType, true);
            cName += speFromActor.getDisplayName() + "/";
            complex.addComponent(speFromActor);
        }
        cName = cName.substring(0, cName.length()-1);
        assignName(cName, complex);

        model.add(complex);
        return complex;
    }


    private Pathway createPathway(Model model, ActorType actor) {
        Pathway pathway = create(Pathway.class, actor.getId());
        transferNames(actor, pathway);
        model.add(pathway);
        return pathway;
    }

    private SimplePhysicalEntity createEntityFromActor(
            Model model,
            ActorType actorType,
            Class<? extends SimplePhysicalEntity> entityClass,
            Class<? extends EntityReference> referenceClass,
            boolean createNewEntity
    ) {
        String actorTypeId = actorType.getId();

        String form = actorType.getForm();
        ActorTypeType actorTypeType = CTDUtil.extractActorTypeType(actorType);
        // Override all forms of chemicals (none || analog)
        if(actorTypeType.equals(ActorTypeType.CHEMICAL)) {
            form = "chemical";
        }
        // If we still don't have any forms, then it means it is a gene, hence we need a protein by default
        if((form == null || form.isEmpty()) && actorTypeType.equals(ActorTypeType.GENE)) {
            form = "protein";
        }
        if(form == null) { form = "chemical"; }
        String refId = CTDUtil.createRefRDFId(form.toUpperCase(), actorTypeId);

        String entityId = CTDUtil.sanitizeId(actorTypeId + "_" + form
                + (createNewEntity ? "_" + UUID.randomUUID() : ""));

        EntityReference entityReference = (EntityReference) model.getByID(refId);
        if(entityReference == null) {
            entityReference = create(referenceClass, refId);
            transferNames(actorType, entityReference);
            model.add(entityReference);
        }

        SimplePhysicalEntity simplePhysicalEntity = (SimplePhysicalEntity) model.getByID(entityId);
        if(simplePhysicalEntity == null) {
            simplePhysicalEntity = create(entityClass, entityId);
            transferNames(actorType, simplePhysicalEntity);
            simplePhysicalEntity.setEntityReference(entityReference);
            model.add(simplePhysicalEntity);
        }

        return simplePhysicalEntity;
    }

    private void assignName(String name, Named named) {
        named.setStandardName(name);
        named.setDisplayName(name);
        named.getName().add(name);
    }

    private void transferNames(ActorType actor, Named named) {
        assignName(CTDUtil.extractName(actor), named);
    }

    private void transferNames(IxnType ixn, Named named) {
        assignName(CTDUtil.extractName(ixn), named);
    }


}
