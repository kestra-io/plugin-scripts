package io.kestra.plugin.scripts.python;

import io.kestra.core.models.property.Property;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.python.internals.PythonBasedPlugin;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractPythonExecScript extends AbstractExecScript implements PythonBasedPlugin {

    @Builder.Default
    protected Property<String> containerImage = Property.of(DEFAULT_IMAGE);

    protected Property<List<String>> dependencies;

    protected Property<String> pythonVersion;

    protected Property<Boolean> dependencyCacheEnabled = Property.of(true);

}
