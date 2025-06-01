package com.jb.ess.pattern.controller;

import com.jb.ess.common.domain.EmpCalendar;
import com.jb.ess.common.domain.ShiftCalendar;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.domain.ShiftPattern;
import com.jb.ess.common.domain.ShiftPatternDtl;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.EmpCalendarMapper;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.mapper.ShiftCalendarMapper;
import com.jb.ess.common.mapper.ShiftMasterMapper;
import com.jb.ess.common.mapper.ShiftPatternMapper;
import com.jb.ess.common.util.WorkHoursCalculator;
import com.jb.ess.pattern.service.PatternService;
import com.jb.ess.common.util.DateUtil;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ShiftCalendarMapper shiftCalendarMapper;
    private static final String VIEW_CREATE = "admin/pattern/create";
    private final EmpCalendarMapper empCalendarMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;

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
                if (Boolean.TRUE.equals(patternService.findEmpCalendar(pattern.getWorkPatternCode(), monthStr.replace("-", "")))){
                    patternService.generateEmpCalendar(pattern.getWorkPatternCode(), selectedMonth);
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
    public String deletePatterns(@RequestParam(value = "workPatternCodes", required = false) List<String> workPatternCodes) {
        patternService.deletePatternsByCodes(workPatternCodes);
        return "redirect:/admin/pattern/list";
    }

    /* 근태패턴 생성 Form */
    @GetMapping("/create")
    public String createPatternForm(Model model) {
        List<ShiftMaster> shiftCodes = shiftMasterMapper.findAllShiftCodes(); // HRTSHIFTMASTER 전체 조회
        model.addAttribute("shiftCodes", shiftCodes);
        return VIEW_CREATE;
    }

    /* 근태패턴 생성 처리*/
    @PostMapping("/create")
    public String savePattern(@RequestParam String workPatternCode,
                              @RequestParam String workPatternName,
                              @RequestParam Map<String, String> dayOfWeekMap,
                              Model model) {
        if (shiftPatternMapper.findPatternByCode(workPatternCode) != null) {
            return sendErrorMsg("중복된 근태패턴코드입니다.", model);
        }

        // 마스터 패턴 도메인 생성
        ShiftPattern pattern = new ShiftPattern();
        pattern.setWorkPatternCode(workPatternCode);
        pattern.setWorkPatternName(workPatternName);

        // 디테일 목록 생성
        List<ShiftPatternDtl> detailList = new ArrayList<>();
        Duration totalWorkTime = Duration.ZERO;
        for (int i = 1; i <= 7; i++) {
            String paramKey = "dayOfWeekMap[" + i + "]";
            if (dayOfWeekMap.containsKey(paramKey)) {
                ShiftPatternDtl detail = new ShiftPatternDtl();
                detail.setWorkPatternCode(workPatternCode);
                detail.setDayOfWeek(i);
                detail.setShiftCode(dayOfWeekMap.get(paramKey));
                detailList.add(detail);

                ShiftMaster shift = shiftMasterMapper.findShiftByCode(detail.getShiftCode());

                if (!Objects.equals(shift.getWorkDayType(), "0")) continue; // 휴무, 휴일 배제
                totalWorkTime = totalWorkTime.plus(WorkHoursCalculator.getTotalWorkTime(shift));

                /* 익일근무 다음날 주간근무 불가 */
                if (i < 5 && Objects.equals(shift.getWorkOffDayType(), "N1") &&
                    Objects.equals(shiftMasterMapper.findShiftByCode(dayOfWeekMap.get("dayOfWeekMap[" + (i + 1) + "]")).getWorkOffDayType(), "N0")){
                    return sendErrorMsg("익일근무 다음날 주간근무는 불가능합니다.", model);
                }
            }
        }

        /* 주 52시간 초과 불가 */
        if (totalWorkTime.toMinutes() > (52 * 60)) {
            return sendErrorMsg("52시간을 초과하였습니다.", model);
        }
        else {
            /* 주 52시간 조건 충족 */
            long totalMinutes = totalWorkTime.toMinutes();
            long hours = totalMinutes / 60;
            long minutes = totalMinutes % 60;
            pattern.setTotalWorkingHours(String.format("%02d%02d", hours, minutes));
            patternService.createPattern(pattern, detailList);
        }

        return "redirect:/admin/pattern/list";
    }

    /* 근태패턴 수정 Form */
    @GetMapping("/edit/{workPatternCode}")
    public String editPatternForm(@PathVariable String workPatternCode,
                                  @RequestParam("month") String monthStr,
                                  Model model) {
        /* 선택된 yyyyMM */
        YearMonth selectedMonth = YearMonth.parse(monthStr);
        List<ShiftMaster> shiftCodeList = shiftMasterMapper.findAllShiftCodes();
        Map<String, String> shiftCodeMap = new LinkedHashMap<>();
        ShiftPattern pattern = shiftPatternMapper.findPatternByCode(workPatternCode);

        /* 일별 근태패턴코드 */
        for (int day = 1; day <= selectedMonth.lengthOfMonth(); day++) {
            LocalDate date = selectedMonth.atDay(day);         // yyyy-MM-dd
            String dateStr = DateUtil.reverseFormatDate(date); // yyyyMMdd
            String shiftCode = shiftCalendarMapper.getShiftCodeByPatternCodeAndDate(workPatternCode, dateStr);
            shiftCodeMap.put(String.valueOf(day), shiftCode);
        }

        model.addAttribute("workPatternCode", workPatternCode);
        model.addAttribute("selectedMonth", selectedMonth.toString()); // YYYY-MM
        model.addAttribute("shiftCodeMap", shiftCodeMap);
        model.addAttribute("shiftCodeList", shiftCodeList);
        model.addAttribute("patternName", pattern.getWorkPatternName());

        return "admin/pattern/edit";
    }

    /* 근태패턴 수정 처리 */
    @PostMapping("/update")
    public String updatePattern(@RequestParam("workPatternCode") String workPatternCode,
                                @RequestParam("selectedMonth") String selectedMonthStr,
                                @RequestParam Map<String, String> shiftCodes) {

        // 선택된 yyyyMM (예: "2025-06" or "202506")
        YearMonth selectedMonth = YearMonth.parse(selectedMonthStr.replace("-", ""));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        // 수정된 캘린더 리스트 생성
        List<ShiftCalendar> updatedList = new ArrayList<>();
        List<String> shiftDates = new ArrayList<>();
        List<EmpCalendar> empCalendarList = new ArrayList<>();

        for (int day = 1; day <= selectedMonth.lengthOfMonth(); day++) {
            String key = "shiftCodes[" + day + "]";
            String shiftCode = shiftCodes.get(key);
            if (shiftCode != null && !shiftCode.isEmpty()) {
                String shiftDate = selectedMonth.atDay(day).format(formatter);
                updatedList.add(new ShiftCalendar(workPatternCode, shiftDate, shiftCode));
                shiftDates.add(shiftDate);
            }
        }

        // 기존 패턴의 해당 월 데이터 삭제 후 배치 삽입
        shiftCalendarMapper.deleteShiftCalendarByMonth(workPatternCode, selectedMonthStr.replace("-", ""));
        if (!updatedList.isEmpty()) {
            shiftCalendarMapper.insertBatch(updatedList);
        }

        // === EmpCalendar 삭제 및 재삽입 ===
        if (!shiftDates.isEmpty()) {
            // 1. 삭제
            empCalendarMapper.deleteEmpCalendarForUpdate(workPatternCode, selectedMonthStr.replace("-", ""));
            // 2. 재삽입할 EmpCalendar 데이터 생성
            List<String> deptCodes = departmentMapper.findDeptCodesByWorkPatternCode(workPatternCode);
            for (String deptCode : deptCodes) {
                List<String> empCodes = employeeMapper.findEmpCodesByDeptCode(deptCode);
                for (String empCode : empCodes) {
                    for (ShiftCalendar sc : updatedList) {
                        int dayOfWeek = LocalDate.parse(sc.getShiftDate(), formatter).getDayOfWeek()
                            .getValue();
                        String holidayYn = (dayOfWeek == 6 || dayOfWeek == 7) ? "Y" : "N";
                        empCalendarList.add(new EmpCalendar(
                            workPatternCode,
                            sc.getShiftDate(),
                            sc.getShiftCode(),
                            sc.getShiftCode(),
                            empCode,
                            deptCode,
                            holidayYn
                        ));
                    }
                }
            }
        }

        // 3. 재삽입
        if (!empCalendarList.isEmpty()) {
            empCalendarMapper.insertBatch(empCalendarList);
        }

        return "redirect:/admin/pattern/list?month=" + selectedMonthStr + "&workPatternCode=" + workPatternCode;
    }

    /* 에러메시지 전달 */
    public String sendErrorMsg(String errorMsg, Model model) {
        List<ShiftMaster> shiftCodes = shiftMasterMapper.findAllShiftCodes();
        model.addAttribute("shiftCodes", shiftCodes);
        model.addAttribute("errorMsg", errorMsg);
        return VIEW_CREATE;
    }
}