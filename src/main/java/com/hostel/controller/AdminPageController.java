package com.hostel.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    @GetMapping({"/admin/room-allotment", "/admin/room-allotment/"})
    public String adminRoomAllotment() {
        return "forward:/admin/room-allotment.html";
    }
}
