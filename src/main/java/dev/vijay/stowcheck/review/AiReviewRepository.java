package dev.vijay.stowcheck.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiReviewRepository extends JpaRepository<AiReview, Long> {

    Optional<AiReview> findTopByRunIdOrderByCreatedAtDesc(long runId);
}
