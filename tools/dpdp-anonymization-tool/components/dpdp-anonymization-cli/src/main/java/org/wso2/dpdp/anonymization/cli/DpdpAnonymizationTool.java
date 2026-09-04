/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.anonymization.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.wso2.dpdp.anonymization.config.ToolConfig;
import org.wso2.dpdp.anonymization.config.ToolConfigLoader;
import org.wso2.dpdp.anonymization.database.DriverManagerConnectionFactory;
import org.wso2.dpdp.anonymization.model.AnonymizationRequest;
import org.wso2.dpdp.anonymization.model.AnonymizationResult;
import org.wso2.dpdp.anonymization.model.AnonymizationStatus;
import org.wso2.dpdp.anonymization.model.ExecutionMode;
import org.wso2.dpdp.anonymization.processor.DpdpAnonymizationProcessor;
import org.wso2.dpdp.anonymization.report.ReportWriter;
import org.wso2.dpdp.anonymization.validation.AnonymizationException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DpdpAnonymizationTool {

    private DpdpAnonymizationTool() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Options options = options();
        File configFile = null;
        ToolConfig config = null;
        AnonymizationRequest request = null;
        try {
            CommandLine command = new DefaultParser().parse(options, args);
            if (command.hasOption("help")) {
                printHelp(options, out);
                return ExitCode.SUCCESS;
            }
            require(command, "tenant-domain");
            require(command, "user-id");
            require(command, "pseudonym");

            configFile = new File(command.getOptionValue("config", "conf/config.json"));
            config = ToolConfigLoader.load(configFile);
            Set<String> usernames = command.hasOption("username")
                    ? new LinkedHashSet<>(Arrays.asList(command.getOptionValues("username")))
                    : Collections.<String>emptySet();
            request = new AnonymizationRequest(
                    command.getOptionValue("tenant-domain"),
                    command.getOptionValue("user-id"),
                    command.getOptionValue("pseudonym"),
                    usernames,
                    command.hasOption("execute") ? ExecutionMode.EXECUTE : ExecutionMode.DRY_RUN);

            DpdpAnonymizationProcessor processor = new DpdpAnonymizationProcessor(
                    new DriverManagerConnectionFactory(config.getDatabase()), config);
            AnonymizationResult result = processor.process(request);
            File reportDirectory = resolveReportDirectory(configFile, config.getReportDirectory());
            File report = new ReportWriter().write(reportDirectory, request, result);
            printSummary(out, request, result);
            out.println("Report: " + report.getAbsolutePath());
            if (!command.hasOption("execute")) {
                out.println("No database changes were committed. Re-run with --execute to apply them.");
            }
            return ExitCode.SUCCESS;
        } catch (ParseException e) {
            err.println("Invalid arguments: " + e.getMessage());
            printHelp(options, err);
            return ExitCode.INVALID_ARGUMENTS;
        } catch (AnonymizationException e) {
            err.println("Anonymization failed: " + e.getMessage());
            writeFailureReport(configFile, config, request, e.getMessage(), err);
            return ExitCode.EXECUTION_ERROR;
        } catch (IOException e) {
            err.println("Could not write the anonymization report: " + e.getMessage());
            return ExitCode.EXECUTION_ERROR;
        }
    }

    private static void printSummary(PrintStream out, AnonymizationRequest request, AnonymizationResult result) {
        out.println("Result: " + result.getStatus());
        out.println("Tenant: " + request.getTenantDomain());
        out.println("Source user ID: " + request.getSourceUserId());
        if (request.getExplicitUsernames().isEmpty()) {
            out.println("Supplied aliases: none");
        } else {
            out.println("Supplied aliases: " + String.join(", ", request.getExplicitUsernames()));
        }
        if (result.getTrustedUsernames().isEmpty()) {
            out.println("Trusted aliases considered: none");
        } else {
            out.println("Trusted aliases considered: " + String.join(", ", result.getTrustedUsernames()));
        }
        out.println("Discovered aliases: " + result.getDiscoveredAliasCount());
        if (result.getCounts().isEmpty()) {
            out.println("Matches: none");
        } else {
            out.println("Matches:");
            for (Map.Entry<String, Long> entry : result.getCounts().entrySet()) {
                out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    private static void writeFailureReport(File configFile, ToolConfig config, AnonymizationRequest request,
                                           String failureMessage, PrintStream err) {
        if (configFile == null || config == null || request == null) {
            return;
        }
        AnonymizationResult failure = new AnonymizationResult();
        failure.setStatus(AnonymizationStatus.FAILED);
        try {
            File report = new ReportWriter().writeFailure(
                    resolveReportDirectory(configFile, config.getReportDirectory()), request, failure, failureMessage);
            err.println("Failure report: " + report.getAbsolutePath());
        } catch (IOException reportError) {
            err.println("Could not write failure report: " + reportError.getMessage());
        }
    }

    private static File resolveReportDirectory(File configFile, String path) {
        File directory = new File(path == null ? "reports" : path);
        return directory.isAbsolute() ? directory : new File(configFile.getAbsoluteFile().getParentFile(), directory.getPath());
    }

    private static void require(CommandLine command, String option) throws ParseException {
        if (!command.hasOption(option) || command.getOptionValue(option).trim().isEmpty()) {
            throw new ParseException("Missing required option: --" + option);
        }
    }

    private static Options options() {
        Options options = new Options();
        options.addOption(Option.builder().longOpt("config").hasArg().argName("file")
                .desc("Configuration file (default: conf/config.json)").build());
        options.addOption(Option.builder().longOpt("tenant-domain").hasArg().argName("tenant")
                .desc("Tenant domain mapped to ORG_ID (required)").build());
        options.addOption(Option.builder().longOpt("user-id").hasArg().argName("uuid")
                .desc("Canonical source user UUID (required)").build());
        options.addOption(Option.builder().longOpt("pseudonym").hasArg().argName("uuid")
                .desc("Canonical replacement UUID (required)").build());
        options.addOption(Option.builder().longOpt("username").hasArg().argName("alias")
                .desc("Trusted username alias; may be supplied multiple times").build());
        options.addOption(Option.builder().longOpt("execute")
                .desc("Commit changes; without this flag the command is a dry-run").build());
        options.addOption(Option.builder("h").longOpt("help").desc("Show help").build());
        return options;
    }

    private static void printHelp(Options options, PrintStream stream) {
        new HelpFormatter().printHelp(new java.io.PrintWriter(stream, true), 110, "dpdp-anonymize", null,
                options, 2, 4, null, true);
    }
}
