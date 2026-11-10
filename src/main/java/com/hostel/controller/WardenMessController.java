package com.hostel.controller;

import com.hostel.dto.MessDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.MessTokenService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/warden/mess")
@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
public class WardenMessController {

    private final MessTokenService messService;
    private final UserRepository userRepository;

    public WardenMessController(MessTokenService messService, UserRepository userRepository) {
        this.messService = messService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @GetMapping("/items")
    public ResponseEntity<List<FoodItemResponse>> listItems(@RequestParam(value = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(messService.listFoodItems(activeOnly));
    }

    @PostMapping(value = "/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FoodItemResponse> createItem(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam("name") String name,
            @RequestParam("category") String category,
            @RequestParam(value = "price", defaultValue = "0") BigDecimal price,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.ok(messService.createFoodItem(getActor(ud), name, category, price, photo));
    }

    @PatchMapping("/items/{id}/active")
    public ResponseEntity<FoodItemResponse> toggleItem(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestParam("active") boolean active) {
        return ResponseEntity.ok(messService.toggleFoodItem(getActor(ud), id, active));
    }

    @GetMapping("/periods")
    public ResponseEntity<List<PeriodConfigDto>> listPeriods() {
        return ResponseEntity.ok(messService.listPeriodConfigs());
    }

    @PutMapping("/periods")
    public ResponseEntity<PeriodConfigDto> updatePeriod(
            @AuthenticationPrincipal UserDetails ud,
            @Valid @RequestBody PeriodConfigDto request) {
        return ResponseEntity.ok(messService.updatePeriodConfig(getActor(ud), request));
    }

    @GetMapping("/schedule")
    public ResponseEntity<List<MenuScheduleResponse>> getSchedule(
            @RequestParam("date") LocalDate date,
            @RequestParam("period") String period) {
        return ResponseEntity.ok(messService.getMenuForDateAndPeriod(date, period));
    }

    @PostMapping("/schedule")
    public ResponseEntity<MenuScheduleResponse> scheduleItem(
            @AuthenticationPrincipal UserDetails ud,
            @Valid @RequestBody MenuScheduleItemRequest request) {
        return ResponseEntity.ok(messService.scheduleMenuItem(getActor(ud), request));
    }

    @GetMapping("/summary")
    public ResponseEntity<WardenMessSummaryResponse> getSummary(
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "period", required = false) String period) {
        return ResponseEntity.ok(messService.getWardenSummary(date, period));
    }

    @GetMapping("/tokens")
    public ResponseEntity<Page<MessTokenResponse>> listTokens(
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(messService.searchTokens(date, period, status, query, PageRequest.of(page, size)));
    }

    @GetMapping("/verify-qr")
    public ResponseEntity<MessScanVerifyResponse> verifyQr(
            @RequestParam("tokenOrCode") String tokenOrCode,
            @RequestParam(value = "hostelBlock", required = false) String hostelBlock) {
        return ResponseEntity.ok(messService.verifyQrOrCode(tokenOrCode, hostelBlock));
    }

    @PostMapping("/deliver")
    public ResponseEntity<MessTokenResponse> confirmDelivery(
            @AuthenticationPrincipal UserDetails ud,
            @Valid @RequestBody ConfirmMessDeliveryRequest request) {
        return ResponseEntity.ok(messService.confirmDelivery(getActor(ud), request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate) {
        String csv = messService.exportTokensCsv(fromDate, toDate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mess-tokens.csv\"")
                .body(csv.getBytes());
    }
}

