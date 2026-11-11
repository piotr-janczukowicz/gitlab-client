package io.github.piotr_janczukowicz.gitlab.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an operation that GitLab offers only from a subscription tier, as the {@code x-gitlab-tier} of its OpenAPI
 * description says. It is metadata: the method that carries it calls {@link GitLabCaller#requireTier(GitLabTier,
 * String)} first, and nothing reads the annotation at run time. An operation without it has no tier guard.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresTier {

    /**
     * The lowest tier that offers the operation.
     *
     * @return the tier
     */
    GitLabTier value();
}
