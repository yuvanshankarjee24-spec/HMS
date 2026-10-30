package com.hostel.service;

import com.hostel.dto.OnlineAllocationDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
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
public class OnlineAllocationService {

    private final AllocationWhitelistRepository whitelistRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final StudentRepository studentRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final AllocationHistoryRepository allocationHistoryRepository;
    private final AuditService auditService;

    public OnlineAllocationService(AllocationWhitelistRepository whitelistRepository,
                                   SystemSettingRepository systemSettingRepository,
                                   StudentRepository studentRepository,
                                   RoomRepository roomRepository,
                                   BedRepository bedRepository,
                                   AllocationHistoryRepository allocationHistoryRepository,
                                   AuditService auditService) {
        this.whitelistRepository = whitelistRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.studentRepository = studentRepository;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.allocationHistoryRepository = allocationHistoryRepository;
        this.auditService = auditService;
    }

    public boolean isRoundOpen() {
        return systemSettingRepository.findByKey("ONLINE_ALLOCATION_ROUND")
                .map(s -> "OPEN".equalsIgnoreCase(s.getValue()))
                .orElse(true);
    }

    @Transactional
    public void toggleRound(boolean open, User actor) {
        SystemSetting setting = systemSettingRepository.findByKey("ONLINE_ALLOCATION_ROUND")
                .orElse(new SystemSetting("ONLINE_ALLOCATION_ROUND", "OPEN", "Online room allocation portal status"));
        setting.setValue(open ? "OPEN" : "CLOSED");
        systemSettingRepository.save(setting);
        auditService.log(actor, "TOGGLE_ONLINE_ALLOCATION", "Status=" + setting.getValue(), "SUCCESS");
    }

    public OnlineAllocationAccessResponse getStudentAllocationAccess(User studentUser) {
        Student student = studentRepository.findByUserId(studentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));

        boolean roundOpen = isRoundOpen();
        String roundStatusStr = roundOpen ? "OPEN" : "CLOSED";

        // Check if already has a bed
        Optional<Bed> bedOpt = bedRepository.findByStudentId(student.getId());
        if (bedOpt.isPresent()) {
            Bed b = bedOpt.get();
            return new OnlineAllocationAccessResponse(
                    roundOpen,
                    roundStatusStr,
                    "ALREADY_ALLOCATED",
                    "You are already allocated to Room " + b.getRoom().getRoomNumber() + ", Bed #" + b.getBedNumber() + ".",
                    null,
                    student.getFullName(),
                    1,
                    b.getRoom().getRoomNumber(),
                    List.of(),
                    b.getRoom().getRoomNumber(),
                    b.getRoom().getRoomNumber(),
                    b.getBedNumber()
            );
        }

        // Check if student is a whitelisted leader
        Optional<AllocationWhitelist> wlOpt = whitelistRepository.findByLeaderRegisterNumber(student.getRegisterNumber());
        if (wlOpt.isPresent()) {
            AllocationWhitelist wl = wlOpt.get();
            if (wl.getStatus() == AllocationWhitelist.Status.ALLOCATED) {
                return new OnlineAllocationAccessResponse(
                        roundOpen,
                        roundStatusStr,
                        "ALREADY_BOOKED",
                        "Your group has successfully booked Room " + wl.getAllocatedRoomNumber() + ". Room beds are allocated.",
                        wl.getLeaderRegisterNumber(),
                        wl.getLeaderName(),
                        wl.getGroupSize(),
                        wl.getTargetRoomNumber(),
                        parseFriendsList(wl.getFriendRegisterNumbers()),
                        wl.getAllocatedRoomNumber(),
                        null,
                        null
                );
            } else if (wl.getStatus() == AllocationWhitelist.Status.REVOKED) {
                return new OnlineAllocationAccessResponse(
                        roundOpen,
                        roundStatusStr,
                        "NOT_AUTHORIZED",
                        "Your group booking access has been suspended by the Warden. Please contact the hostel office.",
                        wl.getLeaderRegisterNumber(),
                        wl.getLeaderName(),
                        wl.getGroupSize(),
                        wl.getTargetRoomNumber(),
                        List.of(),
                        null,
                        null,
                        null
                );
            } else {
                return new OnlineAllocationAccessResponse(
                        roundOpen,
                        roundStatusStr,
                        "AUTHORIZED_LEADER",
                        "🌟 You are the authorized group representative! You have permission to book a room for your group of " + wl.getGroupSize() + " members.",
                        wl.getLeaderRegisterNumber(),
                        wl.getLeaderName(),
                        wl.getGroupSize(),
                        wl.getTargetRoomNumber(),
                        parseFriendsList(wl.getFriendRegisterNumbers()),
                        null,
                        null,
                        null
                );
            }
        }

