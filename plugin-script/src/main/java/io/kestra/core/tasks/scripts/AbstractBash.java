package io.kestra.core.tasks.scripts;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.runners.PluginUtilsService;
import io.kestra.core.models.tasks.runners.ScriptService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;
import io.kestra.plugin.scripts.runner.docker.Docker;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwBiConsumer;
import static io.kestra.core.utils.Rethrow.throwConsumer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Deprecated
public abstract class AbstractBash extends Task {
    @Builder.Default
    @Schema(
        title = "The task runner."
    )
    @NotNull
    protected Property<RunnerType> runner = Property.of(RunnerType.PROCESS);

    @Schema(
        title = "Docker options when using the `DOCKER` runner."
    )
    @PluginProperty
    protected DockerOptions dockerOptions;

    @Builder.Default
    @Schema(
        title = "Interpreter to use when launching the process."
    )
    @NotNull
    protected Property<String> interpreter = Property.of("/bin/sh");

    @Builder.Default
    @Schema(
        title = "Interpreter arguments to be used."
    )
    @PluginProperty
    protected String[] interpreterArgs = {"-c"};

    @Builder.Default
    @Schema(
        title = "Exit if any non-true value is returned.",
        description = "This tells bash that it should exit the script if any statement returns a non-true return value. \n" +
            "Setting this to `true` helps catch cases where a command fails and the script continues to run anyway."
    )
    @NotNull
    protected Property<Boolean> exitOnFailed = Property.of(true);

    @Schema(
        title = "[Deprecated] The list of files that will be uploaded to Kestra's internal storage.",
        description ="Use `outputFiles` instead.",
        deprecated = true
    )
    @Deprecated
    protected Property<List<String>> files;

    @Schema(
        title = "[Deprecated] Output files.",
        description = "Use `outputFiles` instead.",
        deprecated = true
    )
    @Deprecated
    protected Property<List<String>> outputsFiles;

    @Schema(
        title = "Output file list that will be uploaded to Kestra's internal storage.",
        description = "List of keys that will generate temporary files.\n" +
            "This property can be used with a special variable named `outputFiles.key`.\n" +
            "If you add a file with `[\"first\"]`, you can use the special var `echo 1 >> {[ outputFiles.first }}`," +
            " and on other tasks, you can reference it using `{{ outputs.taskId.outputFiles.first }}`."
    )
    protected Property<List<String>> outputFiles;

    @Schema(
        title = "List of output directories that will be uploaded to Kestra's internal storage.",
        description = "List of keys that will generate temporary directories.\n" +
            "This property can be used with a special variable named `outputDirs.key`.\n" +
            "If you add a file with `[\"myDir\"]`, you can use the special var `echo 1 >> {[ outputDirs.myDir }}/file1.txt` " +
            "and `echo 2 >> {[ outputDirs.myDir }}/file2.txt`, and both the files will be uploaded to Kestra's internal storage. " +
            "You can reference them in other tasks using `{{ outputs.taskId.outputFiles['myDir/file1.txt'] }}`."
    )
    protected Property<List<String>> outputDirs;

    @Schema(
        title = "Input files are extra files that will be available in the script's working directory.",
        description = "Define the files **as a map** of a file name being the key, and the value being the file's content.\n" +
            "Alternatively, configure the files **as a JSON string** with the same key/value structure as the map.\n" +
            "In both cases, you can either specify the file's content inline, or reference a file from Kestra's internal " +
            "storage by its URI, e.g. a file from an input, output of a previous task, or a [Namespace File](https://kestra.io/docs/developer-guide/namespace-files)."
    )
    @PluginProperty(
        additionalProperties = String.class,
        dynamic = true
    )
    protected Object inputFiles;

    @Schema(
        title = "One or more additional environment variable(s) to add to the task run."
    )
    protected Property<Map<String, String>> env;

    @Builder.Default
    @Schema(
        title = "Whether to set the execution state to `WARNING` if any `stdErr` is emitted."
    )
    @NotNull
    protected Property<Boolean> warningOnStdErr = Property.of(true);

    @Getter(AccessLevel.NONE)
    protected transient Path workingDirectory;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    protected transient Map<String, Object> additionalVars = new HashMap<>();

    protected List<String> finalInterpreter(RunContext runContext) throws IllegalVariableEvaluationException {
        List<String> interpreters = new ArrayList<>();

        interpreters.add(runContext.render(this.interpreter).as(String.class).orElse(null));
        interpreters.addAll(Arrays.asList(this.interpreterArgs));

        return interpreters;
    }

