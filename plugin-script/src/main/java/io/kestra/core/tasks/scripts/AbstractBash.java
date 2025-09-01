package io.kestra.core.tasks.scripts;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.OutputFilesInterface;
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
public abstract class AbstractBash extends Task implements OutputFilesInterface {
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
        description = "Use `outputFiles` instead.",
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
        title = "[Deprecated] List of output directories that will be uploaded to Kestra's internal storage.",
        description = "Use `outputFiles` instead. List of keys that will generate temporary directories.\n" +
            "This property can be used with a special variable named `outputDirs.key`.\n" +
            "If you add a file with `[\"myDir\"]`, you can use the special var `echo 1 >> {[ outputDirs.myDir }}/file1.txt` " +
            "and `echo 2 >> {[ outputDirs.myDir }}/file2.txt`, and both the files will be uploaded to Kestra's internal storage. " +
            "You can reference them in other tasks using `{{ outputs.taskId.outputFiles['myDir/file1.txt'] }}`."
    )
    @Deprecated
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

    @Schema(
        title = "Not used anymore, will be removed soon"
    )
    @Deprecated
    protected Property<Boolean> warningOnStdErr;

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
        var rEnv = runContext.render(this.env).asMap(String.class, String.class);
        return !rEnv.isEmpty() ? new HashMap<>(rEnv) : new HashMap<>();
    }

    protected io.kestra.core.tasks.scripts.ScriptOutput run(RunContext runContext, Supplier<String> commandsSupplier) throws Exception {
        List<String> allOutputs = new ArrayList<>();

        if (this.workingDirectory == null) {
            this.workingDirectory = runContext.workingDir().path();
        }

        additionalVars.put("workingDir", workingDirectory.toAbsolutePath().toString());

        runContext.logger().info("Running script with working directory: {}", workingDirectory.toAbsolutePath());

        var rOutputFiles = runContext.render(this.outputFiles).asList(String.class);
        if (!rOutputFiles.isEmpty()) {
            allOutputs.addAll(rOutputFiles);
        }

        // deprecated properties
        var rOutputsFiles = runContext.render(this.outputsFiles).asList(String.class);
        if (!rOutputsFiles.isEmpty()) {
            allOutputs.addAll(rOutputsFiles);
        }
        var rFiles = runContext.render(this.files).asList(String.class);
        if (!rFiles.isEmpty()) {
            allOutputs.addAll(rFiles);
        }
        
        Map<String, String> outputFilePaths = PluginUtilsService.createOutputFiles(
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

        var rOutputDirs = runContext.render(this.outputDirs).asList(String.class);
        if (!rOutputDirs.isEmpty()) {
            allOutputDirs.addAll(rOutputDirs);
        }

        Map<String, String> outputDirPaths = PluginUtilsService.createOutputFiles(
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
            case DOCKER ->
                Docker.from(this.getDockerOptions()).toBuilder().fileHandlingStrategy(Property.of(Docker.FileHandlingStrategy.MOUNT)).build();
            case PROCESS -> Process.instance();
        };

        ScriptOutput run = new CommandsWrapper(runContext)
            .withEnv(this.finalEnv(runContext))
            .withTaskRunner(taskRunner)
            .withCommands(new Property<>(JacksonMapper.ofJson().writeValueAsString(commandsArgs)))
            .withOutputFiles(allOutputs)
            .addAdditionalVars(this.additionalVars)
            .run();

        // upload output files to storage
        Map<String, URI> uploaded = new HashMap<>();

        // upload regular output files
        outputFilePaths.forEach(throwBiConsumer((key, filePath) -> {
            File file = new File(runContext.render(filePath, additionalVars));
            if (file.exists() && file.isFile()) {
                uploaded.put(key, runContext.storage().putFile(file));
            } else {
                runContext.logger().debug("Output file not found or is not a file: {}", file.getAbsolutePath());
            }
        }));

        // upload files from deprecated output directories
        outputDirPaths.forEach(throwBiConsumer((key, dirPath) -> {
            File dir = new File(runContext.render(dirPath, additionalVars));
            if (dir.exists() && dir.isDirectory()) {
                try (Stream<Path> walk = Files.walk(dir.toPath())) {
                    walk
                        .filter(Files::isRegularFile)
                        .forEach(throwConsumer(path -> {
                            String filename = Path.of(
                                key,
                                dir.toPath().relativize(path).toString()
                            ).toString();

                            uploaded.put(
                                filename,
                                runContext.storage().putFile(path.toFile(), filename)
                            );
                        }));
                } catch (IOException e) {
                    runContext.logger().warn("Failed to walk directory: {}", dir.getAbsolutePath(), e);
                }
            }
        }));

        // include files that CommandsWrapper may have uploaded
        if (run.getOutputFiles() != null) {
            uploaded.putAll(run.getOutputFiles());
        }

        return io.kestra.core.tasks.scripts.ScriptOutput.builder()
            .exitCode(run.getExitCode())
            .stdOutLineCount(run.getStdOutLineCount())
            .stdErrLineCount(run.getStdErrLineCount())
            .vars(run.getVars())
            .files(uploaded)
            .outputFiles(uploaded)
            .build();
    }
}