package com.jb.ess.pattern.controller;

import com.jb.ess.common.domain.PatternDetail;
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
import org.springframework.web.bind.annotation.ModelAttribute;
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
                                  @RequestParam(value = "patternName", required = false) String patternName,
                                  Model model) {
        YearMonth selectedMonth = (monthStr != null) ? YearMonth.parse(monthStr) : YearMonth.now();

        int daysInMonth = selectedMonth.lengthOfMonth();
        List<String> dateHeaders = DateUtil.getDateHeaders(selectedMonth);
        List<Map<String, Object>> patternTable = patternService.getPatternCalendar(selectedMonth, patternName);
        List<ShiftMaster> shiftCodes = shiftMasterMapper.findAllShiftCodes();
        Map<String, String> colorMap = patternService.generateShiftCodeColors(shiftCodes);

        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("patternTable", patternTable);
        model.addAttribute("daysInMonth", daysInMonth);
        model.addAttribute("shiftCodeList", shiftCodes);
        model.addAttribute("shiftColorMap", colorMap);
        model.addAttribute("dateHeaders", dateHeaders);
        model.addAttribute("patternName", patternName);

        return "admin/pattern/list";
    }

    /* 근태패턴 생성폼 */
    @GetMapping("/create")
    public String createPatternForm(Model model) {
        List<ShiftMaster> shiftCodes = shiftMasterMapper.findAllShiftCodes(); // HRTSHIFTMASTER 전체 조회
        model.addAttribute("shiftCodes", shiftCodes);
        return "admin/pattern/create";
    }

    /* 근태패턴 생성 */
    @PostMapping("/create")
    public String savePattern(@ModelAttribute PatternDetail form) {
        patternService.savePattern(form); // 또는 mapper 직접 호출
        return "redirect:/admin/pattern/list";
    }

    /* 근태패턴 삭제 */
    @PostMapping("/delete")
    public String deletePatterns(@RequestParam(value = "patternCodes", required = false) List<String> patternCodes) {
        patternService.deletePatternsByCodes(patternCodes);
        return "redirect:/admin/pattern/list";
    }
}
