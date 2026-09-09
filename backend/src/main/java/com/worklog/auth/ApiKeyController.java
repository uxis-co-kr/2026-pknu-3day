package com.worklog.auth;

import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개인 API Key 관리 (PRD 7. POST/DELETE /me/api-keys).
 *
 * <p>목록에는 평문 키가 없다. 발급 응답에만 있다.
 */
@RestController
@RequestMapping("/me/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<IssuedResponse> issue(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody(required = false) IssueRequest request) {
        String label = request == null ? null : request.label();
        ApiKeyService.Issued issued = apiKeyService.issue(principal.id(), label);
        ApiKey apiKey = issued.apiKey();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IssuedResponse(
                        apiKey.getId(), issued.plainKey(), apiKey.getLabel(), apiKey.getCreatedAt()));
    }

    @GetMapping
    public List<KeyResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return apiKeyService.list(principal.id()).stream()
                .map(k -> new KeyResponse(k.getId(), k.getLabel(), k.getCreatedAt(), k.getLastUsedAt()))
                .toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        apiKeyService.revoke(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    public record IssueRequest(@Size(max = 100) String label) {}

    /** {@code key} 는 이 응답에서만 볼 수 있다. */
    public record IssuedResponse(Long id, String key, String label, OffsetDateTime createdAt) {}

    public record KeyResponse(
            Long id, String label, OffsetDateTime createdAt, OffsetDateTime lastUsedAt) {}
}
