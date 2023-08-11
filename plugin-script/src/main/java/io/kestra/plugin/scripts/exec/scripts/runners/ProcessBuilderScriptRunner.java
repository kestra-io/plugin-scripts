package io.kestra.plugin.scripts.exec.scripts.runners;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

public class ProcessBuilderScriptRunner {
    public RunnerResult run(CommandsWrapper commands) throws Exception {
        Logger logger = commands.getRunContext().logger();
        Path workingDirectory = commands.getWorkingDirectory();
        AbstractLogConsumer defaultLogConsumer = commands.getLogConsumer();

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

        LogThread stdOut = new LogThread(process.getInputStream(), defaultLogConsumer, false);
        LogThread stdErr = new LogThread(process.getErrorStream(), defaultLogConsumer, true);

        stdOut.start();
        stdErr.start();

        try {
            int exitCode = process.waitFor();

            stdOut.join();
            stdErr.join();

            if (exitCode != 0) {
                throw new ScriptException(exitCode, defaultLogConsumer.getStdOutCount(), defaultLogConsumer.getStdErrCount());
            } else {
                logger.debug("Command succeed with code " + exitCode);
            }

            return new RunnerResult(exitCode, defaultLogConsumer);
        } catch (InterruptedException e) {
            logger.warn("Killing process {} for InterruptedException", pid);
            killDescendantsOf(process.toHandle(), logger);
            process.destroy();
            throw e;
        } finally {
            stdOut.join();
            stdErr.join();
        }
    }

    private void killDescendantsOf(ProcessHandle process, Logger logger) {
        process.descendants().forEach(processHandle -> {
            if (!processHandle.destroy()) {
                logger.warn("Descendant process {} of {} couldn't be killed", processHandle.pid(), process.pid());
            }
        });
    }

    public static class LogThread extends Thread {
        private final InputStream inputStream;

        private final AbstractLogConsumer logConsumerInterface;

        private final boolean isStdErr;

        protected LogThread(InputStream inputStream, AbstractLogConsumer logConsumerInterface, boolean isStdErr) {
            this.inputStream = inputStream;
            this.logConsumerInterface = logConsumerInterface;
            this.isStdErr = isStdErr;
        }

        @Override
        public void run() {
            try {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        this.logConsumerInterface.accept(line, this.isStdErr);
                    }
                }
            } catch (Exception e) {
                try {
                    this.logConsumerInterface.accept(e.getMessage(), true);
                } catch (Exception ex) {
                    // do nothing if we cannot send the error message to the log consumer
                }
            }
        }
    }
}
