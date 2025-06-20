package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.domain.ShiftMaster;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AttendanceApplyMapper {

    // 사원 정보 조회 (직위, 직책, 부서장 여부 포함)
    @Select("""
        SELECT h.*, p.POSITION_NAME, d.DUTY_NAME
        FROM HRIMASTER h
        LEFT JOIN HRTGRADEINFO p ON h.POSITION_CODE = p.POSITION_CODE
        LEFT JOIN HRTDUTYINFO d ON h.DUTY_CODE = d.DUTY_CODE
        WHERE h.EMP_CODE = #{empCode}
    """)
    Employee findEmployeeByEmpCode(String empCode);

    // 필터링된 근태 마스터 조회
    @Select("""
        <script>
        SELECT * FROM HRTSHIFTMASTER 
        WHERE USE_YN = 'Y' 
        AND SHIFT_NAME IN 
        <foreach collection="shiftNames" item="name" open="(" close=")" separator=",">
            #{name}
        </foreach>
        ORDER BY SHIFT_CODE
        </script>
    """)
    List<ShiftMaster> findShiftMastersByNames(@Param("shiftNames") List<String> shiftNames);

    // 유효한 TIME_ITEM_CODE 조회
    @Select("SELECT TOP 1 TIME_ITEM_CODE FROM HRTTIMEITEM ORDER BY TIME_ITEM_CODE")
    String getValidTimeItemCode();

    // 🔧 수정: 일반근태 신청 조회 - 신청근무별 완전 분리 처리
    @Select("""
        <script>
        SELECT TOP 1 APPLY_GENERAL_NO, STATUS, REASON, APPLY_TYPE, START_TIME, END_TIME
        FROM HRTATTAPLGENERAL 
        WHERE EMP_CODE = #{empCode} AND TARGET_DATE = #{workDate}
        AND STATUS != '삭제'
        <if test="applyTypeCategory != null and applyTypeCategory != ''">
            <if test="applyTypeCategory == '연장근로'">
                AND APPLY_TYPE IN ('연장', '조출연장')
            </if>
            <if test="applyTypeCategory == '휴일근로'">
                AND APPLY_TYPE = '휴일근무'
            </if>
            <if test="applyTypeCategory == '조퇴외출반차'">
                AND APPLY_TYPE IN ('조퇴', '외근', '외출', '전반차', '후반차')
            </if>
        </if>
        ORDER BY 
            CASE STATUS
                WHEN '승인완료' THEN 1
                WHEN '상신' THEN 2
                WHEN '저장' THEN 3
                ELSE 4
            END,
            APPLY_DATE DESC
        </script>
    """)
    AttendanceApplyGeneral findGeneralApplyByEmpAndDateWithCategory(@Param("empCode") String empCode,
                                                                    @Param("workDate") String workDate,
                                                                    @Param("applyTypeCategory") String applyTypeCategory);

    // 기존 일반근태 신청 조회 (전체)
    @Select("""
        SELECT TOP 1 APPLY_GENERAL_NO, STATUS, REASON, APPLY_TYPE, START_TIME, END_TIME
        FROM HRTATTAPLGENERAL 
        WHERE EMP_CODE = #{empCode} AND TARGET_DATE = #{workDate}
        AND STATUS != '삭제'
        ORDER BY 
            CASE STATUS
                WHEN '승인완료' THEN 1
                WHEN '상신' THEN 2
                WHEN '저장' THEN 3
                ELSE 4
            END,
            APPLY_DATE DESC
    """)
    AttendanceApplyGeneral findGeneralApplyByEmpAndDate(@Param("empCode") String empCode, @Param("workDate") String workDate);

    // 요구사항: 신청근무별 개별 조회 - 신청근무별 분리 관리용
    @Select("""
        SELECT TOP 1 APPLY_GENERAL_NO, STATUS, REASON, APPLY_TYPE, START_TIME, END_TIME
        FROM HRTATTAPLGENERAL 
        WHERE EMP_CODE = #{empCode} AND TARGET_DATE = #{workDate}
        AND APPLY_TYPE = #{applyType}
        AND STATUS != '삭제'
        ORDER BY 
            CASE STATUS
                WHEN '승인완료' THEN 1
                WHEN '상신' THEN 2
                WHEN '저장' THEN 3
                ELSE 4
            END,
            APPLY_DATE DESC
    """)
    AttendanceApplyGeneral findGeneralApplyByEmpAndDateAndType(@Param("empCode") String empCode,
                                                               @Param("workDate") String workDate,
                                                               @Param("applyType") String applyType);

    // 기존 기타근태 신청 조회 - BETWEEN 사용으로 변경
    @Select("""
        SELECT TOP 1 APPLY_ETC_NO, STATUS, REASON, SHIFT_CODE, TARGET_START_DATE, TARGET_END_DATE
        FROM HRTATTAPLETC 
        WHERE EMP_CODE = #{empCode} 
        AND #{workDate} BETWEEN TARGET_START_DATE AND TARGET_END_DATE
        AND STATUS != '삭제'
        ORDER BY 
            CASE STATUS
                WHEN '승인완료' THEN 1
                WHEN '상신' THEN 2
                WHEN '저장' THEN 3
                ELSE 4
            END,
            APPLY_DATE DESC
    """)
    AttendanceApplyEtc findEtcApplyByEmpAndDate(@Param("empCode") String empCode, @Param("workDate") String workDate);

    // 저장된 일반근태 신청 조회
    @Select("""
        SELECT * FROM HRTATTAPLGENERAL 
        WHERE APPLY_GENERAL_NO = #{applyGeneralNo}
    """)
    AttendanceApplyGeneral findGeneralApplyByNo(String applyGeneralNo);

    // 저장된 기타근태 신청 조회
    @Select("""
        SELECT * FROM HRTATTAPLETC 
        WHERE APPLY_ETC_NO = #{applyEtcNo}
    """)
    AttendanceApplyEtc findEtcApplyByNo(String applyEtcNo);

    // 해당일 연차/휴가 신청 확인 (기타근태 - 연장근로 검증용) - BETWEEN 사용으로 변경
    @Select("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
        FROM HRTATTAPLETC etc
        INNER JOIN HRTSHIFTMASTER sm ON etc.SHIFT_CODE = sm.SHIFT_CODE
        WHERE etc.EMP_CODE = #{empCode} 
        AND #{workDate} BETWEEN etc.TARGET_START_DATE AND etc.TARGET_END_DATE
        AND etc.STATUS IN ('승인완료', '상신')
        AND sm.SHIFT_NAME IN ('연차', '휴가')
    """)
    boolean hasAnnualOrVacationApply(@Param("empCode") String empCode, @Param("workDate") String workDate);

    // 해당일 반차/조퇴 신청 확인 (일반근태 - 일반 연장근로 검증용)
    @Select("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
        FROM HRTATTAPLGENERAL 
        WHERE EMP_CODE = #{empCode} 
        AND TARGET_DATE = #{workDate}
        AND STATUS IN ('승인완료', '상신')
        AND APPLY_TYPE IN ('조퇴', '외근', '외출', '전반차', '후반차')
    """)
    boolean hasHalfDayOrEarlyLeaveApply(@Param("empCode") String empCode, @Param("workDate") String workDate);

    // 시간 겹침 확인 (조퇴/외출/반차 중복 검증용) - 함수 사용으로 변경
    @Select("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
        FROM HRTATTAPLGENERAL 
        WHERE EMP_CODE = #{empCode} 
        AND TARGET_DATE = #{workDate}
        AND STATUS IN ('승인완료', '상신')
        AND APPLY_TYPE IN ('조퇴', '외근', '외출', '전반차', '후반차')
        AND (
            (START_TIME <= #{startTime} AND END_TIME > #{startTime}) OR
            (START_TIME < #{endTime} AND END_TIME >= #{endTime}) OR
            (START_TIME >= #{startTime} AND END_TIME <= #{endTime})
        )
    """)
    boolean hasTimeOverlap(@Param("empCode") String empCode,
                           @Param("workDate") String workDate,
                           @Param("startTime") String startTime,
                           @Param("endTime") String endTime);

    // 요구사항: 조출연장 시간 제한 검증 - 07:30 이전인지 확인
    @Select("""
        SELECT CASE WHEN #{startTime} < 730 THEN 1 ELSE 0 END
    """)
    boolean isValidEarlyOvertimeTime(@Param("startTime") int startTime);

    // 부서별 사원 조회 - 부서장용
    @Select("""
        <script>
        SELECT h.*, p.POSITION_NAME, d.DUTY_NAME, dept.DEPT_NAME,
               wc.SHIFT_CODE as workPlan,
               sm.SHIFT_NAME as workPlanName,
               ISNULL(p.POSITION_CODE, '999') as gradeOrder
        FROM HRIMASTER h
        LEFT JOIN HRTGRADEINFO p ON h.POSITION_CODE = p.POSITION_CODE
        LEFT JOIN HRTDUTYINFO d ON h.DUTY_CODE = d.DUTY_CODE
        LEFT JOIN ORGDEPTMASTER dept ON h.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR wc ON h.EMP_CODE = wc.EMP_CODE 
               AND wc.YYYYMMDD = #{workDate}
        LEFT JOIN HRTSHIFTMASTER sm ON wc.SHIFT_CODE = sm.SHIFT_CODE
        WHERE h.DEPT_CODE = #{deptCode}
        AND h.EMP_STATE = 'WORK'
        <if test="workPlan != null and workPlan != ''">
            AND sm.SHIFT_NAME = #{workPlan}
        </if>
        ORDER BY 
            CASE h.POSITION_CODE
                WHEN '110' THEN 1   -- 사장
                WHEN '120' THEN 2   -- 부사장
                WHEN '130' THEN 3   -- 전무
                WHEN '140' THEN 4   -- 전무보
                WHEN '150' THEN 5   -- 상무
                WHEN '160' THEN 6   -- 상무보
                WHEN '170' THEN 7   -- 이사
                WHEN '210' THEN 8   -- 부장
                WHEN '220' THEN 9   -- 차장
                WHEN '230' THEN 10  -- 과장
                WHEN '240' THEN 11  -- 대리
                WHEN '250' THEN 12  -- 사원
                ELSE 999
            END ASC,
            h.EMP_CODE ASC
        </script>
    """)
    List<Employee> findEmployeesByDeptWithSort(@Param("deptCode") String deptCode,
                                               @Param("workDate") String workDate,
                                               @Param("workPlan") String workPlan,
                                               @Param("sortBy") String sortBy);

    // 현재 사원 정보 조회 - 일반 사원용
    @Select("""
        SELECT h.*, p.POSITION_NAME, d.DUTY_NAME, dept.DEPT_NAME,
               wc.SHIFT_CODE as workPlan,
               sm.SHIFT_NAME as workPlanName,
               ISNULL(p.POSITION_CODE, '999') as gradeOrder
        FROM HRIMASTER h
        LEFT JOIN HRTGRADEINFO p ON h.POSITION_CODE = p.POSITION_CODE
        LEFT JOIN HRTDUTYINFO d ON h.DUTY_CODE = d.DUTY_CODE
        LEFT JOIN ORGDEPTMASTER dept ON h.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR wc ON h.EMP_CODE = wc.EMP_CODE 
               AND wc.YYYYMMDD = #{workDate}
        LEFT JOIN HRTSHIFTMASTER sm ON wc.SHIFT_CODE = sm.SHIFT_CODE
        WHERE h.EMP_CODE = #{empCode}
        ORDER BY 
            CASE h.POSITION_CODE
                WHEN '110' THEN 1   -- 사장
                WHEN '120' THEN 2   -- 부사장
                WHEN '130' THEN 3   -- 전무
                WHEN '140' THEN 4   -- 전무보
                WHEN '150' THEN 5   -- 상무
                WHEN '160' THEN 6   -- 상무보
                WHEN '170' THEN 7   -- 이사
                WHEN '210' THEN 8   -- 부장
                WHEN '220' THEN 9   -- 차장
                WHEN '230' THEN 10  -- 과장
                WHEN '240' THEN 11  -- 대리
                WHEN '250' THEN 12  -- 사원
                ELSE 999
            END ASC,
            h.EMP_CODE ASC
    """)
    List<Employee> findCurrentEmployeeWithCalendar(@Param("empCode") String empCode,
                                                   @Param("workDate") String workDate);

    // 일반근태 중복 신청 확인
    @Select("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
        FROM HRTATTAPLGENERAL
        WHERE EMP_CODE = #{empCode}
        AND TARGET_DATE = #{targetDate}
        AND APPLY_TYPE = #{applyType}
        AND STATUS != '삭제'
    """)
    boolean checkDuplicateGeneralApply(@Param("empCode") String empCode,
                                       @Param("targetDate") String targetDate,
                                       @Param("applyType") String applyType);

    // 기타근태 중복 신청 확인
    @Select("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
        FROM HRTATTAPLETC
        WHERE EMP_CODE = #{empCode}
        AND (
            (#{startDate} BETWEEN TARGET_START_DATE AND TARGET_END_DATE) OR
            (#{endDate} BETWEEN TARGET_START_DATE AND TARGET_END_DATE) OR
            (TARGET_START_DATE BETWEEN #{startDate} AND #{endDate}) OR
            (TARGET_END_DATE BETWEEN #{startDate} AND #{endDate})
        )
        AND STATUS != '삭제'
    """)
    boolean checkDuplicateEtcApply(@Param("empCode") String empCode,
                                   @Param("startDate") String startDate,
                                   @Param("endDate") String endDate);

    // 일반근태 신청 저장
    @Insert("""
        INSERT INTO HRTATTAPLGENERAL (
            APPLY_GENERAL_NO, EMP_CODE, TIME_ITEM_CODE, APPLY_DATE, 
            TARGET_DATE, START_TIME, END_TIME, APPLY_TYPE, REASON, STATUS, 
            DEPT_CODE, APPLICANT_CODE
        ) VALUES (
            #{applyGeneralNo}, #{empCode}, #{timeItemCode}, #{applyDate},
            #{targetDate}, #{startTime}, #{endTime}, #{applyType}, #{reason}, #{status},
            #{deptCode}, #{applicantCode}
        )
    """)
    void insertGeneralApply(AttendanceApplyGeneral apply);

    // 기타근태 신청 저장
    @Insert("""
        INSERT INTO HRTATTAPLETC (
            APPLY_ETC_NO, EMP_CODE, SHIFT_CODE, APPLY_DATE,
            TARGET_START_DATE, TARGET_END_DATE, APPLY_DATE_TIME, REASON,
            STATUS, DEPT_CODE, APPLICANT_CODE
        ) VALUES (
            #{applyEtcNo}, #{empCode}, #{shiftCode}, #{applyDate},
            #{targetStartDate}, #{targetEndDate}, #{applyDateTime}, #{reason},
            #{status}, #{deptCode}, #{applicantCode}
        )
    """)
    void insertEtcApply(AttendanceApplyEtc apply);

    // 일반근태 신청 상태 변경
    @Update("UPDATE HRTATTAPLGENERAL SET STATUS = #{status} WHERE APPLY_GENERAL_NO = #{applyGeneralNo}")
    void updateGeneralApplyStatus(@Param("applyGeneralNo") String applyNo, @Param("status") String status);

    // 기타근태 신청 상태 변경
    @Update("UPDATE HRTATTAPLETC SET STATUS = #{status} WHERE APPLY_ETC_NO = #{applyEtcNo}")
    void updateEtcApplyStatus(@Param("applyEtcNo") String applyNo, @Param("status") String status);

    // 신청번호로 부서코드 조회 - 일반근태
    @Select("SELECT DEPT_CODE FROM HRTATTAPLGENERAL WHERE APPLY_GENERAL_NO = #{applyGeneralNo}")
    String getDeptCodeByGeneralApplyNo(String applyGeneralNo);

    // 신청번호로 부서코드 조회 - 기타근태
    @Select("SELECT DEPT_CODE FROM HRTATTAPLETC WHERE APPLY_ETC_NO = #{applyEtcNo}")
    String getDeptCodeByEtcApplyNo(String applyEtcNo);

    // 부서코드로 부서장 조회
    @Select("SELECT DEPT_LEADER FROM ORGDEPTMASTER WHERE DEPT_CODE = #{deptCode}")
    String getDeptLeaderByDeptCode(String deptCode);

    // 일반근태 결재 이력 생성
    @Insert("""
        INSERT INTO HRTAPRHIST (
            APPROVAL_NO, APPLY_GENERAL_NO, APPROVER_CODE, APPROVAL_STATUS
        ) VALUES (
            #{approvalNo}, #{applyGeneralNo}, #{approverCode}, '대기'
        )
    """)
    void insertGeneralApprovalHistory(@Param("approvalNo") String approvalNo,
                                      @Param("applyGeneralNo") String applyGeneralNo,
                                      @Param("approverCode") String approverCode);

    // 기타근태 결재 이력 생성
    @Insert("""
        INSERT INTO HRTAPRHIST (
            APPROVAL_NO, APPLY_ETC_NO, APPROVER_CODE, APPROVAL_STATUS
        ) VALUES (
            #{approvalNo}, #{applyEtcNo}, #{approverCode}, '대기'
        )
    """)
    void insertEtcApprovalHistory(@Param("approvalNo") String approvalNo,
                                  @Param("applyEtcNo") String applyEtcNo,
                                  @Param("approverCode") String approverCode);

    // 일반근태 결재 이력 삭제
    @Delete("DELETE FROM HRTAPRHIST WHERE APPLY_GENERAL_NO = #{applyGeneralNo}")
    void deleteGeneralApprovalHistory(String applyGeneralNo);

    // 기타근태 결재 이력 삭제
    @Delete("DELETE FROM HRTAPRHIST WHERE APPLY_ETC_NO = #{applyEtcNo}")
    void deleteEtcApprovalHistory(String applyEtcNo);

    // 신청 소유권 확인 - 일반근태
    @Select("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END 
        FROM HRTATTAPLGENERAL 
        WHERE APPLY_GENERAL_NO = #{applyGeneralNo} 
        AND APPLICANT_CODE = #{applicantCode}
    """)
    boolean checkGeneralApplyOwnership(@Param("applyGeneralNo") String applyGeneralNo,
                                       @Param("applicantCode") String applicantCode);

    // 신청 소유권 확인 - 기타근태
    @Select("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END 
        FROM HRTATTAPLETC 
        WHERE APPLY_ETC_NO = #{applyEtcNo} 
        AND APPLICANT_CODE = #{applicantCode}
    """)
    boolean checkEtcApplyOwnership(@Param("applyEtcNo") String applyEtcNo,
                                   @Param("applicantCode") String applicantCode);

    // 일반근태 신청 상태 조회
    @Select("SELECT STATUS FROM HRTATTAPLGENERAL WHERE APPLY_GENERAL_NO = #{applyGeneralNo}")
    String getGeneralApplyStatus(String applyGeneralNo);

    // 기타근태 신청 상태 조회
    @Select("SELECT STATUS FROM HRTATTAPLETC WHERE APPLY_ETC_NO = #{applyEtcNo}")
    String getEtcApplyStatus(String applyEtcNo);

    // 일반근태 신청 삭제
    @Delete("DELETE FROM HRTATTAPLGENERAL WHERE APPLY_GENERAL_NO = #{applyGeneralNo}")
    void deleteGeneralApply(String applyGeneralNo);

    // 기타근태 신청 삭제
    @Delete("DELETE FROM HRTATTAPLETC WHERE APPLY_ETC_NO = #{applyEtcNo}")
    void deleteEtcApply(String applyEtcNo);

    @Select("""
        SELECT APPLY_TYPE
        FROM HRTATTAPLGENERAL
        WHERE EMP_CODE = #{empCode}
        AND TARGET_DATE = #{workYmd}
        AND STATUS = #{status}
        AND APPLY_TYPE IN (N'외출', N'조퇴', N'전반차', N'후반차')
    """)
        // 승인된 가근태 찾기
    List<String> findApprovedTimeItemCode(@Param("empCode") String empCode,
                                          @Param("workYmd") String workYmd,
                                          @Param("status") String status);

    @Select("""
        SELECT APPLY_TYPE
        FROM HRTATTAPLGENERAL
        WHERE EMP_CODE = #{empCode}
        AND TARGET_DATE = #{workYmd}
        AND STATUS = #{status}
        AND APPLY_TYPE NOT IN (N'연장', N'조출연장')
    """)
        // 승인된 가근태 찾기
    List<String> findApprovedNotOvertime(@Param("empCode") String empCode,
                                         @Param("workYmd") String workYmd,
                                         @Param("status") String status);

    @Select("""
        SELECT START_TIME, END_TIME
        FROM HRTATTAPLGENERAL
        WHERE EMP_CODE = #{empCode}
        AND TARGET_DATE = #{targetDate}
        AND STATUS = #{status}
        AND APPLY_TYPE = #{applyType}
    """)
        // 승인된 가근태 시작시간, 종료시간 찾기
    AttendanceApplyGeneral findStartTimeAndEndTime(@Param("empCode") String empCode,
                                                   @Param("targetDate") String targetDate,
                                                   @Param("status") String status,
                                                   @Param("applyType") String applyType);

    @Select("""
        SELECT *
        FROM HRTATTAPLGENERAL
        WHERE EMP_CODE = #{empCode}
        AND STATUS = N'승인완료'
        AND APPLY_TYPE IN (N'연장', N'조출연장')
        AND TARGET_DATE = #{workYmd}
    """)
        // 승인된 연장, 조출연장
    List<AttendanceApplyGeneral> findApprovedOverTimes(@Param("empCode") String empCode,
                                                       @Param("workYmd") String workYmd);

    @Select("""
        SELECT *
        FROM HRTATTAPLGENERAL
        WHERE EMP_CODE = #{empCode}
        AND STATUS = N'승인완료'
        AND APPLY_TYPE = N'휴일근무'
        AND TARGET_DATE = #{workYmd}
    """)
        // 승인된 휴일근무
    AttendanceApplyGeneral findApprovedOverTime2(@Param("empCode") String empCode,
                                                 @Param("workYmd") String workYmd);

    // 계획 (SHIFT_CODE_ORIG) 조회
    @Select("""
        SELECT SHIFT_CODE_ORIG 
        FROM HRTWORKEMPCALENDAR 
        WHERE EMP_CODE = #{empCode} 
        AND YYYYMMDD = #{workDate}
    """)
    String getOriginalShiftCode(@Param("empCode") String empCode, @Param("workDate") String workDate);

    // 일반근태 승인 완료 시 SHIFT_CODE 업데이트
    @Update("""
        UPDATE HRTWORKEMPCALENDAR 
        SET SHIFT_CODE = CASE 
            WHEN #{applyType} = '휴일근무' THEN '14-1'
            WHEN #{applyType} = '전반차' THEN (
                SELECT TOP 1 sm.SHIFT_CODE 
                FROM HRTSHIFTMASTER sm 
                WHERE sm.SHIFT_NAME = '전반차' AND sm.USE_YN = 'Y'
            )
            WHEN #{applyType} = '후반차' THEN (
                SELECT TOP 1 sm.SHIFT_CODE 
                FROM HRTSHIFTMASTER sm 
                WHERE sm.SHIFT_NAME = '후반차' AND sm.USE_YN = 'Y'
            )
            ELSE SHIFT_CODE
        END
        WHERE EMP_CODE = #{empCode} 
        AND YYYYMMDD = #{workDate}
    """)
    void updateShiftCodeAfterGeneralApproval(@Param("empCode") String empCode,
                                             @Param("workDate") String workDate,
                                             @Param("applyType") String applyType);

    // 기타근태 승인 완료 시 SHIFT_CODE 업데이트
    @Update("""
        UPDATE HRTWORKEMPCALENDAR 
        SET SHIFT_CODE = #{shiftCode}
        WHERE EMP_CODE = #{empCode} 
        AND YYYYMMDD >= #{startDate} 
        AND YYYYMMDD <= #{endDate}
    """)
    void updateShiftCodeAfterEtcApproval(@Param("empCode") String empCode,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate,
                                         @Param("shiftCode") String shiftCode);
}
