package com.rinda.backend.ml;

import com.rinda.backend.common.ApiException;
import com.rinda.backend.common.ThresholdService;
import com.rinda.backend.config.RindaProperties;
import com.rinda.backend.ml.MlDtos.MlResultRequest;
import com.rinda.backend.ml.MlDtos.MlResultResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

    private static final String KEY_HEADER = "X-Internal-Api-Key";

    private final MlResultService service;
    private final ThresholdService thresholds;
    private final byte[] apiKey;

    public InternalMlController(MlResultService service, ThresholdService thresholds, RindaProperties properties) {
        this.service = service;
        this.thresholds = thresholds;
        this.apiKey = properties.ml().internalApiKey().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/results")
    public MlResultResponse results(@RequestHeader(name = KEY_HEADER, required = false) String key,
                                    @Valid @RequestBody MlResultRequest result) {
        requireKey(key);
        return service.save(result);
    }

    @GetMapping("/thresholds")
    public Map<String, BigDecimal> thresholds(@RequestHeader(name = KEY_HEADER, required = false) String key) {
        requireKey(key);
        return thresholds.all();
    }

    private void requireKey(String key) {
        if (key == null || !MessageDigest.isEqual(apiKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_KEY_INVALID", "A valid internal API key is required.");
        }
    }
}
