package com.hostel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageRedirectController {

    // Student portal redirects
    @GetMapping("/student/mess-booking.html")
    public String redirectStudentMessBooking() {
        return "redirect:/student/mess-tokens.html?tab=book";
    }

    @GetMapping("/student/my-tokens.html")
    public String redirectStudentMyTokens() {
        return "redirect:/student/mess-tokens.html?tab=my-tokens";
    }

    @GetMapping("/student/raise-request.html")
    public String redirectStudentRaiseRequest() {
        return "redirect:/student/maintenance.html?tab=raise";
    }

    @GetMapping("/student/my-requests.html")
    public String redirectStudentMyRequests() {
        return "redirect:/student/maintenance.html?tab=list";
    }

    @GetMapping("/student/booking.html")
    public String redirectStudentBooking() {
        return "redirect:/student/dashboard.html";
    }

    // Warden portal redirects
    @GetMapping("/warden/mess-scan.html")
    public String redirectWardenMessScan() {
        return "redirect:/warden/mess.html?tab=scan";
    }

    @GetMapping("/warden/mess-menu.html")
    public String redirectWardenMessMenu() {
        return "redirect:/warden/mess.html?tab=menu";
    }

    @GetMapping("/warden/parcel-scan.html")
    public String redirectWardenParcelScan() {
        return "redirect:/warden/parcels.html?tab=scan";
    }

    @GetMapping("/warden/workers.html")
    public String redirectWardenWorkers() {
        return "redirect:/warden/maintenance.html?tab=workers";
    }

    @GetMapping("/warden/rooms.html")
    public String redirectWardenRooms() {
        return "redirect:/warden/dashboard.html";
    }

    @GetMapping("/warden/whitelist.html")
    public String redirectWardenWhitelist() {
        return "redirect:/warden/dashboard.html";
    }
}

