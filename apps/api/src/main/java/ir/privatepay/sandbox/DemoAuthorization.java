package ir.privatepay.sandbox;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Role checks for the loopback-only sandbox. This is not production authentication. */
@Component
public class DemoAuthorization {
    public void requireRole(String actual, String... allowed) {
        for (String role : allowed) if (role.equals(actual)) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "این نقش در محیط آزمایشی اجازهٔ این عملیات را ندارد.");
    }
}
