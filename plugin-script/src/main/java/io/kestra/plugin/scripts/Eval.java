package io.kestra.plugin.scripts;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    description = "You can use a full script. \n" +
        "The script contains some predefined variables:\n" +
        "- All the variables you have in handlebars vars like `execution.id` for example\n" +
        "- `logger` that you can used as the standard java logger (`logger.info('my message')`)\n" +
        "- `runContext` that allow you to :\n" +
        "  - `runContext.metric(Counter.of(\"file.size\", response.contentLength()))`: send metrics\n" +
        "  - `runContext.uriToInputStream(URI uri): get a file from kestra internal storage\n" +
        "  - `runContext.putTempFile(File file)`: store a file in kestra internal storage\n" +
        "\n" +
        "The stdOut & stdErr is not captured, so you must use `logger`\n"
)
public abstract class Eval extends AbstractScript implements RunnableTask<Eval.Output> {
    @Schema(
        title = "A List of outputs variables that will be usable in outputs."
    )
    @PluginProperty
    protected List<String> outputs;

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

        if (outputs != null && outputs.size() > 0) {
            builder.outputs(gatherOutputs(scripts.getEngine(), bindings));
        }

        return builder
            .result(result)
            .build();
    }

    protected Map<String, Object> gatherOutputs(ScriptEngine engine, Bindings bindings) throws Exception {
        Map<String, Object> outputs = new HashMap<>();
        this.outputs
            .forEach(s -> outputs.put(s, bindings.get(s)));

        return outputs;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The resulting object",
            description = "Mostly the last return of eval (if the language allow it)"
        )
        private final Object result;

        @Schema(
            title = "The captured outputs as declared on task property."
        )
        private final Map<String, Object> outputs;
    }

}
