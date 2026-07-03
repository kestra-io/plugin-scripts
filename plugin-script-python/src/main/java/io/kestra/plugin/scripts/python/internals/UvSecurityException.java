package io.kestra.plugin.scripts.python.internals;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Thrown by {@link PythonDependenciesResolver#getUvCmd()} for the two security-relevant conditions
 * that must hard-fail the task rather than silently degrade to a PIP fallback: a SHA-256 mismatch on
 * the downloaded 'uv' installer, and an explicit opt-out of automatic installation while 'uv' is absent.
 * <p>
 * {@link PackageManagerType#UV}'s {@code isAvailable} re-throws this type instead of swallowing it,
 * so these two conditions always surface as the task's terminal failure with their actionable message,
 * while other, benign "uv could not be installed" failures keep falling back to PIP.
 */
public class UvSecurityException extends KestraRuntimeException {

    public UvSecurityException(String message) {
        super(message);
    }
}
