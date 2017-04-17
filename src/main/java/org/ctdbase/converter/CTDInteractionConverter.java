package org.ctdbase.converter;

import org.apache.commons.lang3.StringUtils;
import org.ctdbase.util.CtdUtil;
import org.ctdbase.util.model.*;
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
                convertIxn(ixn);
            }
        } catch (JAXBException e) {
            log.error("Could not initialize the JAXB Reader. ", e);
        }
        return model;
    }

    private Interaction convertIxn(IxnType ixn)
    {
        // the first actor is to make a Control process
        List<ActorType> actors = ixn.getActor();
        AxnCode axnCode = CtdUtil.axnCode(ixn);
        if(actors.size() < 2) {
            log.error("Ignored ixn:" + ixn.getId() + ", which has < 2 actors (violates the CTD XML schema)");
            return null;
        } else if(actors.size() > 2 && axnCode != AxnCode.W && axnCode != AxnCode.B) {
            log.warn(String.format("Ixn %s has %d actors, but we convert only two...",
                    ixn.getId(), actors.size()));
        }
        if(ixn.getAxn().size() > 1) {
            log.warn(String.format("IXN #%d has more than one axn", ixn.getId()));
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

        // Create the interaction object from the ixn's second actor
        Interaction process = createInteraction(ixn);
        if(process==null) {
            log.warn("Skipped - failed to generate a sub-process from the second actor");
            return null;
        }

        // Create a Contol (from the first actor) unless the process is binding or co-treatment
        if(!axnCode.equals(AxnCode.B) && !axnCode.equals(AxnCode.W)) {
            Control control = createControlFromActor(process, ixn);
            control.addControlled(process);
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

    private ControlType controlTypeAction(AxnType action, AxnCode axnCode) {
        if(action!=null && action.getDegreecode()!=null) {
            switch (action.getDegreecode().charAt(0)) {
                case '+':
                    return (axnCode!=AxnCode.STA) ? ControlType.ACTIVATION : ControlType.INHIBITION;
                case '-':
                    return (axnCode!=AxnCode.STA) ? ControlType.INHIBITION : ControlType.ACTIVATION;
                case '1': //affects
                case '0': //does not affect
                default:
                    break;
            }
        }
        return null;
    }

    private Interaction createInteraction(IxnType ixn)
    {
        AxnCode axnCode = CtdUtil.axnCode(ixn);
        ActorType actor = ixn.getActor().get(1); //all the create* methods here use this actor too

        String processRdfId;
        if(axnCode == AxnCode.B || axnCode == AxnCode.W)
            processRdfId = String.format("%s_%s", axnCode, ixn.getId());
        else
            processRdfId =  String.format("%s_%s", axnCode, CtdUtil.sanitizeId(actor.getId()));

        Interaction process = (Interaction) model.getByID(absoluteUri(processRdfId));
        if(process != null) {
            log.info("using existing " + process.getModelInterface().getSimpleName() + ": " + processRdfId);
            return process;
        }

        //a shortcut when the actor has 'ixn' type (nested processes),
        // except for binding -
        if((CtdUtil.extractActor(actor)==Actor.IXN && axnCode != AxnCode.B && axnCode != AxnCode.W)
                || axnCode == AxnCode.RXN)
        {
            try {
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                process = convertIxn(subIxn);
                if(process instanceof Control)
                    process.addComment(axnCode.getDescription());
                return process;
            } catch (Exception e) {
                log.error("Skipped due to error: " + e);
                return null;
            }
        }

        switch (axnCode) {
            case EXP:
                process = createTemplateReaction(ixn, processRdfId);
                break;
            case B: //complex or binding
                process = createBindingReaction(ixn, processRdfId);
                break;
            case W:   // co-treatment effect
            case REC: // response to substance
            case ACT: // activity
                process = createBlackboxControl(ixn, processRdfId);
                break;
            case ABU: // abundance
            case CSY: // synthesis
                process = createConversion(ixn, processRdfId,false,true);
                break;
            case MET: // metabolism
                process = createConversion(ixn, processRdfId, true, false);
                break;
            case MUT: // mutation
            case SPL: // splicing
            case CLV: // cleavage
            case FOL: // folding
                process = createConversion(ixn, processRdfId, true, true);
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
            case DEG:
            case HYD:
            case STA: // stability (stable - inhibited degradation; unstable - activated degradation)
                process = createDegradation(ixn, processRdfId);
                break;
            case RXN: // Reaction
                //already done
                throw new IllegalStateException("Should not get here...; ixn:" + ixn.getId());
            case EXT:
            case SEC:
                process = createTransport(ixn, processRdfId, null, "extracellular matrix");
                break;
            case UPT:
            case IMT:
                process = createTransport(ixn, processRdfId, "extracellular matrix", null);
                break;
            case TRT: // transport
            case LOC: // localization
                process = createTransport(ixn, processRdfId, null, null);
                break;
            default:
                log.error(String.format("Ignored ixn:%s having axn code:%s - mapping is not implemented yet",
                        ixn.getId(), axnCode));
                break;
        }

        return process;
    }

    // Converts an ixn (axn code='b' of course) to a complex assembly process
    private Interaction createBindingReaction(IxnType ixn, String processId) {
        List<ActorType> actors = ixn.getActor();
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
                    PhysicalEntity pe = createSPEFromActor(actor, null);
                    complex.addComponent(pe);
                    complexAssembly.addLeft(pe);
                    nameBuilder.append(pe.getDisplayName()).append("/");
                } else {
                    //IXN : create sub-process(es) and collect their products to use as complex components here
                    IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                    AxnCode subAxn = CtdUtil.axnCode(subIxn);
                    if(subAxn == AxnCode.W) {
                        //TODO: unsure what does axn code 'w' (co-treatment) mean inside an ixn actor of a 'b' parent ..
                        for (ActorType actorType : subIxn.getActor()) {
                            PhysicalEntity pe = createSPEFromActor(actorType, null);
                            complex.addComponent(pe);
                            complexAssembly.addLeft(pe);
                            nameBuilder.append(pe.getDisplayName()).append("/");
                        }
                    }
                    else {
                        Interaction proc = convertIxn(subIxn);
                        for(PhysicalEntity pe : getProducts(proc)) {
                            complex.addComponent(pe);
                            complexAssembly.addLeft(pe);
                            nameBuilder.append(pe.getDisplayName()).append("/");
                        }
                        if(complex.getComponent().isEmpty() && proc instanceof Control) {
                            for(Process controlled : ((Control)proc).getControlled()) {
                                if(controlled instanceof Control) {
                                    for(Controller c: ((Control)controlled).getController()) {
                                        complexAssembly.addLeft((PhysicalEntity) c);//ok to cast here
                                        complex.addComponent((PhysicalEntity) c);//ok to cast here
                                        nameBuilder.append(c.getDisplayName()).append("/");
                                    }
                                }
                            }
                        }
                    }
                }
            }
            String name = nameBuilder.toString().substring(0, nameBuilder.length()-1);
            complex.setDisplayName((name.endsWith("complex"))?name:name+" complex");
        }
        return complexAssembly;
    }

    private Interaction createBlackboxControl(IxnType ixn, String rdfId) {
        Control control = (Control) model.getByID(absoluteUri(rdfId));
        if (control == null)
        {
            AxnCode axnCode = CtdUtil.axnCode(ixn);
            if (axnCode == AxnCode.ACT) {
                control = create(Control.class, rdfId);
                control.setControlType(ControlType.ACTIVATION);
                // - always activation (this sill can be inhibited by outer control);
                // - could use Catalysis instead of Control, but then we can only set Conversion to 'controlled'
                // property of this one, and there are examples where we want to set a Control/Modulation as well.
                // see ixn id="3727084".
            } else {
                control = create(Modulation.class, rdfId);
            }

            control.addComment(axnCode.getDescription());
            model.add(control);

            if(axnCode==AxnCode.W) {
                setNameFromIxnType(ixn, control, true);
                for(ActorType actor : ixn.getActor()) {
                    for (Controller c : createControllersFromActor(actor, null)) {
                        control.addController(c);
                    }
                }
            } else {
                setNameFromIxnType(ixn, control, false);
                ActorType actor = ixn.getActor().get(1);
                for (Controller c : createControllersFromActor(actor, null)) {
                    control.addController(c);
                }
            }
        }
        return control;
    }

    private Interaction createDegradation(IxnType ixn, String processId)
    {
        ActorType actor = ixn.getActor().get(1);
        Degradation degradation = (Degradation) model.getByID(absoluteUri(processId));
        if (degradation == null) {
            degradation = create(Degradation.class, processId);
            setNameFromIxnType(ixn, degradation, false);
            SimplePhysicalEntity par = createSPEFromActor(actor, null);
            degradation.addLeft(par);
            degradation.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            model.add(degradation);
        }
        return degradation;
    }

    private Interaction createTransport(IxnType ixn, String processId, String leftLoc, String rightLoc)
    {
        ActorType actor = ixn.getActor().get(1);;
        Transport transport = (Transport) model.getByID(absoluteUri(processId));
        if (transport == null) {
            transport = create(Transport.class, processId);
            SimplePhysicalEntity leftPar = createSPEFromActor(actor, leftLoc);
            SimplePhysicalEntity rightPar = createSPEFromActor(actor, rightLoc);
            transport.addLeft(leftPar);
            transport.addRight(rightPar);
            transport.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            if (leftLoc != null) {
                leftPar.setCellularLocation(createCellularLocation(leftLoc));
            }
            if (rightLoc != null) {
                rightPar.setCellularLocation(createCellularLocation(rightLoc));
            }
            setNameFromIxnType(ixn, transport, false);
            model.add(transport);
        }
        return transport;
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


    private Interaction createConversion(IxnType ixn, String processId, boolean useLeft, boolean useRight)
    {
        ActorType actor = ixn.getActor().get(1);
        AxnCode axnCode = CtdUtil.axnCode(ixn);
        String term;
        switch (axnCode) {
            case ABU: // abundance
            case MET: // metabolism
            case CSY: // synthesis
                term = null;
                break;
            case MUT: //mutation
                term = "mutated";
                break;
            case SPL: // splicing
                term = "spliced";
                break;
            case CLV: //cleavage
                term = "cleaved";
                break;
            case FOL: // folding
                term = "folded";
                break;
            default://never
                throw new IllegalArgumentException("createModificationReaction, ixn:" +
                        ixn.getId() + ", unsupported axn code:" + axnCode);
        }
        BiochemicalReaction biochemicalReaction = (BiochemicalReaction) model.getByID(absoluteUri(processId));
        if (biochemicalReaction == null) {
            biochemicalReaction = create(BiochemicalReaction.class, processId);
            setNameFromIxnType(ixn, biochemicalReaction, false);
            if(useLeft) {
                SimplePhysicalEntity leftPar = createSPEFromActor(actor, null);
                biochemicalReaction.addLeft(leftPar);
            }
            if(useRight) {
                SimplePhysicalEntity rightPar = createSPEFromActor(actor, term);
                biochemicalReaction.addRight(rightPar);
                if (term != null) {
                    if (CtdUtil.extractActor(actor).equals(Actor.CHEMICAL)) {
                        rightPar.setDisplayName(rightPar.getDisplayName() + " (" + term + ")");
                    } else {
                        ModificationFeature mf = createModFeature("modf_" + processId, term);
                        rightPar.addFeature(mf);
                    }
                }
            }
            biochemicalReaction.setConversionDirection(ConversionDirectionType.LEFT_TO_RIGHT);
            model.add(biochemicalReaction);
        }
        return biochemicalReaction;
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

    private Interaction createTemplateReaction(IxnType ixn, String processId) {
        ActorType actor = ixn.getActor().get(1);
        TemplateReaction templateReaction = (TemplateReaction) model.getByID(absoluteUri(processId));
        if(templateReaction==null) {
            templateReaction = create(TemplateReaction.class, processId);
            setNameFromIxnType(ixn, templateReaction, false);
            templateReaction.setTemplateDirection(TemplateDirectionType.FORWARD);
            SimplePhysicalEntity actorEntity = createSPEFromActor(actor, null);
            templateReaction.addProduct(actorEntity);
            model.add(templateReaction);
        }
        return templateReaction;
    }

    private Control createControlFromActor(Interaction controlled, IxnType ixn)
    {
        AxnCode axnCode = CtdUtil.axnCode(ixn);
        AxnType axnType = CtdUtil.axnType(ixn);
        ActorType actor = ixn.getActor().get(0);

        String rdfId = String.format("%s_%s", axnCode, ixn.getId());
        Control control = (Control) model.getByID(absoluteUri(rdfId));
        if(control == null) {
            ControlType controlType = controlTypeAction(axnType, axnCode);
            Collection<Controller> controllers = createControllersFromActor(actor, controlled);

            if (controlled instanceof TemplateReaction) {
                control = create(TemplateReactionRegulation.class, rdfId);
            }
            else if(controlled instanceof Catalysis && controllers.size()==1
                    && controllers.iterator().next() instanceof SmallMolecule)
            {
                control = create(Modulation.class, rdfId);
            }
            else {
                //try Catalysis if BioPAX restrictions are satisfied
                if(controlType == ControlType.ACTIVATION && controlled instanceof Conversion)
                    control = create(Catalysis.class, rdfId);
                else
                    control = create(Control.class, rdfId);
            }

            model.add(control);
            control.setControlType(controlType);
            setNameFromIxnType(ixn, control, true);

            for (Controller controller : controllers) {
                control.addController(controller);
            }
        }
        return control;
    }

    // controlled process - when this actor is ixn and is inside an outer ixn/actor with e.g., 'csy' type
    // (controls synthesis, conversion), then the second parameter can be used to set left participant of that proc.
    private Collection<Controller> createControllersFromActor(ActorType actor, Interaction controlled) {
        HashSet<Controller> controllers = new HashSet<Controller>();
        switch (CtdUtil.extractActor(actor)) {
            case IXN:
                IxnType subIxn = CtdUtil.convertActorToIxn(actor);
                AxnCode axnCode = CtdUtil.axnCode(subIxn);
                Interaction process = convertIxn(subIxn);
                log.debug(String.format("createControllersFromActor; actor ixn:%s, parent:%s, " +
                        "axn:%s, sub-process:%s (%s)", subIxn.getId(), actor.getParentid(), axnCode,
                        process.getUri(), process.getModelInterface().getSimpleName()));
                for (PhysicalEntity pe : getProducts(process)) {
                    controllers.add(pe);
                }

                if(process instanceof Control && controllers.isEmpty()) {
                    Control control = (Control) process;
                    controllers.addAll(control.getController());
                    for(Process p : new HashSet<Process>(control.getControlled())) {
                        if(p instanceof Conversion && axnCode == AxnCode.MET && controlled instanceof Conversion) {
                            control.removeControlled(p);
                            for(PhysicalEntity e : ((Conversion)p).getLeft()) {
                                ((Conversion) controlled).addLeft(e);
                            }
                        } else if(axnCode == AxnCode.ACT && p instanceof Control) {
                            ((Control) p).addControlled(controlled);
                        }
                    }
                    if(control.getControlled().isEmpty())
                        model.remove(control);
                }

                break;
            default: // If not an IXN, then it is a physical entity
                controllers.add(createSPEFromActor(actor, null));
                break;
        }

        return controllers;
    }

    private SimplePhysicalEntity createSPEFromActor(ActorType actor, String state) {
        SimplePhysicalEntity spe;
        Actor aType = CtdUtil.extractActor(actor);
        switch (aType) {
            case CHEMICAL:
                spe = createEntityFromActor(actor, SmallMolecule.class, SmallMoleculeReference.class, state);
                break;
            case GENE:
                String form = actor.getForm();
                GeneForm geneForm = (form == null)
                        ? GeneForm.PROTEIN
                            : GeneForm.valueOf(CtdUtil.sanitizeGeneForm(form).toUpperCase());

                Class<? extends SimplePhysicalEntity> eClass = geneForm.getEntityClass();
                Class<? extends EntityReference> refClass = geneForm.getReferenceClass();
                spe = createEntityFromActor(actor, eClass, refClass, state);
                break;
            case IXN:
            default:
                throw new IllegalArgumentException("createSPEFromActor does not support " +
                        aType + " actor (nested ixn)");
        }
        return spe;
    }

    private SimplePhysicalEntity createEntityFromActor(ActorType actorType,
                                                       Class<? extends SimplePhysicalEntity> entityClass,
                                                       Class<? extends EntityReference> referenceClass,
                                                       String state)
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
                + (StringUtils.isEmpty(state) ? "" : "_" + state.toLowerCase()));

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
            if(named instanceof Interaction)
                named.addName(name.trim());
            else
                named.setDisplayName(name.trim());
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

    private static Set<PhysicalEntity> getProducts(Process process) {
        Set<PhysicalEntity> products = new HashSet<PhysicalEntity>();
        if(process instanceof Control) {
            for (Process controlled : ((Control) process).getControlled()) {
                products.addAll(getProducts(controlled));
            }
        }
        else if (process instanceof Conversion) {
            products.addAll(((Conversion)process).getRight());
        }
        else if (process instanceof TemplateReaction) {
            products.addAll(((TemplateReaction)process).getProduct());
        }
        else { //never gets here ;)
            throw new IllegalArgumentException("getProducts - impossible "
                    + process.getModelInterface().getSimpleName() + " " + process.getUri());
        }

        return products;
    }

}
