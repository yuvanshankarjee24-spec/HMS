package com.hostel.controller;

import com.hostel.dto.ParcelDtos.*;
import com.hostel.entity.Parcel;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.ParcelService;
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
@RequestMapping("/api/warden/parcels")
@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
public class WardenParcelController {

    private final ParcelService parcelService;
    private final UserRepository userRepository;

    public WardenParcelController(ParcelService parcelService, UserRepository userRepository) {
        this.parcelService = parcelService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @GetMapping("/students")
    public ResponseEntity<List<StudentSearchItem>> searchStudents(@RequestParam(value = "query", required = false) String query) {
        return ResponseEntity.ok(parcelService.searchStudents(query));
    }

    @GetMapping("/summary")
    public ResponseEntity<WardenParcelSummaryResponse> getSummary() {
        return ResponseEntity.ok(parcelService.getWardenSummary());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParcelResponse> createParcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("studentId") Long studentId,
            @RequestParam("courierName") String courierName,
            @RequestParam(value = "trackingNumber", required = false) String trackingNumber,
            @RequestParam(value = "parcelType", defaultValue = "MEDIUM") String parcelType,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "cod", defaultValue = "false") boolean cod,
            @RequestParam(value = "codAmount", required = false) BigDecimal codAmount,
            @RequestParam(value = "storageLocation", required = false) String storageLocation,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.ok(parcelService.createParcel(
                getActor(userDetails), studentId, courierName, trackingNumber,
                parcelType, description, cod, codAmount, storageLocation, photo));
    }

    @GetMapping
    public ResponseEntity<Page<ParcelResponse>> listParcels(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "courier", required = false) String courier,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(parcelService.searchParcels(
                status, courier, fromDate, toDate, query, PageRequest.of(page, size)));
    }

    @GetMapping("/long-pending")
    public ResponseEntity<List<ParcelResponse>> getLongPending() {
        return ResponseEntity.ok(parcelService.getLongPendingParcels());
    }

    @GetMapping("/verify-qr")
    public ResponseEntity<ParcelScanVerifyResponse> verifyQr(
            @RequestParam("tokenOrCode") String tokenOrCode,
            @RequestParam(value = "hostelBlock", required = false) String hostelBlock) {
        return ResponseEntity.ok(parcelService.verifyScan(tokenOrCode, hostelBlock));
    }

    @PostMapping("/deliver")
    public ResponseEntity<ParcelResponse> confirmDelivery(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ConfirmDeliveryRequest request) {
        return ResponseEntity.ok(parcelService.confirmDelivery(getActor(userDetails), request));
    }

    @PostMapping("/{id}/return-sender")
    public ResponseEntity<ParcelResponse> markReturnToSender(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ParcelReasonRequest request) {
        return ResponseEntity.ok(parcelService.transitionStatus(
                getActor(userDetails), id, Parcel.Status.RETURNED_TO_SENDER, request.reason()));
    }

    @PostMapping("/{id}/unclaimed")
    public ResponseEntity<ParcelResponse> markUnclaimed(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ParcelReasonRequest request) {
        return ResponseEntity.ok(parcelService.transitionStatus(
                getActor(userDetails), id, Parcel.Status.UNCLAIMED, request.reason()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ParcelResponse> cancelParcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ParcelReasonRequest request) {
        return ResponseEntity.ok(parcelService.transitionStatus(
                getActor(userDetails), id, Parcel.Status.CANCELLED, request.reason()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate) {
        String csv = parcelService.exportParcelsCsv(fromDate, toDate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parcel-register.csv\"")
                .body(csv.getBytes());
    }
}

