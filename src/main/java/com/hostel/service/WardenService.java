package com.hostel.service;

import com.hostel.dto.WardenDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WardenService {

    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final StudentRepository studentRepository;
    private final RoomRequestRepository roomRequestRepository;
    private final AllocationHistoryRepository allocationHistoryRepository;
    private final AuditService auditService;

    public WardenService(RoomRepository roomRepository,
                         BedRepository bedRepository,
                         StudentRepository studentRepository,
                         RoomRequestRepository roomRequestRepository,
                         AllocationHistoryRepository allocationHistoryRepository,
                         AuditService auditService) {
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.studentRepository = studentRepository;
        this.roomRequestRepository = roomRequestRepository;
        this.allocationHistoryRepository = allocationHistoryRepository;
        this.auditService = auditService;
    }

    public WardenSummaryResponse getSummary() {
        long totalRooms = roomRepository.count();
        long totalBeds = bedRepository.count();
        long occupiedBeds = bedRepository.countByStudentIsNotNull();
        long availableBeds = Math.max(0, totalBeds - occupiedBeds);
        long pendingRequests = roomRequestRepository.countByStatus(RoomRequest.Status.PENDING);

        return new WardenSummaryResponse(
                totalRooms,
                totalBeds,
                occupiedBeds,
                availableBeds,
                pendingRequests
        );
    }

    public List<RoomListItem> getAllRooms() {
        List<Room> rooms = roomRepository.findAllByOrderByRoomNumberAsc();
        return rooms.stream().map(room -> {
            List<Bed> beds = bedRepository.findByRoomIdOrderByBedNumberAsc(room.getId());
            long occupied = beds.stream().filter(b -> b.getStudent() != null).count();
            int available = (int) Math.max(0, room.getCapacity() - occupied);

            List<BedDetail> bedDetails = beds.stream().map(b -> {
                Student s = b.getStudent();
                return new BedDetail(
                        b.getId(),
                        b.getBedNumber(),
                        s != null ? s.getId() : null,
                        s != null ? s.getFullName() : null,
                        s != null ? s.getRegisterNumber() : null,
                        s != null ? s.getDepartment() : null,
                        s != null ? s.getPhone() : null
                );
            }).collect(Collectors.toList());

            return new RoomListItem(
                    room.getId(),
                    room.getRoomNumber(),
                    room.getFloor(),
                    room.getCapacity(),
                    (int) occupied,
                    available,
                    room.getStatus().name(),
                    bedDetails
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public Room createRoom(CreateRoomRequest request, User actorUser) {
        String roomNumber = request.roomNumber().trim();
        if (roomRepository.findByRoomNumber(roomNumber).isPresent()) {
            throw new BadRequestException("Room with number " + roomNumber + " already exists");
        }

        Room room = new Room(roomNumber, request.floor(), request.capacity(), Room.Status.ACTIVE);
        Room savedRoom = roomRepository.save(room);

        // Auto-create matching beds
        List<Bed> beds = new ArrayList<>();
        for (int i = 1; i <= request.capacity(); i++) {
            beds.add(new Bed(savedRoom, i));
        }
        bedRepository.saveAll(beds);

        auditService.log(actorUser, "CREATE_ROOM", "Room=" + roomNumber + ", Capacity=" + request.capacity(), "SUCCESS");
        return savedRoom;
    }

    @Transactional
    public Room updateRoomCapacity(Long roomId, UpdateRoomCapacityRequest request, User actorUser) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));

        long occupiedCount = bedRepository.countByRoomIdAndStudentIsNotNull(roomId);
        int newCapacity = request.capacity();

        if (newCapacity < occupiedCount) {
            throw new BadRequestException("Cannot reduce capacity to " + newCapacity + ". There are currently " + occupiedCount + " students occupied in this room.");
        }

        List<Bed> existingBeds = bedRepository.findByRoomIdOrderByBedNumberAsc(roomId);
        int currentCapacity = existingBeds.size();

        if (newCapacity > currentCapacity) {
            List<Bed> newBeds = new ArrayList<>();
            for (int i = currentCapacity + 1; i <= newCapacity; i++) {
                newBeds.add(new Bed(room, i));
            }
            bedRepository.saveAll(newBeds);
        } else if (newCapacity < currentCapacity) {
            // Remove unoccupied beds from the top down
            for (int i = currentCapacity - 1; i >= newCapacity; i--) {
                Bed b = existingBeds.get(i);
                if (b.getStudent() == null) {
                    bedRepository.delete(b);
                }
            }
        }

        room.setCapacity(newCapacity);
        Room updated = roomRepository.save(room);

        auditService.log(actorUser, "UPDATE_ROOM_CAPACITY", "Room=" + room.getRoomNumber() + ", NewCapacity=" + newCapacity, "SUCCESS");
        return updated;
    }

    public Page<StudentWardenView> getStudents(String query, Pageable pageable) {
        Page<Student> students = studentRepository.searchStudents(query, pageable);
        return students.map(s -> {
            Optional<Bed> bedOpt = bedRepository.findByStudentId(s.getId());
            String roomNumber = null;
            Integer bedNumber = null;
            Long bedId = null;
            String allocationStatus;

            if (bedOpt.isPresent()) {
                allocationStatus = "ALLOCATED";
                roomNumber = bedOpt.get().getRoom().getRoomNumber();
                bedNumber = bedOpt.get().getBedNumber();
                bedId = bedOpt.get().getId();
            } else if (roomRequestRepository.existsByStudentIdAndStatus(s.getId(), RoomRequest.Status.PENDING)) {
                allocationStatus = "REQUEST PENDING";
            } else {
                allocationStatus = "NOT ALLOCATED";
            }

            return new StudentWardenView(
                    s.getId(),
                    s.getUser().getId(),
                    s.getFullName(),
                    s.getRegisterNumber(),
                    s.getCollegeEmail(),
                    s.getPhone(),
                    s.getDepartment(),
                    s.getYear(),
                    roomNumber,
                    bedNumber,
                    bedId,
                    allocationStatus
            );
        });
    }

    public List<RoomRequest> getRoomRequests(RoomRequest.Status status) {
        if (status == null) {
            return roomRequestRepository.findAllByOrderByCreatedAtDesc();
        }
        return roomRequestRepository.findByStatusWithDetails(status);
    }

    @Transactional
    public Bed approveRoomRequest(Long requestId, ApproveRequest approveDto, User actorUser) {
        RoomRequest request = roomRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Room request not found"));

        if (request.getStatus() != RoomRequest.Status.PENDING) {
            throw new BadRequestException("Request is already " + request.getStatus());
        }

        Student student = request.getStudent();

        // Re-check that student has no bed
        if (bedRepository.findByStudentId(student.getId()).isPresent()) {
            request.setStatus(RoomRequest.Status.REJECTED);
            request.setDecidedBy(actorUser);
            request.setDecisionNote("Student is already allocated to another bed.");
            request.setDecidedAt(LocalDateTime.now());
            roomRequestRepository.save(request);
            throw new ConflictException("Student is already allocated to a bed.");
        }

        Bed targetBed;

        if (approveDto != null && approveDto.bedId() != null) {
            // Lock target bed row
            targetBed = bedRepository.findByIdWithLock(approveDto.bedId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));

            if (targetBed.getStudent() != null) {
                throw new ConflictException("Bed " + targetBed.getBedNumber() + " in room " + targetBed.getRoom().getRoomNumber() + " is already occupied by another student.");
            }
            if (targetBed.getRoom().getStatus() != Room.Status.ACTIVE) {
                throw new BadRequestException("Room is currently under maintenance");
            }
        } else {
            // Auto-pick first free bed: preferred room first, then any room
            List<Bed> candidates = List.of();
            if (request.getPreferredRoom() != null) {
                candidates = bedRepository.findAvailableBedsInRoomWithLock(request.getPreferredRoom().getId());
            }

            if (candidates.isEmpty()) {
                candidates = bedRepository.findAllAvailableBedsWithLock();
            }

            if (candidates.isEmpty()) {
                throw new ConflictException("No free beds are currently available in the hostel.");
            }

            targetBed = candidates.get(0);
        }

        // Double check student assignment with lock in effect
        if (targetBed.getStudent() != null) {
            throw new ConflictException("Bed was taken by another allocation.");
        }

        // Assign bed
        targetBed.setStudent(student);
        bedRepository.save(targetBed);

        // Update request
        request.setStatus(RoomRequest.Status.APPROVED);
        request.setDecidedBy(actorUser);
        request.setDecisionNote("Approved into Room " + targetBed.getRoom().getRoomNumber() + ", Bed " + targetBed.getBedNumber());
        request.setDecidedAt(LocalDateTime.now());
        roomRequestRepository.save(request);

        // Allocation history
        AllocationHistory history = new AllocationHistory(student, targetBed, AllocationHistory.Action.ALLOCATED, actorUser);
        allocationHistoryRepository.save(history);

        // Audit log
        auditService.log(
                actorUser,
                "APPROVE_ROOM_REQUEST",
                "Student=" + student.getRegisterNumber() + " -> Room " + targetBed.getRoom().getRoomNumber() + " Bed " + targetBed.getBedNumber(),
                "SUCCESS"
        );

        return targetBed;
    }

    @Transactional
    public void rejectRoomRequest(Long requestId, RejectRequest rejectDto, User actorUser) {
        RoomRequest request = roomRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Room request not found"));

        if (request.getStatus() != RoomRequest.Status.PENDING) {
            throw new BadRequestException("Request is already " + request.getStatus());
        }

        request.setStatus(RoomRequest.Status.REJECTED);
        request.setDecisionNote(rejectDto.reason().trim());
        request.setDecidedBy(actorUser);
        request.setDecidedAt(LocalDateTime.now());
        roomRequestRepository.save(request);

        auditService.log(
                actorUser,
                "REJECT_ROOM_REQUEST",
                "RequestId=" + requestId + ", Reason=" + rejectDto.reason(),
                "SUCCESS"
        );
    }

    @Transactional
    public void vacateStudent(Long studentId, User actorUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        Bed bed = bedRepository.findByStudentId(studentId)
                .orElseThrow(() -> new BadRequestException("Student is not currently allocated to any bed"));

        bed.setStudent(null);
        bedRepository.save(bed);

        AllocationHistory history = new AllocationHistory(student, bed, AllocationHistory.Action.VACATED, actorUser);
        allocationHistoryRepository.save(history);

        auditService.log(
                actorUser,
                "VACATE_STUDENT",
                "Student=" + student.getRegisterNumber() + " vacated from Room " + bed.getRoom().getRoomNumber() + " Bed " + bed.getBedNumber(),
                "SUCCESS"
        );
    }
}
