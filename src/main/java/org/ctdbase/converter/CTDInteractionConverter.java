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

        Process process;

        AxnCode axnCode = CtdUtil.extractAxnCode(ixn);
        // Create a Contol if the process was nt binding or co-treatment special case
        if(axnCode.equals(AxnCode.B) || axnCode.equals(AxnCode.W)) {
            process = createProcessFromAction(model, ixn, null);
        } else {
            process = createProcessFromAction(model, ixn, actors.get(1));
            Control control = createControlFromActor(model, process, ixn, actors.get(0));
            control.addControlled(process);
            assignControlVocabulary(control, ixn.getAxn().iterator().next());
            process = control;
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
            process.addXref(publicationXref);
        }

        return process;
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
        final String processId = (actor != null) ?  CtdUtil.createProcessId(ixn, actor) : "process_" + ixn.getId();
        Process process = (Process) model.getByID(absoluteUri(processId));
        if(process != null) {
            log.info("got previously created " + process.getModelInterface().getSimpleName() + ": " + processId);
            return process;
        }

        AxnCode axnCode = CtdUtil.extractAxnCode(ixn);
        switch (axnCode) {
            case B:
                process = createBindingReaction(model, ixn);
                break;
            case W:
                process = createCoTreatmentControl(model, ixn);
                break;
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
                process = createReaction(model, ixn, actor, processId);
                break;
            case EXT:
            case SEC:
                process = createTransport(model, ixn, actor, processId, null, "extracellular matrix");
                break;
            case UPT:
            case IMT:
                process = createTransport(model, ixn, actor, processId, "extracellular matrix", null);
                break;
            case CSY:
                process = createSynthesisReaction(model, ixn, actor, processId);
                break;
            case FOL: // folding
                process = createModificationReaction(model, ixn, actor, processId, null, axnCode.getTypeName());
                break;
            case TRT: // transport
                process = createTransport(model, ixn, actor, processId, null, null);
                break;
            case LOC: // localization
                process = createTransport(model, ixn, actor, processId, null, null);
                break;
            case REC: // Response to substance
            case ABU: // abundance
            case MET: // metabolism
            default:
                process =  createReaction(model, ixn, actor, processId);
                break;
        }

        if(process != null && axnCode != null && !(process instanceof Control))
            process.addComment(axnCode.getDescription());

        return process;
    }

    private Process convertNestedProcess(Model model, IxnType ixn, ActorType actor, String processId) {
        Actor aType = CtdUtil.extractActor(actor);
        if(aType != Actor.IXN)
            throw new IllegalArgumentException("Actor type here must be 'ixn'");

        log.info("Creting nested process(es); id: " + processId);
        //build a new ixn object (sub-process) from the actor type element
        IxnType subIxn = CtdUtil.convertActorToIxn(actor);
        Process process = convertInteraction(model, subIxn);
        return process;
    }

    private Process createBindingReaction(Model model, IxnType ixn) {
        List<ActorType> actors = ixn.getActor();
        assert actors.size() >= 2;
        String processId = "process_" + ixn.getId();
        ComplexAssembly complexAssembly = (ComplexAssembly) model.getByID(absoluteUri(processId));
        if(complexAssembly == null) {
            complexAssembly = create(ComplexAssembly.class, processId);
            setNameFromIxnType(ixn, complexAssembly, true);
            Complex complex = create(Complex.class, "complex_" + ixn.getId());
            model.add(complex);
            // build complex components from actors
            for(ActorType actor : actors) {
                Actor axn = CtdUtil.extractActor(actor);
                if(axn != Actor.IXN) {
                    PhysicalEntity pe = createSPEFromActor(model, actor, false);
                    complex.addComponent(pe);
                    complexAssembly.addLeft(pe);
                } else {
                    IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                    AxnCode subAxn = CtdUtil.extractAxnCode(subIxn);
                    //convert only 'w' and 'b' type content here
                    if(subAxn == AxnCode.W) {
                        //TODO: unsure... a sub-complex or complex assembly? 'w' (co-treatment) probably means logical 'OR'
                        for (ActorType actorType : subIxn.getActor()) {
                            PhysicalEntity pe = createSPEFromActor(model, actorType, true);
                            complex.addComponent(pe);
                            complexAssembly.addLeft(pe);
                        }
                    }
                    else {
                        Process proc = convertInteraction(model, subIxn);
                        for(PhysicalEntity pe : getProductsFromProcess(proc)) {
                            complex.addComponent(pe);
                            complexAssembly.addLeft(pe);
                        }
                    }
                }
            }
            complexAssembly.addRight(complex);
            complexAssembly.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            model.add(complexAssembly);
        }
        return complexAssembly;
    }

    //a Control with controllers but without controlled process
    private Process createCoTreatmentControl(Model model, IxnType ixn) {
        String rdfId = "process_" + ixn.getId();
        Control control = (Control) model.getByID(absoluteUri(rdfId));
        if(control == null) {
            control = create(Control.class, rdfId);
            setNameFromIxnType(ixn, control);
            for (ActorType actor : ixn.getActor()) {
                control.addController(createSPEFromActor(model, actor, false));
            }
            model.add(control);
        }
        control.addComment(AxnCode.W.getDescription());
        return control;
    }

    private Process createDegradationReaction(Model model, IxnType ixn, ActorType actor, String processId)
    {
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                process = convertNestedProcess(model, ixn, actor, processId);
                break;
            default:
                Degradation degradation = (Degradation) model.getByID(absoluteUri(processId));
                if (degradation == null) {
                    degradation = create(Degradation.class, processId);
                    setNameFromIxnType(ixn, degradation, false);
                    SimplePhysicalEntity par = createSPEFromActor(model, actor, false);
                    degradation.addLeft(par);
                    degradation.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
                    model.add(degradation);
                }
                process = degradation;
                break;
        }
        return process;
    }

    private Process createReaction(Model model, IxnType ixn, ActorType actor, String processId) {
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                process = convertNestedProcess(model, ixn, actor, processId);
                break;
            default:
                Conversion react = (Conversion) model.getByID(absoluteUri(processId));
                if (react == null) {
                    react = create(Conversion.class, processId);
                    setNameFromIxnType(ixn, react, false);
                    SimplePhysicalEntity par = createSPEFromActor(model, actor, false);
                    react.addParticipant(par);
                    model.add(react);
                }
                process = react;
                break;
        }
        return process;
    }

    private Process createSynthesisReaction(Model model, IxnType ixn, ActorType actor, String processId)
    {
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                process = convertNestedProcess(model, ixn, actor, processId);
                break;
            default:
                BiochemicalReaction biochemicalReaction = (BiochemicalReaction) model.getByID(absoluteUri(processId));
                if (biochemicalReaction == null) {
                    biochemicalReaction = create(BiochemicalReaction.class, processId);
                    setNameFromIxnType(ixn, biochemicalReaction, false);
                    SimplePhysicalEntity rightPar = createSPEFromActor(model, actor, false);
                    biochemicalReaction.addRight(rightPar);
                    biochemicalReaction.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
                    model.add(biochemicalReaction);
                }
                process = biochemicalReaction;
                break;
        }
        return process;
    }

    private Process createTransport(Model model, IxnType ixn, ActorType actor,
                                            String processId, String leftLoc, String rightLoc)
    {
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                process = convertNestedProcess(model, ixn, actor, processId);
                break;
            default:
                Transport transport = (Transport) model.getByID(absoluteUri(processId));
                if (transport == null) {
                    transport = create(Transport.class, processId);
                    setNameFromIxnType(ixn, transport, false);
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
                process = transport;
                break;
        }
        return process;
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
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                process = convertNestedProcess(model, ixn, actor, processId);
                break;
            default:
                BiochemicalReaction biochemicalReaction = (BiochemicalReaction) model.getByID(absoluteUri(processId));
                if (biochemicalReaction == null) {
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
                process = biochemicalReaction;
                break;
        }

        return process;
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
                process = convertNestedProcess(model, ixn, actorType, processId);
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
        String rdfId = "process_" + ixn.getId();
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
        control.addComment(CtdUtil.extractAxnCode(ixn).getDescription());
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
                        Pathway pathway = create(Pathway.class, "pathway_" + actor.getId());
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
            case CHEMICAL:
                spe = createEntityFromActor(model, actor, SmallMolecule.class, SmallMoleculeReference.class, createNewInstance);
                break;
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

    private Complex createComplex(Model model, IxnType ixn) {
        Complex complex = create(Complex.class, "complex_" + ixn.getId());
        String cName = "";
        for (ActorType actorType : ixn.getActor()) {
            SimplePhysicalEntity speFromActor = createSPEFromActor(model, actorType, true);
            cName += speFromActor.getDisplayName() + "/";
            complex.addComponent(speFromActor);
        }
        cName = cName.substring(0, cName.length()-1) + " complex";
        assignName(cName, complex);
        model.add(complex);
        return complex;
    }


    private SimplePhysicalEntity createEntityFromActor(Model model, ActorType actorType,
                                                       Class<? extends SimplePhysicalEntity> entityClass,
                                                       Class<? extends EntityReference> referenceClass,
                                                       boolean createNewEntity)
    {
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

    private static Set<PhysicalEntity> getProductsFromProcess(Process process) {
        Set<PhysicalEntity> products = new HashSet<PhysicalEntity>();
        if(process instanceof Control) {
            for (Process controlled : ((Control) process).getControlled()) {
                products.addAll(getProductsFromProcess(controlled));
            }
        }
        else if (process instanceof Conversion) {
            products.addAll(((Conversion)process).getRight());
        }
        else if (process instanceof TemplateReaction) {
            products.addAll(((TemplateReaction)process).getProduct());
        }
//        else if (process instanceof Pathway) {
//            Pathway p = (Pathway) process;
//            for (Process pc : p.getPathwayComponent()) {
//                products.addAll(getProductsFromProcess(pc));
//            }
//        }
        else { //never gets here ;)
            throw new IllegalArgumentException("getProductsFromProcess - impossible "
                    + process.getModelInterface().getSimpleName() + " " + process.getUri());
        }

        return products;
    }

}
