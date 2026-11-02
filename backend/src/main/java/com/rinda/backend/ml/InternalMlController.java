package com.rinda.backend.ml;

import com.rinda.backend.common.ApiException;
import com.rinda.backend.config.RindaProperties;
import com.rinda.backend.ml.MlDtos.MlResultRequest;
import com.rinda.backend.ml.MlDtos.MlResultResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Called only by the ML service. It has no JWT, so the API key is checked here. */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/ml")
public class InternalMlController {

    private final MlResultService service;
    private final byte[] apiKey;

    public InternalMlController(MlResultService service, RindaProperties properties) {
        this.service = service;
        this.apiKey = properties.ml().internalApiKey().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/results")
    public MlResultResponse results(
            @RequestHeader(name = "X-Internal-Api-Key", required = false) String key,
            @Valid @RequestBody MlResultRequest result) {
        if (key == null || !MessageDigest.isEqual(apiKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_KEY_INVALID", "A valid internal API key is required.");
        }
        return service.save(result);
    }
}
