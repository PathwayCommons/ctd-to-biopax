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

/**
 * A CTD chem-gene interactions data to BioPAX L3 converter.
 *
 * @author armish (Arman Aksoy) - design and implementation
 * @author rodche (Igor Rodchenkov) - lots of improvements: nested reactions, complexes, entity names, ids, etc...
 */
public class CTDInteractionConverter extends Converter {
    private static Logger log = LoggerFactory.getLogger(CTDInteractionConverter.class);

    private  Model model;
    private final String taxId;

    public CTDInteractionConverter(String taxId) {
        this.taxId = taxId;
    }

    @Override
    public Model convert(InputStream inputStream) {
        IxnSetType interactions;
        model = createNewModel();
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance("org.ctdbase.model");
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            interactions = (IxnSetType) ((JAXBElement) unmarshaller.unmarshal(inputStream)).getValue();
            for (IxnType ixn : interactions.getIxn()) {
                convertInteraction(ixn);
            }
        } catch (JAXBException e) {
            log.error("Could not initialize the JAXB Reader. ", e);
        }

        return model;
    }

    private Process convertInteraction(IxnType ixn) {
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
        // Create a Contol if the process is not about binding (axn code 'b') or co-treatment ('w')
        if(axnCode.equals(AxnCode.B) || axnCode.equals(AxnCode.W)) {
            process = createProcessFromAction(ixn, axnCode, null);
        } else {
            process = createProcessFromAction(ixn, axnCode, actors.get(1));
            Control control = createControlFromActor(process, ixn, axnCode, actors.get(0));
            control.addControlled(process);
            setControlType(control, ixn.getAxn().iterator().next());
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

    private void setControlType(Control control, AxnType action) {
        if(action!=null && action.getDegreecode()!=null) {
            switch (action.getDegreecode().charAt(0)) {
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
    }

    private Process createProcessFromAction(IxnType ixn, AxnCode axnCode, ActorType actor)
    {
        //sanity test
        if(actor==null && axnCode != AxnCode.B && axnCode != AxnCode.W)
            throw new IllegalArgumentException("ActorType can be null only if axn code is either W or B");

        final String processId = (actor != null) ?  CtdUtil.createProcessId(ixn, actor) : "process_" + ixn.getId();
        Process process = (Process) model.getByID(absoluteUri(processId));
        if(process != null) {
            log.info("got previously created " + process.getModelInterface().getSimpleName() + ": " + processId);
            return process;
        }
        switch (axnCode) {
            case B:
                //makes either a binding reaction (ComplexAssembly) or just a Complex, based on the parentAxnCode
                process = createBindingReaction(ixn);
                break;
            case W:
                process = createBlackboxControlWithoutProducts(ixn, axnCode);
                break;
            case EXP:
                process = createExpressionReaction(ixn, axnCode, actor, processId);
                break;
            case ACT:
                process = createModificationReaction(ixn, axnCode, actor, processId, "inactive", "active");
                break;
            case MUT:
                process = createModificationReaction(ixn, axnCode, actor, processId, "wildtype", "mutated");
                break;
            case SPL: // splicing
                process = createModificationReaction(ixn, axnCode, actor, processId, null, "spliced");
                break;
            case STA: // stability
                process = createModificationReaction(ixn, axnCode, actor, processId, "instable", "stable");
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
                process = createModificationReaction(ixn, axnCode, actor, processId, null, axnCode.getTypeName());
                break;
            case DEG:
            case HYD:
                process = createDegradationReaction(ixn, axnCode, actor, processId);
                break;
            case RXN: // Reaction (or Control with TemplateReaction)
                process = createReaction(ixn, axnCode, actor, processId);
                break;
            case EXT:
            case SEC:
                process = createTransport(ixn, axnCode, actor, processId, null, "extracellular matrix");
                break;
            case UPT:
            case IMT:
                process = createTransport(ixn, axnCode, actor, processId, "extracellular matrix", null);
                break;
            case CSY:
                process = createSynthesisReaction(ixn, actor, processId);
                break;
            case FOL: // folding
                process = createModificationReaction(ixn, axnCode, actor, processId, null, axnCode.getTypeName());
                break;
            case TRT: // transport
                process = createTransport(ixn, axnCode, actor, processId, null, null);
                break;
            case LOC: // localization
                process = createTransport(ixn, axnCode, actor, processId, null, null);
                break;
            case REC: // Response to substance
            case ABU: // abundance
            case MET: // metabolism
            default:
                process =  createReaction(ixn, axnCode, actor, processId);
                break;
        }

        return process;
    }

    // Converts an ixn (axn code='b' of course) to a complex assembly process
    private Process createBindingReaction(IxnType ixn) {
        List<ActorType> actors = ixn.getActor();
        if(actors.size() < 2) {
            log.error("createBindingReaction ignored ixn " + ixn.getId() + " - there is none or just one actor");
        }

        String processId = "process_" + ixn.getId();
        ComplexAssembly complexAssembly = (ComplexAssembly) model.getByID(absoluteUri(processId));
        if(complexAssembly == null)
        {
            complexAssembly = create(ComplexAssembly.class, processId);
            setNameFromIxnType(ixn, complexAssembly, true);
            Complex complex = create(Complex.class, "complex_" + ixn.getId());
            model.add(complex);
            complexAssembly.addRight(complex);
            complexAssembly.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            model.add(complexAssembly);
            // add complex components and make its name from actors
            StringBuilder nameBuilder = new StringBuilder();
            for(ActorType actor : actors) {
                if(CtdUtil.extractActor(actor) != Actor.IXN) {
                    PhysicalEntity pe = createSPEFromActor(actor, false);
                    complex.addComponent(pe);
                    complexAssembly.addLeft(pe);
                    nameBuilder.append(pe.getDisplayName()).append("/");
                } else {
                    //IXN : create sub-process(es) and collect their products to use as complex components here
                    IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                    AxnCode subAxn = CtdUtil.extractAxnCode(subIxn);
                    if(subAxn == AxnCode.W) {
                        //TODO: unsure what does axn code 'w' (co-treatment) mean inside an ixn actor of a 'b' parent ..
                        for (ActorType actorType : subIxn.getActor()) {
                            PhysicalEntity pe = createSPEFromActor(actorType, true);
                            complex.addComponent(pe);
                            complexAssembly.addLeft(pe);
                            nameBuilder.append(pe.getDisplayName()).append("/");
                        }
                    }
                    else {
                        Process proc = convertInteraction(subIxn);
                        for(PhysicalEntity pe : getProductsFromProcess(proc)) {
                            complex.addComponent(pe);
                            complexAssembly.addLeft(pe);
                            nameBuilder.append(pe.getDisplayName()).append("/");
                        }
                    }
                }
            }
            String name = nameBuilder.toString().substring(0, nameBuilder.length()-1);
            complex.setDisplayName((name.endsWith("complex"))?name:name+" complex");
        }
        return complexAssembly;
    }

    private Process createBlackboxControlWithoutProducts(IxnType ixn, AxnCode axnCode) {
        String rdfId = "process_" + ixn.getId();
        Control control = (Control) model.getByID(absoluteUri(rdfId));
        if(control == null) {
            control = create(Control.class, rdfId);
            setNameFromIxnType(ixn, control);
            for (ActorType actor : ixn.getActor()) {
                for(Controller c : createControllersFromActor(actor, null)) {
                    control.addController(c);
                }
            }
            model.add(control);
        }
        control.addComment(axnCode.getDescription());
        return control;
    }

    private Process createDegradationReaction(IxnType ixn, AxnCode axnCode, ActorType actor, String processId)
    {
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                process = convertInteraction(subIxn);
                break;
            default:
                Degradation degradation = (Degradation) model.getByID(absoluteUri(processId));
                if (degradation == null) {
                    degradation = create(Degradation.class, processId);
                    setNameFromIxnType(ixn, degradation, false);
                    SimplePhysicalEntity par = createSPEFromActor(actor, false);
                    degradation.addLeft(par);
                    degradation.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
                    model.add(degradation);
                }
                process = degradation;
                break;
        }
        return process;
    }

    private Process createReaction(IxnType ixn, AxnCode axnCode, ActorType actor, String processId) {
        Process process;
        Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                process = convertInteraction(subIxn);
                if(process instanceof Control)
                    process.addComment(axnCode.getDescription());
                break;
            default:
                Conversion react = (Conversion) model.getByID(absoluteUri(processId));
                if (react == null) {
                    react = create(Conversion.class, processId);
                    setNameFromIxnType(ixn, react, false);
                    SimplePhysicalEntity par = createSPEFromActor(actor, false);
                    if(axnCode == AxnCode.MET)
                        react.addLeft(par);
                    else
                        react.addRight(par);
                    model.add(react);
                }
                process = react;
                break;
        }
        return process;
    }

    private Process createSynthesisReaction(IxnType ixn, ActorType actor, String processId)
    {
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                process = convertInteraction(subIxn);
                break;
            default:
                BiochemicalReaction biochemicalReaction = (BiochemicalReaction) model.getByID(absoluteUri(processId));
                if (biochemicalReaction == null) {
                    biochemicalReaction = create(BiochemicalReaction.class, processId);
                    setNameFromIxnType(ixn, biochemicalReaction, false);
                    SimplePhysicalEntity rightPar = createSPEFromActor(actor, false);
                    biochemicalReaction.addRight(rightPar);
                    biochemicalReaction.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
                    model.add(biochemicalReaction);
                }
                process = biochemicalReaction;
                break;
        }
        return process;
    }

    private Process createTransport(IxnType ixn, AxnCode axnCode, ActorType actor,
                                    String processId, String leftLoc, String rightLoc)
    {
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                process = convertInteraction(subIxn);
                break;
            default:
                Transport transport = (Transport) model.getByID(absoluteUri(processId));
                if (transport == null) {
                    transport = create(Transport.class, processId);
                    setNameFromIxnType(ixn, transport, false);
                    SimplePhysicalEntity leftPar = createSPEFromActor(actor, false);
                    SimplePhysicalEntity rightPar = createSPEFromActor(actor, true);
                    transport.addLeft(leftPar);
                    transport.addRight(rightPar);
                    transport.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
                    if (leftLoc != null) {
                        leftPar.setCellularLocation(createCellularLocation(leftLoc));
                    }
                    if (rightLoc != null) {
                        rightPar.setCellularLocation(createCellularLocation(rightLoc));
                    }
                    model.add(transport);
                }
                process = transport;
                break;
        }
        return process;
    }

    private CellularLocationVocabulary createCellularLocation(String location) {
        String locId = CtdUtil.locationToId(location);
        CellularLocationVocabulary cellularLocationVocabulary = (CellularLocationVocabulary) model.getByID(absoluteUri(locId));
        if(cellularLocationVocabulary == null) {
            cellularLocationVocabulary = create(CellularLocationVocabulary.class, locId);
            cellularLocationVocabulary.addTerm(location);
            model.add(cellularLocationVocabulary);
        }
        return cellularLocationVocabulary;
    }


    private Process createModificationReaction(IxnType ixn, AxnCode axnCode, ActorType actor,
                                               String processId, String leftTerm, String rightTerm)
    {
        Process process;
        final Actor a = CtdUtil.extractActor(actor);
        switch (a) {
            case IXN:
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                process = convertInteraction(subIxn);
                break;
            default:
                BiochemicalReaction biochemicalReaction = (BiochemicalReaction) model.getByID(absoluteUri(processId));
                if (biochemicalReaction == null) {
                    biochemicalReaction = create(BiochemicalReaction.class, processId);
                    setNameFromIxnType(ixn, biochemicalReaction, false);
                    SimplePhysicalEntity leftPar = createSPEFromActor(actor, leftTerm != null);
                    SimplePhysicalEntity rightPar = createSPEFromActor(actor, rightTerm != null);
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
                            leftPar.addFeature(createModFeature("modl_" + processId, leftTerm));
                        }

                        if (rightTerm != null) {
                            rightPar.addFeature(createModFeature("modr_" + processId, rightTerm));
                        }
                    }

                    model.add(biochemicalReaction);
                }
                process = biochemicalReaction;
                break;
        }

        return process;
    }

    private ModificationFeature createModFeature(String id, String term)
    {
        if(term!=null) id += "_" + term;
        ModificationFeature feature = create(ModificationFeature.class, id);
        SequenceModificationVocabulary modificationVocabulary = create(SequenceModificationVocabulary.class, "seqmod_" + id);
        modificationVocabulary.addTerm(term);
        feature.setModificationType(modificationVocabulary);
        model.add(feature);
        model.add(modificationVocabulary);
        return feature;
    }

    private Process createExpressionReaction(IxnType ixn, AxnCode axnCode, ActorType actor, String processId) {
        Process process;
        switch (CtdUtil.extractActor(actor)) {
            case IXN:
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                process = convertInteraction(subIxn);
                break;
            default:
                TemplateReaction templateReaction = (TemplateReaction) model.getByID(absoluteUri(processId));
                if(templateReaction==null) {
                    templateReaction = create(TemplateReaction.class, processId);
                    setNameFromIxnType(ixn, templateReaction, false);
                    templateReaction.setTemplateDirection(TemplateDirectionType.FORWARD); // Expression is always forward
                    SimplePhysicalEntity actorEntity = createSPEFromActor(actor, false);
                    templateReaction.addProduct(actorEntity);
                    model.add(templateReaction);
                }
                process = templateReaction;
                break;
        }
        return process;
    }

    //TODO: will use the axnCode parameter here and elsewhere in the future
    // (due to multiple axn elements per ixn/actor is possible...)
    private Control createControlFromActor(Process controlled, IxnType ixn, AxnCode axnCode, ActorType actor)
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

            for (Controller controller : createControllersFromActor(actor, controlled)) {
                control.addController(controller);
            }
            model.add(control);
        }
        return control;
    }

    // controlled process - when this actor is ixn and is inside an outer ixn/actor with e.g., 'csy' type
    // (controls synthesis, conversion), then the second parameter can be used to set left participant of that proc.
    private Collection<Controller> createControllersFromActor(ActorType actor, Process controlled) {
        HashSet<Controller> controllers = new HashSet<Controller>();
        switch (CtdUtil.extractActor(actor)) {
            case IXN:
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                AxnCode axnCode = CtdUtil.extractAxnCode(subIxn);
                Process process = convertInteraction(subIxn);

                for (PhysicalEntity pe : getProductsFromProcess(process)) {
                    controllers.add(pe);
                }

                if(process instanceof Control && controllers.isEmpty()) {
                    Control control = (Control) process;
                    controllers.addAll(control.getController());
                    for(Process p : new HashSet<Process>(control.getControlled())) {
                        if(p instanceof Conversion && axnCode == AxnCode.MET && controlled instanceof Conversion) {
                            control.removeControlled(p);
                            model.remove(p); //the outer process - controlled - is the one we keep
                            for(PhysicalEntity e : ((Conversion)p).getLeft()) {
                                ((Conversion) controlled).addLeft(e);
                            }
                        }
                    }
                    if(control.getControlled().isEmpty())
                        model.remove(control);
                }

                break;
            default: // If not an IXN, then it is a physical entity
                controllers.add(createSPEFromActor(actor, false));
                break;
        }

        return controllers;
    }

    private SimplePhysicalEntity createSPEFromActor(ActorType actor, boolean createNewInstance) {
        SimplePhysicalEntity spe;
        Actor aType = CtdUtil.extractActor(actor);
        switch (aType) {
            case CHEMICAL:
                spe = createEntityFromActor(actor, SmallMolecule.class, SmallMoleculeReference.class, createNewInstance);
                break;
            case GENE:
                String form = actor.getForm();
                GeneForm geneForm = (form == null)
                        ? GeneForm.PROTEIN
                            : GeneForm.valueOf(CtdUtil.sanitizeGeneForm(form).toUpperCase());

                Class<? extends SimplePhysicalEntity> eClass = geneForm.getEntityClass();
                Class<? extends EntityReference> refClass = geneForm.getReferenceClass();
                spe = createEntityFromActor(actor, eClass, refClass, createNewInstance);
                break;
            case IXN:
            default:
                throw new IllegalArgumentException("createSPEFromActor does not support " +
                        aType + " actor (nested ixn)");
        }
        return spe;
    }

//    private Collection<? extends Controller> createMultipleControllers(IxnType ixnType) {
//        HashSet<Controller> controllers = new HashSet<Controller>();
//        for (ActorType actorType : ixnType.getActor()) {
//            controllers.addAll(createControllersFromActor(actorType));
//        }
//        return controllers;
//    }

    private SimplePhysicalEntity createEntityFromActor(ActorType actorType,
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
        else { //never gets here ;)
            throw new IllegalArgumentException("getProductsFromProcess - impossible "
                    + process.getModelInterface().getSimpleName() + " " + process.getUri());
        }

        return products;
    }

}
