package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.scripts.services.LogService;

public class DefaultLogConsumer extends AbstractLogConsumer {
    private final RunContext runContext;

    public DefaultLogConsumer(RunContext runContext) {
        this.runContext = runContext;
    }

    @Override
    public void accept(String line, Boolean isStdErr) throws Exception {
        outputs.putAll(LogService.parse(line, runContext.logger(), runContext));

        if (isStdErr) {
            this.stdErrCount.incrementAndGet();
            runContext.logger().warn(line);
        } else {
            this.stdOutCount.incrementAndGet();
            runContext.logger().info(line);
        }
    }
}
