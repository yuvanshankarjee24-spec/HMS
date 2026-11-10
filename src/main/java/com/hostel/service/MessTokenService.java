package com.hostel.service;

import com.hostel.dto.MessDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    private final FoodItemRepository foodItemRepository;
    private final MessPeriodConfigRepository periodConfigRepository;
    private final MenuScheduleRepository menuScheduleRepository;
    private final MessTokenRepository messTokenRepository;
    private final MessTokenItemRepository tokenItemRepository;
    private final TokenQrRepository tokenQrRepository;
    private final TokenStatusHistoryRepository historyRepository;
    private final StudentRepository studentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final BedRepository bedRepository;
    private final SystemSettingRepository settingRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${app.jwt.secret}")
    private String hmacSecret;

    @Value("${app.upload.dir:./storage/photos}")
    private String uploadDir;

    public MessTokenService(FoodItemRepository foodItemRepository,
                            MessPeriodConfigRepository periodConfigRepository,
                            MenuScheduleRepository menuScheduleRepository,
                            MessTokenRepository messTokenRepository,
                            MessTokenItemRepository tokenItemRepository,
                            TokenQrRepository tokenQrRepository,
                            TokenStatusHistoryRepository historyRepository,
                            StudentRepository studentRepository,
                            StaffProfileRepository staffProfileRepository,
                            BedRepository bedRepository,
                            SystemSettingRepository settingRepository,
                            NotificationService notificationService,
                            AuditService auditService) {
        this.foodItemRepository = foodItemRepository;
        this.periodConfigRepository = periodConfigRepository;
        this.menuScheduleRepository = menuScheduleRepository;
        this.messTokenRepository = messTokenRepository;
        this.tokenItemRepository = tokenItemRepository;
        this.tokenQrRepository = tokenQrRepository;
        this.historyRepository = historyRepository;
        this.studentRepository = studentRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.bedRepository = bedRepository;
        this.settingRepository = settingRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // =====================================================
    // SEED HELPER (7-DAY SAMPLE MENU)
    // =====================================================

    @Transactional
    public void ensureSampleWeeklyMenu(LocalDate startDate) {
        List<FoodItem> items = foodItemRepository.findAllByOrderByNameAsc();
        if (items.isEmpty()) return;

        Map<String, FoodItem> byName = items.stream()
                .collect(Collectors.toMap(FoodItem::getName, f -> f, (a, b) -> a));

        for (int i = 0; i < 7; i++) {
            LocalDate d = startDate.plusDays(i);
            if (menuScheduleRepository.existsByMenuDate(d)) continue;

            seedIfPresent(d, MessPeriodConfig.Period.BREAKFAST, byName.get("Boiled Egg"), 100, 4);
            seedIfPresent(d, MessPeriodConfig.Period.BREAKFAST, byName.get("Omelette"), 80, 2);

            seedIfPresent(d, MessPeriodConfig.Period.LUNCH, byName.get("Chicken Biryani"), 80, 2);
            seedIfPresent(d, MessPeriodConfig.Period.LUNCH, byName.get("Chicken Gravy"), 80, 2);
            seedIfPresent(d, MessPeriodConfig.Period.LUNCH, byName.get("Gobi Manchurian"), 100, 2);

            seedIfPresent(d, MessPeriodConfig.Period.SNACKS, byName.get("Omelette"), 60, 2);
            seedIfPresent(d, MessPeriodConfig.Period.SNACKS, byName.get("Gobi Manchurian"), 60, 2);

            seedIfPresent(d, MessPeriodConfig.Period.DINNER, byName.get("Chicken Gravy"), 90, 2);
            seedIfPresent(d, MessPeriodConfig.Period.DINNER, byName.get("Gobi Manchurian"), 90, 2);
            seedIfPresent(d, MessPeriodConfig.Period.DINNER, byName.get("Boiled Egg"), 100, 3);
        }
    }

    private void seedIfPresent(LocalDate d, MessPeriodConfig.Period p, FoodItem item, int stock, int maxPerStudent) {
        if (item == null) return;
        MenuSchedule ms = new MenuSchedule();
        ms.setMenuDate(d);
        ms.setPeriod(p);
        ms.setFoodItem(item);
        ms.setDailyStock(stock);
        ms.setRemainingStock(stock);
        ms.setMaxPerStudent(maxPerStudent);
        ms.setSpecialMenu(false);
        ms.setMessClosed(false);
        menuScheduleRepository.save(ms);
    }

    // =====================================================
    // FOOD ITEMS & PERIOD CONFIG & MENU SCHEDULE
    // =====================================================

    public List<FoodItemResponse> listFoodItems(boolean activeOnly) {
        List<FoodItem> list = activeOnly ? foodItemRepository.findByActiveTrueOrderByNameAsc()
                : foodItemRepository.findAllByOrderByNameAsc();
        return list.stream().map(this::toFoodResponse).collect(Collectors.toList());
    }

    @Transactional
    public FoodItemResponse createFoodItem(User actor, String name, String categoryStr, BigDecimal price, MultipartFile photo) {
        if (name == null || name.isBlank()) throw new BadRequestException("Food item name is required");
        if (foodItemRepository.existsByNameIgnoreCase(name.trim())) {
            throw new BadRequestException("Food item already exists: " + name.trim());
        }
        FoodItem f = new FoodItem();
        f.setName(name.trim());
        f.setCategory(parseEnum(FoodItem.Category.class, categoryStr, "category"));
        f.setPrice(price != null && price.compareTo(BigDecimal.ZERO) >= 0 ? price : BigDecimal.ZERO);
        f.setActive(true);
        if (photo != null && !photo.isEmpty()) {
            f.setPhotoPath(storeFoodPhoto(photo, name.trim()));
        }
        FoodItem saved = foodItemRepository.save(f);
        auditService.log(actor, "CREATE_FOOD_ITEM", saved.getName(), "SUCCESS");
        return toFoodResponse(saved);
    }

    @Transactional
    public FoodItemResponse toggleFoodItem(User actor, Long id, boolean active) {
        FoodItem f = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food item not found"));
        f.setActive(active);
        return toFoodResponse(foodItemRepository.save(f));
    }

    public List<PeriodConfigDto> listPeriodConfigs() {
        return periodConfigRepository.findAll().stream()
                .map(c -> new PeriodConfigDto(c.getPeriod().name(), c.getStartTime(), c.getEndTime(),
                        c.getCutoffMinutesBefore(), c.getQrOpenMinutesBefore()))
                .collect(Collectors.toList());
    }

    @Transactional
    public PeriodConfigDto updatePeriodConfig(User actor, PeriodConfigDto dto) {
        MessPeriodConfig.Period period = parseEnum(MessPeriodConfig.Period.class, dto.period(), "period");
        if (!dto.startTime().isBefore(dto.endTime())) {
            throw new BadRequestException("Meal start time must be before end time");
        }
        MessPeriodConfig cfg = periodConfigRepository.findById(period).orElseGet(() -> {
            MessPeriodConfig c = new MessPeriodConfig();
            c.setPeriod(period);
            return c;
        });
        cfg.setStartTime(dto.startTime());
        cfg.setEndTime(dto.endTime());
        cfg.setCutoffMinutesBefore(dto.cutoffMinutesBefore());
        cfg.setQrOpenMinutesBefore(dto.qrOpenMinutesBefore());
        MessPeriodConfig saved = periodConfigRepository.save(cfg);
        auditService.log(actor, "UPDATE_MESS_PERIOD", period.name(), "SUCCESS");
        return new PeriodConfigDto(saved.getPeriod().name(), saved.getStartTime(), saved.getEndTime(),
                saved.getCutoffMinutesBefore(), saved.getQrOpenMinutesBefore());
    }

    @Transactional
    public MenuScheduleResponse scheduleMenuItem(User actor, MenuScheduleItemRequest req) {
        MessPeriodConfig.Period period = parseEnum(MessPeriodConfig.Period.class, req.period(), "period");
        FoodItem item = foodItemRepository.findById(req.foodItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Food item not found"));

        MenuSchedule ms = menuScheduleRepository.findByMenuDateAndPeriodAndFoodItemId(
                req.menuDate(), period, item.getId()).orElseGet(MenuSchedule::new);
        int delta = ms.getId() != null ? (req.dailyStock() - ms.getDailyStock()) : req.dailyStock();
        ms.setMenuDate(req.menuDate());
        ms.setPeriod(period);
        ms.setFoodItem(item);
        ms.setDailyStock(req.dailyStock());
        ms.setRemainingStock(Math.max(0, ms.getId() != null ? ms.getRemainingStock() + delta : req.dailyStock()));
        ms.setMaxPerStudent(req.maxPerStudent());
        ms.setSpecialMenu(req.specialMenu());
        ms.setMessClosed(req.messClosed());

        MenuSchedule saved = menuScheduleRepository.save(ms);
        return toScheduleResponse(saved, LocalDateTime.now());
    }

    public List<MenuScheduleResponse> getMenuForDateAndPeriod(LocalDate date, String periodStr) {
        return getMenuForDateAndPeriodAt(date, periodStr, LocalDateTime.now());
    }

    public List<MenuScheduleResponse> getMenuForDateAndPeriodAt(LocalDate date, String periodStr, LocalDateTime serverNow) {
        MessPeriodConfig.Period period = parseEnum(MessPeriodConfig.Period.class, periodStr, "period");
        return menuScheduleRepository.findByMenuDateAndPeriodOrderByFoodItemNameAsc(date, period).stream()
                .filter(ms -> ms.getFoodItem().isActive())
                .map(ms -> toScheduleResponse(ms, serverNow))
                .collect(Collectors.toList());
    }

    // =====================================================
    // STUDENT BOOKING, CANCELLATION & MY TOKENS
    // =====================================================

    @Transactional
    public MessTokenResponse bookToken(User studentUser, BookTokenRequest req) {
        return bookTokenAt(studentUser, req, LocalDateTime.now());
    }

    @Transactional
    public MessTokenResponse bookTokenAt(User studentUser, BookTokenRequest req, LocalDateTime serverNow) {
        Student student = getStudentByUser(studentUser);
        MessPeriodConfig.Period period = parseEnum(MessPeriodConfig.Period.class, req.period(), "period");

        int advDays = Integer.parseInt(getSetting("mess.advance_booking_days", "7"));
        LocalDate today = serverNow.toLocalDate();
        if (req.menuDate().isBefore(today) || req.menuDate().isAfter(today.plusDays(advDays))) {
            throw new BadRequestException("Booking date must be between today and " + advDays + " days ahead");
        }

        MessPeriodConfig cfg = getPeriodConfigOrThrow(period);
        LocalDateTime cutoff = LocalDateTime.of(req.menuDate(), cfg.getStartTime()).minusMinutes(cfg.getCutoffMinutesBefore());
        if (!serverNow.isBefore(cutoff)) {
            throw new BadRequestException("Booking closed for " + period + " on " + req.menuDate()
                    + " (cutoff was " + cutoff.format(TS_FMT) + ")");
        }

        String block = bedRepository.findByStudentId(student.getId())
                .map(b -> "Floor " + b.getRoom().getFloor())
                .orElse("Main Block");

        MessToken token = new MessToken();
        token.setTokenCode(generateUniqueTokenCode());
        token.setStudent(student);
        token.setHostelBlock(block);
        token.setMenuDate(req.menuDate());
        token.setPeriod(period);
        token.setStatus(MessToken.Status.BOOKED);

        BigDecimal total = BigDecimal.ZERO;
        List<MessTokenItem> builtItems = new ArrayList<>();

        // Lock each schedule row deterministically sorted by ID to prevent deadlocks & overselling
        List<BookItemQty> sortedItems = req.items().stream()
                .sorted(Comparator.comparing(BookItemQty::menuScheduleId))
                .collect(Collectors.toList());

        for (BookItemQty line : sortedItems) {
            MenuSchedule ms = menuScheduleRepository.findByIdWithLock(line.menuScheduleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Scheduled menu item not found: " + line.menuScheduleId()));

            if (ms.isMessClosed()) {
                throw new BadRequestException("Mess is marked closed for " + period + " on " + req.menuDate());
            }
            if (!ms.getMenuDate().equals(req.menuDate()) || ms.getPeriod() != period) {
                throw new BadRequestException("Menu item does not belong to selected date/period");
            }
            if (!ms.getFoodItem().isActive()) {
                throw new BadRequestException(ms.getFoodItem().getName() + " is currently inactive");
            }
            int alreadyBooked = tokenItemRepository.sumActiveBookedQuantityForStudentAndSchedule(student.getId(), ms.getId());
            if (alreadyBooked + line.quantity() > ms.getMaxPerStudent()) {
                throw new BadRequestException("Max limit (" + ms.getMaxPerStudent() + ") exceeded for " + ms.getFoodItem().getName());
            }
            if (ms.getRemainingStock() < line.quantity()) {
                throw new ConflictException("Sold out / insufficient stock for " + ms.getFoodItem().getName()
                        + " (remaining: " + ms.getRemainingStock() + ")");
            }

            ms.setRemainingStock(ms.getRemainingStock() - line.quantity());
            menuScheduleRepository.save(ms);

            MessTokenItem item = new MessTokenItem();
            item.setFoodItem(ms.getFoodItem());
            item.setMenuSchedule(ms);
            item.setQuantity(line.quantity());
            item.setUnitPrice(ms.getFoodItem().getPrice());
            builtItems.add(item);

            total = total.add(ms.getFoodItem().getPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }

        token.setTotalAmount(total);
        // Check if already inside QR open window right at booking
        LocalDateTime qrOpensAt = LocalDateTime.of(req.menuDate(), cfg.getStartTime()).minusMinutes(cfg.getQrOpenMinutesBefore());
        if (!serverNow.isBefore(qrOpensAt)) {
            token.setStatus(MessToken.Status.QR_ACTIVE);
        }

        MessToken savedToken = messTokenRepository.save(token);
        for (MessTokenItem item : builtItems) {
            item.setToken(savedToken);
            tokenItemRepository.save(item);
        }

        TokenQr qr = new TokenQr();
        qr.setToken(savedToken);
        qr.setStudent(student);
        qr.setQrToken(generateSignedQrToken(savedToken.getId(), student.getId()));
        qr.setPickupCode(generateUniquePickupCode());
        qr.setUsed(false);
        qr.setClosed(false);
        tokenQrRepository.save(qr);

        recordHistory(savedToken, null, savedToken.getStatus().name(), studentUser, "Token booked");

        notificationService.notifyUser(
                student.getUser(),
                "Mess Token Confirmed: " + savedToken.getTokenCode(),
                "Booked " + savedToken.getPeriod() + " for " + savedToken.getMenuDate()
                        + ". QR opens at " + qrOpensAt.format(TIME_FMT) + ".",
                "MESS_TOKEN",
                savedToken.getId()
        );

        auditService.log(studentUser, "BOOK_MESS_TOKEN", savedToken.getTokenCode(), "SUCCESS");
        return toTokenResponse(savedToken, true, serverNow);
    }

    @Transactional
    public MessTokenResponse cancelToken(User studentUser, Long tokenId) {
        return cancelTokenAt(studentUser, tokenId, LocalDateTime.now());
    }

    @Transactional
    public MessTokenResponse cancelTokenAt(User studentUser, Long tokenId, LocalDateTime serverNow) {
        MessToken token = messTokenRepository.findByIdWithLock(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Mess token not found"));
        if (!token.getStudent().getUser().getId().equals(studentUser.getId())) {
            throw new ForbiddenException("You can only cancel your own mess tokens");
        }
        if (token.getStatus() != MessToken.Status.BOOKED && token.getStatus() != MessToken.Status.QR_ACTIVE) {
            throw new BadRequestException("Cannot cancel token in status: " + token.getStatus());
        }

        MessPeriodConfig cfg = getPeriodConfigOrThrow(token.getPeriod());
        LocalDateTime cutoff = LocalDateTime.of(token.getMenuDate(), cfg.getStartTime()).minusMinutes(cfg.getCutoffMinutesBefore());
        if (!serverNow.isBefore(cutoff)) {
            throw new BadRequestException("Cancellation closed (cutoff was " + cutoff.format(TS_FMT) + ")");
        }

        String oldStatus = token.getStatus().name();
        token.setStatus(MessToken.Status.CANCELLED);
        MessToken saved = messTokenRepository.save(token);

        for (MessTokenItem item : tokenItemRepository.findByTokenId(saved.getId())) {
            MenuSchedule ms = menuScheduleRepository.findByIdWithLock(item.getMenuSchedule().getId()).orElse(null);
            if (ms != null) {
                ms.setRemainingStock(ms.getRemainingStock() + item.getQuantity());
                menuScheduleRepository.save(ms);
            }
        }

        tokenQrRepository.findByTokenId(saved.getId()).ifPresent(qr -> {
            qr.setClosed(true);
            tokenQrRepository.save(qr);
        });

        recordHistory(saved, oldStatus, MessToken.Status.CANCELLED.name(), studentUser, "Cancelled by student before cutoff");
        auditService.log(studentUser, "CANCEL_MESS_TOKEN", saved.getTokenCode(), "SUCCESS");
        return toTokenResponse(saved, true, serverNow);
    }

    public Page<MessTokenResponse> getStudentTokens(User studentUser, String tab, Pageable pageable) {
        return getStudentTokensAt(studentUser, tab, pageable, LocalDateTime.now());
    }

    public Page<MessTokenResponse> getStudentTokensAt(User studentUser, String tab, Pageable pageable, LocalDateTime serverNow) {
        Student student = getStudentByUser(studentUser);
        String t = tab != null ? tab.trim().toUpperCase() : "UPCOMING";
        List<MessToken.Status> statuses;
        if ("DELIVERED".equals(t)) {
            statuses = List.of(MessToken.Status.DELIVERED, MessToken.Status.PARTIALLY_DELIVERED);
        } else if ("PAST".equals(t) || "CANCELLED".equals(t) || "EXPIRED".equals(t)) {
            statuses = List.of(MessToken.Status.CANCELLED, MessToken.Status.EXPIRED);
        } else {
            statuses = List.of(MessToken.Status.BOOKED, MessToken.Status.QR_ACTIVE);
        }
        return messTokenRepository.findByStudentAndStatuses(student.getId(), statuses, pageable)
                .map(tok -> toTokenResponse(tok, true, serverNow));
    }

    // =====================================================
    // WARDEN SCAN VERIFY & ATOMIC DELIVERY
    // =====================================================

    public MessScanVerifyResponse verifyQrOrCode(String tokenOrCode, String expectedBlock) {
        return verifyQrOrCodeAt(tokenOrCode, expectedBlock, LocalDateTime.now());
    }

    public MessScanVerifyResponse verifyQrOrCodeAt(String tokenOrCode, String expectedBlock, LocalDateTime serverNow) {
        if (tokenOrCode == null || tokenOrCode.isBlank()) {
            throw new BadRequestException("Scan QR or enter Token ID / 6-digit pickup code");
        }
        String trimmed = tokenOrCode.trim();
        if (trimmed.startsWith("TKN1.") && !verifySignedQrTokenFormat(trimmed)) {
            return new MessScanVerifyResponse(false, "Invalid or tampered QR token signature.", null);
        }

        Optional<TokenQr> qrOpt = tokenQrRepository.findByQrToken(trimmed);
        if (qrOpt.isEmpty()) {
            qrOpt = tokenQrRepository.findByPickupCode(trimmed);
        }

        MessToken token;
        TokenQr qr;
        if (qrOpt.isPresent()) {
            qr = qrOpt.get();
            token = qr.getToken();
        } else {
            token = messTokenRepository.findByTokenCode(trimmed.toUpperCase()).orElse(null);
            if (token == null) {
                return new MessScanVerifyResponse(false, "Token not found: " + trimmed, null);
            }
            qr = tokenQrRepository.findByTokenId(token.getId()).orElse(null);
        }

        if (expectedBlock != null && !expectedBlock.isBlank() && !"ALL".equalsIgnoreCase(expectedBlock.trim())) {
            if (!expectedBlock.trim().equalsIgnoreCase(token.getHostelBlock())) {
                return new MessScanVerifyResponse(false, "Token belongs to another hostel/block (" + token.getHostelBlock() + ")",
                        toTokenResponse(token, false, serverNow));
            }
        }

        if (token.getStatus() == MessToken.Status.DELIVERED || token.getStatus() == MessToken.Status.PARTIALLY_DELIVERED) {
            String when = token.getDeliveredAt() != null ? token.getDeliveredAt().format(TS_FMT) : "earlier";
            return new MessScanVerifyResponse(false, "Already delivered on " + when, toTokenResponse(token, false, serverNow));
        }
        if (token.getStatus() == MessToken.Status.CANCELLED) {
            return new MessScanVerifyResponse(false, "Token was cancelled.", toTokenResponse(token, false, serverNow));
        }
        if (token.getStatus() == MessToken.Status.EXPIRED) {
            return new MessScanVerifyResponse(false, "Token has expired (meal window ended).", toTokenResponse(token, false, serverNow));
        }
        if (qr != null && (qr.isUsed() || qr.isClosed())) {
            return new MessScanVerifyResponse(false, "QR token is already closed or used.", toTokenResponse(token, false, serverNow));
        }

        MessPeriodConfig cfg = getPeriodConfigOrThrow(token.getPeriod());
        LocalDateTime qrOpensAt = LocalDateTime.of(token.getMenuDate(), cfg.getStartTime()).minusMinutes(cfg.getQrOpenMinutesBefore());
        LocalDateTime mealEndsAt = LocalDateTime.of(token.getMenuDate(), cfg.getEndTime());

        if (serverNow.isBefore(qrOpensAt)) {
            return new MessScanVerifyResponse(false,
                    "QR is not active yet. Opens at " + qrOpensAt.format(TS_FMT),
                    toTokenResponse(token, false, serverNow));
        }
        if (serverNow.isAfter(mealEndsAt)) {
            return new MessScanVerifyResponse(false,
                    "Meal period ended at " + mealEndsAt.format(TS_FMT) + "; token expired.",
                    toTokenResponse(token, false, serverNow));
        }

        return new MessScanVerifyResponse(true, null, toTokenResponse(token, false, serverNow));
    }

    @Transactional
    public MessTokenResponse confirmDelivery(User warden, ConfirmMessDeliveryRequest req) {
        return confirmDeliveryAt(warden, req, LocalDateTime.now());
    }

    @Transactional
    public MessTokenResponse confirmDeliveryAt(User warden, ConfirmMessDeliveryRequest req, LocalDateTime serverNow) {
        Long tokenId = req.tokenId();
        if (tokenId == null) {
            MessScanVerifyResponse v = verifyQrOrCodeAt(req.tokenOrCode(), null, serverNow);
            if (!v.valid() || v.token() == null) {
                throw new BadRequestException(v.errorMessage() != null ? v.errorMessage() : "Invalid mess token");
            }
            tokenId = v.token().id();
        }

        MessToken token = messTokenRepository.findByIdWithLock(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Mess token not found"));

        if (token.getStatus() == MessToken.Status.DELIVERED || token.getStatus() == MessToken.Status.PARTIALLY_DELIVERED) {
            String when = token.getDeliveredAt() != null ? token.getDeliveredAt().format(TS_FMT) : "earlier";
            throw new ConflictException("Already delivered on " + when);
        }
        if (token.getStatus() == MessToken.Status.CANCELLED || token.getStatus() == MessToken.Status.EXPIRED) {
            throw new BadRequestException("Cannot deliver token in status: " + token.getStatus());
        }

        TokenQr qr = tokenQrRepository.findByTokenId(token.getId()).orElse(null);
        if (qr != null && (qr.isUsed() || qr.isClosed())) {
            throw new ConflictException("QR token is already closed or used");
        }

        MessPeriodConfig cfg = getPeriodConfigOrThrow(token.getPeriod());
        LocalDateTime qrOpensAt = LocalDateTime.of(token.getMenuDate(), cfg.getStartTime()).minusMinutes(cfg.getQrOpenMinutesBefore());
        LocalDateTime mealEndsAt = LocalDateTime.of(token.getMenuDate(), cfg.getEndTime());

        if (serverNow.isBefore(qrOpensAt)) {
            throw new BadRequestException("QR is not active yet. Opens at " + qrOpensAt.format(TS_FMT));
        }
        if (serverNow.isAfter(mealEndsAt)) {
            throw new BadRequestException("Meal period ended; token cannot be delivered");
        }

        List<MessTokenItem> items = tokenItemRepository.findByTokenId(token.getId());
        boolean partial = false;
        if (req.itemDeliveries() != null && !req.itemDeliveries().isEmpty()) {
            Map<Long, DeliveredItemLine> lineMap = req.itemDeliveries().stream()
                    .collect(Collectors.toMap(DeliveredItemLine::tokenItemId, d -> d, (a, b) -> b));
            for (MessTokenItem item : items) {
                DeliveredItemLine dl = lineMap.get(item.getId());
                if (dl != null) {
                    if (dl.deliveredQuantity() > item.getQuantity()) {
                        throw new BadRequestException("Delivered quantity cannot exceed booked quantity");
                    }
                    item.setDeliveredQuantity(dl.deliveredQuantity());
                    if (dl.deliveredQuantity() < item.getQuantity()) {
                        if (dl.undeliveredReason() == null || dl.undeliveredReason().isBlank()) {
                            throw new BadRequestException("Reason required for partial/undelivered item: " + item.getFoodItem().getName());
                        }
                        item.setUndeliveredReason(dl.undeliveredReason().trim());
                        partial = true;
                    }
                } else {
                    item.setDeliveredQuantity(item.getQuantity());
                }
                tokenItemRepository.save(item);
            }
        } else {
            for (MessTokenItem item : items) {
                item.setDeliveredQuantity(item.getQuantity());
                tokenItemRepository.save(item);
            }
        }

        String oldStatus = token.getStatus().name();
        MessToken.Status newStatus = partial ? MessToken.Status.PARTIALLY_DELIVERED : MessToken.Status.DELIVERED;
        token.setStatus(newStatus);
        token.setDeliveredAt(serverNow);
        token.setDeliveredBy(warden);
        token.setDeliveryRemarks(req.remarks() != null && !req.remarks().isBlank() ? req.remarks().trim() : null);

        if (qr != null) {
            qr.setUsed(true);
            qr.setClosed(true);
            qr.setUsedAt(serverNow);
            tokenQrRepository.save(qr);
        }

        MessToken saved = messTokenRepository.save(token);
        recordHistory(saved, oldStatus, newStatus.name(), warden,
                (partial ? "Partially delivered" : "Delivered in full")
                        + (saved.getDeliveryRemarks() != null ? " — " + saved.getDeliveryRemarks() : ""));

        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Mess Order Delivered: " + saved.getTokenCode(),
                "Your " + saved.getPeriod() + " token " + saved.getTokenCode() + " has been marked "
                        + newStatus.name().replace('_', ' ') + ".",
                "MESS_TOKEN",
                saved.getId()
        );

        auditService.log(warden, "DELIVER_MESS_TOKEN", saved.getTokenCode() + " Status=" + newStatus, "SUCCESS");
        return toTokenResponse(saved, false, serverNow);
    }

    // =====================================================
    // SCHEDULED JOBS: ACTIVATE & EXPIRE TOKENS + STOCK RELEASE
    // =====================================================

    @Transactional
    public void processMessTokenJobs(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        Map<MessPeriodConfig.Period, MessPeriodConfig> configs = periodConfigRepository.findAll().stream()
                .collect(Collectors.toMap(MessPeriodConfig::getPeriod, c -> c));

        // 1. Activate BOOKED tokens when QR opens (default 15m before start)
        List<MessToken> bookedToday = messTokenRepository.findByStatusInAndMenuDate(
                List.of(MessToken.Status.BOOKED), today);
        for (MessToken t : bookedToday) {
            MessPeriodConfig cfg = configs.get(t.getPeriod());
            if (cfg == null) continue;
            LocalDateTime opensAt = LocalDateTime.of(t.getMenuDate(), cfg.getStartTime()).minusMinutes(cfg.getQrOpenMinutesBefore());
            LocalDateTime endsAt = LocalDateTime.of(t.getMenuDate(), cfg.getEndTime());
            if (!now.isBefore(opensAt) && !now.isAfter(endsAt)) {
                t.setStatus(MessToken.Status.QR_ACTIVE);
                if (!t.isQrActivationNotified()) {
                    t.setQrActivationNotified(true);
                    notificationService.notifyUser(
                            t.getStudent().getUser(),
                            "Mess Token QR Active: " + t.getTokenCode(),
                            "Your " + t.getPeriod().name().toLowerCase() + " token (" + t.getTokenCode()
                                    + ") is now active. Show your QR at the mess counter.",
                            "MESS_TOKEN",
                            t.getId()
                    );
                }
                messTokenRepository.save(t);
                recordHistory(t, MessToken.Status.BOOKED.name(), MessToken.Status.QR_ACTIVE.name(), null, "QR activated automatically");
            }
        }

        // 2. Expire uncollected BOOKED / QR_ACTIVE tokens past end_time and release unused stock
        List<MessToken> openTokens = messTokenRepository.findByStatusInAndMenuDateLessThanEqual(
                List.of(MessToken.Status.BOOKED, MessToken.Status.QR_ACTIVE), today);
        for (MessToken t : openTokens) {
            MessPeriodConfig cfg = configs.get(t.getPeriod());
            if (cfg == null) continue;
            LocalDateTime endsAt = LocalDateTime.of(t.getMenuDate(), cfg.getEndTime());
            if (now.isAfter(endsAt)) {
                String old = t.getStatus().name();
                t.setStatus(MessToken.Status.EXPIRED);
                messTokenRepository.save(t);

                tokenQrRepository.findByTokenId(t.getId()).ifPresent(qr -> {
                    qr.setClosed(true);
                    tokenQrRepository.save(qr);
                });

                for (MessTokenItem item : tokenItemRepository.findByTokenId(t.getId())) {
                    menuScheduleRepository.findByIdWithLock(item.getMenuSchedule().getId()).ifPresent(ms -> {
                        ms.setRemainingStock(ms.getRemainingStock() + item.getQuantity());
                        menuScheduleRepository.save(ms);
                    });
                }
                recordHistory(t, old, MessToken.Status.EXPIRED.name(), null, "Expired after meal period; unused stock released");
            }
        }
    }

    // =====================================================
    // WARDEN & ADMIN LISTS, SUMMARIES & REPORTS
    // =====================================================

    public Page<MessTokenResponse> searchTokens(LocalDate date, String periodStr, String statusStr, String query, Pageable pageable) {
        MessPeriodConfig.Period period = (periodStr != null && !periodStr.isBlank() && !"ALL".equalsIgnoreCase(periodStr))
                ? parseEnum(MessPeriodConfig.Period.class, periodStr, "period") : null;
        MessToken.Status status = (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr))
                ? parseEnum(MessToken.Status.class, statusStr, "status") : null;
        String q = (query != null && !query.isBlank()) ? query.trim() : null;
        LocalDateTime now = LocalDateTime.now();
        return messTokenRepository.searchFiltered(date, period, status, q, pageable)
                .map(t -> toTokenResponse(t, false, now));
    }

    public WardenMessSummaryResponse getWardenSummary(LocalDate date, String periodStr) {
        LocalDate target = date != null ? date : LocalDate.now();
        MessPeriodConfig.Period period = (periodStr != null && !periodStr.isBlank() && !"ALL".equalsIgnoreCase(periodStr))
                ? parseEnum(MessPeriodConfig.Period.class, periodStr, "period")
                : MessPeriodConfig.Period.LUNCH;

        List<MessToken> tokens = messTokenRepository.findByMenuDateAndPeriod(target, period);
        long total = tokens.size();
        long delivered = tokens.stream().filter(t -> t.getStatus() == MessToken.Status.DELIVERED || t.getStatus() == MessToken.Status.PARTIALLY_DELIVERED).count();
        long pending = tokens.stream().filter(t -> t.getStatus() == MessToken.Status.BOOKED || t.getStatus() == MessToken.Status.QR_ACTIVE).count();
        long expired = tokens.stream().filter(t -> t.getStatus() == MessToken.Status.EXPIRED).count();
        long cancelled = tokens.stream().filter(t -> t.getStatus() == MessToken.Status.CANCELLED).count();
        BigDecimal revenue = tokens.stream()
                .filter(t -> t.getStatus() == MessToken.Status.DELIVERED || t.getStatus() == MessToken.Status.PARTIALLY_DELIVERED)
                .map(MessToken::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MessTokenItem> activeItems = tokenItemRepository.findActiveItemsForDateAndPeriod(target, period);
        Map<Long, ItemKitchenSummary> summaryMap = new LinkedHashMap<>();
        for (MessTokenItem i : activeItems) {
            Long fid = i.getFoodItem().getId();
            ItemKitchenSummary prev = summaryMap.getOrDefault(fid,
                    new ItemKitchenSummary(fid, i.getFoodItem().getName(), i.getFoodItem().getCategory().name(), 0, 0));
            long delQty = i.getDeliveredQuantity() != null ? i.getDeliveredQuantity() : 0;
            summaryMap.put(fid, new ItemKitchenSummary(
                    fid, prev.foodName(), prev.category(),
                    prev.totalBookedQty() + i.getQuantity(),
                    prev.totalDeliveredQty() + delQty
            ));
        }

        return new WardenMessSummaryResponse(target, period.name(), total, delivered, pending, expired, cancelled,
                revenue, new ArrayList<>(summaryMap.values()));
    }

    public String exportTokensCsv(LocalDate fromDate, LocalDate toDate) {
        LocalDate start = fromDate != null ? fromDate : LocalDate.now().minusDays(30);
        LocalDate end = toDate != null ? toDate : LocalDate.now();
        List<MessToken> list = messTokenRepository.findByMenuDateBetween(start, end);
        StringBuilder sb = new StringBuilder();
        sb.append("TokenCode,StudentRegNo,StudentName,MenuDate,Period,Items,TotalAmount,Status,DeliveredAt\n");
        for (MessToken t : list) {
            String itemsStr = tokenItemRepository.findByTokenId(t.getId()).stream()
                    .map(i -> i.getFoodItem().getName() + " x" + i.getQuantity())
                    .collect(Collectors.joining("; "));
            sb.append(escapeCsv(t.getTokenCode())).append(',')
              .append(escapeCsv(t.getStudent().getRegisterNumber())).append(',')
              .append(escapeCsv(t.getStudent().getFullName())).append(',')
              .append(t.getMenuDate()).append(',')
              .append(t.getPeriod().name()).append(',')
              .append(escapeCsv(itemsStr)).append(',')
              .append(t.getTotalAmount()).append(',')
              .append(t.getStatus().name()).append(',')
              .append(t.getDeliveredAt() != null ? t.getDeliveredAt() : "").append('\n');
        }
        return sb.toString();
    }

    public AdminMessReportResponse getAdminReport() {
        LocalDate end = LocalDate.now().plusDays(7);
        LocalDate start = LocalDate.now().minusDays(30);
        List<MessToken> tokens = messTokenRepository.findByMenuDateBetween(start, end);

        long delivered = tokens.stream().filter(t -> t.getStatus() == MessToken.Status.DELIVERED || t.getStatus() == MessToken.Status.PARTIALLY_DELIVERED).count();
        long expired = tokens.stream().filter(t -> t.getStatus() == MessToken.Status.EXPIRED).count();
        long denom = delivered + expired;
        double noShow = denom > 0 ? Math.round((expired * 10000.0) / denom) / 100.0 : 0.0;

        BigDecimal rev = tokens.stream()
                .filter(t -> t.getStatus() == MessToken.Status.DELIVERED || t.getStatus() == MessToken.Status.PARTIALLY_DELIVERED)
                .map(MessToken::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> consumption = new LinkedHashMap<>();
        Map<String, Long> byBlock = new LinkedHashMap<>();
        for (MessToken t : tokens) {
            byBlock.merge(t.getHostelBlock(), 1L, Long::sum);
            if (t.getStatus() == MessToken.Status.DELIVERED || t.getStatus() == MessToken.Status.PARTIALLY_DELIVERED) {
                for (MessTokenItem i : tokenItemRepository.findByTokenId(t.getId())) {
                    int qty = i.getDeliveredQuantity() != null ? i.getDeliveredQuantity() : i.getQuantity();
                    consumption.merge(i.getFoodItem().getName(), (long) qty, Long::sum);
                }
            }
        }
        return new AdminMessReportResponse(tokens.size(), delivered, expired, noShow, rev, consumption, byBlock);
    }

    public Resource loadFoodPhoto(Long foodItemId) {
        FoodItem item = foodItemRepository.findById(foodItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Food item not found"));
        if (item.getPhotoPath() == null) throw new ResourceNotFoundException("No photo");
        try {
            Path p = Paths.get(item.getPhotoPath()).normalize();
            Resource r = new UrlResource(p.toUri());
            if (!r.exists() || !r.isReadable()) throw new ResourceNotFoundException("Photo missing");
            return r;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Invalid photo path");
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private MenuScheduleResponse toScheduleResponse(MenuSchedule ms, LocalDateTime serverNow) {
        MessPeriodConfig cfg = getPeriodConfigOrThrow(ms.getPeriod());
        LocalDateTime cutoff = LocalDateTime.of(ms.getMenuDate(), cfg.getStartTime()).minusMinutes(cfg.getCutoffMinutesBefore());
        boolean beforeCutoff = serverNow.isBefore(cutoff);
        boolean open = !ms.isMessClosed() && ms.getRemainingStock() > 0 && beforeCutoff;
        String msg = ms.isMessClosed() ? "Mess Closed"
                : (!beforeCutoff ? "Booking Closed"
                : (ms.getRemainingStock() <= 0 ? "Sold Out" : "Available"));
        return new MenuScheduleResponse(
                ms.getId(), ms.getMenuDate(), ms.getPeriod().name(),
                ms.getFoodItem().getId(), ms.getFoodItem().getName(), ms.getFoodItem().getCategory().name(),
                ms.getFoodItem().getPrice(), ms.getFoodItem().getPhotoPath() != null ? "/api/mess/food-photos/" + ms.getFoodItem().getId() : null,
                ms.getDailyStock(), ms.getRemainingStock(), ms.getMaxPerStudent(),
                ms.isSpecialMenu(), ms.isMessClosed(), open, msg
        );
    }

    public MessTokenResponse toTokenResponse(MessToken t, boolean isStudentView, LocalDateTime serverNow) {
        Student s = t.getStudent();
        String room = bedRepository.findByStudentId(s.getId()).map(b -> b.getRoom().getRoomNumber()).orElse("-");
        String studentPhoto = s.getPhotoPath() != null ? "/api/student/photo/" + s.getId() : null;

        MessPeriodConfig cfg = getPeriodConfigOrThrow(t.getPeriod());
        LocalDateTime qrOpensAt = LocalDateTime.of(t.getMenuDate(), cfg.getStartTime()).minusMinutes(cfg.getQrOpenMinutesBefore());
        LocalDateTime mealEndsAt = LocalDateTime.of(t.getMenuDate(), cfg.getEndTime());
        LocalDateTime cutoff = LocalDateTime.of(t.getMenuDate(), cfg.getStartTime()).minusMinutes(cfg.getCutoffMinutesBefore());

        boolean notFinal = t.getStatus() == MessToken.Status.BOOKED || t.getStatus() == MessToken.Status.QR_ACTIVE;
        boolean qrLockedBefore = notFinal && serverNow.isBefore(qrOpensAt);
        boolean qrActiveNow = notFinal && !serverNow.isBefore(qrOpensAt) && !serverNow.isAfter(mealEndsAt);
        long secUntilOpen = qrLockedBefore ? Math.max(0, Duration.between(serverNow, qrOpensAt).getSeconds()) : 0;
        boolean cancellable = notFinal && serverNow.isBefore(cutoff);

        TokenQr qr = tokenQrRepository.findByTokenId(t.getId()).orElse(null);

        // Enforce rule: Before active window, QR is LOCKED and QR token string is NOT displayed to student
        String exposedQrToken = (isStudentView && qr != null && qrActiveNow && !qr.isUsed() && !qr.isClosed())
                ? qr.getQrToken() : null;

        List<MessTokenItemDto> items = tokenItemRepository.findByTokenId(t.getId()).stream()
                .map(i -> new MessTokenItemDto(
                        i.getId(), i.getFoodItem().getId(), i.getFoodItem().getName(),
                        i.getFoodItem().getCategory().name(), i.getQuantity(), i.getUnitPrice(),
                        i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())),
                        i.getDeliveredQuantity(), i.getUndeliveredReason()
                )).collect(Collectors.toList());

        List<TokenHistoryDto> hist = historyRepository.findByTokenIdOrderByCreatedAtAsc(t.getId()).stream()
                .map(h -> new TokenHistoryDto(h.getOldStatus(), h.getNewStatus(),
                        resolveActorName(h.getChangedBy()), h.getRemarks(), h.getCreatedAt()))
                .collect(Collectors.toList());

        return new MessTokenResponse(
                t.getId(), t.getTokenCode(), s.getId(), s.getFullName(), s.getRegisterNumber(), room,
                studentPhoto, t.getHostelBlock(), t.getMenuDate(), t.getPeriod().name(),
                t.getTotalAmount(), t.getStatus().name(), qrActiveNow, qrLockedBefore,
                qrOpensAt, mealEndsAt, secUntilOpen,
                exposedQrToken,
                (isStudentView && qr != null && qrActiveNow && !qr.isUsed()) ? qr.getPickupCode() : null,
                qr != null && qr.isUsed(), qr != null && qr.isClosed(), cancellable,
                t.getDeliveredAt(), t.getDeliveryRemarks(), t.getCreatedAt(), items, hist
        );
    }

    private String storeFoodPhoto(MultipartFile f, String itemName) {
        String ct = f.getContentType();
        if (ct == null || !ALLOWED_IMAGE_TYPES.contains(ct.toLowerCase())) {
            throw new BadRequestException("Only JPG, PNG, and WebP images are allowed");
        }
        if (f.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("Food photo must be 2 MB or smaller");
        }
        try {
            Path dir = Paths.get(uploadDir, "mess").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String ext = ct.contains("png") ? ".png" : (ct.contains("webp") ? ".webp" : ".jpg");
            String safeName = itemName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + UUID.randomUUID().toString().substring(0, 6) + ext;
            Path target = dir.resolve(safeName);
            Files.copy(f.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save food photo: " + e.getMessage());
        }
    }

    private MessPeriodConfig getPeriodConfigOrThrow(MessPeriodConfig.Period period) {
        return periodConfigRepository.findById(period)
                .orElseThrow(() -> new ResourceNotFoundException("Meal period config not found: " + period));
    }

    private Student getStudentByUser(User user) {
        return studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    private String generateUniqueTokenCode() {
        int year = LocalDate.now().getYear();
        String prefix = "TKN-" + year + "-";
        long seq = messTokenRepository.countByTokenCodePrefix(prefix) + 1;
        String code = String.format("%s%05d", prefix, seq);
        while (messTokenRepository.findByTokenCode(code).isPresent()) {
            seq++;
            code = String.format("%s%05d", prefix, seq);
        }
        return code;
    }

    private String generateUniquePickupCode() {
        for (int i = 0; i < 20; i++) {
            String code = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
            if (!tokenQrRepository.existsByPickupCode(code)) return code;
        }
        return String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
    }

    public String generateSignedQrToken(Long tokenId, Long studentId) {
        byte[] rnd = new byte[12];
        SECURE_RANDOM.nextBytes(rnd);
        String payload = "TKN1." + tokenId + "." + studentId + "." + HexFormat.of().formatHex(rnd);
        return payload + "." + computeHmacHex(payload);
    }

    private boolean verifySignedQrTokenFormat(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 5 || !"TKN1".equals(parts[0])) return false;
        String payload = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        String expected = computeHmacHex(payload);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[4].getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmacHex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign mess QR token", e);
        }
    }

    private void recordHistory(MessToken t, String oldStatus, String newStatus, User actor, String remarks) {
        TokenStatusHistory h = new TokenStatusHistory();
        h.setToken(t);
        h.setOldStatus(oldStatus);
        h.setNewStatus(newStatus);
        h.setChangedBy(actor);
        h.setRemarks(remarks != null && remarks.length() > 500 ? remarks.substring(0, 500) : remarks);
        historyRepository.save(h);
    }

    private String resolveActorName(User u) {
        if (u == null) return "System";
        if ("STUDENT".equals(u.getRole())) {
            return studentRepository.findByUserId(u.getId()).map(Student::getFullName).orElse(u.getEmail());
        }
        return staffProfileRepository.findByUserId(u.getId()).map(StaffProfile::getFullName).orElse(u.getEmail());
    }

    private FoodItemResponse toFoodResponse(FoodItem f) {
        return new FoodItemResponse(f.getId(), f.getName(), f.getCategory().name(), f.getPrice(),
                f.getPhotoPath() != null ? "/api/mess/food-photos/" + f.getId() : null, f.isActive());
    }

    private String getSetting(String key, String defaultVal) {
        return settingRepository.findById(key).map(SystemSetting::getValue).orElse(defaultVal);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> cls, String raw, String label) {
        try {
            return Enum.valueOf(cls, raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid " + label + ": " + raw);
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}

