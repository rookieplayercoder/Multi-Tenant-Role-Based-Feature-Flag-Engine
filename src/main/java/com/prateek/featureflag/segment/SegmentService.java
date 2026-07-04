package com.prateek.featureflag.segment;

import com.prateek.featureflag.organization.Organization;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link Segment}. Duplicate-name check reuses
 * {@code findByOrganizationIdAndNameAndDeletedAtIsNull} — no new
 * repository method added.
 */
@Service
@Transactional(readOnly = true)
public class SegmentService {

    private final SegmentRepository segmentRepository;

    public SegmentService(SegmentRepository segmentRepository) {
        this.segmentRepository = segmentRepository;
    }

    @Transactional
    public Segment create(Organization organization, String name) {
        if (segmentRepository.findByOrganizationIdAndNameAndDeletedAtIsNull(organization.getId(), name).isPresent()) {
            throw new IllegalStateException("Segment name already in use in this organization: " + name);
        }
        return segmentRepository.save(new Segment(organization, name));
    }

    public Segment getActiveById(UUID id) {
        return segmentRepository.findById(id)
                .filter(segment -> !segment.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Segment not found: " + id));
    }

    public List<Segment> listActiveByOrganization(UUID organizationId) {
        return segmentRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    @Transactional
    public Segment rename(UUID id, String newName) {
        Segment segment = getActiveById(id);
        segment.setName(newName);
        return segmentRepository.save(segment);
    }

    @Transactional
    public Segment updateDescription(UUID id, String description) {
        Segment segment = getActiveById(id);
        segment.setDescription(description);
        return segmentRepository.save(segment);
    }

    @Transactional
    public void softDelete(UUID id) {
        Segment segment = getActiveById(id);
        segment.setDeletedAt(Instant.now());
        segmentRepository.save(segment);
    }
}
