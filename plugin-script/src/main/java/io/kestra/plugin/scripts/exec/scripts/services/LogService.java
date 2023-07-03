package io.kestra.plugin.scripts.exec.scripts.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutputFormat;
import io.kestra.plugin.scripts.exec.scripts.runners.LogSupplierInterface;
import io.kestra.plugin.scripts.exec.scripts.runners.LogThread;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogService {
    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private static final Pattern PATTERN = Pattern.compile("^::(\\{.*})::$");

    public static Map<String, Object> parse(String line, Logger logger, RunContext runContext)  {
        Matcher m = PATTERN.matcher(line);
        Map<String, Object> outputs = new HashMap<>();

        if (m.find()) {
            try {
                ScriptOutputFormat<?> scriptOutputFormat = MAPPER.readValue(m.group(1), ScriptOutputFormat.class);

                if (scriptOutputFormat.getOutputs() != null) {
                    outputs.putAll(scriptOutputFormat.getOutputs());
                }

                if (scriptOutputFormat.getMetrics() != null) {
                    scriptOutputFormat.getMetrics().forEach(runContext::metric);
                }
            }
            catch (JsonProcessingException e) {
                logger.warn("Invalid outputs '{}'", e.getMessage(), e);
            }
        }

        return outputs;
    }

    public static LogSupplierInterface defaultLogSupplier(RunContext runContext) {
        return (inputStream, isStdErr) -> {
            LogThread thread = new LogThread(inputStream, isStdErr, runContext);

            thread.setName("bash-log-" + (isStdErr ? "-err" : "-out"));
            thread.start();

            return thread;
        };
    }
}
