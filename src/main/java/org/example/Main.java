package org.example;

import org.example.cli.IndexCommand;
import org.example.cli.ListIndicesCommand;
import org.example.cli.QueryCommand;
import org.example.cli.ServeCommand;
import org.example.cli.StatusCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main entry point for the Codebase Knowledge Graph CLI.
 */
@Command(name = "codebase-graph", mixinStandardHelpOptions = true, version = "1.0-SNAPSHOT",
    description = "A knowledge graph that indexes repositories, resolves symbols, and exposes dependency relationships.",
    subcommands = {
        IndexCommand.class,
        ListIndicesCommand.class,
        QueryCommand.class,
        StatusCommand.class,
        ServeCommand.class
    })
public class Main implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
