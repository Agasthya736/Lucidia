package com.lucidia.backend.consent;

import com.lucidia.backend.api.ScanController;
import com.lucidia.backend.auth.User;
import com.lucidia.backend.auth.UserRepository;
import com.lucidia.backend.feedback.ScanFeedbackRepository;
import com.lucidia.backend.quota.QuotaService;
import com.lucidia.backend.scan.ImageStorageService;
import com.lucidia.backend.scan.ReportPdfService;
import com.lucidia.backend.scan.ScanService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScanConsentControllerTest {

    @Test
    void submitWithoutConsentReturnsMachineReadableForbiddenResponse()
            throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User("Consent User", "consent@example.com", "hash");
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, userId);

        UserRepository users = mock(UserRepository.class);
        ConsentService consents = mock(ConsentService.class);
        when(users.findByEmail("consent@example.com")).thenReturn(Optional.of(user));
        when(consents.hasConsented(userId)).thenReturn(false);

        ScanController controller = new ScanController(
                mock(ScanService.class),
                users,
                mock(ReportPdfService.class),
                mock(ImageStorageService.class),
                mock(QuotaService.class),
                mock(com.lucidia.backend.synthesis.ReportSynthesisService.class),
                consents,
                mock(ScanFeedbackRepository.class)
        );
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "consent@example.com")
        );

        ResponseEntity<?> response =
                controller.submit(jwt, (List<MultipartFile>) null, null, "CT_SERIES", null);

        assertEquals(403, response.getStatusCode().value());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("CONSENT_REQUIRED", body.get("code"));
        assertEquals("Please accept the consent screen first", body.get("message"));
    }
}
