package ir.privatepay.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sandbox")
@Testcontainers
class SandboxFinancialScenarioTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("privatepay_test")
            .withUsername("privatepay")
            .withPassword("privatepay-test-only");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private SandboxScenarioRunner scenarios;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void allTwentyFinancialScenariosRunAgainstPostgresAndPreserveIntegrity() {
        List<Map<String, Object>> results = scenarios.runAll();

        assertEquals(20, results.size(), "The published financial suite must include all twenty cases.");
        assertTrue(results.stream().allMatch(result -> "PASS".equals(result.get("status"))),
                () -> "Scenario failures: " + results.stream().filter(result -> !"PASS".equals(result.get("status"))).toList());
        assertEquals(20, jdbc.queryForObject("SELECT count(*) FROM sandbox_scenario_runs", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT e.id FROM journal_entries e LEFT JOIN journal_lines l ON l.journal_id=e.id
                    GROUP BY e.id HAVING count(l.id)<2 OR COALESCE(sum(l.debit_irr),0)<>COALESCE(sum(l.credit_irr),0)
                ) invalid_journals
                """, Integer.class));
    }

    @Test
    void customerRoleCannotChangeCreditStatus() throws Exception {
        mvc.perform(post("/api/v1/sandbox/admin/credit/suspended")
                        .header("X-Demo-Role", "CUSTOMER")
                        .contentType("application/json")
                        .content("{\"reason\":\"unauthorized test\"}"))
                .andExpect(status().isForbidden());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM credit_accounts WHERE customer_id=?",
                String.class, SandboxService.DEMO_CUSTOMER));
    }

    @Test
    void invalidDemoOtpIsRejected() throws Exception {
        mvc.perform(post("/api/v1/sandbox/login")
                        .contentType("application/json")
                        .content("{\"mobile\":\"00000000000\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());
    }
}
