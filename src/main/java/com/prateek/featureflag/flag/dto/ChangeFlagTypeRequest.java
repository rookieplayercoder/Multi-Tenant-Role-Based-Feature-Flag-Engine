package com.prateek.featureflag.flag.dto;

import com.prateek.featureflag.flag.FlagType;
import jakarta.validation.constraints.NotNull;

/**
 * Changes a flag's {@code flagType} after creation — {@link CreateFeatureFlagRequest}
 * has no field for this (always defaults to {@code BOOLEAN}), so this is
 * the only way to move a flag to {@code PERCENTAGE}/{@code TARGETED} and
 * have its rule tree actually consulted at evaluation time.
 */
public record ChangeFlagTypeRequest(@NotNull FlagType flagType) {
}
