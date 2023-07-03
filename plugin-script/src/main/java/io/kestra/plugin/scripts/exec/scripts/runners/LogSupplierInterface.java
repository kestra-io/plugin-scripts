package io.kestra.plugin.scripts.exec.scripts.runners;

import java.io.InputStream;

@FunctionalInterface
public interface LogSupplierInterface {
    AbstractLogThread call(InputStream inputStream, boolean isStdErr) throws Exception;
}
