package io.kestra.plugin.scripts.jvm;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.reactivestreams.Publisher;

import java.io.*;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import javax.script.Bindings;
import javax.script.ScriptException;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Transform ion format file from Kestra with a groovy script.",
    description = "This allows you to transform the data, previously loaded by Kestra, as you need.\n\n" +
        "Take a ion format file from Kestra and iterate row per row.\n" +
        "Each row will populate a `row` global variable. You need to alter this variable that will be saved on output file.\n" +
        "If you set the `row` to `null`, the row will be skipped.\n" +
        "You can create a variable `rows` to return multiple rows for a single `row`.\n"
)
public abstract class FileTransform extends AbstractJvmScript implements RunnableTask<FileTransform.Output> {
    @NotNull
    @Schema(
        title = "Source file containing rows to transform.",
        description = "Can be Kestra's internal storage URI, a map or a list."
    )
    @PluginProperty(dynamic = true)
    private String from;

    @Min(2)
    @Schema(
        title = "Number of concurrent parallel transformations to execute.",
        description = "Take care that the order is **not respected** if you use parallelism."
    )
    @PluginProperty
    private Integer concurrent;

    @SuppressWarnings("unchecked")
    protected FileTransform.Output run(RunContext runContext, String engineName) throws Exception {
        // temp out file
        String from = runContext.render(this.from);
        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        // prepare script
        ScriptEngineService.CompiledScript scripts = ScriptEngineService.scripts(
            runContext,
            engineName,
            generateScript(runContext),
            this.getClass().getClassLoader()
        );

        try (
            var output = new BufferedWriter(new FileWriter(tempFile), FileSerde.BUFFER_SIZE)
        ) {
            if (from.startsWith("kestra://")) {
                try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(runContext.storage().getFile(URI.create(from))), FileSerde.BUFFER_SIZE)) {
                    this.finalize(
                        runContext,
                        FileSerde.readAll(inputStream),
                        scripts,
                        output
                    );
                }
            } else {
                this.finalize(
                    runContext,
                    Flux.create(throwConsumer(emitter -> {
                        Object o = JacksonMapper.toObject(from);

                        if (o instanceof List) {
                            ((List<Object>) o).forEach(emitter::next);
                        } else {
                            emitter.next(o);
                        }

                        emitter.complete();
                    }), FluxSink.OverflowStrategy.BUFFER),
                    scripts,
                    output
                );
            }

            output.flush();
        }

        return Output
            .builder()
            .uri(runContext.storage().putFile(tempFile))
            .build();
    }

    protected void finalize(
        RunContext runContext,
        Flux<Object> flowable,
        ScriptEngineService.CompiledScript scripts,
        Writer output
    ) throws IOException, ScriptException {
        Flux<Object> sequential;

        if (this.concurrent != null) {
            sequential = flowable
                .parallel(this.concurrent)
                .runOn(Schedulers.boundedElastic())
                .flatMap(this.convert(scripts))
                .sequential();
        } else {
            sequential = flowable
                .flatMap(this.convert(scripts));
        }

        Mono<Long> count = FileSerde.writeAll(output, sequential);

        // metrics & finalize
        Long lineCount = count.block();
        runContext.metric(Counter.of("records", lineCount));
    }

    abstract protected Collection<Object> convertRows(Object rows);

    @SuppressWarnings("unchecked")
    protected Function<Object, Publisher<Object>> convert(ScriptEngineService.CompiledScript script) throws ScriptException {
        return throwFunction(row -> {
            Bindings bindings = script.getBindings().get();
            bindings.put("row", row);

            script.getScript().eval(bindings);

            if (bindings.get("rows") != null) {
                return Flux.fromIterable(this.convertRows(bindings.get("rows")));
            }

            if (bindings.get("row") != null) {
                return Flux.just(bindings.get("row"));
            }

            return Flux.empty();
        });
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "URI of a temporary result file.",
            description = "The file will be serialized as ion file."
        )
        private final URI uri;
    }
}
