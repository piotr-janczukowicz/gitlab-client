package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

/** The subscription tier of a GitLab instance, from the lowest to the highest. */
public enum GitLabTier {

    /** The free tier. */
    FREE,

    /** The Premium tier. */
    PREMIUM,

    /** The Ultimate tier. */
    ULTIMATE;

    /**
     * Whether this tier offers everything that {@code required} does.
     *
     * @param required the tier an operation needs
     * @return {@code true} if this tier is {@code required} or a higher one
     * @throws NullPointerException if {@code required} is {@code null}
     */
    public boolean includes(final GitLabTier required) {
        return compareTo(requireNonNull(required, "required")) >= 0;
    }
}
