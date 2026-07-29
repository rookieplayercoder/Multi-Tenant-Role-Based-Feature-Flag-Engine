package com.prateek.featureflag.apikey;

import com.prateek.featureflag.environment.Environment;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
@Transactional(readOnly = true)
public class EnvironmentApiKeyService {

    private final EnvironmentApiKeyRepository environmentApiKeyRepository;

    public EnvironmentApiKeyService(EnvironmentApiKeyRepository environmentApiKeyRepository) {
        this.environmentApiKeyRepository = environmentApiKeyRepository;
    }

    @Transactional
    public EnvironmentApiKey issue(Environment environment, String keyHash, String keyPrefix, String name) {
        return environmentApiKeyRepository.save(new EnvironmentApiKey(environment, keyHash, keyPrefix, name));
    }

    public EnvironmentApiKey getActiveByHash(String keyHash) {
        return environmentApiKeyRepository.findByKeyHashAndRevokedAtIsNull(keyHash)
                .orElseThrow(() -> new EntityNotFoundException("No active API key found for provided hash"));
    }

    public EnvironmentApiKey getById(UUID id) {
        return environmentApiKeyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("API key not found: " + id));
    }

    public List<EnvironmentApiKey> listByEnvironment(UUID environmentId) {
        return environmentApiKeyRepository.findByEnvironmentId(environmentId);
    }

    @Transactional
    public void revoke(UUID id) {
        EnvironmentApiKey apiKey = environmentApiKeyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("API key not found: " + id));
        apiKey.setRevokedAt(Instant.now());
        environmentApiKeyRepository.save(apiKey);
    }

    @Transactional
    public void recordUsage(UUID id) {
        EnvironmentApiKey apiKey = environmentApiKeyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("API key not found: " + id));
        apiKey.setLastUsedAt(Instant.now());
        environmentApiKeyRepository.save(apiKey);
    }
}
