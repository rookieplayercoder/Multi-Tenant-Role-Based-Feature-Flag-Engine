package com.prateek.featureflag.segment;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for {@link SegmentUser}. No soft delete — a pure
 * associative row (see the entity's own Javadoc), so membership removal is
 * a real delete keyed by the composite {@link SegmentUserId}.
 */
@Service
@Transactional(readOnly = true)
public class SegmentUserService {

    private final SegmentUserRepository segmentUserRepository;

    public SegmentUserService(SegmentUserRepository segmentUserRepository) {
        this.segmentUserRepository = segmentUserRepository;
    }

    @Transactional
    public SegmentUser addMember(Segment segment, String userIdentifier) {
        SegmentUserId id = new SegmentUserId(segment.getId(), userIdentifier);
        if (segmentUserRepository.findById(id).isPresent()) {
            throw new IllegalStateException(
                    "User %s is already in segment %s".formatted(userIdentifier, segment.getId()));
        }
        return segmentUserRepository.save(new SegmentUser(segment, userIdentifier));
    }

    public List<SegmentUser> listMembers(UUID segmentId) {
        return segmentUserRepository.findBySegmentId(segmentId);
    }

    public List<SegmentUser> listSegmentsForUser(String userIdentifier) {
        return segmentUserRepository.findByIdUserIdentifier(userIdentifier);
    }

    @Transactional
    public void removeMember(UUID segmentId, String userIdentifier) {
        SegmentUserId id = new SegmentUserId(segmentId, userIdentifier);
        SegmentUser member = segmentUserRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User %s is not in segment %s".formatted(userIdentifier, segmentId)));
        segmentUserRepository.delete(member);
    }
}
