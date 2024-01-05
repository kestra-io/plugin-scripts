package io.kestra.plugin.scripts.exec.scripts.runners;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public abstract class AbstractLogConsumer implements BiConsumer<String, Boolean> {
    protected final AtomicInteger stdOutCount = new AtomicInteger();

    protected final AtomicInteger stdErrCount = new AtomicInteger();

    @Getter
    protected final Map<String, Object> outputs = new ConcurrentHashMap<>();

    public int getStdOutCount() {
        return this.stdOutCount.get();
    }

    public int getStdErrCount() {
        return this.stdErrCount.get();
    }
}
