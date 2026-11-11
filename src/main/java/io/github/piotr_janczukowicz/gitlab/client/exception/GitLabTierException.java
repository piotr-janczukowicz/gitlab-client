package io.github.piotr_janczukowicz.gitlab.client.exception;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.client.GitLabTier;
import java.io.Serial;

/**
 * An operation needs a higher subscription tier than the one the client was configured with. It is thrown before any
 * request is sent, so nothing reached the instance.
 */
public final class GitLabTierException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String operation;
    private final GitLabTier required;
    private final GitLabTier configured;

    /**
     * Describes a refused operation.
     *
     * @param operation the operation, such as its {@code operationId}
     * @param required the lowest tier that offers the operation
     * @param configured the tier the client was configured with
     * @throws NullPointerException if an argument is {@code null}
     */
    public GitLabTierException(final String operation, final GitLabTier required, final GitLabTier configured) {
        super(describe(
                requireNonNull(operation, "operation"),
                requireNonNull(required, "required"),
                requireNonNull(configured, "configured")));
        this.operation = operation;
        this.required = required;
        this.configured = configured;
    }

    /**
     * The refused operation.
     *
     * @return the operation
     */
    public String operation() {
        return operation;
    }

    /**
     * The lowest tier that offers the operation.
     *
     * @return the tier
     */
    public GitLabTier required() {
        return required;
    }

    /**
     * The tier the client was configured with.
     *
     * @return the tier
     */
    public GitLabTier configured() {
        return configured;
    }

    private static String describe(final String operation, final GitLabTier required, final GitLabTier configured) {
        return operation + " needs the " + required + " tier, but the client is configured for the " + configured
                + " tier";
    }
}
