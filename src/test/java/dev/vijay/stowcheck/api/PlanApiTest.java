package dev.vijay.stowcheck.api;

import dev.vijay.stowcheck.TestPlans;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
class PlanApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void acceptsCleanPlan() throws Exception {
        mvc.perform(post("/api/plans").contentType(MediaType.TEXT_PLAIN)
                        .content(TestPlans.load("valid-plan.edi")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.summary.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.summary.vesselName").value("MSC AURORA"))
                .andExpect(jsonPath("$.summary.units").value(6))
                .andExpect(jsonPath("$.issues", hasSize(0)));
    }

    @Test
    void rejectsBrokenPlanUploadedAsFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "broken.edi",
                MediaType.TEXT_PLAIN_VALUE, TestPlans.load("broken-plan.edi").getBytes());

        mvc.perform(multipart("/api/plans").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.status").value("REJECTED"))
                .andExpect(jsonPath("$.issues[*].rule", hasItem("CHECK_DIGIT")))
                .andExpect(jsonPath("$.issues[*].rule", hasItem("CELL_CONFLICT")));
    }

    @Test
    void returns422ForTextThatIsNotEdifact() throws Exception {
        mvc.perform(post("/api/plans").contentType(MediaType.TEXT_PLAIN).content("not an edi file"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void returns404ForUnknownRun() throws Exception {
        mvc.perform(get("/api/plans/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesCellsAndDiff() throws Exception {
        long first = submit("valid-plan.edi");
        long second = submit("valid-plan-final.edi");

        mvc.perform(get("/api/plans/{id}/cells", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].cell").value("0010282"));

        mvc.perform(get("/api/plans/{from}/diff/{to}", first, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes", hasSize(5)));
    }

    @Test
    void exposesPrometheusMetrics() throws Exception {
        submit("broken-plan.edi");

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "stowcheck_issues_total{")));
    }

    private long submit(String fixture) throws Exception {
        MvcResult result = mvc.perform(post("/api/plans").contentType(MediaType.TEXT_PLAIN)
                        .content(TestPlans.load(fixture)))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }
}
