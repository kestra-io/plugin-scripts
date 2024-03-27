package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.script.*;
import io.kestra.core.models.script.DefaultLogConsumer;
import io.kestra.core.models.script.types.ProcessScriptRunner;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.NamespaceFilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.DockerScriptRunner;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@AllArgsConstructor
@Getter
public class CommandsWrapper implements ScriptCommands {
    private RunContext runContext;

    private Path workingDirectory;

    private Path outputDirectory;

    private Map<String, Object> additionalVars;

    @With
    private List<String> commands;

    private Map<String, String> env;

    @With
    private io.kestra.core.models.script.AbstractLogConsumer logConsumer;

    @With
    private RunnerType runnerType;

    @With
    private String containerImage;

    @With
    private ScriptRunner scriptRunner;

    @With
    private DockerOptions dockerOptions;

    @With
    private Boolean warningOnStdErr;

    @With
    private NamespaceFiles namespaceFiles;

    @With
    private Object inputFiles;

    @With
    private List<String> outputFiles;

    public CommandsWrapper(RunContext runContext) {
        this.runContext = runContext;

        this.workingDirectory = runContext.tempDir();
        this.outputDirectory = this.workingDirectory.resolve(IdUtils.create());
        //noinspection ResultOfMethodCallIgnored
        this.outputDirectory.toFile().mkdirs();

        this.additionalVars = new HashMap<>(Map.of(
            "workingDir", workingDirectory.toAbsolutePath().toString(),
            "outputDir", outputDirectory.toString()
        ));

        this.logConsumer = new DefaultLogConsumer(runContext);
    }

    public CommandsWrapper withEnv(Map<String, String> envs) throws IllegalVariableEvaluationException {
        return new CommandsWrapper(
            runContext,
            workingDirectory,
            outputDirectory,
            additionalVars,
            commands,
            (envs == null ? Map.<String, String>of() : envs)
                .entrySet()
                .stream()
                .map(throwFunction(r -> new AbstractMap.SimpleEntry<>(
                        runContext.render(r.getKey(), additionalVars),
                        runContext.render(r.getValue(), additionalVars)
                    )
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
            logConsumer,
            runnerType,
            containerImage,
            scriptRunner,
            dockerOptions,
            warningOnStdErr,
            namespaceFiles,
            inputFiles,
            outputFiles
        );
    }

    public CommandsWrapper addAdditionalVars(Map<String, Object> additionalVars) {
        this.additionalVars.putAll(additionalVars);

        return this;
    }

    public CommandsWrapper addEnv(Map<String, String> envs) {
        this.env.putAll(envs);

        return this;
    }

    @SuppressWarnings("unchecked")
    public ScriptOutput run() throws Exception {
        List<String> filesToUpload = new ArrayList<>();
        if (this.namespaceFiles != null) {
            String tenantId = ((Map<String, String>) runContext.getVariables().get("flow")).get("tenantId");
            String namespace = ((Map<String, String>) runContext.getVariables().get("flow")).get("namespace");

            NamespaceFilesService namespaceFilesService = runContext.getApplicationContext().getBean(NamespaceFilesService.class);
            List<URI> injectedFiles = namespaceFilesService.inject(
                runContext,
                tenantId,
                namespace,
                this.workingDirectory,
                this.namespaceFiles
            );
            injectedFiles.forEach(uri -> filesToUpload.add(uri.toString().substring(1))); // we need to remove the leading '/'
        }

        if (this.inputFiles != null) {
            Map<String, String> finalInputFiles = FilesService.inputFiles(runContext, this.inputFiles);
            filesToUpload.addAll(finalInputFiles.keySet());
        }

        ScriptRunner realScriptRunner = scriptRunner != null ? scriptRunner : switch (runnerType) {
            case DOCKER -> DockerScriptRunner.from(this.dockerOptions);
            case PROCESS -> new ProcessScriptRunner();
        };
        RunContext scriptRunnerRunContext = runContext.forScriptRunner(realScriptRunner);
        RunnerResult runnerResult = realScriptRunner.run(scriptRunnerRunContext, this, filesToUpload, this.outputFiles);

        // FIXME should we really upload all files even if not configured via this.outputFiles ?
        // FIXME isn't it a security risk if we upload a file that is not listed in the output files?
        Map<String, URI> outputFiles = ScriptService.uploadOutputFiles(scriptRunnerRunContext, outputDirectory);

        if (this.outputFiles != null) {
            outputFiles.putAll(FilesService.outputFiles(scriptRunnerRunContext, this.outputFiles));
        }

        return ScriptOutput.builder()
            .exitCode(runnerResult.getExitCode())
            .stdOutLineCount(runnerResult.getLogConsumer().getStdOutCount())
            .stdErrLineCount(runnerResult.getLogConsumer().getStdErrCount())
            .warningOnStdErr(this.warningOnStdErr)
            .vars(runnerResult.getLogConsumer().getOutputs())
            .outputFiles(outputFiles)
            .build();
    }
}

