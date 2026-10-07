package de.zbidi.jobtracker.jobapplication;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull Status status) {
}
