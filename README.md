# 🏢 Smart Hostel Management System (Phase 1)

A full-stack, enterprise-grade web application built for college hostel administration. Phase 1 provides secure authentication, role-based access control, room inventory management, transactional bed allocation with pessimistic concurrency locking, student room requests, audit logging, and modern responsive dashboards.

---

## 🌟 Key Features (Phase 1)

1. **Authentication & Session Security**
   - Pure **JWT stored in an HTTP-only, `SameSite=Strict` cookie** (`hostel_jwt`), immune to XSS token theft.
   - **Role-matched login**: Users choose their login persona tab (Student, Warden, Admin, Staff); unauthorized cross-role logins are blocked.
   - **Brute-force protection**: 5 consecutive failed login attempts lock the account for 15 minutes.
   - **BCrypt password hashing** with mandatory password change flag for newly provisioned accounts.

2. **Student Portal**
   - Real-time room allocation status badge (`ALLOCATED`, `REQUEST PENDING`, `NOT ALLOCATED`).
   - Profile viewer with protected student photo upload and viewing.
   - Room request submission modal displaying available rooms and free bed counts.
   - Historical requests tracker with the ability to cancel pending requests.
   - Interactive preview cards for upcoming modules.

3. **Warden Portal**
   - KPI metrics: Total Rooms, Total Beds, Occupied Beds, Vacant Beds, and Pending Requests.
   - Dynamic Room Occupancy table with real-time occupancy progress bars.
   - Request approval/rejection queue with automated next-available-bed assignment.
   - Student roster search and filtering with 1-click **Vacate / Release Bed** action.
   - Room management: Create new rooms and adjust capacity dynamically.

4. **Admin Portal**
   - User directory with role filtering, searching, and pagination.
   - Student onboarding modal (Registration number, department, year, phone).
   - Staff onboarding modal (Staff ID, designation, department).
   - User account status toggling (Active / Deactivated) and instant password reset.

5. **Staff Shell Dashboards (Coming Soon Placeholders)**
   - Dedicated authenticated dashboard shells for:
     - 🛡️ **Security Guard** (`SECURITY`)
     - 🧹 **Cleaning Worker** (`CLEANING_WORKER`)
     - 🔧 **Maintenance Staff** (`MAINTENANCE_STAFF`)
     - 🍲 **Mess Staff** (`MESS_STAFF`)
     - 🥤 **Vending Manager** (`VENDING_MANAGER`)

6. **High-Concurrency Race Condition Prevention**
   - Database-level **Pessimistic Write Locking** (`SELECT ... FOR UPDATE`) on the `beds` inventory during allocation.
   - When multiple wardens or concurrent requests attempt to allocate the last available bed in a room, exactly one succeeds while competing transactions are safely rejected with HTTP 409 Conflict.

7. **Strict IDOR & Data Isolation**
   - Students can only view, request, and cancel their own records.
   - Access to other students' personal files or requests is rejected with HTTP 403 Forbidden.

---

## 🛠️ Technology Stack

| Layer | Technology | Details |
|---|---|---|
| **Frontend** | Plain HTML5, CSS3, Vanilla JS | Zero third-party JS/CSS libraries or frameworks. Pure `fetch` API, strict DOM `textContent` escaping, CSS Custom Properties design system. |
| **Backend** | Java 17 / Spring Boot 3.3.4 | Spring Web, Spring Security 6, Spring Data JPA, Hibernate, Bean Validation. |
| **Database** | MySQL 8 (`hostel_db`) | Normalized relational schema with foreign keys, indexes, and unique bed allocation constraints. |
| **Migration** | Flyway 10 | Automated baseline migrations and seed data scripts in `db/migration`. |
| **Tokens & Security** | JJWT 0.12.6, BCrypt | Signed HMAC-SHA512 JWT tokens, secure cookie handling. |
| **Fallback Demo** | H2 Database (MySQL Mode) | Built-in `--spring.profiles.active=local` profile for instant zero-dependency demonstration without pre-running MySQL. |

---

## 🔑 Demo Accounts & Credentials

> **Default Password for ALL Accounts:** `Hostel@123`

### Administrative & Staff Accounts

