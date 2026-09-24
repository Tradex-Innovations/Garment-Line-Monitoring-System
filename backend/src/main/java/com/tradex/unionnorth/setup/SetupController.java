package com.tradex.unionnorth.setup;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {
    private final SetupCatalog catalog;
    private final MasterDataService masters;
    private final PayrollProfileService profiles;

    public SetupController(
            SetupCatalog catalog, MasterDataService masters, PayrollProfileService profiles) {
        this.catalog = catalog;
        this.masters = masters;
        this.profiles = profiles;
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog() {
        SetupAccess.require("EMPLOYEE_VIEW_ALL");
        return response(
                Map.of(
                        "masters",
                        catalog.definitions().stream()
                                .filter(d -> !d.financial() || SetupAccess.has("SALARY_VIEW"))
                                .toList(),
                        "generalFields",
                        catalog.generalFields(),
                        "financialFields",
                        SetupAccess.has("SALARY_VIEW") ? catalog.financialFields() : List.of()));
    }

    @GetMapping("/masters/{kind}")
    public ResponseEntity<?> list(@PathVariable String kind) {
        return response(masters.list(kind));
    }

    @PostMapping("/masters/{kind}")
    public ResponseEntity<?> create(
            @PathVariable String kind, @RequestBody MasterDataService.Save body) {
        return response(masters.save(kind, null, body));
    }

    @PutMapping("/masters/{kind}/{id}")
    public ResponseEntity<?> revise(
            @PathVariable String kind,
            @PathVariable UUID id,
            @RequestBody MasterDataService.Save body) {
        return response(masters.save(kind, id, body));
    }

    @GetMapping("/masters/{kind}/{id}/history")
    public ResponseEntity<?> masterHistory(@PathVariable String kind, @PathVariable UUID id) {
        return response(masters.history(kind, id));
    }

    @GetMapping("/employees/{id}")
    public ResponseEntity<?> profile(@PathVariable UUID id) {
        return response(profiles.get(id));
    }

    @PutMapping("/employees/{id}/general")
    public ResponseEntity<?> general(
            @PathVariable UUID id, @RequestBody PayrollProfileService.Save body) {
        return response(profiles.saveGeneral(id, body));
    }

    @PutMapping("/employees/{id}/financial")
    public ResponseEntity<?> financial(
            @PathVariable UUID id, @RequestBody PayrollProfileService.Save body) {
        return response(profiles.saveFinancial(id, body));
    }

    @PostMapping("/employees/{id}/linematrix")
    public ResponseEntity<?> link(
            @PathVariable UUID id, @RequestBody PayrollProfileService.Link body) {
        return response(profiles.link(id, body));
    }

    @PostMapping("/employees/{id}/activate")
    public ResponseEntity<?> activate(
            @PathVariable UUID id, @RequestBody PayrollProfileService.Action body) {
        return response(profiles.activate(id, body));
    }

    @PostMapping("/employees/{id}/hold")
    public ResponseEntity<?> hold(
            @PathVariable UUID id, @RequestBody PayrollProfileService.Action body) {
        return response(profiles.hold(id, body));
    }

    @PostMapping("/employees/{id}/resume")
    public ResponseEntity<?> resume(
            @PathVariable UUID id, @RequestBody PayrollProfileService.Action body) {
        return response(profiles.resume(id, body));
    }

    @GetMapping("/employees/{id}/history")
    public ResponseEntity<?> history(@PathVariable UUID id) {
        return response(profiles.history(id));
    }

    private ResponseEntity<?> response(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
