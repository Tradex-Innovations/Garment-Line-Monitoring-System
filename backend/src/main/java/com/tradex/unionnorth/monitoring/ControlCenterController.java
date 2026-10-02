package com.tradex.unionnorth.monitoring;

import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping("/api/v1/developer")
public class ControlCenterController {
    private final ControlCenterService service;
    private final PipelineStore store;
    private final Set<SseEmitter> streams = ConcurrentHashMap.newKeySet();

    public ControlCenterController(ControlCenterService service, PipelineStore store) {
        this.service = service;
        this.store = store;
    }

    @GetMapping("/snapshot")
    @PreAuthorize("hasRole('DEVELOPER')")
    public org.springframework.http.ResponseEntity<Map<String, Object>> snapshot() {
        return org.springframework.http.ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(service.snapshot());
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('DEVELOPER')")
    public org.springframework.http.ResponseEntity<List<PipelineEvent>> events(
            @RequestParam(required = false) @Size(max = 60) String pipeline,
            @RequestParam(required = false) @Size(max = 80) String search,
            @RequestParam(defaultValue = "250") int limit) {
        try {
            return org.springframework.http.ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(store.events(pipeline, search, limit));
        } catch (RuntimeException ignored) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Telemetry history is unavailable");
        }
    }

    // Bounded streams end before JWT expiry. Each reconnect revalidates the bearer token.
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('DEVELOPER')")
    public SseEmitter stream(
            JwtAuthenticationToken auth, jakarta.servlet.http.HttpServletResponse response) {
        Instant expiry = auth.getToken().getExpiresAt();
        if (expiry == null || !expiry.isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        long timeout =
                Math.max(
                        1,
                        Math.min(
                                55000,
                                java.time.Duration.between(Instant.now(), expiry).toMillis()));
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        var emitter = new SseEmitter(timeout);
        synchronized (streams) {
            if (streams.size() >= 20)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            streams.add(emitter);
        }
        emitter.onCompletion(() -> streams.remove(emitter));
        emitter.onTimeout(
                () -> {
                    streams.remove(emitter);
                    emitter.complete();
                });
        emitter.onError(error -> streams.remove(emitter));
        send(emitter);
        return emitter;
    }

    @Scheduled(fixedDelay = 5000)
    public void heartbeat() {
        streams.forEach(this::send);
    }

    private void send(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("refresh").data(Map.of("at", Instant.now())));
        } catch (Exception ignored) {
            streams.remove(emitter);
            emitter.complete();
        }
    }
}
