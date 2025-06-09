package com.jb.ess.pattern.service;

import com.jb.ess.common.domain.EmpCalendar;
import com.jb.ess.common.domain.ShiftCalendar;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.domain.ShiftPattern;
import com.jb.ess.common.domain.ShiftPatternDtl;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.EmpCalendarMapper;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.util.DateUtil;
import com.jb.ess.common.mapper.ShiftCalendarMapper;
import com.jb.ess.common.mapper.ShiftPatternDtlMapper;
import com.jb.ess.common.mapper.ShiftPatternMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatternService {
    private final ShiftPatternMapper shiftPatternMapper;
    private final ShiftPatternDtlMapper shiftPatternDtlMapper;
    private final ShiftCalendarMapper shiftCalendarMapper;
    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final EmpCalendarMapper empCalendarMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 요일별 shiftCode 매핑 캐시
    public Map<Integer, String> getShiftCodeMap(String workPatternCode) {
        Map<Integer, String> shiftCodeMap = new HashMap<>();
        for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
            String shiftCode = shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, dayOfWeek);
            if (shiftCode != null) {
                shiftCodeMap.put(dayOfWeek, shiftCode);
            }
        }
        return shiftCodeMap;
    }

    /* 달마다 몇일인지 계산 */
    public List<LocalDate> getDatesInMonth(YearMonth yearMonth) {
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    /* 근태패턴캘린더 생성자 */
    public void generateShiftCalendar(String workPatternCode, YearMonth yearMonth) {
        Map<Integer, String> shiftCodeMap = getShiftCodeMap(workPatternCode);
        List<LocalDate> dates = getDatesInMonth(yearMonth);

        List<ShiftCalendar> batchList = new ArrayList<>();
        for (LocalDate date : dates) {
            int dayOfWeek = date.getDayOfWeek().getValue();
            String shiftCode = shiftCodeMap.get(dayOfWeek);
            if (shiftCode != null) {
                batchList.add(new ShiftCalendar(
                    workPatternCode,
                    date.format(FORMATTER),
                    shiftCode
                ));
            }
        }

        if (!batchList.isEmpty()) {
            shiftCalendarMapper.insertBatch(batchList);
        }
    }

    /* 사원의 근태패턴캘린더 생성자 */
    public void generateEmpCalendar(String workPatternCode, YearMonth yearMonth) {
        Map<Integer, String> shiftCodeMap = getShiftCodeMap(workPatternCode);
        List<LocalDate> dates = getDatesInMonth(yearMonth);
        List<EmpCalendar> batchList = new ArrayList<>();

        List<String> deptCodes = departmentMapper.findDeptCodesByWorkPatternCode(workPatternCode);
        for (String deptCode : deptCodes) {
            List<String> empCodes = employeeMapper.findEmpCodesByDeptCode(deptCode);
            for (String empCode : empCodes) {
                for (LocalDate date : dates) {
                    if (empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, date.format(FORMATTER)) != null) continue;
                    int dayOfWeek = date.getDayOfWeek().getValue();
                    String shiftCode = shiftCodeMap.get(dayOfWeek);
                    if (shiftCode != null) {
                        String holidayYn = (dayOfWeek == 6 || dayOfWeek == 7) ? "Y" : "N";
                        batchList.add(new EmpCalendar(
                            workPatternCode,
                            date.format(FORMATTER),
                            shiftCode,
                            shiftCode,
                            empCode,
                            deptCode,
                            holidayYn
                        ));
                    }
                }
            }
        }

        if (!batchList.isEmpty()) {
            empCalendarMapper.insertBatch(batchList);
        }
    }

    /* 각 근태코드에 색상 부여 */
    public Map<String, String> generateShiftCodeColors(List<ShiftMaster> shiftCodes) {
        String[] colors = {
            "bg-red-200", "bg-green-200", "bg-blue-200", "bg-yellow-200",
            "bg-purple-200", "bg-pink-200", "bg-orange-200", "bg-indigo-200",
            "bg-teal-200", "bg-lime-200", "bg-amber-200"
        };

        Map<String, String> colorMap = new HashMap<>();
        int i = 0;
        for (ShiftMaster sm : shiftCodes) {
            colorMap.put(sm.getShiftCode(), colors[i % colors.length]);
            i++;
        }
        return colorMap;
    }

    /* 근태패턴 테이블 생성 */
    public List<Map<String, Object>> getPatternCalendar(YearMonth month, String workPatternCode) {
        List<ShiftPattern> patterns;
        if (workPatternCode == null || workPatternCode.isEmpty()) {
            patterns = shiftPatternMapper.findAllPatterns();
        } else {
            patterns = shiftPatternMapper.findPatternsByCode(workPatternCode);
        }
        List<Map<String, Object>> result = new ArrayList<>();

        for (ShiftPattern pattern : patterns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("workPatternCode", pattern.getWorkPatternCode());
            row.put("workPatternName", pattern.getWorkPatternName());

            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                String shiftCode = shiftCalendarMapper.getShiftCodeByPatternCodeAndDate(pattern.getWorkPatternCode(),
                                                                                        DateUtil.reverseFormatDate(date));
                row.put(String.valueOf(day), shiftCode);
            }

            result.add(row);
        }

        return result;
    }

    /* 근태패턴 삭제 */
    public void deletePatternsByCodes(List<String> workPatternCodes) {
        if (workPatternCodes == null) return;
        for (String code : workPatternCodes) {
            if (code == null || code.isEmpty()) continue;
            shiftCalendarMapper.deleteShiftCalendar(code);
            shiftPatternDtlMapper.deletePatternDtl(code);
            empCalendarMapper.deleteEmpCalendar(code);
            shiftPatternMapper.deletePattern(code);
        }
    }

    @Transactional
    /* 근태패턴 저장 */
    public void createPattern(ShiftPattern pattern, List<ShiftPatternDtl> detailList) {
        shiftPatternMapper.insertShiftPattern(pattern);

        for (ShiftPatternDtl detail : detailList) {
            shiftPatternDtlMapper.insertShiftPatternDetail(detail);
        }
    }

    /* dateStr 날짜에 workPatternCode의 캘린더가 존재하는가? False : True */
    public Boolean findShiftCalendar(String workPatternCode, String dateStr) {
        return shiftCalendarMapper.getCountShiftCalendar(workPatternCode, dateStr) == 0;
    }

    /* dateStr 날짜에 workPatternCode의 사원용 캘린더가 존재하는가? False : True */
    public Boolean findEmpCalendar(String workPatternCode, String dateStr) {
        List<String> deptCodes = departmentMapper.findDeptCodesByWorkPatternCode(workPatternCode);
        for (String deptCode : deptCodes) {
            List<String> empCodes = employeeMapper.findEmpCodesByDeptCode(deptCode);
            for (String empCode : empCodes) {
                if (empCalendarMapper.getCountEmpCalendar(workPatternCode, dateStr, empCode) == 0) {
                    return true;
                }
            }
        }
        return false;
    }
}