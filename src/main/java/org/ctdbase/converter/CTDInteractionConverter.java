package org.ctdbase.converter;

import org.ctdbase.util.CtdUtil;
import org.ctdbase.util.model.Actor;
import org.ctdbase.util.model.AxnCode;
import org.ctdbase.util.model.GeneForm;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;
import org.ctdbase.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.util.*;

public class CTDInteractionConverter extends Converter {
    private static Logger log = LoggerFactory.getLogger(CTDInteractionConverter.class);

    private final String taxId;

    public CTDInteractionConverter(String taxId) {
        this.taxId = taxId;
    }

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
        } catch (JAXBException e) {
            log.error("Could not initialize the JAXB Reader. ", e);
        }

        return model;
    }

    private Process convertInteraction(Model model, IxnType ixn) {
        // We use the first (0) actor to create a Control interaction
        List<ActorType> actors = ixn.getActor();

        if(actors.size() < 2) {
            log.warn("Ixn #" + ixn.getId() + " has less than two actors; skipping.");
            return null;
        }

        //filter by organism (taxonomy) if needed
        if(taxId != null) {
            boolean skip = (ixn.getTaxon().isEmpty()) ? false : true;
            for (TaxonType taxonType : ixn.getTaxon()) {
                if (taxId.equalsIgnoreCase(taxonType.getId())) {
                    skip = false;
                    break;
                }
            }
            if(skip) {
                log.debug("Ixn #" + ixn.getId() + " is NOT about taxonomy:" + taxId + "; skipping.");
                return null;
            }
        }

        Process process = createProcessFromAction(model, ixn, actors.get(1));
        Control control = createControlFromActor(model, process, ixn, actors.get(0));
        control.addControlled(process);

        assignControlVocabulary(control, ixn.getAxn().iterator().next());

        Process topProcess;
        // Binding reactions are special (in BioPAX): we don't want controls for them -- so let's back roll - TODO: why (@armish)?..
        if(CtdUtil.extractAxnCode(ixn).equals(AxnCode.B)) {
            control.removeControlled(process);
            model.remove(control);
            topProcess = process;
        } else {
            topProcess = control;
        }

        // add publication xrefs
        for (ReferenceType referenceType : ixn.getReference())
        {
            final String pmid = referenceType.getPmid().toString();
            final String uri =  "http://identifiers.org/pubmed/" + pmid;
            PublicationXref publicationXref = (PublicationXref) model.getByID(uri);
            if(publicationXref==null) {
                publicationXref = create(PublicationXref.class, uri);
                publicationXref.setDb("pubmed");
                publicationXref.setId(pmid);
                model.add(publicationXref);
            }
            topProcess.addXref(publicationXref);
        }

        return topProcess;
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
                break; // No vocab -- unknown
        }
    }

    private Process createProcessFromAction(Model model, IxnType ixn, ActorType actor)
    {
        AxnCode axnCode = CtdUtil.extractAxnCode(ixn);
        final String processId = CtdUtil.createProcessId(ixn, actor);
        Process process = (Process) model.getByID(absoluteUri(processId));

        if(process != null) {
            log.info("found previously created " +
                    process.getModelInterface().getSimpleName() + " process: " + processId);
            return process;
        }

        switch (axnCode) {
            case EXP:
                process = createExpressionReaction(model, ixn, actor, processId);
                break;
            case ACT:
                process = createModificationReaction(model, ixn, actor, processId, "inactive", "active");
                break;
            case MUT:
                process = createModificationReaction(model, ixn, actor, processId, "wildtype", "mutated");
                break;
            case SPL: // splicing
                process = createModificationReaction(model, ixn, actor, processId, null, "spliced");
                break;
            case STA: // stability
                process = createModificationReaction(model, ixn, actor, processId, "instable", "stable");
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
                process = createModificationReaction(model, ixn, actor, processId, null, axnCode.getTypeName());
                break;
            case DEG:
            case HYD:
                process = createDegradationReaction(model, ixn, actor, processId);
                break;
            case RXN: // Reaction (or Control with TemplateReaction)
                process = convertRxnSubProcess(model, ixn, actor, processId);
                break;
            case EXT:
            case SEC:
                process = createTransportReaction(model, ixn, actor, processId, null, "extracellular matrix");
                break;
            case UPT:
            case IMT:
                process = createTransportReaction(model, ixn, actor, processId, "extracellular matrix", null);
                break;
            case CSY:
                process = createSynthesisReaction(model, ixn, actor, processId);
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
                log.info("for a " + axnCode.getTypeName() + ", ixn #" + ixn.getId() +
                        ", creating blackbox: " + processId);
                process = createReaction(model, ixn, actor, processId);
                break;
        }

        if(process != null && axnCode!= null)
            process.addComment(axnCode.getDescription());

        return process;
    }

    private Process convertRxnSubProcess(Model model, IxnType ixn, ActorType actor, String processId) {
        Process process;
        Actor aType = CtdUtil.extractActor(actor);
        if(aType == Actor.IXN) {
            log.info("(RXN) creting sub-process(es), using rdfId: " + processId);
            IxnType subIxn = CtdUtil.convertActorToIxn(actor); //extract the new ixn (of the sub-process)
            process = convertInteraction(model, subIxn);
        } else {
            log.info("for actor " + aType + ", " + actor.getId()  + " in the rxn ixn #"
                    + ixn.getId() + ", creating a blackbox: " + processId);
            process = createReaction(model, ixn, actor, processId);
        }
        return process;
    }

    private Process createBindingReaction(Model model, IxnType ixn) {
        List<ActorType> actors = ixn.getActor();
        assert actors.size() >= 2;

        Complex complex = createComplex(model, ixn);
        SimplePhysicalEntity spe1 = createSPEFromActor(model, actors.get(0), false);
        SimplePhysicalEntity spe2 = createSPEFromActor(model, actors.get(1), false);

        String processId = CtdUtil.createProcessId(ixn, actors.get(0));
        ComplexAssembly complexAssembly = (ComplexAssembly) model.getByID(absoluteUri(processId));
        if(complexAssembly == null) {
            complexAssembly = create(ComplexAssembly.class, processId);
            setNameFromIxnType(ixn, complexAssembly, true);
            complexAssembly.addLeft(spe1);
            complexAssembly.addLeft(spe2);
            complexAssembly.addRight(complex);
            complexAssembly.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            model.add(complexAssembly);
        }

        return complexAssembly;
    }

    private Process createDegradationReaction(Model model, IxnType ixn, ActorType actor, String processId)
    {
        Degradation degradation = (Degradation) model.getByID(absoluteUri(processId));
        if(degradation == null) {
            degradation = create(Degradation.class, processId);
            setNameFromIxnType(ixn, degradation, false);
            SimplePhysicalEntity par = createSPEFromActor(model, actor, false);
            degradation.addLeft(par);
            degradation.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            model.add(degradation);
        }
        return degradation;
    }

    private Process createReaction(Model model, IxnType ixn, ActorType actor, String processId) {
        Conversion react = (Conversion) model.getByID(absoluteUri(processId));
        if(react == null) {
            react = create(Conversion.class, processId);
            setNameFromIxnType(ixn, react, false);
            SimplePhysicalEntity par = createSPEFromActor(model, actor, false);
            react.addParticipant(par);
            model.add(react);
        }
        return react;
    }

    private Process createSynthesisReaction(Model model, IxnType ixn, ActorType actor, String processId) {
        BiochemicalReaction biochemicalReaction = (BiochemicalReaction) model.getByID(absoluteUri(processId));
        if(biochemicalReaction == null) {
            biochemicalReaction = create(BiochemicalReaction.class, processId);
            setNameFromIxnType(ixn, biochemicalReaction, false);
            SimplePhysicalEntity rightPar = createSPEFromActor(model, actor, false);
            biochemicalReaction.addRight(rightPar);
            biochemicalReaction.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            model.add(biochemicalReaction);
        }
        return biochemicalReaction;
    }

    private Process createTransportReaction(Model model, IxnType ixn, ActorType actor,
                                            String processId, String leftLoc, String rightLoc)
    {
        Transport transport = (Transport) model.getByID(absoluteUri(processId));
        if(transport == null) {
            transport = create(Transport.class, processId);
            setNameFromIxnType(ixn, transport,false);
            SimplePhysicalEntity leftPar = createSPEFromActor(model, actor, false);
            SimplePhysicalEntity rightPar = createSPEFromActor(model, actor, true);
            transport.addLeft(leftPar);
            transport.addRight(rightPar);
            transport.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            if (leftLoc != null) {
                leftPar.setCellularLocation(createCellularLocation(model, leftLoc));
            }
            if (rightLoc != null) {
                rightPar.setCellularLocation(createCellularLocation(model, rightLoc));
            }
            model.add(transport);
        }

        return transport;
    }

    private CellularLocationVocabulary createCellularLocation(Model model, String location) {
        String locId = CtdUtil.locationToId(location);

        CellularLocationVocabulary cellularLocationVocabulary = (CellularLocationVocabulary) model.getByID(absoluteUri(locId));
        if(cellularLocationVocabulary == null) {
            cellularLocationVocabulary = create(CellularLocationVocabulary.class, locId);
            cellularLocationVocabulary.addTerm(location);
            model.add(cellularLocationVocabulary);
        }

        return cellularLocationVocabulary;
    }


    private Process createModificationReaction(Model model, IxnType ixn, ActorType actor,
                                               String processId, String leftTerm, String rightTerm)
    {
        BiochemicalReaction biochemicalReaction = (BiochemicalReaction) model.getByID(absoluteUri(processId));
        if(biochemicalReaction == null) {
            biochemicalReaction = create(BiochemicalReaction.class, processId);
            setNameFromIxnType(ixn, biochemicalReaction, false);
            SimplePhysicalEntity leftPar = createSPEFromActor(model, actor, leftTerm != null);
            SimplePhysicalEntity rightPar = createSPEFromActor(model, actor, rightTerm != null);
            biochemicalReaction.addLeft(leftPar);
            biochemicalReaction.addRight(rightPar);
            biochemicalReaction.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);

            if (CtdUtil.extractActor(actor).equals(Actor.CHEMICAL)) {
                if (leftTerm != null) {
                    leftPar.setDisplayName(leftPar.getDisplayName() + " (" + leftTerm + ")");
                }

                if (rightTerm != null) {
                    rightPar.setDisplayName(rightPar.getDisplayName() + " (" + rightTerm + ")");
                }
            } else {
                if (leftTerm != null) {
                    leftPar.addFeature(createModFeature(model, "modl_" + processId, leftTerm));
                }

                if (rightTerm != null) {
                    rightPar.addFeature(createModFeature(model, "modr_" + processId, rightTerm));
                }
            }

            model.add(biochemicalReaction);
        }

        return biochemicalReaction;
    }

    private ModificationFeature createModFeature(Model model, String id, String term)
    {
        ModificationFeature feature = create(ModificationFeature.class, id);
        SequenceModificationVocabulary modificationVocabulary = create(SequenceModificationVocabulary.class, "seqmod_" + id);
        modificationVocabulary.addTerm(term);
        feature.setModificationType(modificationVocabulary);
        model.add(feature);
        model.add(modificationVocabulary);
        return feature;
    }

    private Process createExpressionReaction(Model model, IxnType ixn, ActorType actorType, String processId) {
        Process process;
        final Actor actor = CtdUtil.extractActor(actorType);
        switch (actor) {
            case IXN:
                log.warn("An expression reaction with the second " +
                        "actor also a reaction, ixn #" + ixn.getId() + "; will do like for RXN");

                process = convertRxnSubProcess(model, ixn, actorType, processId);
                break;
            default:
                TemplateReaction templateReaction = (TemplateReaction) model.getByID(absoluteUri(processId));
                if(templateReaction==null) {
                    templateReaction = create(TemplateReaction.class, processId);
                    setNameFromIxnType(ixn, templateReaction, false);
                    templateReaction.setTemplateDirection(TemplateDirectionType.FORWARD); // Expression is always forward
                    SimplePhysicalEntity actorEntity = createSPEFromActor(model, actorType, false);
                    templateReaction.addProduct(actorEntity);
                    model.add(templateReaction);
                }
                process = templateReaction;
                break;
        }

        return process;
    }

    private Control createControlFromActor(Model model, Process controlled, IxnType ixn, ActorType actor)
    {
        String rdfId = "control_" + ixn.getId();
        Control control = (Control) model.getByID(absoluteUri(rdfId));

        if(control == null) {
            if (controlled instanceof TemplateReaction)
                control = create(TemplateReactionRegulation.class, rdfId);
            else {
                control = create(Control.class, rdfId);
            }

            setNameFromIxnType(ixn, control);

            for (Controller controller : createControllersFromActor(model, actor)) {
                control.addController(controller);
            }
            model.add(control);
        }

        return control;
    }

    private Collection<Controller> createControllersFromActor(Model model, ActorType actor) {
        HashSet<Controller> controllers = new HashSet<Controller>();
        Actor aType = CtdUtil.extractActor(actor);
        switch (aType) {
            case IXN:
                final IxnType ixnType = CtdUtil.convertActorToIxn(actor);
                switch(CtdUtil.extractAxnCode(ixnType)) {
                    case B:
                        controllers.add(createComplex(model, ixnType));
                        break;
                    case W:
                        controllers.addAll(createMultipleControllers(model, ixnType));
                        break;
                    default:
                        Process proc = convertInteraction(model, ixnType);
                        //controller pathway to contain the process(es)
                        Pathway pathway = create(Pathway.class, "controller_pathway_" + actor.getId());
                        setNameFromActor(actor, pathway);
                        pathway.addPathwayComponent(proc);
                        if(proc instanceof Control)
                            for(Process subproc : ((Control) proc).getControlled())
                                pathway.addPathwayComponent(subproc);
                        model.add(pathway);
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
        Actor aType = CtdUtil.extractActor(actor);

        switch (aType) {
            case GENE:
            default:
                String form = actor.getForm();
                GeneForm geneForm = (form == null)
                        ? GeneForm.PROTEIN
                            : GeneForm.valueOf(CtdUtil.sanitizeGeneForm(form).toUpperCase());

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
            controllers.addAll(createControllersFromActor(model, actorType));
        }
        return controllers;
    }

    private Complex createComplex(Model model, IxnType ixnType) {
        Complex complex = create(Complex.class, "complex_" + ixnType.getId());

        String cName = "";
        for (ActorType actorType : ixnType.getActor()) {
            SimplePhysicalEntity speFromActor = createSPEFromActor(model, actorType, true);
            cName += speFromActor.getDisplayName() + "/";
            complex.addComponent(speFromActor);
        }
        cName = cName.substring(0, cName.length()-1) + " complex";
        assignName(cName, complex);

        model.add(complex);
        return complex;
    }


    private SimplePhysicalEntity createEntityFromActor(
            Model model,
            ActorType actorType,
            Class<? extends SimplePhysicalEntity> entityClass,
            Class<? extends EntityReference> referenceClass,
            boolean createNewEntity
    ) {
        String form = actorType.getForm();
        Actor actor = CtdUtil.extractActor(actorType);

        // Override all forms of chemical type (none || analog)
        if(actor.equals(Actor.CHEMICAL)) {
            form = "chemical";
        }

        if((form==null) && actor.equals(Actor.GENE)) {
            form = "protein";
        }

        if(form==null) {
            form = "chemical";
        } else {
            form = form.toLowerCase();
        }

        String actorTypeId = actorType.getId();
        String refId = CtdUtil.sanitizeId("ref_" + form + "_" + actorTypeId.toLowerCase());
        String entityId = CtdUtil.sanitizeId(form + "_" + actorTypeId.toLowerCase()
                + (createNewEntity ? "_" + UUID.randomUUID() : ""));

        EntityReference entityReference = (EntityReference) model.getByID(absoluteUri(refId));
        if(entityReference == null) {
            entityReference = create(referenceClass, refId);
            setNameFromActor(actorType, entityReference);
            model.add(entityReference);
            //TODO: set organism property from ixn taxon
            //add Xref
            String rxUri = absoluteUri(CtdUtil.sanitizeId("rx_" + actorTypeId.toLowerCase()));
            RelationshipXref rx = (RelationshipXref)model.getByID(rxUri);
            if(rx == null) {
                if(actorTypeId.contains(":")) {
                    rx = model.addNew(RelationshipXref.class, rxUri);
                    String[] t = actorTypeId.split(":");
                    rx.setDb(("gene".equalsIgnoreCase(t[0])) ? "NCBI Gene" : t[0]);
                    rx.setId(t[1]);
                } else {
                    log.warn("Cannot make RX for ER " + refId + " due to no ':' in actor.id=" + actorTypeId);
                }
            }
            entityReference.addXref(rx);
        }

        SimplePhysicalEntity simplePhysicalEntity = (SimplePhysicalEntity) model.getByID(absoluteUri(entityId));
        if(simplePhysicalEntity == null) {
            simplePhysicalEntity = create(entityClass, entityId);
            setNameFromActor(actorType, simplePhysicalEntity);
            simplePhysicalEntity.setEntityReference(entityReference);
            model.add(simplePhysicalEntity);
        }

        return simplePhysicalEntity;
    }

    private void assignName(String name, Named named) {
        if(name!=null && !name.isEmpty()) {
            if(named instanceof Process)
                named.addName(name);
            else
                named.setDisplayName(name);
        }
    }

    private String setNameFromActor(ActorType actor, Named named) {
        String name = CtdUtil.extractName(actor);
        assignName(name, named);
        return name;
    }

    private String setNameFromIxnType(IxnType ixn, Named named, boolean isTopControl) {
        String name = CtdUtil.extractName(ixn, !isTopControl);
        assignName(name, named);
        return name;
    }

    private String setNameFromIxnType(IxnType ixn, Named named){
        return setNameFromIxnType(ixn,named,true);
    }

}