    protected Map<String, String> finalInputFiles(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        return this.inputFiles != null ? new HashMap<>(PluginUtilsService.transformInputFiles(runContext, this.inputFiles)) : new HashMap<>();
    }

    protected Map<String, String> finalInputFiles(RunContext runContext, Map<String, Object> additionalVars) throws IOException, IllegalVariableEvaluationException {
        return this.inputFiles != null ? new HashMap<>(PluginUtilsService.transformInputFiles(runContext, additionalVars, this.inputFiles)) : new HashMap<>();
    }

    protected Map<String, String> finalEnv(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        var renderedEnv = runContext.render(this.env).asMap(String.class, String.class);
        return !renderedEnv.isEmpty() ? new HashMap<>(renderedEnv) : new HashMap<>();
    }

    protected io.kestra.core.tasks.scripts.ScriptOutput run(RunContext runContext, Supplier<String> commandsSupplier) throws Exception {
        List<String> allOutputs = new ArrayList<>();

        if (this.workingDirectory == null) {
            this.workingDirectory = runContext.workingDir().path();
        }

        additionalVars.put("workingDir", workingDirectory.toAbsolutePath().toString());

        // deprecated properties
        var renderedOutputFiles = runContext.render(this.outputFiles).asList(String.class);
        if (!renderedOutputFiles.isEmpty()) {
            allOutputs.addAll(renderedOutputFiles);
        }

        if (!renderedOutputFiles.isEmpty()) {
            allOutputs.addAll(renderedOutputFiles);
        }

        var renderedFiles = runContext.render(this.files).asList(String.class);
        if (!renderedFiles.isEmpty()) {
            allOutputs.addAll(renderedFiles);
        }

        Map<String, String> outputFiles = PluginUtilsService.createOutputFiles(
            workingDirectory,
            allOutputs,
            additionalVars
        );

        PluginUtilsService.createInputFiles(
            runContext,
            workingDirectory,
            this.finalInputFiles(runContext, additionalVars),
            additionalVars
        );

        List<String> allOutputDirs = new ArrayList<>();

        var renderedOutputDirs = runContext.render(this.outputDirs).asList(String.class);
        if (!renderedOutputDirs.isEmpty()) {
            allOutputDirs.addAll(renderedOutputDirs);
        }

        Map<String, String> outputDirs = PluginUtilsService.createOutputFiles(
            workingDirectory,
            allOutputDirs,
            additionalVars,
            true
        );

        List<String> commandsArgs = ScriptService.scriptCommands(
            this.finalInterpreter(runContext),
            List.of(),
            commandsSupplier.get()
        );

        var taskRunner = switch (runContext.render(this.runner).as(RunnerType.class).orElseThrow()) {
            case DOCKER -> Docker.from(this.getDockerOptions()).toBuilder().fileHandlingStrategy(Property.of(Docker.FileHandlingStrategy.MOUNT)).build();
            case PROCESS -> Process.instance();
        };

        ScriptOutput run = new CommandsWrapper(runContext)
            .withEnv(this.finalEnv(runContext))
            .withWarningOnStdErr(runContext.render(this.warningOnStdErr).as(Boolean.class).orElseThrow())
            .withTaskRunner(taskRunner)
            .withCommands(new Property<>(JacksonMapper.ofJson().writeValueAsString(commandsArgs)))
            .addAdditionalVars(this.additionalVars)
            .run();

        // upload output files
        Map<String, URI> uploaded = new HashMap<>();

        // outputFiles
        outputFiles
            .forEach(throwBiConsumer((k, v) -> uploaded.put(k, runContext.storage().putFile(new File(runContext.render(v, additionalVars))))));

        // outputDirs
        outputDirs
            .forEach(throwBiConsumer((k, v) -> {
                try (Stream<Path> walk = Files.walk(new File(runContext.render(v, additionalVars)).toPath())) {
                    walk
                        .filter(Files::isRegularFile)
                        .forEach(throwConsumer(path -> {
                            String filename = Path.of(
                                k,
                                Path.of(runContext.render(v, additionalVars)).relativize(path).toString()
                            ).toString();

                            uploaded.put(
                                filename,
                                runContext.storage().putFile(path.toFile(), filename)
                            );
                        }));
                }
            }));

        // output
        return io.kestra.core.tasks.scripts.ScriptOutput.builder()
            .exitCode(run.getExitCode())
            .stdOutLineCount(run.getStdOutLineCount())
            .stdErrLineCount(run.getStdErrLineCount())
            .warningOnStdErr(runContext.render(this.warningOnStdErr).as(Boolean.class).orElseThrow())
            .vars(run.getVars())
            .files(uploaded)
            .outputFiles(uploaded)
            .build();
    }
}
