package io.kestra.plugin.scripts.jvm;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute JVM script",
    description = "Deprecated; use GraalVM Eval tasks instead (`io.kestra.plugin.graalvm.js|python|ruby.Eval`). Runs a rendered script with bindings for flow variables, logger, and runContext; stdout/stderr are not captured—log via `logger`. Optional outputs list persists selected binding values."
)
@Deprecated
public abstract class Eval extends AbstractJvmScript implements RunnableTask<Eval.Output> {
    @Schema(
        title = "Binding names to return",
        description = "List of binding keys to copy into task outputs after execution."
    )
    protected Property<List<String>> outputs;

    protected Eval.Output run(RunContext runContext, String engineName) throws Exception {
        ScriptEngineService.CompiledScript scripts = ScriptEngineService.scripts(
            runContext,
            engineName,
            generateScript(runContext),
            this.getClass().getClassLoader()
        );

        Bindings bindings = scripts.getBindings().get();

        Object result = scripts.getEngine().eval(
            generateScript(runContext),
            bindings
        );

        Output.OutputBuilder builder = Output.builder();

        List<String> renderedOutputs = runContext.render(this.outputs).asList(String.class);
        if (renderedOutputs.size() > 0) {
            builder.outputs(gatherOutputs(scripts.getEngine(), bindings, renderedOutputs));
        }

        return builder
            .result(result)
            .build();
    }

    protected Map<String, Object> gatherOutputs(ScriptEngine engine, Bindings bindings, List<String> renderedOutputs) throws Exception {
        Map<String, Object> outputs = new HashMap<>();
        renderedOutputs.forEach(s -> outputs.put(s, bindings.get(s)));
        return outputs;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Evaluated result",
            description = "Last expression returned by the script, if the engine provides one."
        )
        private final Object result;

        @Schema(
            title = "Captured bindings",
            description = "Map of binding values whose keys were listed in outputs."
        )
        private final Map<String, Object> outputs;
    }

}