| Role | Person / Title | Login Identifier / Email | Role Tab to Select |
|---|---|---|---|
| **ADMIN** | Dr. Rajesh Kumar (Chief Admin) | `admin@hostel.edu` | **Admin** |
| **WARDEN** | Prof. Suresh Iyer (Chief Warden) | `warden@hostel.edu` | **Warden** |
| **SECURITY** | Ramesh Patil (Chief Guard) | `security@hostel.edu` | **Staff** |
| **CLEANING_WORKER** | Lakshmi Devi (Sanitation Lead) | `cleaner@hostel.edu` | **Staff** |
| **MAINTENANCE_STAFF**| Ganesh Sharma (Lead Electrician) | `maintenance@hostel.edu` | **Staff** |
| **MESS_STAFF** | Murugan Velu (Head Chef) | `mess@hostel.edu` | **Staff** |
| **VENDING_MANAGER** | Priya Nair (Vending Admin) | `vending@hostel.edu` | **Staff** |

### Student Demo Accounts

| Reg Number | Student Name | Department | Year | Initial Allocation State |
|---|---|---|---|---|
| `REG202601` | Aarav Sharma | Computer Science | 2 | **Allocated** (Room 101, Bed 1) |
| `REG202602` | Ananya Patel | Electronics & Comm | 2 | **Allocated** (Room 101, Bed 2) |
| `REG202603` | Rohan Verma | Information Tech | 3 | **Allocated** (Room 101, Bed 3) |
| `REG202604` | Sneha Reddy | Mechanical Engg | 3 | **Allocated** (Room 101, Bed 4) |
| `REG202605` | Vikram Singh | Civil Engg | 4 | **Allocated** (Room 102, Bed 1) |
| `REG202606` | Pooja Joshi | Electrical Engg | 4 | **Allocated** (Room 102, Bed 2) |
| `REG202607` | Rahul Nair | Chemical Engg | 1 | **Allocated** (Room 103, Bed 1) |
| `REG202608` | Sanya Kapoor | Biotechnology | 1 | **Request Pending** (Requested Room 102) |
| **`REG202609`** | **Arjun Menon** | **AI & Data Science** | **1** | **NOT ALLOCATED** *(Ideal for Live Demo)* |
| **`REG202610`** | **Meera Iyer** | **Cyber Security** | **1** | **NOT ALLOCATED** *(Secondary Demo)* |

---

## 🚀 Getting Started

### 1. Prerequisites
- **Java 17** or higher (`java -version`)
- **MySQL 8** (or use the built-in local fallback profile)
- A web browser (Chrome, Safari, Edge, Firefox)

### 2. Configure MySQL Database
Create the database:
```sql
CREATE DATABASE IF NOT EXISTS hostel_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

*(Optional)* Configure custom database credentials via environment variables:
```bash
export DB_URL="jdbc:mysql://localhost:3306/hostel_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata"
export DB_USERNAME="root"
export DB_PASSWORD="your_password"
```

### 3. Run the Application

#### Option A: Running with MySQL (Default)
```bash
./mvnw spring-boot:run
```
Or execute the pre-built jar:
```bash
java -jar target/smart-hostel-management-1.0.0.jar
```

#### Option B: Zero-Setup Instant Demo (H2 In-Memory MySQL Mode)
If MySQL is not installed or running, launch with the `local` profile:
```bash
java -jar target/smart-hostel-management-1.0.0.jar --spring.profiles.active=local
```
*(Flyway will automatically execute migrations `V1` and `V2`, populating all seed rooms, beds, and users.)*

### 4. Access the Web Portal
Open your browser and navigate to:
```
http://localhost:8080/
```
(Redirects automatically to `http://localhost:8080/login.html`)

---

## 🎬 3-Minute Live Presentation Demo Script

### Act 1: The Student Experience (Arjun Menon - `REG202609`)
1. Open `http://localhost:8080/login.html`.
2. Notice the modern responsive split UI with campus hero images on the left and the role selector card on the right.
3. Keep the **Student** persona pill selected.
4. Enter Identifier: `REG202609`, Password: `Hostel@123`.
5. Click **Sign In**.
6. **Point out on Dashboard:**
   - Welcome banner: "Welcome, Arjun Menon".
   - Status badge shows in bold red: **NOT ALLOCATED**.
   - Profile summary shows Year 1 AI & Data Science details.
7. Click the blue **Request Room** button.
8. In the modal, select **Room 102 - Floor 1 (2 beds free)**.
9. Type note: *"Prefer quiet corner desk near window"*.
10. Click **Submit Request**.
11. Observe instant toast notification and the allocation status badge dynamically flips to amber **REQUEST PENDING**.
12. In the top navbar, click **Sign Out**.

