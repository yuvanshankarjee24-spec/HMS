package com.hostel.service;

import com.hostel.entity.*;
import com.hostel.repository.WardenAssignmentRepository;
import com.hostel.util.StatusConstants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Provides compact aggregate metrics, pending action queues, and recent audit activity
 * for the Warden Dashboard, strictly scoped to the warden's assigned hostels without full-table loads.
 */
@Service
public class WardenDashboardService {

    private final WardenAssignmentRepository wardenAssignmentRepository;

    @PersistenceContext
    private EntityManager em;

    public WardenDashboardService(WardenAssignmentRepository wardenAssignmentRepository) {
        this.wardenAssignmentRepository = wardenAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardOverview(User actor) {
        List<WardenAssignment> assignments = actor != null
                ? wardenAssignmentRepository.findByWardenUserIdAndActiveTrue(actor.getId())
                : List.of();

        List<Long> hostelIds = assignments.stream()
                .map(a -> a.getHostel().getId())
                .distinct()
                .toList();
        boolean scoped = actor != null && !"ADMIN".equalsIgnoreCase(actor.getRole()) && !hostelIds.isEmpty();

        String hostelScopeLabel = assignments.isEmpty()
                ? "All Campus Hostels"
                : String.join(", ", assignments.stream().map(a -> a.getHostel().getName()).distinct().toList());

        LocalDate todayIst = StatusConstants.todayIst();
        LocalDateTime parcelCutoff = StatusConstants.nowUtc().minusDays(7);

        // 1. Outpass Aggregates
        long outpassPending = countQuery(
                "SELECT COUNT(o) FROM Outpass o WHERE o.status = :st AND o.student.active = true AND o.student.deletedAt IS NULL"
                        + (scoped ? " AND o.student.hostel.id IN :hids" : ""),
                Map.of("st", Outpass.Status.PENDING), scoped ? hostelIds : null);
        long outpassCurrentlyOut = countQuery(
                "SELECT COUNT(o) FROM Outpass o WHERE o.status = :st AND o.student.active = true AND o.student.deletedAt IS NULL"
                        + (scoped ? " AND o.student.hostel.id IN :hids" : ""),
                Map.of("st", Outpass.Status.OUT), scoped ? hostelIds : null);

        // 2. Digital Attendance Aggregates (Today IST)
        long totalStudents = countQuery(
                "SELECT COUNT(s) FROM Student s WHERE s.active = true AND s.deletedAt IS NULL"
                        + (scoped ? " AND s.hostel.id IN :hids" : ""),
                Map.of(), scoped ? hostelIds : null);
        long lockedOutpasses = countQuery(
                "SELECT COUNT(s) FROM Student s WHERE s.active = true AND s.deletedAt IS NULL AND s.locked = true"
                        + (scoped ? " AND s.hostel.id IN :hids" : ""),
                Map.of(), scoped ? hostelIds : null);

        Map<DigitalAttendanceRecord.Status, Long> attBreakdown = getAttendanceTodayCounts(todayIst, scoped ? hostelIds : null);
        long attMarked = attBreakdown.getOrDefault(DigitalAttendanceRecord.Status.MARKED, 0L)
                + attBreakdown.getOrDefault(DigitalAttendanceRecord.Status.MANUAL_PRESENT, 0L);
        long attPending = attBreakdown.getOrDefault(DigitalAttendanceRecord.Status.PENDING, 0L);
        long attMissed = attBreakdown.getOrDefault(DigitalAttendanceRecord.Status.MISSED, 0L);
        long attOnOutpass = attBreakdown.getOrDefault(DigitalAttendanceRecord.Status.ON_OUTPASS, 0L);

        // 3. Parcels Aggregates
        List<Parcel.Status> waitingStatuses = List.of(Parcel.Status.RECEIVED, Parcel.Status.NOTIFIED);
        long parcelsWaiting = countQuery(
                "SELECT COUNT(p) FROM Parcel p WHERE p.status IN :sts"
                        + (scoped ? " AND p.hostel.id IN :hids" : ""),
                Map.of("sts", waitingStatuses), scoped ? hostelIds : null);
        long parcelsCodPending = countQuery(
                "SELECT COUNT(p) FROM Parcel p WHERE p.status IN :sts AND p.cod = true AND p.codPaymentPending = true"
                        + (scoped ? " AND p.hostel.id IN :hids" : ""),
                Map.of("sts", waitingStatuses), scoped ? hostelIds : null);
        long parcelsLongPending = countQuery(
                "SELECT COUNT(p) FROM Parcel p WHERE p.status IN :sts AND p.receivedAt < :cutoff"
                        + (scoped ? " AND p.hostel.id IN :hids" : ""),
                Map.of("sts", waitingStatuses, "cutoff", parcelCutoff), scoped ? hostelIds : null);

        // 4. Mess Aggregates (Today IST)
        List<MessToken.Status> activeMessStatuses = List.of(
                MessToken.Status.BOOKED, MessToken.Status.QR_ACTIVE,
                MessToken.Status.DELIVERED, MessToken.Status.PARTIALLY_DELIVERED);
        List<MessToken.Status> pendingMessStatuses = List.of(MessToken.Status.BOOKED, MessToken.Status.QR_ACTIVE);
        long messTokensToday = countQuery(
                "SELECT COUNT(t) FROM MessToken t WHERE t.menuDate = :td AND t.status IN :sts"
                        + (scoped ? " AND t.hostel.id IN :hids" : ""),
                Map.of("td", todayIst, "sts", activeMessStatuses), scoped ? hostelIds : null);
        long messTokensPending = countQuery(
                "SELECT COUNT(t) FROM MessToken t WHERE t.menuDate = :td AND t.status IN :sts"
                        + (scoped ? " AND t.hostel.id IN :hids" : ""),
                Map.of("td", todayIst, "sts", pendingMessStatuses), scoped ? hostelIds : null);

        // 5. Maintenance Aggregates
        List<MaintenanceRequest.Status> openMaintStatuses = List.of(
                MaintenanceRequest.Status.PENDING, MaintenanceRequest.Status.ASSIGNED,
                MaintenanceRequest.Status.IN_PROGRESS, MaintenanceRequest.Status.ON_HOLD,
                MaintenanceRequest.Status.REOPENED);
        long maintOpen = countQuery(
                "SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.status IN :sts"
                        + (scoped ? " AND m.hostel.id IN :hids" : ""),
                Map.of("sts", openMaintStatuses), scoped ? hostelIds : null);
        long activeWorkers = em.createQuery(
                "SELECT COUNT(w) FROM WorkerProfile w WHERE w.active = true AND w.deletedAt IS NULL", Long.class)
                .getSingleResult();

        // 6. Circulars Aggregate (published requiring acknowledgement with unacknowledged active students)
        long unacknowledgedCirculars = countQuery(
                "SELECT COUNT(c) FROM Circular c WHERE c.status = :st AND c.requireAcknowledgement = true"
                        + (scoped ? " AND (c.targetScope = :allScope OR c.hostel.id IN :hids)" : ""),
                scoped
                        ? Map.of("st", Circular.Status.PUBLISHED, "allScope", Circular.TargetScope.ALL)
                        : Map.of("st", Circular.Status.PUBLISHED),
                scoped ? hostelIds : null);

        // 7. Pending Action Queues (capped at 10 rows per category)
        List<Map<String, Object>> pendingOutpassList = fetchPendingOutpasses(scoped ? hostelIds : null);
        List<Map<String, Object>> lockedStudentList = fetchLockedStudents(scoped ? hostelIds : null);
        List<Map<String, Object>> longPendingParcelList = fetchLongPendingParcels(waitingStatuses, parcelCutoff, scoped ? hostelIds : null);
        List<Map<String, Object>> openMaintList = fetchOpenMaintenance(openMaintStatuses, scoped ? hostelIds : null);
        List<Map<String, Object>> unackCircularList = fetchUnackCirculars(scoped ? hostelIds : null);

        // 8. Recent Activity (Top 15 from shared AuditLog)
        List<Map<String, Object>> recentActivity = fetchRecentActivity();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("hostelScopeLabel", hostelScopeLabel);
        summary.put("outpassPending", outpassPending);
        summary.put("outpassCurrentlyOut", outpassCurrentlyOut);
        summary.put("totalStudents", totalStudents);
        summary.put("attMarked", attMarked);
        summary.put("attPending", attPending);
        summary.put("attMissed", attMissed);
        summary.put("attOnOutpass", attOnOutpass);
        summary.put("lockedOutpasses", lockedOutpasses);
        summary.put("parcelsWaiting", parcelsWaiting);
        summary.put("parcelsCodPending", parcelsCodPending);
        summary.put("parcelsLongPending", parcelsLongPending);
        summary.put("messTokensToday", messTokensToday);
        summary.put("messTokensPending", messTokensPending);
        summary.put("maintOpen", maintOpen);
        summary.put("activeWorkers", activeWorkers);
        summary.put("unacknowledgedCirculars", unacknowledgedCirculars);

        Map<String, Object> pendingActions = new LinkedHashMap<>();
        pendingActions.put("outpasses", pendingOutpassList);
        pendingActions.put("lockedStudents", lockedStudentList);
        pendingActions.put("longPendingParcels", longPendingParcelList);
        pendingActions.put("openMaintenance", openMaintList);
        pendingActions.put("unackCirculars", unackCircularList);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("pendingActions", pendingActions);
        result.put("recentActivity", recentActivity);
        return result;
    }

    private long countQuery(String jpql, Map<String, Object> params, List<Long> hostelIds) {
        TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        params.forEach(q::setParameter);
        if (hostelIds != null) {
            q.setParameter("hids", hostelIds);
        }
        return q.getSingleResult();
    }

    private Map<DigitalAttendanceRecord.Status, Long> getAttendanceTodayCounts(LocalDate today, List<Long> hostelIds) {
        String jpql = "SELECT r.status, COUNT(r) FROM DigitalAttendanceRecord r "
                + "WHERE r.attendanceDate = :td AND r.student.active = true AND r.student.deletedAt IS NULL"
                + (hostelIds != null ? " AND r.student.hostel.id IN :hids" : "")
                + " GROUP BY r.status";
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class).setParameter("td", today);
        if (hostelIds != null) {
            q.setParameter("hids", hostelIds);
        }
        Map<DigitalAttendanceRecord.Status, Long> map = new EnumMap<>(DigitalAttendanceRecord.Status.class);
        for (Object[] row : q.getResultList()) {
            map.put((DigitalAttendanceRecord.Status) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private List<Map<String, Object>> fetchPendingOutpasses(List<Long> hostelIds) {
        String jpql = "SELECT o FROM Outpass o JOIN FETCH o.student s WHERE o.status = :st "
                + "AND s.active = true AND s.deletedAt IS NULL"
                + (hostelIds != null ? " AND s.hostel.id IN :hids" : "")
                + " ORDER BY o.createdAt DESC";
        TypedQuery<Outpass> q = em.createQuery(jpql, Outpass.class)
                .setParameter("st", Outpass.Status.PENDING)
                .setMaxResults(10);
        if (hostelIds != null) q.setParameter("hids", hostelIds);

        return q.getResultList().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("studentName", o.getStudent().getFullName());
            m.put("registerNumber", o.getStudent().getRegisterNumber());
            m.put("passType", o.getPassType().name());
            m.put("departureDateTime", o.getDepartureDateTime());
            m.put("reason", o.getReason());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> fetchLockedStudents(List<Long> hostelIds) {
        String jpql = "SELECT s FROM Student s WHERE s.active = true AND s.deletedAt IS NULL AND s.locked = true"
                + (hostelIds != null ? " AND s.hostel.id IN :hids" : "")
                + " ORDER BY s.lockedAt DESC";
        TypedQuery<Student> q = em.createQuery(jpql, Student.class).setMaxResults(10);
        if (hostelIds != null) q.setParameter("hids", hostelIds);

        return q.getResultList().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", s.getId());
            m.put("fullName", s.getFullName());
            m.put("registerNumber", s.getRegisterNumber());
            m.put("lockReason", s.getLockReason());
            m.put("lockedAt", s.getLockedAt());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> fetchLongPendingParcels(List<Parcel.Status> waiting, LocalDateTime cutoff, List<Long> hostelIds) {
        String jpql = "SELECT p FROM Parcel p JOIN FETCH p.student s WHERE p.status IN :sts AND p.receivedAt < :cutoff"
                + (hostelIds != null ? " AND p.hostel.id IN :hids" : "")
                + " ORDER BY p.receivedAt ASC";
        TypedQuery<Parcel> q = em.createQuery(jpql, Parcel.class)
                .setParameter("sts", waiting)
                .setParameter("cutoff", cutoff)
                .setMaxResults(10);
        if (hostelIds != null) q.setParameter("hids", hostelIds);

        return q.getResultList().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("parcelCode", p.getParcelCode());
            m.put("studentName", p.getStudent().getFullName());
            m.put("courierName", p.getCourierName());
            m.put("receivedAt", p.getReceivedAt());
            m.put("cod", p.isCod());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> fetchOpenMaintenance(List<MaintenanceRequest.Status> openSts, List<Long> hostelIds) {
        String jpql = "SELECT m FROM MaintenanceRequest m JOIN FETCH m.student s WHERE m.status IN :sts"
                + (hostelIds != null ? " AND m.hostel.id IN :hids" : "")
                + " ORDER BY m.createdAt DESC";
        TypedQuery<MaintenanceRequest> q = em.createQuery(jpql, MaintenanceRequest.class)
                .setParameter("sts", openSts)
                .setMaxResults(10);
        if (hostelIds != null) q.setParameter("hids", hostelIds);

        return q.getResultList().stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("requestCode", m.getRequestCode());
            row.put("title", m.getTitle());
            row.put("roomNumber", m.getRoomNumber());
            row.put("priority", m.getPriority().name());
            row.put("status", m.getStatus().name());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> fetchUnackCirculars(List<Long> hostelIds) {
        String jpql = "SELECT c FROM Circular c WHERE c.status = :st AND c.requireAcknowledgement = true"
                + (hostelIds != null ? " AND (c.targetScope = :allScope OR c.hostel.id IN :hids)" : "")
                + " ORDER BY c.publishedAt DESC";
        TypedQuery<Circular> q = em.createQuery(jpql, Circular.class)
                .setParameter("st", Circular.Status.PUBLISHED)
                .setMaxResults(10);
        if (hostelIds != null) {
            q.setParameter("allScope", Circular.TargetScope.ALL);
            q.setParameter("hids", hostelIds);
        }

        return q.getResultList().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("circularCode", c.getCircularCode());
            m.put("title", c.getTitle());
            m.put("priority", c.getPriority().name());
            m.put("publishedAt", c.getPublishedAt());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> fetchRecentActivity() {
        List<AuditLog> logs = em.createQuery(
                "SELECT a FROM AuditLog a LEFT JOIN FETCH a.actorUser ORDER BY a.createdAt DESC", AuditLog.class)
                .setMaxResults(15)
                .getResultList();
        return logs.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("module", a.getModule() != null ? a.getModule() : "SYSTEM");
            m.put("action", a.getAction());
            m.put("target", a.getTarget());
            m.put("result", a.getResult());
            String actorName = a.getActorUser() != null
                    ? (a.getActorUser().getName() != null ? a.getActorUser().getName() : a.getActorUser().getEmail())
                    : "System";
            m.put("actorName", actorName);
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList();
    }
}

