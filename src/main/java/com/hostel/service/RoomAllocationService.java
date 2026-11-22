package com.hostel.service;

import com.hostel.dto.RoomAllocationDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.BadRequestException;
import com.hostel.exception.CustomExceptions.ConflictException;
import com.hostel.exception.CustomExceptions.ForbiddenException;
import com.hostel.exception.CustomExceptions.ResourceNotFoundException;
import com.hostel.repository.*;
import com.hostel.util.RollNumberValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final RoomRequestRepository roomRequestRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public RoomAllocationService(RoomRepository roomRepository,
                                 BedRepository bedRepository,
                                 StudentRepository studentRepository,
                                 RoomAllocationRepository roomAllocationRepository,
                                 AllocationHistoryRepository allocationHistoryRepository,
                                 WardenAssignmentRepository wardenAssignmentRepository,
                                 RoomRequestRepository roomRequestRepository,
                                 NotificationService notificationService,
                                 AuditService auditService) {
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.studentRepository = studentRepository;
        this.roomAllocationRepository = roomAllocationRepository;
        this.allocationHistoryRepository = allocationHistoryRepository;
        this.wardenAssignmentRepository = wardenAssignmentRepository;
        this.roomRequestRepository = roomRequestRepository;
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

    public AdminRoomSummary getAdminSummary() {
        long totalRooms = roomRepository.count();
        long totalBeds = bedRepository.count();
        long occupiedBeds = bedRepository.countByStudentIsNotNull();
        long availableBeds = bedRepository.countAvailableBeds();

        List<Room> allRooms = roomRepository.findAll();
        long fullyOccupied = allRooms.stream()
                .filter(r -> bedRepository.countByRoomIdAndStudentIsNotNull(r.getId()) >= r.getCapacity())
                .count();

        long pendingRequests = roomRequestRepository.countByStatus(RoomRequest.Status.PENDING);
        long studentsWithoutRoom = studentRepository.countUnallocatedStudents();

        return new AdminRoomSummary(
                totalRooms,
                totalBeds,
                occupiedBeds,
                availableBeds,
                fullyOccupied,
                pendingRequests,
                studentsWithoutRoom
        );
    }

    public List<FloorStatsItem> getFloorStats() {
        List<FloorStatsItem> floorStats = new ArrayList<>();
        List<Room> allRooms = roomRepository.findAll();

        for (int f = 1; f <= 4; f++) {
            final int floorNum = f;
            List<Room> floorRooms = allRooms.stream().filter(r -> r.getFloor() == floorNum).toList();
            int totalRooms = floorRooms.size();
            int totalBeds = totalRooms * 4;
            int occupied = 0;
            int available = 0;

            for (Room r : floorRooms) {
                List<Bed> beds = bedRepository.findByRoomIdOrderByBedNumberAsc(r.getId());
                for (Bed b : beds) {
                    if (b.getStudent() != null) {
                        occupied++;
                    } else if (!"maintenance".equalsIgnoreCase(b.getStatus()) && r.getStatus() == Room.Status.ACTIVE) {
                        available++;
                    }
                }
            }

            double percentage = totalBeds > 0 ? ((double) occupied * 100.0) / totalBeds : 0.0;
            floorStats.add(new FloorStatsItem(floorNum, totalRooms, totalBeds, occupied, available, Math.round(percentage * 10.0) / 10.0));
        }

        return floorStats;
    }

    public Page<StudentSearchResult> searchStudents(String query, boolean unallocatedOnly, Pageable pageable) {
        Page<Student> page = studentRepository.searchStudentsForAllocation(query, unallocatedOnly, pageable);
        return page.map(s -> {
            String roomNum = s.getRoom() != null ? s.getRoom().getRoomNumber() : null;
            String bedLabel = null;
            if (s.getRoom() != null) {
                Optional<Bed> bedOpt = bedRepository.findByStudentId(s.getId());
                if (bedOpt.isPresent()) {
                    bedLabel = bedOpt.get().getBedLabel();
                }
            }
            return new StudentSearchResult(
                    s.getId(),
                    s.getRegisterNumber(),
                    s.getFullName(),
                    s.getDepartment(),
                    s.getYear(),
                    s.getGender(),
                    s.getPhone(),
                    s.getPhotoPath(),
                    roomNum,
                    bedLabel,
                    s.getRoom() != null
            );
        });
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
                        s != null ? s.getDepartment() : null,
                        s != null ? s.getYear() : null,
                        s != null ? s.getGender() : null,
                        s != null ? s.getPhotoPath() : null
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
        return getAvailableRooms(null, null);
    }

    public List<Map<String, Object>> getAvailableRooms(Integer floor, String block) {
        List<Room> rooms = roomRepository.findAllByOrderByRoomNumberAsc();
        List<Map<String, Object>> results = new ArrayList<>();

        for (Room r : rooms) {
            if (r.getStatus() != Room.Status.ACTIVE) continue;
            if (floor != null && floor > 0 && r.getFloor() != floor) continue;
            if (block != null && !block.isBlank() && !block.equalsIgnoreCase("ALL")) {
                String hostelName = r.getHostel() != null ? r.getHostel().getName() : "PSG iTech Hostel";
                if (!hostelName.toLowerCase().contains(block.toLowerCase())) continue;
            }

            List<Bed> beds = bedRepository.findByRoomIdOrderByBedNumberAsc(r.getId());
            long freeCount = beds.stream().filter(b -> b.getStudent() == null && !"maintenance".equalsIgnoreCase(b.getStatus())).count();
            if (freeCount > 0) {
                // Determine current room gender if any
                String roomGender = null;
                for (Bed b : beds) {
                    if (b.getStudent() != null && b.getStudent().getGender() != null) {
                        roomGender = b.getStudent().getGender();
                        break;
                    }
                }

                List<String> freeBedLabels = beds.stream()
                        .filter(b -> b.getStudent() == null && !"maintenance".equalsIgnoreCase(b.getStatus()))
                        .map(Bed::getBedLabel)
                        .toList();

                Map<String, Object> map = new HashMap<>();
                map.put("id", r.getId());
                map.put("roomNumber", r.getRoomNumber());
                map.put("floor", r.getFloor());
                map.put("capacity", r.getCapacity());
                map.put("freeBeds", freeCount);
                map.put("freeBedLabels", freeBedLabels);
                map.put("currentGender", roomGender);
                map.put("display", "Room " + r.getRoomNumber() + " (" + freeCount + " of " + r.getCapacity() + " free)");
                results.add(map);
            }
        }
        return results;
    }

    @Transactional
    public RoomAllocation allocate(Long studentId, Long roomId, String bedLabel, String remarks, User actorUser) {
        // 1. Verify actor permissions: Warden can only allocate in their assigned hostel (Admin has campus-wide access)
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

        // 6. Gender Compatibility Rule
        if (student.getGender() != null && !student.getGender().isBlank()) {
            String studentGender = student.getGender().trim().toLowerCase();
            List<Bed> bedsInRoom = bedRepository.findByRoomIdOrderByBedNumberAsc(roomId);
            for (Bed b : bedsInRoom) {
                if (b.getStudent() != null && b.getStudent().getGender() != null && !b.getStudent().getGender().isBlank()) {
                    String existingGender = b.getStudent().getGender().trim().toLowerCase();
                    if (!existingGender.equalsIgnoreCase(studentGender)) {
                        throw new ConflictException("Gender mismatch: Room " + room.getRoomNumber() +
                                " is currently occupied by " + b.getStudent().getGender().toLowerCase() + " students.");
                    }
                }
            }
        }

        // 7. Select and lock target bed
        Bed targetBed;
        if (bedLabel != null && !bedLabel.isBlank()) {
            targetBed = bedRepository.findByRoomIdAndBedLabelWithLock(roomId, bedLabel.trim().toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("Bed " + bedLabel + " not found in Room " + room.getRoomNumber()));

            if (targetBed.getStudent() != null || "occupied".equalsIgnoreCase(targetBed.getStatus())) {
                throw new ConflictException("Bed " + targetBed.getBedLabel() + " in Room " + room.getRoomNumber() + " is already occupied");
            }
            if ("maintenance".equalsIgnoreCase(targetBed.getStatus())) {
                throw new BadRequestException("Bed " + targetBed.getBedLabel() + " in Room " + room.getRoomNumber() + " is currently under maintenance");
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

        // 8. Update Bed
        targetBed.setStudent(student);
        targetBed.setStatus("occupied");
        bedRepository.save(targetBed);

        // 9. Update Student canonical Room and Hostel FKs
        student.setRoom(room);
        if (room.getHostel() != null) {
            student.setHostel(room.getHostel());
        }
        studentRepository.save(student);

        // 10. Create RoomAllocation record
        RoomAllocation allocation = new RoomAllocation();
        allocation.setStudent(student);
        allocation.setRoom(room);
        allocation.setBed(targetBed);
        allocation.setAllocatedBy(actorUser);
        allocation.setAllocatedAt(LocalDateTime.now());
        allocation.setStatus("active");
        allocation.setRemarks(remarks != null ? remarks : "Allocated by " + actorUser.getName());
        allocation = roomAllocationRepository.save(allocation);

        // 11. Record in AllocationHistory for backward compatibility
        AllocationHistory history = new AllocationHistory(student, targetBed, AllocationHistory.Action.ALLOCATED, actorUser);
        allocationHistoryRepository.save(history);

        // 12. Audit Log & Student Notification
        auditService.log(actorUser, "ROOMS", "ALLOCATE_ROOM", allocation.getId(), null,
                "Room=" + room.getRoomNumber() + ", Bed=" + targetBed.getBedLabel(),
                student.getRegisterNumber(), "SUCCESS");

        notificationService.notifyUser(
                student.getUser(),
                "Room Allocation Confirmed",
                "Room " + room.getRoomNumber() + ", Bed " + targetBed.getBedLabel() + " has been allotted to you (" + (room.getHostel() != null ? room.getHostel().getName() : "PSG iTech Hostel") + ").",
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

        String oldRoomBedInfo = "";
        if (oldBedOpt.isPresent()) {
            Bed oldBed = oldBedOpt.get();
            oldRoomBedInfo = "Room " + oldBed.getRoom().getRoomNumber() + ", Bed " + oldBed.getBedLabel();
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
        String newRemarks = remarks != null && !remarks.isBlank() ? remarks : ("Transferred from " + oldRoomBedInfo);
        RoomAllocation newAlloc = allocate(studentId, targetRoomId, targetBedLabel, newRemarks, actorUser);

        auditService.log(actorUser, "ROOMS", "TRANSFER_ROOM", newAlloc.getId(), oldRoomBedInfo,
                "Room=" + newAlloc.getRoom().getRoomNumber() + ", Bed=" + newAlloc.getBed().getBedLabel(),
                student.getRegisterNumber(), "SUCCESS");

        return newAlloc;
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
    public Room toggleRoomMaintenance(Long roomId, User actorUser) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with ID: " + roomId));

        if (room.getStatus() == Room.Status.MAINTENANCE) {
            room.setStatus(Room.Status.ACTIVE);
        } else {
            room.setStatus(Room.Status.MAINTENANCE);
        }

        Room saved = roomRepository.save(room);
        auditService.log(actorUser, "ROOMS", "TOGGLE_ROOM_MAINTENANCE", room.getId(), null,
                "Room=" + room.getRoomNumber() + ", NewStatus=" + room.getStatus(), null, "SUCCESS");
        return saved;
    }

    @Transactional
    public Bed toggleBedMaintenance(Long bedId, User actorUser) {
        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found with ID: " + bedId));

        if ("maintenance".equalsIgnoreCase(bed.getStatus())) {
            bed.setStatus(bed.getStudent() != null ? "occupied" : "available");
        } else {
            if (bed.getStudent() != null) {
                throw new ConflictException("Bed " + bed.getBedLabel() + " is currently occupied by student " +
                        bed.getStudent().getRegisterNumber() + ". Please vacate or transfer the student before marking bed under maintenance.");
            }
            bed.setStatus("maintenance");
        }

        Bed saved = bedRepository.save(bed);
        auditService.log(actorUser, "ROOMS", "TOGGLE_BED_MAINTENANCE", bed.getId(), null,
                "Room=" + bed.getRoom().getRoomNumber() + ", Bed=" + bed.getBedLabel() + ", NewStatus=" + bed.getStatus(), null, "SUCCESS");
        return saved;
    }

    public BulkValidationResponse bulkValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please upload a valid CSV file");
        }

        List<BulkValidationRow> rows = new ArrayList<>();
        int validCount = 0;
        int errorCount = 0;

        // Track batch allocations to prevent duplicate batch assignments in same room
        Map<String, Integer> batchRoomCounts = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && (line.toLowerCase().contains("roll") || line.toLowerCase().contains("room"))) {
                    continue; // Header row
                }
                if (line.trim().isEmpty()) continue;

                String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim().replaceAll("^\"|\"$", "");
                }

                String roll = cols.length > 0 ? cols[0] : "";
                String roomNum = cols.length > 1 ? cols[1] : "";
                String bedLabel = cols.length > 2 && !cols[2].isBlank() ? cols[2].trim().toUpperCase() : null;

                if (!RollNumberValidator.isValid(roll)) {
                    rows.add(new BulkValidationRow(lineNumber, roll, "N/A", roomNum, bedLabel, "INVALID", "Invalid roll number format: 12 digits starting with 7155 required"));
                    errorCount++;
                    continue;
                }

                Optional<Student> studentOpt = studentRepository.findByRegisterNumber(roll);
                if (studentOpt.isEmpty()) {
                    rows.add(new BulkValidationRow(lineNumber, roll, "N/A", roomNum, bedLabel, "INVALID", "Student not found with roll number: " + roll));
                    errorCount++;
                    continue;
                }

                Student student = studentOpt.get();
                Optional<Room> roomOpt = roomRepository.findByRoomNumber(roomNum);
                if (roomOpt.isEmpty()) {
                    rows.add(new BulkValidationRow(lineNumber, roll, student.getFullName(), roomNum, bedLabel, "INVALID", "Room " + roomNum + " does not exist"));
                    errorCount++;
                    continue;
                }

                Room room = roomOpt.get();
                if (room.getStatus() == Room.Status.MAINTENANCE) {
                    rows.add(new BulkValidationRow(lineNumber, roll, student.getFullName(), roomNum, bedLabel, "INVALID", "Room " + roomNum + " is under maintenance"));
                    errorCount++;
                    continue;
                }

                // Check capacity
                long currentOccupied = bedRepository.countByRoomIdAndStudentIsNotNull(room.getId());
                int batchAllocated = batchRoomCounts.getOrDefault(roomNum, 0);
                if (currentOccupied + batchAllocated >= room.getCapacity()) {
                    rows.add(new BulkValidationRow(lineNumber, roll, student.getFullName(), roomNum, bedLabel, "INVALID", "Room " + roomNum + " would exceed max capacity of " + room.getCapacity()));
                    errorCount++;
                    continue;
                }

                // Check gender
                if (student.getGender() != null && !student.getGender().isBlank()) {
                    List<Bed> existingBeds = bedRepository.findByRoomIdOrderByBedNumberAsc(room.getId());
                    boolean genderMismatch = false;
                    for (Bed b : existingBeds) {
                        if (b.getStudent() != null && b.getStudent().getGender() != null &&
                            !b.getStudent().getGender().trim().equalsIgnoreCase(student.getGender().trim())) {
                            genderMismatch = true;
                            break;
                        }
                    }
                    if (genderMismatch) {
                        rows.add(new BulkValidationRow(lineNumber, roll, student.getFullName(), roomNum, bedLabel, "INVALID", "Gender mismatch: Room " + roomNum + " has students of opposite gender"));
                        errorCount++;
                        continue;
                    }
                }

                // If bed label specified, check that bed
                if (bedLabel != null) {
                    Optional<Bed> bedOpt = bedRepository.findByRoomIdAndBedLabel(room.getId(), bedLabel);
                    if (bedOpt.isEmpty()) {
                        rows.add(new BulkValidationRow(lineNumber, roll, student.getFullName(), roomNum, bedLabel, "INVALID", "Bed " + bedLabel + " not found in Room " + roomNum));
                        errorCount++;
                        continue;
                    }
                    if ("maintenance".equalsIgnoreCase(bedOpt.get().getStatus())) {
                        rows.add(new BulkValidationRow(lineNumber, roll, student.getFullName(), roomNum, bedLabel, "INVALID", "Bed " + bedLabel + " is under maintenance"));
                        errorCount++;
                        continue;
                    }
                    if (bedOpt.get().getStudent() != null) {
                        rows.add(new BulkValidationRow(lineNumber, roll, student.getFullName(), roomNum, bedLabel, "INVALID", "Bed " + bedLabel + " is already occupied"));
                        errorCount++;
                        continue;
                    }
                }

                batchRoomCounts.put(roomNum, batchAllocated + 1);
                rows.add(new BulkValidationRow(lineNumber, roll, student.getFullName(), roomNum, bedLabel, "VALID", "Ready for allotment"));
                validCount++;
            }
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse file: " + e.getMessage());
        }

        return new BulkValidationResponse(rows.size(), validCount, errorCount, rows);
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
                if (lineNumber == 1 && (line.toLowerCase().contains("roll") || line.toLowerCase().contains("room"))) {
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

    public List<AllocationHistoryItem> searchAllocationHistory(String query, String status) {
        List<RoomAllocation> allocations = roomAllocationRepository.searchAllocationHistory(query, status);
        return allocations.stream().map(ra -> {
            Student s = ra.getStudent();
            Room r = ra.getRoom();
            Bed b = ra.getBed();
            User u = ra.getAllocatedBy();

            String action = "ALLOCATED";
            if ("transferred".equalsIgnoreCase(ra.getStatus())) {
                action = "TRANSFERRED";
            } else if ("vacated".equalsIgnoreCase(ra.getStatus())) {
                action = "VACATED";
            }

            return new AllocationHistoryItem(
                    ra.getId(),
                    s.getId(),
                    s.getRegisterNumber(),
                    s.getFullName(),
                    s.getDepartment(),
                    r.getRoomNumber(),
                    b.getBedLabel(),
                    action,
                    u != null ? u.getName() : "System",
                    ra.getAllocatedAt(),
                    ra.getVacatedAt(),
                    ra.getStatus(),
                    ra.getRemarks()
            );
        }).toList();
    }

    public String exportAllotmentsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Roll Number,Student Name,Department,Year,Gender,Room Number,Floor,Bed Label,Allotted Date,Remarks\n");

        List<RoomAllocation> activeAllocations = roomAllocationRepository.findAllActiveAllocationsWithDetails();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (RoomAllocation ra : activeAllocations) {
            Student s = ra.getStudent();
            Room r = ra.getRoom();
            Bed b = ra.getBed();

            sb.append(escapeCsv(s.getRegisterNumber())).append(",")
              .append(escapeCsv(s.getFullName())).append(",")
              .append(escapeCsv(s.getDepartment())).append(",")
              .append(s.getYear()).append(",")
              .append(escapeCsv(s.getGender() != null ? s.getGender() : "")).append(",")
              .append(escapeCsv(r.getRoomNumber())).append(",")
              .append(r.getFloor()).append(",")
              .append(escapeCsv(b.getBedLabel())).append(",")
              .append(escapeCsv(ra.getAllocatedAt() != null ? ra.getAllocatedAt().format(dtf) : "")).append(",")
              .append(escapeCsv(ra.getRemarks() != null ? ra.getRemarks() : "")).append("\n");
        }

        return sb.toString();
    }

    public String exportVacantBedsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Room Number,Floor,Bed Label,Status,Hostel\n");

        List<Bed> beds = bedRepository.findAllAvailableBeds();
        for (Bed b : beds) {
            Room r = b.getRoom();
            sb.append(escapeCsv(r.getRoomNumber())).append(",")
              .append(r.getFloor()).append(",")
              .append(escapeCsv(b.getBedLabel())).append(",")
              .append(escapeCsv(b.getStatus() != null ? b.getStatus() : "available")).append(",")
              .append(escapeCsv(r.getHostel() != null ? r.getHostel().getName() : "PSG iTech Hostel")).append("\n");
        }

        return sb.toString();
    }

    public String exportHistoryCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Timestamp,Roll Number,Student Name,Department,Action,Room Number,Bed Label,Done By,Remarks\n");

        List<AllocationHistoryItem> history = searchAllocationHistory(null, null);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (AllocationHistoryItem item : history) {
            sb.append(escapeCsv(item.timestamp() != null ? item.timestamp().format(dtf) : "")).append(",")
              .append(escapeCsv(item.rollNumber())).append(",")
              .append(escapeCsv(item.studentName())).append(",")
              .append(escapeCsv(item.department() != null ? item.department() : "")).append(",")
              .append(escapeCsv(item.action())).append(",")
              .append(escapeCsv(item.roomNumber())).append(",")
              .append(escapeCsv(item.bedLabel())).append(",")
              .append(escapeCsv(item.allocatedBy())).append(",")
              .append(escapeCsv(item.remarks() != null ? item.remarks() : "")).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public StudentAllocationDetail getStudentAllocation(Long studentId, User actorUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

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
            return;
        }

        if ("WARDEN".equals(actorUser.getRole())) {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
            if (room.getHostel() == null) return;

            boolean assigned = wardenAssignmentRepository.findByWardenUserIdAndActiveTrue(actorUser.getId()).stream()
                    .anyMatch(a -> a.getHostel().getId().equals(room.getHostel().getId()));

            if (!assigned) {
                throw new ForbiddenException("Wardens may only manage rooms within their assigned hostel (" + room.getHostel().getName() + ")");
            }
        }
    }
}
