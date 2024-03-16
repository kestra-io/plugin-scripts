package io.kestra.plugin.scripts.exec.scripts.validations;

import io.kestra.plugin.scripts.exec.scripts.validations.validators.AbstractExecScriptValidator;

import jakarta.validation.Constraint;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AbstractExecScriptValidator.class)
@Inherited
public @interface AbstractExecScriptValidation {
    String message() default "invalid script ({validatedValue})";
}
