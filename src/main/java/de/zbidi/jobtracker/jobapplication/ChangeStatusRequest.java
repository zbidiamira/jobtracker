package de.zbidi.jobtracker.jobapplication;

import jakarta.validation.constraints.NotNull;

/**
 * @param version the version the client last read; a mismatch means someone else changed it (409)
 */
public record ChangeStatusRequest(@NotNull Status status, @NotNull Long version) {
}
