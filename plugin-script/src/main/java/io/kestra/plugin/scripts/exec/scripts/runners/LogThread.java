package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.services.LogService;
import org.slf4j.Logger;

import java.io.InputStream;

public class LogThread extends AbstractLogThread {
    private final Logger logger;
    private final boolean isStdErr;
    private final RunContext runContext;

    public LogThread(InputStream inputStream, boolean isStdErr, RunContext runContext) {
        super(inputStream);

        this.logger = runContext.logger();
        this.isStdErr = isStdErr;
        this.runContext = runContext;
    }

    public LogThread(Logger logger, InputStream inputStream, boolean isStdErr, RunContext runContext) {
        super(inputStream);

        this.logger = logger;
        this.isStdErr = isStdErr;
        this.runContext = runContext;
    }

    protected void call(String line) {
        outputs.putAll(LogService.parse(line, logger, runContext));

        if (isStdErr) {
            logger.warn(line);
        } else {
            logger.info(line);
        }
    }
}
