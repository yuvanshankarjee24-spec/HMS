package com.hostel.service;

import com.hostel.dto.RoomAllocationDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.BadRequestException;
import com.hostel.exception.CustomExceptions.ConflictException;
import com.hostel.exception.CustomExceptions.ForbiddenException;
import com.hostel.exception.CustomExceptions.ResourceNotFoundException;
import com.hostel.repository.*;
import com.hostel.util.RollNumberValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoomAllocationService {

    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final StudentRepository studentRepository;
    private final RoomAllocationRepository roomAllocationRepository;
    private final AllocationHistoryRepository allocationHistoryRepository;
    private final WardenAssignmentRepository wardenAssignmentRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public RoomAllocationService(RoomRepository roomRepository,
                                 BedRepository bedRepository,
                                 StudentRepository studentRepository,
                                 RoomAllocationRepository roomAllocationRepository,
                                 AllocationHistoryRepository allocationHistoryRepository,
                                 WardenAssignmentRepository wardenAssignmentRepository,
                                 NotificationService notificationService,
                                 AuditService auditService) {
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.studentRepository = studentRepository;
        this.roomAllocationRepository = roomAllocationRepository;
        this.allocationHistoryRepository = allocationHistoryRepository;
        this.wardenAssignmentRepository = wardenAssignmentRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    public RoomAllocationSummary getSummary() {
        long totalRooms = roomRepository.count();
        long totalBeds = bedRepository.count();
        long occupiedBeds = bedRepository.countByStudentIsNotNull();
        long availableBeds = bedRepository.countAvailableBeds();

        List<Room> allRooms = roomRepository.findAll();
        long fullyOccupied = allRooms.stream()
                .filter(r -> bedRepository.countByRoomIdAndStudentIsNotNull(r.getId()) >= r.getCapacity())
                .count();

        return new RoomAllocationSummary(totalRooms, totalBeds, occupiedBeds, availableBeds, fullyOccupied);
    }

    public List<RoomOccupancyItem> getOccupancyList(String query, Integer floor) {
        List<Room> rooms = roomRepository.findAllByOrderByRoomNumberAsc();

        if (floor != null && floor > 0) {
            rooms = rooms.stream().filter(r -> r.getFloor() == floor).collect(Collectors.toList());
        }

        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase();
            rooms = rooms.stream().filter(r -> {
                if (r.getRoomNumber().toLowerCase().contains(q)) return true;
                List<Bed> beds = bedRepository.findByRoomIdOrderByBedNumberAsc(r.getId());
                return beds.stream().anyMatch(b -> b.getStudent() != null && (
                        b.getStudent().getFullName().toLowerCase().contains(q) ||
                        b.getStudent().getRegisterNumber().toLowerCase().contains(q) ||
                        (b.getStudent().getDepartment() != null && b.getStudent().getDepartment().toLowerCase().contains(q))
                ));
            }).collect(Collectors.toList());
        }

        return rooms.stream().map(r -> {
            List<Bed> beds = bedRepository.findByRoomIdOrderByBedNumberAsc(r.getId());
            int occupied = (int) beds.stream().filter(b -> b.getStudent() != null).count();
            int available = Math.max(0, r.getCapacity() - occupied);

            List<BedOccupancyItem> bedItems = beds.stream().map(b -> {
                Student s = b.getStudent();
                return new BedOccupancyItem(
                        b.getId(),
                        b.getBedNumber(),
                        b.getBedLabel(),
                        b.getStatus() != null ? b.getStatus() : (s != null ? "occupied" : "available"),
                        s != null ? s.getId() : null,
                        s != null ? s.getFullName() : null,
                        s != null ? s.getRegisterNumber() : null,
                        s != null ? s.getDepartment() : null
                );
            }).collect(Collectors.toList());

            return new RoomOccupancyItem(
                    r.getId(),
                    r.getRoomNumber(),
                    r.getFloor(),
                    r.getCapacity(),
                    occupied,
                    available,
                    r.getStatus().name(),
                    bedItems
            );
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAvailableRooms() {
        List<Room> rooms = roomRepository.findAllByOrderByRoomNumberAsc();
        List<Map<String, Object>> results = new ArrayList<>();

        for (Room r : rooms) {
            if (r.getStatus() != Room.Status.ACTIVE) continue;
            List<Bed> beds = bedRepository.findByRoomIdOrderByBedNumberAsc(r.getId());
            long freeCount = beds.stream().filter(b -> b.getStudent() == null && !"maintenance".equalsIgnoreCase(b.getStatus())).count();
            if (freeCount > 0) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", r.getId());
                map.put("roomNumber", r.getRoomNumber());
                map.put("floor", r.getFloor());
                map.put("capacity", r.getCapacity());
                map.put("freeBeds", freeCount);
                map.put("display", "Room " + r.getRoomNumber() + " (" + freeCount + " of " + r.getCapacity() + " free)");
                results.add(map);
            }
        }
        return results;
    }

    @Transactional
    public RoomAllocation allocate(Long studentId, Long roomId, String bedLabel, String remarks, User actorUser) {
        // 1. Verify actor permissions: Warden can only allocate in their assigned hostel
        verifyWardenHostelPermission(actorUser, roomId);

        // 2. Lock Room with PESSIMISTIC_WRITE row-lock
        Room room = roomRepository.findByIdWithLock(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new BadRequestException("Room " + room.getRoomNumber() + " is currently under maintenance");
        }

        // 3. Row-level Capacity Check: Room cannot exceed capacity (4 students)
        long currentOccupiedInRoom = bedRepository.countByRoomIdAndStudentIsNotNull(roomId);
        if (currentOccupiedInRoom >= room.getCapacity()) {
            throw new ConflictException("Room " + room.getRoomNumber() + " is fully occupied (" + room.getCapacity() + "/" + room.getCapacity() + ")");
        }

        // 4. Check global capacity: "No beds available"
        long totalFreeBeds = bedRepository.countAvailableBedsWithLock();
        if (totalFreeBeds <= 0) {
            throw new ConflictException("No beds available");
        }

        // 5. Check student exists and ensure one active allocation rule
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        if (bedRepository.findByStudentId(studentId).isPresent() ||
            roomAllocationRepository.findByStudentIdAndStatus(studentId, "active").isPresent()) {
            throw new ConflictException("Student " + student.getRegisterNumber() + " is already allocated to an active room/bed");
        }

        // 6. Select and lock target bed
        Bed targetBed;
        if (bedLabel != null && !bedLabel.isBlank()) {
            targetBed = bedRepository.findByRoomIdAndBedLabelWithLock(roomId, bedLabel.trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Bed " + bedLabel + " not found in Room " + room.getRoomNumber()));

            if (targetBed.getStudent() != null || "occupied".equalsIgnoreCase(targetBed.getStatus())) {
                throw new ConflictException("Bed " + targetBed.getBedLabel() + " in Room " + room.getRoomNumber() + " is already occupied");
            }
        } else {
            List<Bed> candidates = bedRepository.findAvailableBedsInRoomWithLock(roomId);
            if (candidates.isEmpty()) {
                throw new ConflictException("No available beds in Room " + room.getRoomNumber());
            }
            targetBed = candidates.get(0);
        }

        // Double check target bed occupation under row-lock
        if (targetBed.getStudent() != null) {
            throw new ConflictException("Bed was taken by another concurrent allocation");
        }

        // 7. Update Bed
        targetBed.setStudent(student);
        targetBed.setStatus("occupied");
        bedRepository.save(targetBed);

        // 8. Update Student canonical Room and Hostel FKs
        student.setRoom(room);
        if (room.getHostel() != null) {
            student.setHostel(room.getHostel());
        }
        studentRepository.save(student);

        // 9. Create RoomAllocation record
        RoomAllocation allocation = new RoomAllocation();
        allocation.setStudent(student);
        allocation.setRoom(room);
        allocation.setBed(targetBed);
        allocation.setAllocatedBy(actorUser);
        allocation.setAllocatedAt(LocalDateTime.now());
        allocation.setStatus("active");
        allocation.setRemarks(remarks != null ? remarks : "Allocated by " + actorUser.getName());
        allocation = roomAllocationRepository.save(allocation);

        // 10. Record in AllocationHistory for backward compatibility
        AllocationHistory history = new AllocationHistory(student, targetBed, AllocationHistory.Action.ALLOCATED, actorUser);
        allocationHistoryRepository.save(history);

        // 11. Audit Log & Student Notification
        auditService.log(actorUser, "ROOMS", "ALLOCATE_ROOM", allocation.getId(), null,
                "Room=" + room.getRoomNumber() + ", Bed=" + targetBed.getBedLabel(),
                student.getRegisterNumber(), "SUCCESS");

        notificationService.notifyUser(
                student.getUser(),
                "Room Allocation Confirmed",
                "You have been allocated to Room " + room.getRoomNumber() + ", Bed " + targetBed.getBedLabel() + " (" + (room.getHostel() != null ? room.getHostel().getName() : "Hostel") + ").",
                "ROOMS",
                allocation.getId()
        );

        return allocation;
    }

    @Transactional
    public RoomAllocation changeRoom(Long studentId, Long targetRoomId, String targetBedLabel, String remarks, User actorUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        // 1. Close old active allocation
        Optional<RoomAllocation> activeAllocOpt = roomAllocationRepository.findByStudentIdAndStatus(studentId, "active");
        Optional<Bed> oldBedOpt = bedRepository.findByStudentId(studentId);

        if (oldBedOpt.isPresent()) {
            Bed oldBed = oldBedOpt.get();
            oldBed.setStudent(null);
            oldBed.setStatus("available");
            bedRepository.save(oldBed);

            AllocationHistory history = new AllocationHistory(student, oldBed, AllocationHistory.Action.VACATED, actorUser);
            allocationHistoryRepository.save(history);
        }

        if (activeAllocOpt.isPresent()) {
            RoomAllocation oldAlloc = activeAllocOpt.get();
            oldAlloc.setStatus("transferred");
            oldAlloc.setVacatedAt(LocalDateTime.now());
            oldAlloc.setRemarks((oldAlloc.getRemarks() != null ? oldAlloc.getRemarks() + "; " : "") + "Transferred to new room");
            roomAllocationRepository.save(oldAlloc);
        }

        student.setRoom(null);
        studentRepository.save(student);

        // 2. Allocate to new room & bed
        return allocate(studentId, targetRoomId, targetBedLabel, remarks, actorUser);
    }

    @Transactional
    public void vacate(Long studentId, String remarks, User actorUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        Optional<Bed> bedOpt = bedRepository.findByStudentId(studentId);
        Optional<RoomAllocation> allocOpt = roomAllocationRepository.findByStudentIdAndStatus(studentId, "active");

        if (bedOpt.isEmpty() && allocOpt.isEmpty()) {
            throw new BadRequestException("Student is not currently allocated to any bed");
        }

        Bed bed = null;
        if (bedOpt.isPresent()) {
            bed = bedOpt.get();
            verifyWardenHostelPermission(actorUser, bed.getRoom().getId());

            bed.setStudent(null);
            bed.setStatus("available");
            bedRepository.save(bed);

            AllocationHistory history = new AllocationHistory(student, bed, AllocationHistory.Action.VACATED, actorUser);
            allocationHistoryRepository.save(history);
        }

        if (allocOpt.isPresent()) {
            RoomAllocation alloc = allocOpt.get();
            alloc.setStatus("vacated");
            alloc.setVacatedAt(LocalDateTime.now());
            alloc.setRemarks(remarks != null ? remarks : "Vacated by " + actorUser.getName());
            roomAllocationRepository.save(alloc);
        }

        student.setRoom(null);
        studentRepository.save(student);

        auditService.log(actorUser, "ROOMS", "VACATE_ROOM", allocOpt.map(RoomAllocation::getId).orElse(null),
                bed != null ? "Room=" + bed.getRoom().getRoomNumber() + ", Bed=" + bed.getBedLabel() : null,
                null, student.getRegisterNumber(), "SUCCESS");

        notificationService.notifyUser(
                student.getUser(),
                "Room Allocation Vacated",
                "Your hostel room allocation has been vacated.",
                "ROOMS",
                student.getId()
        );
    }

    @Transactional
    public BulkAllocationResponse bulkAllocate(MultipartFile file, User actorUser) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please upload a valid CSV file");
        }

        List<BulkAllocationRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.toLowerCase().contains("roll")) {
                    continue; // header row
                }
                if (line.trim().isEmpty()) continue;

                totalRows++;
                String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim().replaceAll("^\"|\"$", "");
                }

                if (cols.length < 2) {
                    errors.add(new BulkAllocationRowError(lineNumber, cols.length > 0 ? cols[0] : "N/A", "N/A", "Insufficient columns (requires RollNumber, RoomNumber)"));
                    continue;
                }

                String roll = cols[0];
                String roomNum = cols[1];
                String bedLabel = cols.length > 2 && !cols[2].isBlank() ? cols[2].trim().toUpperCase() : null;

                if (!RollNumberValidator.isValid(roll)) {
                    errors.add(new BulkAllocationRowError(lineNumber, roll, roomNum, "Invalid roll number format: must be 12 digits starting with 7155"));
                    continue;
                }

                Optional<Student> studentOpt = studentRepository.findByRegisterNumber(roll);
                if (studentOpt.isEmpty()) {
                    errors.add(new BulkAllocationRowError(lineNumber, roll, roomNum, "Student with roll number " + roll + " not found"));
                    continue;
                }

                Optional<Room> roomOpt = roomRepository.findByRoomNumber(roomNum);
                if (roomOpt.isEmpty()) {
                    errors.add(new BulkAllocationRowError(lineNumber, roll, roomNum, "Room " + roomNum + " does not exist"));
                    continue;
                }

                try {
                    Student student = studentOpt.get();
                    Room room = roomOpt.get();

                    if (bedRepository.findByStudentId(student.getId()).isPresent()) {
                        changeRoom(student.getId(), room.getId(), bedLabel, "Bulk CSV transfer", actorUser);
                    } else {
                        allocate(student.getId(), room.getId(), bedLabel, "Bulk CSV allocation", actorUser);
                    }
                    successCount++;
                } catch (Exception e) {
                    errors.add(new BulkAllocationRowError(lineNumber, roll, roomNum, e.getMessage()));
                }
            }
        } catch (Exception ex) {
            throw new BadRequestException("Failed to read CSV file: " + ex.getMessage());
        }

        return new BulkAllocationResponse(
                totalRows,
                successCount,
                errors.size(),
                errors,
                "Bulk allocation processed: " + successCount + " successful, " + errors.size() + " failed."
        );
    }

    public StudentAllocationDetail getStudentAllocation(Long studentId, User actorUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        // If actor is STUDENT, ensure student can only see their own room
        if ("STUDENT".equals(actorUser.getRole())) {
            Student meStudent = studentRepository.findByUserId(actorUser.getId())
                    .orElseThrow(() -> new ForbiddenException("Access denied"));
            if (!meStudent.getId().equals(studentId)) {
                throw new ForbiddenException("You are only allowed to view your own room allocation");
            }
        }

        Optional<Bed> bedOpt = bedRepository.findByStudentId(studentId);
        if (bedOpt.isEmpty()) {
            return new StudentAllocationDetail(
                    student.getId(),
                    student.getRegisterNumber(),
                    student.getFullName(),
                    null,
                    null,
                    0,
                    null,
                    "Not allocated",
                    null
            );
        }

        Bed bed = bedOpt.get();
        Room room = bed.getRoom();
        Hostel hostel = room.getHostel();

        return new StudentAllocationDetail(
                student.getId(),
                student.getRegisterNumber(),
                student.getFullName(),
                room.getRoomNumber(),
                bed.getBedLabel(),
                room.getFloor(),
                hostel != null ? hostel.getName() : "PSG iTech Hostel",
                "Allocated",
                bed.getCreatedAt()
        );
    }

    public String generateSampleCsv() {
        return "RollNumber,RoomNumber,BedLabel\n" +
               "715521104001,101,A\n" +
               "715521104002,101,B\n" +
               "715521104003,101,C\n" +
               "715521104004,101,D\n" +
               "715521104005,102,A\n";
    }

    private void verifyWardenHostelPermission(User actorUser, Long roomId) {
        if (actorUser == null || "ADMIN".equals(actorUser.getRole())) {
            return; // Admin has global permission across all hostels
        }

        if ("WARDEN".equals(actorUser.getRole())) {
            List<WardenAssignment> assignments = wardenAssignmentRepository.findByWardenUserIdAndActiveTrue(actorUser.getId());
            if (assignments.isEmpty()) {
                // No specific restriction, allow
                return;
            }

            Room room = roomRepository.findById(roomId).orElse(null);
            if (room != null && room.getHostel() != null) {
                Long roomHostelId = room.getHostel().getId();
                boolean authorized = assignments.stream().anyMatch(a -> a.getHostel().getId().equals(roomHostelId));
                if (!authorized) {
                    throw new ForbiddenException("Access denied: You can only allocate students to your assigned hostel block");
                }
            }
        }
    }
}
