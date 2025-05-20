package com.jb.ess.pattern.service;

import com.jb.ess.common.domain.ShiftCalendar;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.domain.ShiftPattern;
import com.jb.ess.common.domain.ShiftPatternDtl;
import com.jb.ess.common.util.DateUtil;
import com.jb.ess.pattern.mapper.ShiftCalendarMapper;
import com.jb.ess.pattern.mapper.ShiftPatternDtlMapper;
import com.jb.ess.pattern.mapper.ShiftPatternMapper;
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

    /* 캘린더 생성 제너레이터 */
    public void generateShiftCalendar(String workPatternCode, YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            int dayOfWeek = date.getDayOfWeek().getValue(); // 1: 월 ~ 7: 일
            String shiftCode = shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, dayOfWeek);

            if (shiftCode != null) {
                String shiftDate = date.format(formatter); // → "20250601" 형식으로 변환
                shiftCalendarMapper.insertShiftCalendar(new ShiftCalendar(
                    workPatternCode,
                    shiftDate,
                    shiftCode
                ));
            }
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

    public Boolean findShiftCalendar(String workPatternCode, String dateStr) {
        return shiftCalendarMapper.getCountShiftCalendar(workPatternCode, dateStr) == 0;
    }
}