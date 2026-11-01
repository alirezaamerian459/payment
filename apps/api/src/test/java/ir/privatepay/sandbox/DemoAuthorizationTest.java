package ir.privatepay.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DemoAuthorizationTest {
    private final DemoAuthorization authorization = new DemoAuthorization();

    @Test
    void onlyExplicitlyAllowedSandboxRolesPass() {
        assertDoesNotThrow(() -> authorization.requireRole("FINANCE_OFFICER", "FINANCE_OFFICER", "SUPER_ADMIN"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> authorization.requireRole("CUSTOMER", "SUPER_ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }
}
