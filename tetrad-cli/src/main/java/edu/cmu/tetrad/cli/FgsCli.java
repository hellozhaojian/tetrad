/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.cli;

import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.BigDataSetUtility;
import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Fast Greedy Search (FGS) Command-line Interface.
 *
 * Nov 30, 2015 9:18:55 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsCli {

    private static final String USAGE = "java -cp tetrad-cli.jar edu.cmu.tetrad.cli.FgsCli";

    private static final Options HELP_OPTIONS = new Options();
    private static final Options MAIN_OPTIONS = new Options();

    static {
        Option helpOption = new Option("h", "help", false, "Show help.");
        HELP_OPTIONS.addOption(helpOption);

        OptionGroup requiredOptionGroup = new OptionGroup();
        requiredOptionGroup.setRequired(true);
        requiredOptionGroup.addOption(new Option("d", "data", true, "Data file."));

        MAIN_OPTIONS.addOptionGroup(requiredOptionGroup);
        MAIN_OPTIONS.addOption("l", "delimiter", true, "Data file delimiter.");
        MAIN_OPTIONS.addOption("k", "depth", true, "Search depth.");
        MAIN_OPTIONS.addOption("v", "verbose", false, "Verbose message.");
        MAIN_OPTIONS.addOption("p", "penalty-discount", true, "Penalty discount.");
        MAIN_OPTIONS.addOption("n", "name", true, "Name of output file.");
        MAIN_OPTIONS.addOption("o", "dir-out", true, "Directory where results is written to.");
        MAIN_OPTIONS.addOption(helpOption);
    }

    private static Path dataFile;
    private static Path dirOut;
    private static char delimiter;
    private static Double penaltyDiscount;
    private static Integer depth;
    private static Boolean verbose;
    private static String fileOut;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            CommandLineParser cmdParser = new DefaultParser();
            CommandLine cmd = cmdParser.parse(HELP_OPTIONS, args, true);
            if (cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(USAGE, MAIN_OPTIONS, true);
                return;
            }

            cmd = cmdParser.parse(MAIN_OPTIONS, args);

            dataFile = Args.getPathFile(cmd.getOptionValue("d"));
            delimiter = Args.getCharacter(cmd.getOptionValue("l", "\t"));
            penaltyDiscount = Args.parseDouble(cmd.getOptionValue("p", "4.0"));
            depth = Args.parseInteger(cmd.getOptionValue("k", "3"));
            verbose = cmd.hasOption("v");
            dirOut = Args.getPathDir(cmd.getOptionValue("o", "./"), false);
            fileOut = cmd.getOptionValue("n", String.format("fgs_pd%1.2f_d%d_%d.txt", penaltyDiscount, depth, System.currentTimeMillis()));
        } catch (ParseException | FileNotFoundException exception) {
            System.err.println(exception.getMessage());
            return;
        }

        Path outputFile = Paths.get(dirOut.toString(), fileOut);
        try (PrintStream stream = new PrintStream(new BufferedOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE)))) {
            printOutParameters(stream);
            stream.flush();

            DataSet dataSet = BigDataSetUtility.readInContinuousData(dataFile.toFile(), delimiter);

            Fgs fgs = new Fgs(new CovarianceMatrixOnTheFly(dataSet));
            fgs.setOut(stream);
            fgs.setDepth(depth);
            fgs.setPenaltyDiscount(penaltyDiscount);
            fgs.setNumPatternsToStore(0);  // always set to zero
            fgs.setFaithfulnessAssumed(true);
            fgs.setVerbose(verbose);
            stream.flush();

            Graph graph = fgs.search();
            stream.println();
            stream.println(graph.toString().trim());
            stream.flush();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    private static void printOutParameters(PrintStream stream) {
        stream.println("Datasets:");
        stream.println(dataFile.getFileName().toString());
        stream.println();

        stream.println("Graph Parameters:");
        stream.println(String.format("penalty discount = %f", penaltyDiscount));
        stream.println(String.format("depth = %s", depth));
        stream.println();
    }

}
