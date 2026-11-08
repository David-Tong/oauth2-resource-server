package io.ytong.resourceserver;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoResourceController {
    record DemoResource(String message, String subject, List<String> items) {}

    @GetMapping("/api/resources/demo")
    ResponseEntity<DemoResource> demo(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new DemoResource(
                "You have successfully accessed the protected resource.", jwt.getSubject(),
                List.of("OAuth2 protected demo resource", "Requires demo.read scope")));
    }
}
