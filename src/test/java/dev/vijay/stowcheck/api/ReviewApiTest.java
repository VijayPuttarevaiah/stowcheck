package dev.vijay.stowcheck.api;

import dev.vijay.stowcheck.TestPlans;
import dev.vijay.stowcheck.review.ModelReview;
import dev.vijay.stowcheck.review.ReviewModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end review flow with a fake model standing in for Groq. */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewApiTest {

    static final AtomicReference<String> lastPrompt = new AtomicReference<>();

    @TestConfiguration
    static class FakeModel {
        @Bean
        ReviewModel reviewModel() {
            return prompt -> {
                lastPrompt.set(prompt);
                // confirms finding 0, says nothing about the rest
                return new ReviewModel.Result(new ModelReview(List.of(
                        new ModelReview.Finding(0, ModelReview.Verdict.CONFIRMED, "Segment count is wrong.", "Resend")),
                        "UNT count is wrong; please resend."), "fake-model", 100, 20);
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void reviewsEveryFindingWithoutChangingTheRunStatus() throws Exception {
        String location = mvc.perform(post("/api/plans").contentType(MediaType.TEXT_PLAIN)
                        .content(TestPlans.load("broken-plan.edi")))
                .andReturn().getResponse().getHeader("Location");
        String run = location.substring(location.lastIndexOf('/') + 1);

        mvc.perform(get("/api/ai/status")).andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(post("/api/plans/{id}/review", run))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model").value("fake-model"))
                .andExpect(jsonPath("$.findings", hasSize(13)))
                .andExpect(jsonPath("$.findings[0].verdict").value("CONFIRMED"))
                .andExpect(jsonPath("$.findings[1].verdict").value("NEEDS_HUMAN"))
                .andExpect(jsonPath("$.carrierMessage").value("UNT count is wrong; please resend."));

        mvc.perform(get("/api/plans/{id}/review", run))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings", hasSize(13)));

        mvc.perform(get("/api/plans/{id}", run))
                .andExpect(jsonPath("$.summary.status").value("REJECTED"));

        // the prompt carries each finding's raw segments as evidence
        assertThat(lastPrompt.get())
                .contains("Finding 1: ERROR CHECK_DIGIT")
                .contains("<segments cell=\"0010282\">")
                .contains("EQD+CN+MSCU1234560+22G1+++5'");
    }

    @Test
    void cleanPlanHasNothingToReview() throws Exception {
        String location = mvc.perform(post("/api/plans").contentType(MediaType.TEXT_PLAIN)
                        .content(TestPlans.load("valid-plan.edi")))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(post(location + "/review"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("This plan has no findings to review"));
    }
}
