package com.hostel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.StudentDtos.RoomRequestCreate;
import com.hostel.entity.*;
import com.hostel.repository.*;
import com.hostel.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class StudentTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("A student with a bed cannot create a room request (Returns 400 Bad Request)")
    void testStudentWithBedCannotCreateRequest() throws Exception {
        // Setup user with an allocated bed
        User user = new User();
        user.setEmail("allocated_test@college.edu");
        user.setRegisterNumber("ALLOC01");
        user.setPasswordHash(passwordEncoder.encode("Test@1234"));
        user.setRole("STUDENT");
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setFullName("Allocated Student");
        student.setRegisterNumber("ALLOC01");
        student.setCollegeEmail("allocated_test@college.edu");
        student.setPhone("+91 9999988881");
        student.setDepartment("CSE");
        student.setYear(2);
        student = studentRepository.save(student);

        Room room = new Room("TEST901", 1, 1, Room.Status.ACTIVE);
        room = roomRepository.save(room);

        Bed bed = new Bed(room, 1);
        bed.setStudent(student);
        bedRepository.save(bed);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        MockCookie cookie = new MockCookie("hostel_jwt", token);

        RoomRequestCreate req = new RoomRequestCreate(room.getId(), 1, "Please allocate");

        mockMvc.perform(post("/api/student/room-requests")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already allocated to a room and bed")));
    }

    @Test
    @DisplayName("A student cannot cancel another student's room request (IDOR prevention)")
    void testStudentCannotCancelOtherStudentRequest() throws Exception {
        // Student A
        User userA = new User();
        userA.setEmail("student_a@college.edu");
        userA.setRegisterNumber("STUDA");
        userA.setPasswordHash(passwordEncoder.encode("Test@1234"));
        userA.setRole("STUDENT");
        userA = userRepository.save(userA);

        Student studentA = new Student();
        studentA.setUser(userA);
        studentA.setFullName("Student A");
        studentA.setRegisterNumber("STUDA");
        studentA.setCollegeEmail("student_a@college.edu");
        studentA.setPhone("+91 9999988882");
        studentA.setDepartment("ECE");
        studentA.setYear(1);
        studentA = studentRepository.save(studentA);

        // Student B
        User userB = new User();
        userB.setEmail("student_b@college.edu");
        userB.setRegisterNumber("STUDB");
        userB.setPasswordHash(passwordEncoder.encode("Test@1234"));
        userB.setRole("STUDENT");
        userB = userRepository.save(userB);

        Student studentB = new Student();
        studentB.setUser(userB);
        studentB.setFullName("Student B");
        studentB.setRegisterNumber("STUDB");
        studentB.setCollegeEmail("student_b@college.edu");
        studentB.setPhone("+91 9999988883");
        studentB.setDepartment("MECH");
        studentB.setYear(1);
        studentB = studentRepository.save(studentB);

        // Room request owned by Student A
        RoomRequest reqA = new RoomRequest();
        reqA.setStudent(studentA);
        reqA.setStatus(RoomRequest.Status.PENDING);
        reqA = roomRequestRepository.save(reqA);

        // Student B attempts to cancel Student A's request
        String tokenB = jwtService.generateToken(userB.getId(), userB.getEmail(), userB.getRole());
        MockCookie cookieB = new MockCookie("hostel_jwt", tokenB);

        mockMvc.perform(delete("/api/student/room-requests/" + reqA.getId())
                        .cookie(cookieB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("cannot cancel another student's request")));
    }
}
