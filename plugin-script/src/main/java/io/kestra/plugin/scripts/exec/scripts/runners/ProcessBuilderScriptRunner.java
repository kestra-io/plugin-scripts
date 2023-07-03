package io.kestra.plugin.scripts.exec.scripts.runners;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;

public class ProcessBuilderScriptRunner {
    public RunnerResult run(Commands commands) throws Exception {
        Logger logger = commands.getRunContext().logger();
        Path workingDirectory = commands.getWorkingDirectory();

        ProcessBuilder processBuilder = new ProcessBuilder();

        if (commands.getEnv() != null && !commands.getEnv().isEmpty()) {
            Map<String, String> environment = processBuilder.environment();
            environment.putAll(commands.getEnv());
        }

        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        processBuilder.command(commands.getCommands());

        Process process = processBuilder.start();
        long pid = process.pid();
        logger.debug("Starting command with pid {} [{}]", pid, String.join(" ", commands.getCommands()));

        try {
            // logs
            AbstractLogThread stdOut = commands.getLogSupplier().call(process.getInputStream(), false);
            AbstractLogThread stdErr = commands.getLogSupplier().call(process.getErrorStream(), true);

            int exitCode = process.waitFor();

            stdOut.join();
            stdErr.join();

            if (exitCode != 0) {
                throw new BashException(exitCode, stdOut.getLogsCount(), stdErr.getLogsCount());
            } else {
                logger.debug("Command succeed with code " + exitCode);
            }

            return new RunnerResult(exitCode, stdOut, stdErr);
        } catch (InterruptedException e) {
            logger.warn("Killing process {} for InterruptedException", pid);
            killDescendantsOf(process.toHandle(), logger);
            process.destroy();
            throw e;
        }
    }

    private void killDescendantsOf(ProcessHandle process, Logger logger) {
        process.descendants().forEach(processHandle -> {
            if (!processHandle.destroy()) {
                logger.warn("Descendant process {} of {} couldn't be killed", processHandle.pid(), process.pid());
            }
        });
    }
}
