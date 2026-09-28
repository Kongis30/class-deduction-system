package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.enrollment.service.ClassBillingService;
import com.classdeduction.system.modules.schedule.service.ClassHolidayService;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 班课计费周期计算服务实现。
 *
 * <p>负责把课包（计费单位 × 数量）映射到 class_enrollment 的
 * enrollment_date / expiry_date，报名与收据更正链路共用。</p>
 */
@Service
@RequiredArgsConstructor
public class ClassBillingServiceImpl implements ClassBillingService {

    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final ClassHolidayService classHolidayService;

    @Override
    public ClassBilling compute(ClassGroupEntity classGroup,
                                LocalDate startDate,
                                LocalDate baselineDate,
                                String packageUnit,
                                BigDecimal packageQuantity) {
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        List<ClassGroupScheduleSlotEntity> slots = loadClassSlots(classGroup);
        String unit = StringUtils.hasText(packageUnit) ? packageUnit : "MONTH";
        int quantity = packageQuantity == null
                ? 1
                : packageQuantity.setScale(0, RoundingMode.CEILING).intValue();
        if (quantity < 1) {
            throw new BusinessException("REGISTRATION_DURATION_INVALID",
                    "Selected package quantity must be greater than zero.");
        }
        if (quantity > 36) {
            throw new BusinessException("REGISTRATION_DURATION_INVALID",
                    "Purchased quantity must be between 1 and 36.");
        }
        LocalDate enrollmentDate = startDate == null
                ? (baselineDate == null ? today : baselineDate)
                : startDate;
        LocalDate expiryDate;
        LocalDate configuredStart = classGroup.getStartDate();
        LocalDate configuredEnd = classGroup.getEndDate();
        if (configuredStart != null && configuredEnd != null && configuredEnd.isBefore(configuredStart)) {
            throw new BusinessException("INVALID_GROUP_DATE_RANGE",
                    "Class end date cannot be earlier than the start date.");
        }
        if (configuredStart != null) {
            if (startDate != null && startDate.isBefore(configuredStart)) {
                throw new BusinessException("ENROLLMENT_START_OUT_OF_RANGE",
                        "Enrollment start date must be within the class date range.");
            }
            if (startDate == null && enrollmentDate.isBefore(configuredStart)) {
                enrollmentDate = configuredStart;
            }
        }
        if (configuredEnd != null && enrollmentDate.isAfter(configuredEnd)) {
            throw new BusinessException("ENROLLMENT_START_OUT_OF_RANGE",
                    "Enrollment start date must be within the class date range.");
        }
        if ("MONTH".equals(unit)) {
            YearMonth selectedEnd = YearMonth.from(enrollmentDate).plusMonths(quantity - 1L);
            expiryDate = selectedEnd.atEndOfMonth();
        } else if ("WEEK".equals(unit)) {
            expiryDate = enrollmentDate.plusWeeks(quantity).minusDays(1);
        } else if ("SESSION".equals(unit)) {
            expiryDate = computeSessionExpiryDate(classGroup, enrollmentDate, slots, quantity, configuredEnd);
        } else {
            throw new BusinessException("INVALID_PACKAGE_UNIT",
                    "Group class package unit must be MONTH, WEEK, or SESSION.");
        }
        if (configuredEnd != null && expiryDate.isAfter(configuredEnd)) {
            // 班课结束日是实际有效期边界；购买数量仍保留，用于课时/次数统计。
            expiryDate = configuredEnd;
        }
        return new ClassBilling(enrollmentDate, expiryDate, quantity);
    }

    private List<ClassGroupScheduleSlotEntity> loadClassSlots(ClassGroupEntity classGroup) {
        return classGroupScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                        .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classGroup.getId())
                        .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
    }

    private LocalDate computeSessionExpiryDate(ClassGroupEntity classGroup, LocalDate startDate,
                                               List<ClassGroupScheduleSlotEntity> slots,
                                               int quantity,
                                               LocalDate configuredEnd) {
        if (!slots.isEmpty()) {
            LocalDate expiryDate = computeNthSessionDate(classGroup, startDate, slots, quantity);
            if (configuredEnd != null && expiryDate.isAfter(configuredEnd)
                    && holidayCausedSessionOverflow(classGroup, startDate, configuredEnd, slots, quantity)) {
                throw new BusinessException("CLASS_HOLIDAY_EXCEEDS_CLASS_END",
                        "The selected SESSION package cannot fit before the class end date because of class holidays.");
            }
            return expiryDate;
        }
        if (configuredEnd != null && !configuredEnd.isBefore(startDate)) {
            return configuredEnd;
        }
        return startDate.plusDays(quantity - 1L);
    }

    private LocalDate computeNthSessionDate(ClassGroupEntity classGroup, LocalDate startDate,
                                            List<ClassGroupScheduleSlotEntity> slots,
                                            int n) {
        Set<Integer> weekdays = slots.stream()
                .map(ClassGroupScheduleSlotEntity::getWeekday)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        LocalDate limit = startDate.plusYears(10);
        List<ClassHolidayVO> holidays = classHolidayService.listHolidays(
                classGroup.getProductId(), classGroup.getGradeBand(), startDate, limit);
        int count = 0;
        for (LocalDate date = startDate; !date.isAfter(limit); date = date.plusDays(1)) {
            if (weekdays.contains(date.getDayOfWeek().getValue()) && !classHolidayService.isHoliday(date, holidays)) {
                count++;
                if (count >= n) {
                    return date;
                }
            }
        }
        throw new BusinessException("SESSION_DATE_UNRESOLVED", "Cannot resolve the date for the selected sessions.");
    }

    /** 假期导致可上课日不足时拒绝新报名，避免收款后无法提供购买的节数。 */
    private boolean holidayCausedSessionOverflow(ClassGroupEntity classGroup, LocalDate startDate,
                                                 LocalDate configuredEnd,
                                                 List<ClassGroupScheduleSlotEntity> slots,
                                                 int quantity) {
        Set<Integer> weekdays = slots.stream().map(ClassGroupScheduleSlotEntity::getWeekday)
                .collect(Collectors.toSet());
        List<ClassHolidayVO> holidays = classHolidayService.listHolidays(
                classGroup.getProductId(), classGroup.getGradeBand(), startDate, configuredEnd);
        int totalSessionDays = 0;
        int availableSessionDays = 0;
        for (LocalDate date = startDate; !date.isAfter(configuredEnd); date = date.plusDays(1)) {
            if (!weekdays.contains(date.getDayOfWeek().getValue())) continue;
            totalSessionDays++;
            if (!classHolidayService.isHoliday(date, holidays)) availableSessionDays++;
        }
        return totalSessionDays >= quantity && availableSessionDays < quantity;
    }
}
