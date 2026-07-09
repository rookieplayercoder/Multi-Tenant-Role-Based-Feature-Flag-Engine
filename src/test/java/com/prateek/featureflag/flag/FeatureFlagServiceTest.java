package com.prateek.featureflag.flag;

import com.prateek.featureflag.audit.AuditAction;
import com.prateek.featureflag.audit.AuditLogService;
import com.prateek.featureflag.audit.ResourceType;
import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.environment.EnvironmentType;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.project.Project;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private FlagVersionService flagVersionService;

    @Mock
    private AuditLogService auditLogService;

    private FeatureFlagService featureFlagService;

    private Environment environment;
    private User actor;

    @BeforeEach
    void setUp() {
        featureFlagService = new FeatureFlagService(
                featureFlagRepository, flagVersionService, auditLogService, new ObjectMapper());

        Organization organization = new Organization("Acme", "acme");
        setId(organization, UUID.randomUUID());
        Project project = new Project(organization, "Web", "web");
        setId(project, UUID.randomUUID());
        environment = new Environment(project, "Development", EnvironmentType.DEV);
        setId(environment, UUID.randomUUID());

        actor = new User("dev@example.com", "hash", "Dev");
        setId(actor, UUID.randomUUID());
    }

    @Test
    void create_persistsSnapshotsAndAudits_whenKeyIsUnique() {
        when(featureFlagRepository.findByEnvironmentIdAndKeyAndDeletedAtIsNull(environment.getId(), "new-flag"))
                .thenReturn(Optional.empty());
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> {
            FeatureFlag f = inv.getArgument(0);
            setId(f, UUID.randomUUID());
            return f;
        });

        FeatureFlag flag = featureFlagService.create(environment, "new-flag", "New Flag", actor);

        assertThat(flag.getKey()).isEqualTo("new-flag");
        assertThat(flag.getFlagType()).isEqualTo(FlagType.BOOLEAN);
        verify(flagVersionService).recordSnapshot(eq(flag), eq(flag.getVersion()), any(), eq(actor), eq("Flag created"));
        verify(auditLogService).record(eq(environment.getProject().getOrganization()), eq(actor),
                eq(AuditAction.FEATURE_FLAG_CREATED), any(), eq(flag.getId()), any());
    }

    @Test
    void create_throwsConflict_whenKeyAlreadyExistsInEnvironment() {
        when(featureFlagRepository.findByEnvironmentIdAndKeyAndDeletedAtIsNull(environment.getId(), "dup"))
                .thenReturn(Optional.of(new FeatureFlag(environment, "dup", "Dup", actor)));

        assertThatThrownBy(() -> featureFlagService.create(environment, "dup", "Dup", actor))
                .isInstanceOf(IllegalStateException.class);

        verify(featureFlagRepository, never()).save(any());
    }

    @Test
    void updateDetails_bumpsVersionAndRecordsSnapshotAndAudit() {
        FeatureFlag flag = flagWithId("f1", 1);
        when(featureFlagRepository.findById(flag.getId())).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));

        FeatureFlag updated = featureFlagService.updateDetails(flag.getId(), "New name", "New desc", actor);

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getVersion()).isEqualTo(2);
        verify(flagVersionService).recordSnapshot(eq(updated), eq(2), any(), eq(actor), eq("Flag details updated"));
        verify(auditLogService).record(any(), eq(actor), eq(AuditAction.FEATURE_FLAG_UPDATED), any(), eq(flag.getId()), any());
    }

    @Test
    void toggle_enable_setsEnabledTrueAndBumpsVersion() {
        FeatureFlag flag = flagWithId("f1", 1);
        when(featureFlagRepository.findById(flag.getId())).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));

        FeatureFlag toggled = featureFlagService.toggle(flag.getId(), true, actor);

        assertThat(toggled.isEnabled()).isTrue();
        assertThat(toggled.getVersion()).isEqualTo(2);
        verify(auditLogService).record(any(), eq(actor), eq(AuditAction.FEATURE_FLAG_ENABLED), any(), any(), any());
    }

    @Test
    void toggle_disable_recordsDisabledAuditAction() {
        FeatureFlag flag = flagWithId("f1", 1);
        flag.setEnabled(true);
        when(featureFlagRepository.findById(flag.getId())).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));

        FeatureFlag toggled = featureFlagService.toggle(flag.getId(), false, actor);

        assertThat(toggled.isEnabled()).isFalse();
        verify(auditLogService).record(any(), eq(actor), eq(AuditAction.FEATURE_FLAG_DISABLED), any(), any(), any());
    }

    @Test
    void changeType_updatesFlagTypeAndBumpsVersion() {
        FeatureFlag flag = flagWithId("f1", 1);
        when(featureFlagRepository.findById(flag.getId())).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));

        FeatureFlag updated = featureFlagService.changeType(flag.getId(), FlagType.TARGETED, actor);

        assertThat(updated.getFlagType()).isEqualTo(FlagType.TARGETED);
        verify(auditLogService).record(any(), eq(actor), eq(AuditAction.FEATURE_FLAG_TYPE_CHANGED), any(), any(), any());
    }

    @Test
    void rollback_restoresNameDescriptionEnabledAndFlagTypeFromSnapshot_notKeyOrEnvironment() {
        FeatureFlag flag = flagWithId("stable-flag", 3);
        flag.setName("Current name");
        flag.setDescription("Current desc");
        flag.setEnabled(true);
        flag.setFlagType(FlagType.TARGETED);
        when(featureFlagRepository.findById(flag.getId())).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));

        String oldSnapshot = """
                {"id":"%s","environmentId":"%s","key":"stable-flag","name":"Old name",
                 "description":"Old desc","enabled":false,"flagType":"BOOLEAN","version":1}
                """.formatted(flag.getId(), environment.getId());
        FlagVersion version1 = new FlagVersion(flag, 1, oldSnapshot, actor, "initial");
        when(flagVersionService.getVersion(flag.getId(), 1)).thenReturn(version1);

        FeatureFlag rolledBack = featureFlagService.rollback(flag.getId(), 1, actor);

        assertThat(rolledBack.getName()).isEqualTo("Old name");
        assertThat(rolledBack.getDescription()).isEqualTo("Old desc");
        assertThat(rolledBack.isEnabled()).isFalse();
        assertThat(rolledBack.getFlagType()).isEqualTo(FlagType.BOOLEAN);
        // Identity fields are untouched by rollback:
        assertThat(rolledBack.getKey()).isEqualTo("stable-flag");
        assertThat(rolledBack.getEnvironment()).isEqualTo(environment);
        // Rollback still bumps version forward rather than rewriting history:
        assertThat(rolledBack.getVersion()).isEqualTo(4);
        verify(auditLogService).record(any(), eq(actor), eq(AuditAction.FEATURE_FLAG_ROLLED_BACK), any(), any(), any());
    }

    @Test
    void rollback_throwsNotFound_whenTargetVersionDoesNotExist() {
        FeatureFlag flag = flagWithId("f1", 1);
        when(featureFlagRepository.findById(flag.getId())).thenReturn(Optional.of(flag));
        when(flagVersionService.getVersion(flag.getId(), 99))
                .thenThrow(new EntityNotFoundException("Version 99 not found"));

        assertThatThrownBy(() -> featureFlagService.rollback(flag.getId(), 99, actor))
                .isInstanceOf(EntityNotFoundException.class);

        verify(featureFlagRepository, never()).save(any());
    }



    @Test
    void softDelete_setsDeletedAtAndAudits_andSubsequentGetActiveByIdThrows() {
        FeatureFlag flag = flagWithId("f1", 1);
        when(featureFlagRepository.findById(flag.getId())).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));

        featureFlagService.softDelete(flag.getId(), actor);

        assertThat(flag.isDeleted()).isTrue();
        verify(auditLogService).record(any(), eq(actor), eq(AuditAction.FEATURE_FLAG_DELETED), any(), eq(flag.getId()), any());

        // getActiveById filters out soft-deleted rows even though findById still returns them:
        assertThatThrownBy(() -> featureFlagService.getActiveById(flag.getId()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getActiveByEnvironmentAndKey_throwsNotFound_whenNoMatch() {
        when(featureFlagRepository.findByEnvironmentIdAndKeyAndDeletedAtIsNull(environment.getId(), "missing"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> featureFlagService.getActiveByEnvironmentAndKey(environment.getId(), "missing"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private FeatureFlag flagWithId(String key, int version) {
        FeatureFlag flag = new FeatureFlag(environment, key, "Flag " + key, actor);
        setId(flag, UUID.randomUUID());
        flag.setVersion(version);
        return flag;
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
