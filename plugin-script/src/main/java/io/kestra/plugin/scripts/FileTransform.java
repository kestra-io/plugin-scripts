package io.kestra.plugin.scripts;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.reactivestreams.Publisher;

import java.io.*;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import javax.script.Bindings;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Transform ion format file from kestra with a groovy script.",
    description = "This allow you to transform the data previously loaded by kestra as you need.\n\n" +
        "Take a ion format file from kestra and iterate row per row.\n" +
        "Each row will populate a `row` global variable, you need to alter this variable that will be saved on output file.\n" +
        "if you set the `row` to `null`, the row will be skipped\n" +
        "You can create a variables `rows` to return many rows for a single `row`.\n"
)
public abstract class FileTransform extends AbstractScript implements RunnableTask<FileTransform.Output> {
    @NotNull
    @Schema(
        title = "Source file of row to transform",
        description = "Can be an internal storage uri, a map or a list."
    )
    @PluginProperty(dynamic = true)
    private String from;

    @Min(2)
    @Schema(
        title = "Number of concurrent parallels transform",
        description = "Take care that the order is **not respected** if you use parallelism"
    )
    @PluginProperty(dynamic = false)
    private Integer concurrent;

    @SuppressWarnings("unchecked")
    protected FileTransform.Output run(RunContext runContext, String engineName) throws Exception {
        // temp out file
        String from = runContext.render(this.from);
        File tempFile = runContext.tempFile().toFile();

        // prepare script
        ScriptEngineService.CompiledScript scripts = ScriptEngineService.scripts(
            runContext,
            engineName,
            generateScript(runContext),
            this.getClass().getClassLoader()
        );

        try (
            OutputStream output = new FileOutputStream(tempFile);
        ) {
            if (from.startsWith("kestra://")) {
                try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(runContext.uriToInputStream(URI.create(from))))) {
                    this.finalize(
                        runContext,
                        Flowable.create(FileSerde.reader(inputStream), BackpressureStrategy.BUFFER),
                        scripts,
                        output
                    );
                }
            } else {
                this.finalize(
                    runContext,
                    Flowable.create(emitter -> {
                        Object o = JacksonMapper.toObject(from);

                        if (o instanceof List) {
                            ((List<Object>) o).forEach(emitter::onNext);
                        } else {
                            emitter.onNext(o);
                        }

                        emitter.onComplete();
                    }, BackpressureStrategy.BUFFER),
                    scripts,
                    output
                );
            }

            output.flush();
        }

        return Output
            .builder()
            .uri(runContext.putTempFile(tempFile))
            .build();
    }

    protected void finalize(
        RunContext runContext,
        Flowable<Object> flowable,
        ScriptEngineService.CompiledScript scripts,
        OutputStream output
    ) {
        Flowable<Object> sequential;

        if (this.concurrent != null) {
            sequential = flowable
                .parallel(this.concurrent)
                .runOn(Schedulers.io())
                .flatMap(this.convert(scripts))
                .sequential();
        } else {
            sequential = flowable
                .flatMap(this.convert(scripts));
        }

        Single<Long> count = sequential
            .doOnNext(row -> FileSerde.write(output, row))
            .count();

        // metrics & finalize
        Long lineCount = count.blockingGet();
        runContext.metric(Counter.of("records", lineCount));
    }

    @SuppressWarnings("unchecked")
    protected Function<Object, Publisher<Object>> convert(ScriptEngineService.CompiledScript script) {
        return row -> {
            Bindings bindings = script.getBindings().get();
            bindings.put("row", row);

            script.getScript().eval(bindings);


            if (bindings.get("rows") != null) {
                return Flowable.fromIterable((Collection<Object>) bindings.get("rows"));
            }

            if (bindings.get("row") != null) {
                return Flowable.just(bindings.get("row"));
            }

            return Flowable.empty();
        };
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "URI of a temporary result file",
            description = "The file will be serialized as ion file."
        )
        private final URI uri;
    }
}
