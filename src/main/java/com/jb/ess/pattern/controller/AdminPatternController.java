package com.jb.ess.pattern.controller;

import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.domain.ShiftPattern;
import com.jb.ess.common.domain.ShiftPatternDtl;
import com.jb.ess.pattern.mapper.ShiftMasterMapper;
import com.jb.ess.pattern.mapper.ShiftPatternMapper;
import com.jb.ess.pattern.service.PatternService;
import com.jb.ess.common.util.DateUtil;
import java.time.YearMonth;
import java.util.ArrayList;
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
    private final ShiftPatternMapper shiftPatternMapper;

    /* 근태패턴 테이블 */
    @GetMapping("/list")
    public String showAllPatterns(@RequestParam(value = "month", required = false) String monthStr,
                                  @RequestParam(value = "workPatternCode", required = false) String workPatternCode,
                                  Model model) {

        /* 선택한 yyyy-mm를 String to YearMonth 변환 */
        if (monthStr == null) monthStr = YearMonth.now().toString();
        YearMonth selectedMonth = YearMonth.parse(monthStr);

        List<ShiftPattern> patterns;
        /* 근태패턴코드 전체 조회인 경우 */
        if (workPatternCode == null || workPatternCode.isEmpty()) {
            patterns = shiftPatternMapper.findAllPatterns();
            for (ShiftPattern pattern : patterns) {
                /* HRTSHIFTCALENDAR에 근태패턴들의 캘린더가 존재하는지 탐색 */
                if (Boolean.TRUE.equals(patternService.findShiftCalendar(pattern.getWorkPatternCode(), monthStr.replace("-", "")))){
                    /* 선택한 yyyy-mm 에 대해 존재하는 근태패턴의 캘린더를 생성 */
                    patternService.generateShiftCalendar(pattern.getWorkPatternCode(), selectedMonth);
                }
            }
        /* 근태패턴코드 검색(1가지) 인 경우 */
        } else {
            /* HRTSHIFTCALENDAR에 근태패턴들의 캘린더가 존재하는지 탐색 */
            if (Boolean.TRUE.equals(patternService.findShiftCalendar(workPatternCode, monthStr.replace("-", "")))){
                /* 선택한 yyyy-mm 에 대해 존재하는 근태패턴의 캘린더를 생성 */
                patternService.generateShiftCalendar(workPatternCode, selectedMonth);
            }
        }

        int daysInMonth = selectedMonth.lengthOfMonth();
        List<String> dateHeaders = DateUtil.getDateHeaders(selectedMonth);
        List<Map<String, Object>> patternTable = patternService.getPatternCalendar(selectedMonth, workPatternCode);
        /* 근태코드 색상관련 */
        List<ShiftMaster> shiftCodeList = shiftMasterMapper.findAllShiftCodes();
        Map<String, String> colorMap = patternService.generateShiftCodeColors(shiftCodeList);

        /* 근태패턴 리스트 */
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("patternTable", patternTable);
        model.addAttribute("daysInMonth", daysInMonth);
        model.addAttribute("dateHeaders", dateHeaders);
        /* 근태코드 색상 */
        model.addAttribute("shiftCodeList", shiftCodeList);
        model.addAttribute("shiftColorMap", colorMap);
        /* 근태패턴코드 검색 */
        model.addAttribute("workPatternCode", workPatternCode);

        return "admin/pattern/list";
    }

    /* 근태패턴 삭제 */
    @PostMapping("/delete")
    public String deletePatterns(
        @RequestParam(value = "workPatternCodes", required = false) List<String> workPatternCodes) {
        patternService.deletePatternsByCodes(workPatternCodes);
        return "redirect:/admin/pattern/list";
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
    public String savePattern(@RequestParam String workPatternCode,
        @RequestParam String workPatternName,
        @RequestParam Map<String, String> dayOfWeekMap) {

        // 마스터 패턴 도메인 생성
        ShiftPattern pattern = new ShiftPattern();
        pattern.setWorkPatternCode(workPatternCode);
        pattern.setWorkPatternName(workPatternName);

        // 디테일 목록 생성
        List<ShiftPatternDtl> detailList = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            String paramKey = "dayOfWeekMap[" + i + "]";
            if (dayOfWeekMap.containsKey(paramKey)) {
                ShiftPatternDtl detail = new ShiftPatternDtl();
                detail.setWorkPatternCode(workPatternCode);
                detail.setDayOfWeek(i);
                detail.setShiftCode(dayOfWeekMap.get(paramKey));
                detailList.add(detail);
            }
        }

        patternService.createPattern(pattern, detailList);
        return "redirect:/admin/pattern/list";
    }
}