package dev.vijay.stowcheck.api;

import dev.vijay.stowcheck.review.AiReview;
import dev.vijay.stowcheck.review.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Optional AI second opinion on a run's findings. Advisory only. */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/ai/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", reviews.enabled());
    }

    /** Ask the model to review this run's findings. Each call creates a new review. */
    @PostMapping("/plans/{id}/review")
    public ResponseEntity<AiReview> review(@PathVariable long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviews.review(id));
    }

    @GetMapping("/plans/{id}/review")
    public ResponseEntity<AiReview> latest(@PathVariable long id) {
        return ResponseEntity.of(reviews.latest(id));
    }
}
