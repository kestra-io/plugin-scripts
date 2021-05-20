package io.kestra.plugin.scripts;

import io.kestra.core.runners.RunContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.function.Supplier;
import javax.script.*;

public abstract class ScriptEngineService {
    public static CompiledScript scripts(RunContext runContext, String engineName, String script, ClassLoader classLoader) throws ScriptException {
        Logger logger = runContext.logger();

        ScriptEngineManager manager = new ScriptEngineManager(classLoader);
        ScriptEngine engine = manager.getEngineByName(engineName);

        // generate a map with with common vars to fill bindings supplier in case of concurrency
        HashMap<String, Object> map = new HashMap<>();

        runContext
            .getVariables()
            .forEach(map::put);
        map.put("runContext", runContext);
        map.put("logger", logger);

        return new CompiledScript(engine, ((Compilable) engine).compile(script), () -> {
            Bindings bindings = engine.createBindings();
            bindings.putAll(map);

            return bindings;
        });
    }

    @Getter
    @AllArgsConstructor
    static class CompiledScript {
        private final ScriptEngine engine;
        private final javax.script.CompiledScript script;
        private final Supplier<Bindings> bindings;
    }
}
