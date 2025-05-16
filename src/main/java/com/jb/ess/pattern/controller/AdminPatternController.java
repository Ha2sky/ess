package com.jb.ess.pattern.controller;

import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.pattern.mapper.ShiftMasterMapper;
import com.jb.ess.pattern.service.PatternService;
import com.jb.ess.common.util.DateUtil;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/pattern")
@RequiredArgsConstructor
public class AdminPatternController {
    private final PatternService patternService;
    private final ShiftMasterMapper shiftMasterMapper;

    /* 근태패턴 테이블 */
    @GetMapping("/list")
    public String showAllPatterns(@RequestParam(value = "month", required = false) String monthStr,
                                  @RequestParam(value = "workPatternName", required = false) String workPatternName,
                                  Model model) {
        YearMonth selectedMonth = (monthStr != null) ? YearMonth.parse(monthStr) : YearMonth.now();

        /* 근태패턴 캘린더 생성 제너레이터 */
//        patternService.generateShiftCalendar("A-1", YearMonth.of(2025, 5));
//        patternService.generateShiftCalendar("B-1", YearMonth.of(2025, 5));


        int daysInMonth = selectedMonth.lengthOfMonth();
        List<String> dateHeaders = DateUtil.getDateHeaders(selectedMonth);
        List<Map<String, Object>> patternTable = patternService.getPatternCalendar(selectedMonth, workPatternName);
        List<ShiftMaster> shiftCodeList = shiftMasterMapper.findAllShiftCodes();
        Map<String, String> colorMap = patternService.generateShiftCodeColors(shiftCodeList);

        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("patternTable", patternTable);
        model.addAttribute("daysInMonth", daysInMonth);
        model.addAttribute("shiftCodeList", shiftCodeList);
        model.addAttribute("shiftColorMap", colorMap);
        model.addAttribute("dateHeaders", dateHeaders);
        model.addAttribute("workPatternName", workPatternName);

        return "admin/pattern/list";
    }

//    /* 근태패턴 생성폼 */
//    @GetMapping("/create")
//    public String createPatternForm(Model model) {
//        List<ShiftMaster> shiftCodes = shiftMasterMapper.findAllShiftCodes(); // HRTSHIFTMASTER 전체 조회
//        model.addAttribute("shiftCodes", shiftCodes);
//        return "admin/pattern/create";
//    }
//
//    /* 근태패턴 생성 */
//    @PostMapping("/create")
//    public String savePattern(@ModelAttribute PatternDetail form) {
//        patternService.savePattern(form); // 또는 mapper 직접 호출
//        return "redirect:/admin/pattern/list";
//    }
//
    /* 근태패턴 삭제 */
    @PostMapping("/delete")
    public String deletePatterns(@RequestParam(value = "workPatternCodes", required = false) List<String> workPatternCodes) {
        System.out.println("ddworkPatternCodes: " + workPatternCodes);
        patternService.deletePatternsByCodes(workPatternCodes);
        return "redirect:/admin/pattern/list";
    }
}