package io.kestra.plugin.scripts.exec.scripts.validations.validators;

import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.validations.AbstractExecScriptValidation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
@Introspected
public class AbstractExecScriptValidator implements ConstraintValidator<AbstractExecScriptValidation, io.kestra.plugin.scripts.exec.AbstractExecScript> {
    @Override
    public boolean isValid(
        @Nullable io.kestra.plugin.scripts.exec.AbstractExecScript value,
        @NonNull AnnotationValue<AbstractExecScriptValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        final DockerOptions defaultOptions = DockerOptions.builder().build();

        if (value.getRunner() != RunnerType.DOCKER && value.getDocker() != null && !value.getDocker().equals(defaultOptions)) {
            context.messageTemplate("invalid script: custom Docker options require the Docker runner");

            return false;
        }

        return true;
    }
}
