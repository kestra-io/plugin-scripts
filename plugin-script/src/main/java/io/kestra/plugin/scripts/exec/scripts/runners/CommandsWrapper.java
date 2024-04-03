package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.script.DefaultLogConsumer;
import io.kestra.core.models.script.*;
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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        if (!this.outputDirectory.toFile().mkdirs()) {
            throw new RuntimeException("Unable to create the output directory " + this.outputDirectory);
        }

        this.logConsumer = new DefaultLogConsumer(runContext);
        this.additionalVars = new HashMap<>();
        this.env = new HashMap<>();
    }

    public CommandsWrapper withEnv(Map<String, String> envs) {
        return new CommandsWrapper(
            runContext,
            workingDirectory,
            outputDirectory,
            additionalVars,
            commands,
            envs,
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
        if (this.additionalVars == null) {
            this.additionalVars = new HashMap<>();
        }
        this.additionalVars.putAll(additionalVars);

        return this;
    }

    public CommandsWrapper addEnv(Map<String, String> envs) {
        if (this.env == null) {
            this.env = new HashMap<>();
        }
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
            Map<String, String> finalInputFiles = FilesService.inputFiles(runContext, this.getScriptRunner().additionalVars(runContext, this), this.inputFiles);
            filesToUpload.addAll(finalInputFiles.keySet());
        }

        RunContext scriptRunnerRunContext = runContext.forScriptRunner(this.getScriptRunner());

        this.commands = this.render(runContext, commands, filesToUpload);

        RunnerResult runnerResult = this.getScriptRunner().run(scriptRunnerRunContext, this, filesToUpload, this.outputFiles);

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

    public ScriptRunner getScriptRunner() {
        if (scriptRunner == null) {
            scriptRunner = switch (runnerType) {
                case DOCKER -> DockerScriptRunner.from(this.dockerOptions);
                case PROCESS -> new ProcessScriptRunner();
            };
        }

        return scriptRunner;
    }

    public String render(RunContext runContext, String command, List<String> internalStorageLocalFiles) throws IllegalVariableEvaluationException, IOException {
        ScriptRunner scriptRunner = this.getScriptRunner();
        return ScriptService.replaceInternalStorage(
            this.runContext,
            scriptRunner.additionalVars(runContext, this),
            command,
            (ignored, localFilePath) -> internalStorageLocalFiles.add(localFilePath),
            scriptRunner instanceof RemoteRunnerInterface
        );
    }

    public List<String> render(RunContext runContext, List<String> commands, List<String> internalStorageLocalFiles) throws IllegalVariableEvaluationException, IOException {
        ScriptRunner scriptRunner = this.getScriptRunner();
        return ScriptService.replaceInternalStorage(
            this.runContext,
            scriptRunner.additionalVars(runContext, this),
            commands,
            (ignored, localFilePath) -> internalStorageLocalFiles.add(localFilePath),
            scriptRunner instanceof RemoteRunnerInterface
        );
    }
}