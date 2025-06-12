package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.HistoryService;
import com.jb.ess.common.domain.AttHistory;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.security.CustomUserDetails;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/user/history")
@RequiredArgsConstructor
public class HistoryController {
    private final EmployeeMapper employeeMapper;
    private final HistoryService historyService;

    @GetMapping("/list")
    public String historyList(
        @AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(value = "startDate", required = false) LocalDate startDate,
        @RequestParam(value = "endDate", required = false) LocalDate endDate,
        @RequestParam(value = "applyType", required = false) String applyType,
        @RequestParam(value = "status", required = false) String status,
        Model model) {

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("applyType", applyType);
        model.addAttribute("status", status);

       List<AttHistory> attList = historyService.setAttList(startDate, endDate, applyType, status, user.getUsername());

       model.addAttribute("attList", attList);
        return "user/history";
    }
}