### Act 2: The Warden Portal & Concurrency Control
1. On `login.html`, click the **Warden** persona pill.
2. Enter Email: `warden@hostel.edu`, Password: `Hostel@123`.
3. Click **Sign In**.
4. **Point out on Warden Dashboard:**
   - Metrics bar: Total Rooms (4), Total Beds (13), Occupied (7), Vacant (6), Pending Requests (2).
   - Dynamic room occupancy table with visual progress indicators for Rooms 101, 102, 103, 104.
5. Scroll to **Pending Allocation Requests**:
   - Locate the request row for **Arjun Menon** (Room 102).
6. Click the green **Approve** button.
7. Confirm the approval dialog.
8. Observe:
   - Request is approved and automatically assigned to the lowest available bed (Bed #3).
   - Room 102 occupancy immediately updates from 2/4 to 3/4.
9. In the top navbar, click **Sign Out**.

### Act 3: Verification & Allocated State
1. Select **Student** persona pill on `login.html`.
2. Login again as `REG202609` / `Hostel@123`.
3. **Show the audience:**
   - The status badge has flipped to vibrant green: **ALLOCATED**.
   - The room badge displays: **Room 102 - Bed #3**.
   - The Request Room button is automatically disabled to prevent duplicate submissions.
   - Request History table lists the request as **APPROVED** with decision timestamps.

### Act 4: Admin Directory & Staff Shells
1. Switch to **Admin** (`admin@hostel.edu` / `Hostel@123`).
2. Show student/staff directory search, role filters, user status toggles, and onboarding modals.
3. Demonstrate logging into staff shell dashboards (e.g. `security@hostel.edu` with **Staff** pill) showing authenticated shells and Phase 2 "Coming Soon" features.

---

## 🧪 Automated Test Suite

Run the full automated test suite covering authentication, security guards, pessimistic locking, and IDOR protection:

```bash
./mvnw test
```

### Verified Test Cases:
- `AuthTests`:
  - `testLoginSuccess`: Successful JWT cookie issuance.
  - `testLoginFailureWrongPassword`: 401 Unauthorized on invalid credentials.
  - `testRoleMismatchRejected`: 401 Unauthorized when a student attempts to login via Warden persona.
  - `testLockoutAfter5Failures`: 423 Locked after 5 consecutive bad login attempts.
- `SecurityTests`:
  - `testUnauthenticatedEndpointReturns401`: Verifies unauthenticated API access rejection.
  - `testStudentAccessingWardenEndpointReturns403`: Verifies RBAC boundary enforcement.
  - `testStudentAccessingAdminEndpointReturns403`: Verifies student cannot access administrative endpoints.
- `StudentTests`:
  - `testAllocatedStudentCannotRequestAgain`: Verifies 400 Bad Request when allocated student requests room.
  - `testCancelOtherStudentRequestReturns403`: Verifies IDOR protection when cancelling requests.
- `AllocationConcurrencyTests`:
  - `testConcurrentAllocationOfLastBed`: Spawns concurrent threads competing for the final free bed; verifies pessimistic write lock ensures **exactly 1 allocation succeeds** and the competing thread receives **409 Conflict**.

---

## 🗄️ Database Schema & Migrations

- **`V1__init_schema.sql`**:
  - `users`: Core credentials, roles, failed attempts counter, lockout timestamp, password change flag.
  - `students`: Student details linked 1:1 with users table.
  - `staff_profiles`: Staff designations and department assignments.
  - `rooms`: Room number, floor, capacity, and active status.
  - `beds`: Bed inventory linked to rooms, unique constraint on student allocation.
  - `room_requests`: Student room requests with preference, notes, status, and warden decision logs.
  - `allocation_history`: Historical audit records of room/bed assignments and vacates.
  - `audit_logs`: Detailed audit trail of user activities, IP addresses, and timestamps.
- **`V2__seed_data.sql`**:
  - Production-ready seed data containing 1 Admin, 1 Warden, 5 Staff members, 10 Students, 4 Rooms (13 Beds), initial allocations, and demo pending requests.

---

## 🔮 Phase 2 Roadmap (Upcoming Modules)
- **Digital Attendance**: QR code and biometric check-ins for nightly roll calls.
- **Digital Outpass**: Automated multi-level approval for day and night leaves with SMS alerts.
- **Parcel Log Desk**: Package intake logging, student notification, and OTP verification upon pickup.
- **Facility Maintenance**: Issue ticketing for electrical, plumbing, carpentry, and AC servicing.
- **Hostel Events**: Event calendars, signups, and sports equipment booking.
- **Mess & Dining**: Weekly menu display, meal preferences, and quality feedback ratings.
- **Smart Vending**: Integration with on-campus snack and beverage automated kiosks.
