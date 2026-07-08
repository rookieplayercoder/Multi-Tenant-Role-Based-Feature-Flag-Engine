package com.prateek.featureflag.flag;

import com.prateek.featureflag.environment.Environment;
import com.prateek.featureflag.environment.EnvironmentType;
import com.prateek.featureflag.organization.Organization;
import com.prateek.featureflag.project.Project;
import com.prateek.featureflag.user.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FlagVersionService}. {@code rollback} itself lives
 * on {@link FeatureFlagService} and is already covered by
 * {@code FeatureFlagServiceTest.Rollback} (successful rollback, snapshot
 * restoration, version-not-found propagation) — this class instead covers
 * {@link FlagVersionService}'s own responsibilities: recording snapshots
 * (including the duplicate-version guard), and resolving a specific or
 * latest version.
 */
@ExtendWith(MockitoExtension.class)
class FlagVersionServiceTest {

    @Mock
    private FlagVersionRepository flagVersionRepository;

    private FlagVersionService flagVersionService;

    @BeforeEach
    void setUp() {
        flagVersionService = new FlagVersionService(flagVersionRepository);
    }

    private static FeatureFlag persistedFlag() {
        Organization organization = new Organization("Acme Inc", "acme");
        Project project = new Project(organization, "Web App", "web");
        Environment environment = new Environment(project, "Production", EnvironmentType.PROD);
        User createdBy = new User("creator@example.com", "hash", "Creator");
        FeatureFlag flag = new FeatureFlag(environment, "checkout-flow", "Checkout Flow", createdBy);
        ReflectionTestUtils.setField(flag, "id", UUID.randomUUID());
        return flag;
    }

    private static User persistedUser(String email) {
        User user = new User(email, "hash", "Test User");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Nested
    class RecordSnapshot {

        @Test
        void savesSnapshotWhenVersionIsNotAlreadyRecorded() {
            FeatureFlag flag = persistedFlag();
            User changedBy = persistedUser("changer@example.com");
            when(flagVersionRepository.findByFeatureFlagIdAndVersion(flag.getId(), 1)).thenReturn(Optional.empty());
            when(flagVersionRepository.save(any(FlagVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

            FlagVersion result = flagVersionService.recordSnapshot(
                    flag, 1, "{\"enabled\":true}", changedBy, "Flag created");

            assertThat(result.getFeatureFlag()).isEqualTo(flag);
            assertThat(result.getVersion()).isEqualTo(1);
            assertThat(result.getSnapshot()).isEqualTo("{\"enabled\":true}");
            assertThat(result.getChangedBy()).isEqualTo(changedBy);
            assertThat(result.getChangeSummary()).isEqualTo("Flag created");
        }

        @Test
        void throwsWhenVersionIsAlreadyRecordedForTheFlag() {
            FeatureFlag flag = persistedFlag();
            User changedBy = persistedUser("changer@example.com");
            FlagVersion existing = new FlagVersion(flag, 1, "{\"enabled\":false}", changedBy, "Flag created");
            when(flagVersionRepository.findByFeatureFlagIdAndVersion(flag.getId(), 1))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> flagVersionService.recordSnapshot(
                    flag, 1, "{\"enabled\":true}", changedBy, "Flag updated"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("1")
                    .hasMessageContaining(flag.getId().toString());

            verify(flagVersionRepository, never()).save(any());
        }
    }

    @Nested
    class ListHistory {

        @Test
        void delegatesToRepositoryOrderedByVersionDescending() {
            FeatureFlag flag = persistedFlag();
            User changedBy = persistedUser("changer@example.com");
            List<FlagVersion> expected = List.of(
                    new FlagVersion(flag, 2, "{\"enabled\":true}", changedBy, "Flag enabled"),
                    new FlagVersion(flag, 1, "{\"enabled\":false}", changedBy, "Flag created"));
            when(flagVersionRepository.findByFeatureFlagIdOrderByVersionDesc(flag.getId())).thenReturn(expected);

            List<FlagVersion> result = flagVersionService.listHistory(flag.getId());

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    class GetVersion {

        @Test
        void returnsVersionWhenFound() {
            FeatureFlag flag = persistedFlag();
            User changedBy = persistedUser("changer@example.com");
            FlagVersion version = new FlagVersion(flag, 1, "{\"enabled\":false}", changedBy, "Flag created");
            when(flagVersionRepository.findByFeatureFlagIdAndVersion(flag.getId(), 1))
                    .thenReturn(Optional.of(version));

            FlagVersion result = flagVersionService.getVersion(flag.getId(), 1);

            assertThat(result).isEqualTo(version);
        }

        @Test
        void throwsWhenVersionNotFoundForFlag() {
            FeatureFlag flag = persistedFlag();
            when(flagVersionRepository.findByFeatureFlagIdAndVersion(flag.getId(), 99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> flagVersionService.getVersion(flag.getId(), 99))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99")
                    .hasMessageContaining(flag.getId().toString());
        }
    }

    @Nested
    class GetLatest {

        @Test
        void returnsTheHighestVersionRecordedForTheFlag() {
            FeatureFlag flag = persistedFlag();
            User changedBy = persistedUser("changer@example.com");
            FlagVersion latest = new FlagVersion(flag, 3, "{\"enabled\":true}", changedBy, "Flag type changed");
            when(flagVersionRepository.findTopByFeatureFlagIdOrderByVersionDesc(flag.getId()))
                    .thenReturn(Optional.of(latest));

            FlagVersion result = flagVersionService.getLatest(flag.getId());

            assertThat(result).isEqualTo(latest);
            assertThat(result.getVersion()).isEqualTo(3);
        }

        @Test
        void throwsWhenNoVersionsHaveEverBeenRecordedForTheFlag() {
            FeatureFlag flag = persistedFlag();
            when(flagVersionRepository.findTopByFeatureFlagIdOrderByVersionDesc(flag.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> flagVersionService.getLatest(flag.getId()))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining(flag.getId().toString());
        }
    }
}