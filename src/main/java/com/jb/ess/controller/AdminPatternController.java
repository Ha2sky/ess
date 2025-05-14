package com.jb.ess.controller;

import com.jb.ess.domain.ShiftMaster;
import com.jb.ess.mapper.ShiftMasterMapper;
import com.jb.ess.service.PatternService;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/pattern")
@RequiredArgsConstructor
public class AdminPatternController {
    private final PatternService patternService;
    private final ShiftMasterMapper shiftMasterMapper;

    @GetMapping("/list")
    public String showAllPatterns(@RequestParam(value = "month", required = false) String monthStr,
        Model model) {
        YearMonth selectedMonth = (monthStr != null) ? YearMonth.parse(monthStr) : YearMonth.now();

        List<Map<String, Object>> patternTable = patternService.getPatternCalendar(selectedMonth);
        List<ShiftMaster> shiftCodes = shiftMasterMapper.findAllShiftCodes();
        Map<String, String> colorMap = patternService.generateShiftCodeColors(shiftCodes);

        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("patternTable", patternTable);
        model.addAttribute("daysInMonth", selectedMonth.lengthOfMonth());
        model.addAttribute("shiftCodeList", shiftCodes);
        model.addAttribute("shiftColorMap", colorMap);

        return "admin/pattern/list";
    }
}
