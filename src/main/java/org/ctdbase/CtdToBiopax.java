package org.ctdbase;

import org.biopax.paxtools.model.level3.UtilityClass;
import org.ctdbase.converter.CTDChemicalConverter;
import org.ctdbase.converter.CTDGeneConverter;
import org.ctdbase.converter.CTDInteractionConverter;
import org.ctdbase.converter.Converter;
import org.apache.commons.cli.*;
import org.biopax.paxtools.controller.Merger;
import org.biopax.paxtools.controller.ModelUtils;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.EntityReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

public class CtdToBiopax {
    private static Logger log = LoggerFactory.getLogger(CtdToBiopax.class);
    private static final String helpText = CtdToBiopax.class.getSimpleName();

    public static void main( String[] args ) {
        final CommandLineParser clParser = new GnuParser();
        Options gnuOptions = new Options();
        gnuOptions
                .addOption("x", "interaction", true, "structured chemical-gene interaction file (XML) [optional]")
                .addOption("g", "gene", true, "CTD gene vocabulary (CSV) [optional]")
                .addOption("c", "chemical", true, "CTD chemical vocabulary (CSV) [optional]")
                .addOption("o", "output", true, "Output (BioPAX file) [required]")
                .addOption("t", "taxonomy", true, "Taxonomy (e.g. '9606' for human) [optional]")
                .addOption("r", "remove-dangling", false,
                    "Remove dangling UtilityClass objects from final model [optional; recommended when using options: -x -t]")
        ;

        try {
            CommandLine commandLine = clParser.parse(gnuOptions, args);

            // Interaction file and output file name are required!
            if(!commandLine.hasOption("o")) {
                HelpFormatter helpFormatter = new HelpFormatter();
                helpFormatter.printHelp(helpText, gnuOptions);
                System.exit(-1);
            }

            SimpleIOHandler simpleIOHandler = new SimpleIOHandler();
            Merger merger = new Merger(simpleIOHandler.getEditorMap());
            Model finalModel = simpleIOHandler.getFactory().createModel();

            // First convert the interactions
            if(commandLine.hasOption("x")) {
                String fileName = commandLine.getOptionValue("x");
                String taxonomy = null;
                if(commandLine.hasOption("t")) {
                    taxonomy = commandLine.getOptionValue("t");
                    log.info("Will do only interactions with taxonomy: " + taxonomy);
                }
                Converter converter = new CTDInteractionConverter(taxonomy);
                log.info("Option 'x'. Using " + converter.getClass().getSimpleName() + " to convert: " + fileName);
                Model model = converter.convert(new FileInputStream(fileName));
                merger.merge(finalModel, model);
            }

            if(commandLine.hasOption("g")) {
                String fileName = commandLine.getOptionValue("g");
                Converter converter = new CTDGeneConverter();
                log.info("Option 'g'. Using " + converter.getClass().getSimpleName() + " to convert: " + fileName);
                Model model = converter.convert(new FileInputStream(fileName));
                merger.merge(finalModel, model);
            }

            if(commandLine.hasOption("c")) {
                String fileName = commandLine.getOptionValue("c");
                Converter converter = new CTDChemicalConverter();
                log.info("Option 'c'. Using " + converter.getClass().getSimpleName() + " to convert: " + fileName);
                Model model = converter.convert(new FileInputStream(fileName));
                merger.merge(finalModel, model);
            }

            if(commandLine.hasOption("r")) {
                Set<BioPAXElement> removed = ModelUtils.removeObjectsIfDangling(finalModel, UtilityClass.class);
                log.info("Removed " + removed.size() + " dangling UtilityClass objects from the model.");
            }

            finalModel.setXmlBase(Converter.sharedXMLBase);
            String outputFile = commandLine.getOptionValue("o");
            log.info("Done with the conversions. Converting the final model to OWL: " + outputFile);
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            simpleIOHandler.convertToOWL(finalModel, outputStream);

            log.info("All done.");
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(helpText, gnuOptions);
            System.exit(-1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
