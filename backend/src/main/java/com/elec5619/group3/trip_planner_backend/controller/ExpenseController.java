package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class ExpenseController {
    private final ExpenseService service;
    public ExpenseController(ExpenseService service) { this.service = service; }

    @GetMapping("/expenses")
    public List<ExpenseResponse> list(@PathVariable Long tripId, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(tripId, user.getUserId());
    }
    @GetMapping("/expenses/summary")
    public ExpenseSummaryResponse summary(@PathVariable Long tripId, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.summary(tripId, user.getUserId());
    }
    @GetMapping("/expenses/{expenseId}")
    public ExpenseResponse get(@PathVariable Long tripId, @PathVariable Long expenseId, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(tripId, expenseId, user.getUserId());
    }
    @PostMapping("/expenses") @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@PathVariable Long tripId, @Valid @RequestBody ExpenseRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.create(tripId, request, user.getUserId());
    }
    @PutMapping("/expenses/{expenseId}")
    public ExpenseResponse update(@PathVariable Long tripId, @PathVariable Long expenseId, @Valid @RequestBody ExpenseRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.update(tripId, expenseId, request, user.getUserId());
    }
    @DeleteMapping("/expenses/{expenseId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long tripId, @PathVariable Long expenseId, @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(tripId, expenseId, user.getUserId());
    }
    @GetMapping("/expenses/{expenseId}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long tripId, @PathVariable Long expenseId, @AuthenticationPrincipal AuthenticatedUser user) {
        var receipt = service.receipt(tripId, expenseId, user.getUserId());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(receipt.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(receipt.fileName(), java.nio.charset.StandardCharsets.UTF_8))
                .header("X-Content-Type-Options", "nosniff").body(receipt.bytes());
    }
    @PostMapping(value = "/expenses/{expenseId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExpenseResponse saveReceipt(@PathVariable Long tripId, @PathVariable Long expenseId, @RequestPart("file") MultipartFile file, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.saveReceipt(tripId, expenseId, file, user.getUserId());
    }
    @DeleteMapping("/expenses/{expenseId}/receipt") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReceipt(@PathVariable Long tripId, @PathVariable Long expenseId, @AuthenticationPrincipal AuthenticatedUser user) {
        service.removeReceipt(tripId, expenseId, user.getUserId());
    }
    @GetMapping("/budget")
    public BudgetResponse budget(@PathVariable Long tripId, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.budget(tripId, user.getUserId());
    }
    @PutMapping("/budget")
    public BudgetResponse budget(@PathVariable Long tripId, @Valid @RequestBody BudgetRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.updateBudget(tripId, request, user.getUserId());
    }
}