        // Check if student is listed as a friend in any group
        List<AllocationWhitelist> friendGroups = whitelistRepository.findByFriendRegisterNumbersContaining(student.getRegisterNumber());
        if (!friendGroups.isEmpty()) {
            AllocationWhitelist parentWl = friendGroups.get(0);
            return new OnlineAllocationAccessResponse(
                    roundOpen,
                    roundStatusStr,
                    "GROUP_MEMBER",
                    "ℹ️ Your group booking is managed online by your designated representative: " +
                            (parentWl.getLeaderName() != null ? parentWl.getLeaderName() : "Group Leader") +
                            " (" + parentWl.getLeaderRegisterNumber() + "). Your leader will book the room for your group.",
                    parentWl.getLeaderRegisterNumber(),
                    parentWl.getLeaderName(),
                    parentWl.getGroupSize(),
                    parentWl.getTargetRoomNumber(),
                    parseFriendsList(parentWl.getFriendRegisterNumbers()),
                    parentWl.getAllocatedRoomNumber(),
                    null,
                    null
            );
        }

        // Not in whitelist
        return new OnlineAllocationAccessResponse(
                roundOpen,
                roundStatusStr,
                "NOT_AUTHORIZED",
                "🔒 Online Room Allocation is restricted to authorized group representatives listed on the warden's allocation sheet. Your register number (" + student.getRegisterNumber() + ") is not designated as a booking leader for this round.",
                null,
                null,
                1,
                null,
                List.of(),
                null,
                null,
                null
        );
    }

    @Transactional
    public GroupBookingResponse bookRoomWithFriends(User studentUser, GroupBookingRequest req) {
        if (!isRoundOpen()) {
            throw new BadRequestException("The Online Room Allocation round is currently CLOSED by the Warden.");
        }

        Student leaderStudent = studentRepository.findByUserId(studentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));

        AllocationWhitelist whitelist = whitelistRepository.findByLeaderRegisterNumber(leaderStudent.getRegisterNumber())
                .orElseThrow(() -> new ForbiddenException("Only the designated group representative listed on the allocation sheet can access room booking."));

        if (whitelist.getStatus() != AllocationWhitelist.Status.ELIGIBLE) {
            throw new BadRequestException("Group allocation is already " + whitelist.getStatus());
        }

        // Validate caller doesn't already have a bed
        if (bedRepository.findByStudentId(leaderStudent.getId()).isPresent()) {
            throw new BadRequestException("You are already allocated to a bed.");
        }

        // Collect all group members (leader + friends)
        LinkedHashSet<String> memberRegs = new LinkedHashSet<>();
        memberRegs.add(leaderStudent.getRegisterNumber());

        if (req.friendRegisterNumbers() != null) {
            for (String reg : req.friendRegisterNumbers()) {
                if (reg != null && !reg.trim().isBlank()) {
                    memberRegs.add(reg.trim().toUpperCase());
                }
            }
        }

        int totalMembers = memberRegs.size();
        if (totalMembers > whitelist.getGroupSize()) {
            throw new BadRequestException("Your authorized group size is " + whitelist.getGroupSize() + ", but you provided " + totalMembers + " members.");
        }

        // Find and validate room
        Room room = roomRepository.findById(req.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Selected room not found"));

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new BadRequestException("Selected room is not active or under maintenance");
        }

        // Lock available beds in this room
        List<Bed> availableBeds = bedRepository.findAvailableBedsInRoomWithLock(room.getId());
        if (availableBeds.size() < totalMembers) {
            throw new ConflictException("Room " + room.getRoomNumber() + " has only " + availableBeds.size() +
                    " free bed(s), but your group requires " + totalMembers + " beds.");
        }

        // Validate all members exist and have no beds
        List<Student> studentsToAllocate = new ArrayList<>();
        for (String reg : memberRegs) {
            Student member = studentRepository.findByRegisterNumber(reg)
                    .orElseThrow(() -> new BadRequestException("Group member with Register Number " + reg + " does not exist in student records."));

            if (bedRepository.findByStudentId(member.getId()).isPresent()) {
                throw new ConflictException("Student " + member.getFullName() + " (" + reg + ") is already allocated to a room bed.");
            }
            studentsToAllocate.add(member);
        }

        // Allocate beds atomically
        List<MemberAllocationResult> results = new ArrayList<>();
        for (int i = 0; i < studentsToAllocate.size(); i++) {
            Student member = studentsToAllocate.get(i);
            Bed targetBed = availableBeds.get(i);

            targetBed.setStudent(member);
            bedRepository.save(targetBed);

            AllocationHistory history = new AllocationHistory(member, targetBed, AllocationHistory.Action.ALLOCATED, studentUser);
            allocationHistoryRepository.save(history);

            results.add(new MemberAllocationResult(
                    member.getRegisterNumber(),
                    member.getFullName(),
                    room.getRoomNumber(),
                    targetBed.getBedNumber()
            ));
        }

        // Update whitelist entry
        whitelist.setStatus(AllocationWhitelist.Status.ALLOCATED);
        whitelist.setAllocatedRoomNumber(room.getRoomNumber());
        whitelist.setAllocatedAt(LocalDateTime.now());
        whitelistRepository.save(whitelist);

        auditService.log(studentUser, "ONLINE_GROUP_ALLOCATION",
                "Leader=" + leaderStudent.getRegisterNumber() + " Room=" + room.getRoomNumber() + " Members=" + memberRegs,
                "SUCCESS");

        return new GroupBookingResponse(
                true,
                room.getRoomNumber(),
                results,
                "🎉 Room " + room.getRoomNumber() + " booked successfully! All " + totalMembers + " beds have been assigned to your group."
        );
    }

    public List<WhitelistItemResponse> getAllWhitelist() {
        return whitelistRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToWhitelistResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WhitelistItemResponse addWhitelistEntry(AddWhitelistRequest req, User actor) {
        String leaderReg = req.leaderRegisterNumber().trim().toUpperCase();
        if (whitelistRepository.existsByLeaderRegisterNumber(leaderReg)) {
            throw new BadRequestException("Register number " + leaderReg + " is already in the allocation whitelist.");
        }

        String studentName = req.leaderName();
        if (studentName == null || studentName.isBlank()) {
            studentName = studentRepository.findByRegisterNumber(leaderReg)
                    .map(Student::getFullName)
                    .orElse("Student");
        }

        AllocationWhitelist entry = new AllocationWhitelist(
                leaderReg,
                studentName,
                req.targetRoomNumber() != null ? req.targetRoomNumber().trim() : null,
                req.groupSize() != null && req.groupSize() > 0 ? req.groupSize() : 1,
                req.friendRegisterNumbers() != null ? req.friendRegisterNumbers().trim() : null,
                req.notes() != null ? req.notes().trim() : "Added by Warden"
        );

        AllocationWhitelist saved = whitelistRepository.save(entry);
        auditService.log(actor, "ADD_ALLOCATION_WHITELIST", "Leader=" + leaderReg, "SUCCESS");
        return mapToWhitelistResponse(saved);
    }

    @Transactional
    public void deleteWhitelistEntry(Long id, User actor) {
        AllocationWhitelist entry = whitelistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Whitelist record not found"));
        whitelistRepository.delete(entry);
        auditService.log(actor, "DELETE_ALLOCATION_WHITELIST", "Id=" + id + " Leader=" + entry.getLeaderRegisterNumber(), "SUCCESS");
    }

    @Transactional
    public CsvUploadSummary uploadWhitelistCsv(MultipartFile file, User actor) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a valid CSV or Excel file");
        }

        int total = 0;
        int imported = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Skip header line if present
                if (firstLine) {
                    firstLine = false;
                    if (line.toLowerCase().contains("leader") || line.toLowerCase().contains("register") || line.toLowerCase().contains("roll")) {
                        continue;
                    }
                }

                total++;
                // Split by comma or semicolon
                String[] parts = line.split("[,;]");
                if (parts.length < 1) {
                    errors.add("Row " + total + ": empty row");
                    continue;
                }

                String leaderReg = parts[0].trim().toUpperCase();
                if (leaderReg.isEmpty()) {
                    errors.add("Row " + total + ": missing leader roll number");
                    continue;
                }

                String leaderName = parts.length > 1 ? parts[1].trim() : null;
                if (leaderName == null || leaderName.isBlank()) {
                    leaderName = studentRepository.findByRegisterNumber(leaderReg)
                            .map(Student::getFullName)
                            .orElse(null);
                }

                Integer groupSize = 1;
                if (parts.length > 2 && !parts[2].trim().isEmpty()) {
                    try {
                        groupSize = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException ignored) {}
                }

                String targetRoom = parts.length > 3 ? parts[3].trim() : null;
                String friendRegs = parts.length > 4 ? parts[4].trim() : null;
                String notes = parts.length > 5 ? parts[5].trim() : "Imported from Excel sheet";

                Optional<AllocationWhitelist> existingOpt = whitelistRepository.findByLeaderRegisterNumber(leaderReg);
                AllocationWhitelist entry;
                if (existingOpt.isPresent()) {
                    entry = existingOpt.get();
                    entry.setLeaderName(leaderName != null ? leaderName : entry.getLeaderName());
                    entry.setGroupSize(groupSize);
                    entry.setTargetRoomNumber(targetRoom);
                    entry.setFriendRegisterNumbers(friendRegs);
                    entry.setNotes(notes);
                } else {
                    entry = new AllocationWhitelist(leaderReg, leaderName, targetRoom, groupSize, friendRegs, notes);
                }

                whitelistRepository.save(entry);
                imported++;
            }

            auditService.log(actor, "UPLOAD_ALLOCATION_WHITELIST_CSV", "Imported=" + imported + ", Total=" + total, "SUCCESS");
            return new CsvUploadSummary(total, imported, errors, "Successfully processed " + imported + " group representatives from Excel/CSV sheet.");
        } catch (Exception e) {
            throw new BadRequestException("Failed to read CSV file: " + e.getMessage());
        }
    }

    public String generateSampleCsv() {
        return "Leader Roll Number,Leader Name,Group Size,Allowed Room,Friend Roll Numbers,Notes\n" +
               "REG202608,Sanya Kapoor,2,104,REG202609,Group Leader for Room 104\n" +
               "REG202610,Pooja Hegde,1,103,,Single Allocation Round 1\n";
    }

    private List<String> parseFriendsList(String friendRegs) {
        if (friendRegs == null || friendRegs.isBlank()) return List.of();
        return Arrays.stream(friendRegs.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    private WhitelistItemResponse mapToWhitelistResponse(AllocationWhitelist w) {
        return new WhitelistItemResponse(
                w.getId(),
                w.getLeaderRegisterNumber(),
                w.getLeaderName(),
                w.getTargetRoomNumber(),
                w.getGroupSize(),
                w.getFriendRegisterNumbers(),
                w.getStatus().name(),
                w.getAllocatedRoomNumber(),
                w.getAllocatedAt(),
                w.getNotes(),
                w.getCreatedAt()
        );
    }
}
