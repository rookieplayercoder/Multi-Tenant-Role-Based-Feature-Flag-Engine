package com.prateek.featureflag.organization.dto;

import com.prateek.featureflag.organization.MemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Invites an existing registered user (by email) into an organization with
 * a given role. There is no signup-via-invite flow in this batch — if the
 * email doesn't match an active {@link com.prateek.featureflag.user.User},
 * the request fails with 404 rather than silently creating an account or
 * sending an email.
 */
public record InviteMemberRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull MemberRole role
) {
}
