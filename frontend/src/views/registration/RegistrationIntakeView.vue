<template>
  <div class="registration-page">
    <el-alert
      v-if="!isReadOnly && validationErrors.length"
      type="error"
      :closable="false"
      show-icon
      class="validation-alert no-print"
    >
      <template #title>Please fix these items before submitting</template>
      <ul class="validation-list">
        <li v-for="item in validationErrors" :key="item">{{ item }}</li>
      </ul>
    </el-alert>

    <section class="registration-desk no-print">
      <div>
        <strong>{{ isReadOnly ? 'Saved receipt' : 'Registration desk' }}</strong>
        <p>{{ isReadOnly
          ? 'This is the saved receipt. It is read-only and prints with the same layout.'
          : 'Fill the familiar paper form, save the record, then print the receipt when ready.' }}</p>
      </div>
      <div class="registration-desk__actions">
        <el-button class="action-secondary" @click="openCurrentStudentHistory">Find student &amp; history</el-button>
      </div>
    </section>

    <section v-if="!isReadOnly" class="registration-flow no-print" aria-label="Registration workflow">
      <article>
        <span>1</span>
        <strong>Student</strong>
        <small>Name, grade, guardian, campus</small>
      </article>
      <article>
        <span>2</span>
        <strong>Course</strong>
        <small>Course, grade, duration, teacher/class</small>
      </article>
      <article>
        <span>3</span>
        <strong>Fees</strong>
        <small>Pickup, drop-off, discounts</small>
      </article>
      <article>
        <span>4</span>
        <strong>Payment</strong>
        <small>Cash, Zelle, transfer, balance</small>
      </article>
      <article>
        <span>5</span>
        <strong>Receipt</strong>
        <small>Save, preview, print</small>
      </article>
    </section>

    <section class="paper-form">
      <header class="form-header">
        <div class="form-title-block">
          <h1>Registration &amp; Receipt</h1>
          <h2>JIN BEST TUTORING · 专业、负责、懂考试</h2>
        </div>
        <button type="button" class="ein-toggle no-print" @click="showTaxIdOnForm = !showTaxIdOnForm">
          {{ showTaxIdOnForm ? 'Hide EIN' : 'Show EIN' }}
        </button>
        <div v-if="showTaxIdOnForm" class="form-tax-id">EIN: {{ currentCampusTaxId || 'Not configured' }}</div>
        <img class="form-logo" src="/logo.jpg" alt="JIN BEST TUTORING" />
      </header>

      <section class="contact-block">
        <strong>不止提分，更帮助孩子建立自信，掌握方法</strong>
        <div class="phone-line">☎ Ms. Jin （929）528–1060 / 办公室 （929）701–9872</div>

        <label class="campus-line">
          <input type="checkbox" :checked="isCampusProfileSelected('SOUTH')" :disabled="isReadOnly" @change="selectCampusProfile('SOUTH')" />
          <span>南校：144–56 Roosevelt Ave, Flushing, NY 11354</span>
        </label>
        <label class="campus-line">
          <input type="checkbox" :checked="isCampusProfileSelected('NORTH')" :disabled="isReadOnly" @change="selectCampusProfile('NORTH')" />
          <span>北校：142–18 32nd Ave, Flushing, NY 11354</span>
        </label>
        <label class="campus-line">
          <input type="checkbox" :checked="isCampusProfileSelected('ONLINE')" :disabled="isReadOnly" @change="selectCampusProfile('ONLINE')" />
          <span>线上课 online</span>
        </label>
      </section>

      <section class="student-lines">
        <div class="full-line">
          <span class="field-label-with-help">
            Student name:
            <el-tooltip placement="top" effect="light">
              <template #content>
                The first 1-on-1 registration creates a student account only when one does not already exist.
                The username is generated from the student's name and ends with @jin. Initial passwords prefer phone digits only,
                with spaces, dashes, and brackets removed; when no phone is available, the system uses 9297019872.
                Existing account credentials never change here.
              </template>
              <el-icon class="registration-help-icon" aria-label="Account creation rules"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
          <div class="student-name-fields">
            <el-input v-model="form.student.firstName" :disabled="isReadOnly" placeholder="First name" />
            <el-input v-model="form.student.lastName" :disabled="isReadOnly" placeholder="Last name" />
          </div>
          <el-tag v-if="!isReadOnly && studentReuseStatus === 'existing'" size="small" type="success" class="reuse-tag">Reuse</el-tag>
          <el-tag v-else-if="!isReadOnly && studentReuseStatus === 'new'" size="small" type="info" class="reuse-tag">New</el-tag>
        </div>
        <div v-if="!isReadOnly && reusableStudent" class="student-context-line no-print">
          <span>Existing profile found. Open history before saving when this is a repeat registration.</span>
          <button type="button" @click="openCurrentStudentHistory">View history</button>
        </div>

        <div class="two-col-line">
          <label>
            <span>Grade:</span>
            <el-input v-model="form.student.grade" :disabled="isReadOnly" class="line-input" />
          </label>
          <label>
            <span>School:</span>
            <el-input v-model="form.student.school" :disabled="isReadOnly" class="line-input" />
          </label>
        </div>

        <div class="two-col-line">
          <label>
            <span>Guardian phone:</span>
            <el-input v-model="form.student.guardianPhone" :disabled="isReadOnly" class="line-input" />
          </label>
          <label>
            <span>Guardian phone 2:</span>
            <el-input v-model="form.student.guardianPhone2" :disabled="isReadOnly" class="line-input" />
          </label>
        </div>

        <div class="two-col-line signup-period-line">
          <label>
            <span>Sign Up Date:</span>
            <el-date-picker v-model="form.signupDate" type="date" value-format="YYYY-MM-DD" :disabled="isReadOnly" class="line-date" />
          </label>
          <label>
            <span>Sign Up Period:</span>
            <div class="period-line">
              <el-date-picker v-model="form.periodStartDate" type="date" value-format="YYYY-MM-DD" placeholder="Start" :disabled="isReadOnly" class="line-date half" />
              <span class="period-separator">–</span>
              <el-date-picker v-model="form.periodEndDate" type="date" value-format="YYYY-MM-DD" placeholder="End" :disabled="isReadOnly" class="line-date half" />
            </div>
          </label>
        </div>

        <label class="address-line">
          <input type="checkbox" v-model="form.student.pickupRequired" :disabled="isReadOnly" />
          <span>Pickup needed</span>
          <el-input v-model="form.student.pickupAddress" :disabled="isReadOnly" class="line-input" />
        </label>
        <label class="address-line">
          <input type="checkbox" v-model="form.student.dropoffRequired" :disabled="isReadOnly" />
          <span>Drop-off needed</span>
          <el-input v-model="form.student.dropoffAddress" :disabled="isReadOnly" class="line-input" />
        </label>
      </section>

      <section class="payment-line">
        <span>Pay method:</span>
        <strong class="paper-payment-summary">{{ paymentMethodSummary }}</strong>
        <span class="amount-label">Amount:</span>
        <strong class="paper-payment-summary paper-payment-summary--amount">{{ formatMoney(paidAmount) }}</strong>
      </section>

      <section v-if="isReadOnly && payments.length > 0" class="payment-history">
        <div v-for="payment in payments" :key="payment.rowKey">
          <span>{{ formatDateValue(payment.paidAt) }}</span>
          <strong>{{ payment.paymentMethod || 'Payment' }}</strong>
          <span>{{ formatMoney(payment.amount) }}</span>
          <small>{{ [payment.handledByName, payment.remark].filter(Boolean).join(' · ') }}</small>
        </div>
      </section>

      <el-alert
        v-if="!isReadOnly && priceOptionsWithProduct.length === 0"
        type="warning"
        :closable="false"
        show-icon
        class="course-pricing-alert no-print"
      >
        <template #title>Course pricing is missing</template>
        <div class="course-pricing-alert__body">
          <span>{{ courseSetupHint }}</span>
          <el-button
            v-if="isAdmin"
            size="small"
            type="warning"
            @click="openCourseSettings"
          >{{ courseSetupActionLabel }}</el-button>
        </div>
      </el-alert>

      <section class="course-table-wrap">
        <table class="course-table">
          <thead>
            <tr>
              <th>1. Course</th>
              <th>2. Grade, duration & schedule</th>
              <th>3. Price</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, index) in courseLines" :key="line.rowKey" class="course-entry-row">
              <td>
                <div v-if="isReadOnly" class="receipt-course-display">
                  <strong>{{ printCourseProductLabel(line) }}</strong>
                </div>
                <div v-else class="course-class-cell">
                  <el-select
                    v-model="line.courseProductId"
                    class="table-select table-select--primary"
                    :placeholder="productSelectPlaceholder(line)"
                    :disabled="isReadOnly || productOptionsForLine(line).length === 0"
                    no-data-text="No linked products"
                    no-match-text="No matching product"
                    filterable
                    @change="() => handleCourseProductChange(line)"
                  >
                    <el-option
                      v-for="product in productOptionsForLine(line)"
                      :key="product.id"
                      :label="productOptionLabel(product)"
                      :value="product.id"
                    />
                  </el-select>
                  <span class="print-only print-course-value">{{ printCourseProductLabel(line) }}</span>
                </div>
              </td>
              <td>
                <div v-if="isReadOnly" class="receipt-course-detail">
                  <div>
                    <span>Grade</span>
                    <strong>{{ line.gradeBand || '—' }}</strong>
                  </div>
                  <div>
                    <span>Duration & schedule</span>
                    <strong>{{ printCourseScheduleLabel(line) }}</strong>
                  </div>
                  <div v-if="showGroupDurationInput(line)">
                    <span>{{ groupDurationInputLabel(line) }}</span>
                    <strong>{{ printGroupDurationLabel(line) }}</strong>
                  </div>
                  <div>
                    <span>{{ isOneOnOneLine(line) ? 'Teacher' : 'Class' }}</span>
                    <strong>{{ printTeacherOrClassLabel(line) }}</strong>
                  </div>
                  <p v-if="isOneOnOneLine(line)">Arrange lesson times on the weekly calendar after this registration is saved.</p>
                </div>
                <div v-else class="course-detail-cell">
                  <div class="detail-row">
                    <label class="course-control">
                      <span>Grade</span>
                      <el-select
                        v-model="line.gradeBand"
                        class="table-select"
                        placeholder="Select grade"
                        :disabled="isReadOnly || !line.courseProductId"
                        no-data-text="No grade options"
                        clearable
                        @change="() => handleGradeChange(line)"
                      >
                        <el-option v-for="grade in gradeOptionsForLine(line)" :key="grade" :label="grade" :value="grade" />
                      </el-select>
                      <strong class="print-only print-course-value">{{ line.gradeBand || '—' }}</strong>
                    </label>
                    <label class="course-control">
                      <span>Duration & schedule</span>
                      <el-select
                        v-model="line.coursePriceOptionId"
                        class="table-select option"
                        :placeholder="detailSelectPlaceholder(line)"
                        :disabled="isReadOnly || !line.courseProductId || !line.gradeBand || optionChoicesForLine(line).length === 0"
                        no-data-text="No detail options"
                        no-match-text="No matching detail"
                        filterable
                        @change="() => handleOptionChange(line)"
                      >
                        <el-option
                          v-for="option in optionChoicesForLine(line)"
                          :key="option.id"
                          :label="option.detailText"
                          :value="option.id"
                        >
                          <div class="price-option-row">
                            <span>{{ durationOptionLabel(option) }}</span>
                            <small>{{ formatMoney(numeric(option.priceAmount)) }}</small>
                          </div>
                        </el-option>
                      </el-select>
                      <strong class="print-only print-course-value">{{ printCourseScheduleLabel(line) }}</strong>
                    </label>
                    <label v-if="showGroupDurationInput(line)" class="course-control">
                      <span>{{ groupDurationInputLabel(line) }}</span>
                      <el-input-number
                        v-if="line.groupBillingType === 'PER_SESSION'"
                        v-model="line.groupSessionCount"
                        class="table-price"
                        :min="1"
                        :max="line.groupTotalSessions"
                        :precision="0"
                        :controls="false"
                        :disabled="isReadOnly"
                        @change="() => handleGroupDurationChange(line)"
                      />
                      <div v-else class="group-month-range">
                        <el-date-picker
                          v-model="line.groupRegistrationStartMonth"
                          type="month"
                          value-format="YYYY-MM"
                          placeholder="Start"
                          :disabled="isReadOnly"
                          @change="() => handleGroupDurationChange(line)"
                        />
                        <span>to</span>
                        <el-date-picker
                          v-model="line.groupRegistrationEndMonth"
                          type="month"
                          value-format="YYYY-MM"
                          placeholder="End"
                          :disabled="isReadOnly"
                          @change="() => handleGroupDurationChange(line)"
                        />
                      </div>
                      <strong class="print-only print-course-value">{{ printGroupDurationLabel(line) }}</strong>
                    </label>
                  </div>
                  <div class="teacher-class-line">
                    <label class="course-control course-control--wide">
                      <span>{{ isOneOnOneLine(line) ? 'Teacher (optional)' : 'Class' }}</span>
                      <el-select
                        v-if="isOneOnOneLine(line)"
                        v-model="line.teacherId"
                        class="table-select"
                        placeholder="Assign later"
                        :disabled="isReadOnly || !line.coursePriceOptionId || teachers.length === 0"
                        no-data-text="No active teachers"
                        filterable
                        clearable
                      >
                        <el-option v-for="teacher in teachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
                      </el-select>
                      <div v-else class="class-schedule-summary">
                        {{ classScheduleLabel(line) }}
                      </div>
                      <strong class="print-only print-course-value">{{ printTeacherOrClassLabel(line) }}</strong>
                    </label>
                    <span v-if="!line.coursePriceOptionId" class="course-detail-hint">{{ courseLineHint(line) }}</span>
                  </div>
                  <p v-if="isOneOnOneLine(line)" class="one-on-one-scheduling-hint">
                    Arrange lesson times on the weekly calendar after this registration is saved.
                  </p>
                  <div v-if="!isReadOnly && line.manualPriceOverride" class="manual-price-line">
                    <el-tag size="small" type="warning">Manual price</el-tag>
                    <el-input
                      v-model="line.remark"
                      size="small"
                      placeholder="Reason, e.g. mid-term enrollment"
                    />
                  </div>
                </div>
              </td>
              <td class="price-cell">
                <strong v-if="isReadOnly" class="receipt-course-price">{{ formatMoney(line.priceAmount) }}</strong>
                <template v-else>
                  <el-input-number v-model="line.priceAmount" :min="0" :precision="2" :controls="false" class="table-price" @change="() => handlePriceChange(line)" />
                  <strong class="print-only print-course-price">{{ formatMoney(line.priceAmount) }}</strong>
                </template>
                <button v-if="!isReadOnly && courseLines.length > 1" type="button" class="remove-row no-print" @click="removeCourseLine(index)">×</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="!isReadOnly" class="paper-actions no-print">
        <el-button class="action-secondary" @click="addCourseLine">Add course row</el-button>
      </section>

      <section v-if="!isReadOnly" class="intake-adjustment-panel no-print">
        <div class="adjustment-column adjustment-column--fees">
          <div class="adjustment-heading">
            <strong>Service & other fees</strong>
            <span>Pickup/drop-off fees are per registration, not per student profile</span>
          </div>
          <div v-if="form.student.pickupRequired" class="adjustment-row adjustment-row--service">
            <label class="service-charge-toggle">
              <input type="checkbox" v-model="form.pickupFeeCharged" />
              <span>Charge pickup on this registration</span>
            </label>
            <el-input-number v-model="form.pickupFeeAmount" :min="0" :precision="2" :controls="false" :disabled="!form.pickupFeeCharged" />
          </div>
          <div v-if="form.student.dropoffRequired" class="adjustment-row adjustment-row--service">
            <label class="service-charge-toggle">
              <input type="checkbox" v-model="form.dropoffFeeCharged" />
              <span>Charge drop-off on this registration</span>
            </label>
            <el-input-number v-model="form.dropoffFeeAmount" :min="0" :precision="2" :controls="false" :disabled="!form.dropoffFeeCharged" />
          </div>
          <div v-if="!form.student.pickupRequired && !form.student.dropoffRequired" class="adjustment-empty">
            Select Pickup needed or Drop-off needed above, then explicitly choose whether to charge this registration.
          </div>
          <div v-for="(fee, index) in extraFees" :key="fee.rowKey" class="adjustment-row">
            <el-input v-model="fee.label" placeholder="Other fee label" />
            <el-input-number v-model="fee.amount" :min="0" :precision="2" :controls="false" />
            <el-button v-if="extraFees.length > 1" class="remove-adjustment-row" text type="danger" aria-label="Remove fee" @click="removeExtraFee(index)">×</el-button>
          </div>
          <el-button class="action-secondary" text @click="addExtraFee">Add fee</el-button>
        </div>

        <div class="adjustment-column adjustment-column--discounts">
          <div class="adjustment-heading">
            <strong>Discounts</strong>
            <div class="adjustment-heading__actions">
              <span>Applied to amount due</span>
              <el-button text size="small" @click="openDiscountPresetManagement">Manage presets</el-button>
            </div>
          </div>
          <div v-for="(discount, index) in discounts" :key="discount.rowKey" class="adjustment-row">
            <div class="discount-preset-control">
              <el-select v-model="discount.presetId" placeholder="Select discount" @change="applyDiscountPreset(discount)">
                <el-option label="Custom discount" value="__custom__" />
                <el-option
                  v-for="preset in discountPresets"
                  :key="preset.id"
                  :label="discountPresetLabel(preset)"
                  :value="preset.id"
                />
              </el-select>
            </div>
            <el-input-number
              v-model="discount.amount"
              :min="0"
              :precision="2"
              :controls="false"
              :disabled="!isCustomDiscount(discount)"
            />
            <el-button v-if="discounts.length > 1" class="remove-adjustment-row" text type="danger" aria-label="Remove discount" @click="removeDiscount(index)">×</el-button>
          </div>
          <el-button class="action-secondary" text @click="addDiscount">Add discount</el-button>
        </div>

        <div class="adjustment-column adjustment-column--payments">
          <div class="adjustment-heading">
            <strong>Payments</strong>
            <span>Use more rows for installments</span>
          </div>
          <div v-for="(payment, index) in payments" :key="payment.rowKey" class="adjustment-row adjustment-row--payment">
            <span>Payment {{ index + 1 }}</span>
            <el-select v-model="payment.paymentMethod" placeholder="Method" filterable>
              <el-option v-for="method in paymentMethods" :key="method.id" :label="method.methodName" :value="method.methodCode" />
            </el-select>
            <el-input-number v-model="payment.amount" :min="0" :precision="2" :controls="false" />
            <el-date-picker v-model="payment.paidAt" type="date" value-format="YYYY-MM-DD" placeholder="Date" />
            <el-button v-if="payments.length > 1" class="remove-adjustment-row" text type="danger" aria-label="Remove payment" @click="removePaymentLine(index)">×</el-button>
            <el-input
              v-if="isOtherPaymentMethod(payment.paymentMethod)"
              v-model="payment.remark"
              class="payment-other-method"
              placeholder="Describe the payment method"
            />
          </div>
          <el-button class="action-secondary" text @click="addPaymentLine">Add payment</el-button>
        </div>
      </section>

      <section class="receipt-financial-summary print-only">
        <div class="receipt-financial-summary__column">
          <strong>Fees</strong>
          <div class="receipt-financial-summary__row">
            <span>Tuition</span>
            <b>{{ formatMoney(courseSubtotal) }}</b>
          </div>
          <div v-if="effectivePickupFeeAmount > 0" class="receipt-financial-summary__row">
            <span>Pickup service</span>
            <b>{{ formatMoney(effectivePickupFeeAmount) }}</b>
          </div>
          <div v-if="effectiveDropoffFeeAmount > 0" class="receipt-financial-summary__row">
            <span>Drop-off service</span>
            <b>{{ formatMoney(effectiveDropoffFeeAmount) }}</b>
          </div>
          <div v-for="fee in extraFees.filter((item) => item.amount > 0)" :key="`print-${fee.rowKey}`" class="receipt-financial-summary__row">
            <span>{{ fee.label || 'Other fee' }}</span>
            <b>{{ formatMoney(fee.amount) }}</b>
          </div>
        </div>

        <div class="receipt-financial-summary__column">
          <strong>Discounts</strong>
          <div v-for="discount in discounts.filter((item) => item.amount > 0)" :key="`print-${discount.rowKey}`" class="receipt-financial-summary__row">
            <span>{{ discount.label || 'Discount' }}</span>
            <b>-{{ formatMoney(discount.amount) }}</b>
          </div>
          <div v-if="discountTotal === 0" class="receipt-financial-summary__row">
            <span>No discount</span>
            <b>—</b>
          </div>
        </div>

        <div class="receipt-financial-summary__column receipt-financial-summary__column--totals">
          <strong>Payment summary</strong>
          <div class="receipt-financial-summary__row">
            <span>Amount due</span>
            <b>{{ formatMoney(amountDue) }}</b>
          </div>
          <div class="receipt-financial-summary__row">
            <span>Paid</span>
            <b>{{ formatMoney(paidAmount) }}</b>
          </div>
          <div class="receipt-financial-summary__row receipt-financial-summary__row--balance">
            <span>Balance due</span>
            <b>{{ formatMoney(Math.max(0, balanceAmount)) }}</b>
          </div>
        </div>
      </section>

      <section class="policies-and-zelle">
        <div class="policy-copy">
          <p><strong>退款政策 / Refund Policy</strong></p>
          <p>开课前退还50%，其余转为学费抵用金；开课后不退，剩余学费转为抵用金。</p>
          <p>Before class starts: 50% refund; remaining becomes tuition credit.</p>
          <p>After class starts: No refund; remaining becomes credit for future courses.</p>
          <p><strong>停课政策 / Course Suspension Policy</strong></p>
          <p>课程中途如需停课，需支付$50手续费，剩余学费转为学费抵用金。</p>
          <p>Mid-course Suspension: $50 fee; remaining tuition becomes credit.</p>
          <p><strong>材料政策 / Material Policy</strong></p>
          <p>首次报名含全套教材。如有遗失，每本需支付$10重新打印教材。</p>
          <p>Materials included. If lost, each item requires a $10 reprint fee.</p>
          <p><strong>纪律政策 / Discipline Policy</strong></p>
          <p>学生须遵守校规。如有三次以上严重违纪（如扰乱课堂、霸凌等），将被劝退。</p>
          <p>Students must follow rules. 3 serious violations may lead to dismissal.</p>
        </div>
        <div class="zelle-placeholder" aria-label="Zelle QR placeholder"></div>
      </section>
    </section>

    <section class="submit-bar no-print">
      <div class="fee-totals">
        <span>
          <small>Amount due</small>
          <strong>{{ formatMoney(amountDue) }}</strong>
        </span>
        <span>
          <small>Paid</small>
          <strong>{{ formatMoney(paidAmount) }}</strong>
        </span>
        <span :class="['balance-pill', { 'balance-pill--due': hasOutstandingBalance, 'balance-pill--paid': isPaidInFull }]">
          <small>Balance</small>
          <strong>{{ balanceLabel }}</strong>
        </span>
        <em v-if="hasOutstandingBalance">Balance due. Record the remaining payment from student history later.</em>
        <em v-else-if="isPaidInFull">Paid in full.</em>
      </div>
      <el-input v-if="!isReadOnly" v-model="form.handledByName" placeholder="Handled by" class="handled-by" />
      <el-input v-if="!isReadOnly" v-model="form.remark" placeholder="Registration remark" class="remark-input" />
      <el-button v-if="!isReadOnly" class="action-primary" type="primary" size="large" :loading="submitting" @click="submitForm">Save</el-button>
      <el-button class="action-print" size="large" @click="printForm">Print</el-button>
    </section>

  </div>
</template>

<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue';
import { ElMessage, ElNotification } from 'element-plus';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { fetchActiveCoursePriceOptions, fetchAllTeachers, fetchCampuses, fetchCourseProducts, fetchDiscountPresets, fetchReusableStudent, fetchStudent } from '@/api/base-data';
import { extractErrorMessage } from '@/api/http';
import { fetchActivePaymentMethods, fetchEnrollmentOrder, submitRegistrationIntake, type RegistrationIntakePayload, type RegistrationIntakeResult } from '@/api/enrollment';
import { fetchClassGroups } from '@/api/schedule';
import { APP_BRAND_NAME } from '@/config/app-config';
import type { CampusItem, ClassGroupItem, CoursePriceOptionItem, CourseProductItem, DiscountPresetItem, EnrollmentOrderDetailItem, EntityId, PaymentMethodItem, StudentItem, TeacherItem } from '@/types/domain';
import { getStoredAuthUser } from '@/utils/auth-storage';
import { isAdminUser } from '@/types/auth';

type ReuseStatus = 'idle' | 'loading' | 'existing' | 'new';

interface CampusChoice {
  key: string;
  campusName: string;
  campusId?: EntityId;
  address: string;
  phone: string;
  taxId?: string;
  defaultPickupFeeAmount?: number;
  defaultDropoffFeeAmount?: number;
}

interface RegistrationCourseLine {
  rowKey: string;
  courseType: string;
  gradeBand: string;
  periodKey: string;
  coursePriceOptionId: EntityId | '';
  courseProductId?: EntityId;
  courseProductName?: string;
  classId?: EntityId;
  className?: string;
  teacherId?: EntityId;
  registerClass: string;
  detail: string;
  periodLabel: string;
  durationLabel: string;
  timeSlotLabel: string;
  priceAmount: number;
  originalPriceAmount?: number;
  manualPriceOverride: boolean;
  oneOnOneHours?: number;
  oneOnOneUnits?: number;
  groupBillingType?: string;
  groupTotalSessions?: number;
  groupStartMonth?: string;
  groupEndMonth?: string;
  groupSessionCount?: number;
  groupRegistrationStartMonth?: string;
  groupRegistrationEndMonth?: string;
  initialLessonStartAt?: string;
  initialLessonEndAt?: string;
  remark?: string;
}

interface MoneyLine {
  rowKey: string;
  label: string;
  amount: number;
}

type DiscountPresetSelection = EntityId | '__custom__';

interface DiscountLine extends MoneyLine {
  presetId: DiscountPresetSelection;
}

interface PaymentLine {
  rowKey: string;
  paymentMethod: string;
  amount: number;
  paidAt: string;
  handledByName?: string;
  remark?: string;
}

interface RegistrationForm {
  campusKey: string;
  signupDate: string;
  periodStartDate: string;
  periodEndDate: string;
  handledByName: string;
  pickupFeeCharged: boolean;
  pickupFeeAmount: number;
  dropoffFeeCharged: boolean;
  dropoffFeeAmount: number;
  remark: string;
  student: {
    firstName: string;
    lastName: string;
    grade: string;
    school: string;
    guardianName: string;
    guardianPhone: string;
    guardianPhone2: string;
    guardianEmail: string;
    pickupRequired: boolean;
    pickupAddress: string;
    dropoffRequired: boolean;
    dropoffAddress: string;
  };
}

const campusFallbacks: CampusChoice[] = [
  { key: 'SOUTH', campusName: 'South Campus', address: 'South campus address', phone: '929-701-9872', taxId: '39-2116039' },
  { key: 'NORTH', campusName: 'North Campus', address: 'North campus address', phone: '929-701-9872', taxId: '42-2229088' },
  { key: 'ONLINE', campusName: 'Online Class', address: 'Online class', phone: '929-701-9872' },
];

const currentUser = getStoredAuthUser();
const route = useRoute();
const router = useRouter();
const today = formatDate(new Date());

const form = reactive<RegistrationForm>({
  campusKey: '',
  signupDate: today,
  periodStartDate: '',
  periodEndDate: '',
  handledByName: currentUser?.displayName ?? '',
  pickupFeeCharged: false,
  pickupFeeAmount: 0,
  dropoffFeeCharged: false,
  dropoffFeeAmount: 0,
  remark: '',
  student: {
    firstName: '',
    lastName: '',
    grade: '',
    school: '',
    guardianName: '',
    guardianPhone: '',
    guardianPhone2: '',
    guardianEmail: '',
    pickupRequired: false,
    pickupAddress: '',
    dropoffRequired: false,
    dropoffAddress: '',
  },
});

const campuses = ref<CampusItem[]>([]);
const teachers = ref<TeacherItem[]>([]);
const paymentMethods = ref<PaymentMethodItem[]>([]);
const priceOptions = ref<CoursePriceOptionItem[]>([]);
const courseProducts = ref<CourseProductItem[]>([]);
const classGroups = ref<ClassGroupItem[]>([]);
const discountPresets = ref<DiscountPresetItem[]>([]);
const reusableStudent = ref<StudentItem | null>(null);
const studentReuseStatus = ref<ReuseStatus>('idle');
const validationErrors = ref<string[]>([]);
const submitting = ref(false);
const showTaxIdOnForm = ref(false);
const receiptTaxId = ref('');
let rowSeed = 0;
const courseLines = ref<RegistrationCourseLine[]>([createCourseLine()]);
const extraFees = ref<MoneyLine[]>([createMoneyLine('Other fee')]);
const discounts = ref<DiscountLine[]>([createDiscountLine()]);
const payments = ref<PaymentLine[]>([createPaymentLine()]);
const printedOrderId = ref<string | null>(null);

let reuseLookupTimer: ReturnType<typeof window.setTimeout> | undefined;

const campusChoices = computed<CampusChoice[]>(() => {
  const apiCampuses = campuses.value.map((campus) => {
    const fallback = resolveCampusProfile(campus.campusName);
    return {
      key: String(campus.id),
      campusId: campus.id,
      campusName: campus.campusName,
      address: fallback.address,
      phone: fallback.phone,
      taxId: fallback.taxId,
      defaultPickupFeeAmount: campus.defaultPickupFeeAmount ?? 0,
      defaultDropoffFeeAmount: campus.defaultDropoffFeeAmount ?? 0,
    };
  });
  const hasOnline = apiCampuses.some((campus) => campus.campusName.toLowerCase().includes('online'));
  return hasOnline ? apiCampuses : [...apiCampuses, campusFallbacks[2]];
});

const selectedCampus = computed(() => campusChoices.value.find((campus) => campus.key === form.campusKey));
const isReadOnly = computed(() => Boolean(route.query.orderId));
const isAdmin = computed(() => isAdminUser(currentUser));
const currentCampusTaxId = computed(() => receiptTaxId.value || selectedCampus.value?.taxId || '');

const priceOptionsWithProduct = computed(() => {
  const activeProductIds = new Set(courseProducts.value.map((product) => String(product.id)));
  return priceOptions.value.filter((option) => option.courseProductId && activeProductIds.has(String(option.courseProductId)));
});
const courseSetupActionLabel = computed(() => courseProducts.value.length === 0 ? 'Create course' : 'Set course prices');
const courseSetupHint = computed(() => courseProducts.value.length === 0
  ? (isAdmin.value
    ? 'Create the first course and its price in Back office before using this form.'
    : 'No courses are ready for registration. Ask an administrator to complete Back office setup.')
  : (isAdmin.value
    ? 'Add an active grade, duration, and price in Back office before registration.'
    : 'No active price is available for this course. Ask an administrator to update Back office setup.'));

const courseSubtotal = computed(() => sumNumbers(courseLines.value.map((line) => line.priceAmount)));
const extraFeeTotal = computed(() => sumNumbers(extraFees.value.map((line) => line.amount)));
const effectivePickupFeeAmount = computed(() => form.pickupFeeCharged ? roundMoney(form.pickupFeeAmount) : 0);
const effectiveDropoffFeeAmount = computed(() => form.dropoffFeeCharged ? roundMoney(form.dropoffFeeAmount) : 0);
const serviceFeeTotal = computed(() => roundMoney(effectivePickupFeeAmount.value + effectiveDropoffFeeAmount.value));
const discountableAmount = computed(() => roundMoney(courseSubtotal.value + serviceFeeTotal.value + extraFeeTotal.value));
const discountTotal = computed(() => sumNumbers(discounts.value.map((line) => line.amount)));
const paidAmount = computed(() => sumNumbers(payments.value.map((line) => line.amount)));
const paymentMethodSummary = computed(() => {
  const methodCodes = Array.from(new Set(payments.value
    .filter((payment) => payment.amount > 0 && Boolean(payment.paymentMethod))
    .map((payment) => payment.paymentMethod)));
  if (methodCodes.length === 0) {
    return 'Not recorded';
  }
  if (methodCodes.length > 1) {
    return 'Mixed';
  }
  return paymentMethods.value.find((method) => method.methodCode === methodCodes[0])?.methodName ?? methodCodes[0];
});
const amountDue = computed(() => Math.max(0, roundMoney(courseSubtotal.value + serviceFeeTotal.value + extraFeeTotal.value - discountTotal.value)));
const balanceAmount = computed(() => roundMoney(amountDue.value - paidAmount.value));
const balanceLabel = computed(() => {
  if (balanceAmount.value < 0) {
    return `Credit ${formatMoney(Math.abs(balanceAmount.value))}`;
  }
  return formatMoney(balanceAmount.value);
});
const hasOutstandingBalance = computed(() => balanceAmount.value > 0);
const isPaidInFull = computed(() => amountDue.value > 0 && balanceAmount.value === 0);
const currentStudentContextId = computed(() => reusableStudent.value?.id);
const fullStudentName = computed(() => [form.student.firstName, form.student.lastName]
  .map((part) => part.trim())
  .filter(Boolean)
  .join(' '));
const studentLookupQuery = computed(() => ({
  keyword: fullStudentName.value || undefined,
  guardian: form.student.guardianPhone.trim() || undefined,
}));

watch(
  () => [fullStudentName.value, form.student.guardianPhone],
  () => {
    if (isReadOnly.value) {
      return;
    }
    if (reuseLookupTimer) {
      window.clearTimeout(reuseLookupTimer);
    }
    reuseLookupTimer = window.setTimeout(checkReusableStudent, 350);
  }
);

watch(() => form.student.pickupAddress, (value) => {
  if (!isReadOnly.value && value.trim()) {
    form.student.pickupRequired = true;
  }
});

watch(() => form.student.dropoffAddress, (value) => {
  if (!isReadOnly.value && value.trim()) {
    form.student.dropoffRequired = true;
  }
});

watch(() => form.student.pickupRequired, (pickupRequired) => {
  if (!pickupRequired) {
    form.pickupFeeCharged = false;
    form.pickupFeeAmount = 0;
  }
});

watch(() => form.student.dropoffRequired, (dropoffRequired) => {
  if (!dropoffRequired) {
    form.dropoffFeeCharged = false;
    form.dropoffFeeAmount = 0;
  }
});

watch(() => form.pickupFeeCharged, (charged) => {
  if (!charged) {
    form.pickupFeeAmount = 0;
  } else if (!isReadOnly.value && form.pickupFeeAmount === 0) {
    form.pickupFeeAmount = selectedCampus.value?.defaultPickupFeeAmount ?? 0;
  }
});

watch(() => form.dropoffFeeCharged, (charged) => {
  if (!charged) {
    form.dropoffFeeAmount = 0;
  } else if (!isReadOnly.value && form.dropoffFeeAmount === 0) {
    form.dropoffFeeAmount = selectedCampus.value?.defaultDropoffFeeAmount ?? 0;
  }
});

watch(() => form.campusKey, () => {
  if (isReadOnly.value) {
    return;
  }
  if (form.pickupFeeCharged) {
    form.pickupFeeAmount = selectedCampus.value?.defaultPickupFeeAmount ?? 0;
  }
  if (form.dropoffFeeCharged) {
    form.dropoffFeeAmount = selectedCampus.value?.defaultDropoffFeeAmount ?? 0;
  }
});

watch(
  () => route.query.orderId,
  async (orderId, previousOrderId) => {
    if (orderId && orderId !== previousOrderId) {
      await loadHistoricalOrder();
    }
  },
);

watch(discountableAmount, () => {
  discounts.value.forEach(recalculatePresetDiscount);
});

onMounted(async () => {
  await loadInitialData();
  if (isReadOnly.value) {
    await loadHistoricalOrder();
  } else {
    await loadExistingStudent();
  }
});

async function loadInitialData() {
  try {
    const [campusRes, teacherRes, paymentRes, optionRes, productRes, classRes, discountPresetRes] = await Promise.all([
      fetchCampuses(),
      fetchAllTeachers(),
      fetchActivePaymentMethods(),
      fetchActiveCoursePriceOptions(),
      fetchCourseProducts({ status: 'ACTIVE', pageNum: 1, pageSize: 200 }),
      fetchClassGroups(),
      fetchDiscountPresets(),
    ]);
    campuses.value = campusRes.data.data ?? [];
    teachers.value = teacherRes.data.data ?? [];
    paymentMethods.value = paymentRes.data.data ?? [];
    priceOptions.value = optionRes.data.data ?? [];
    courseProducts.value = productRes.data.data.records ?? [];
    classGroups.value = classRes.data.data ?? [];
    discountPresets.value = discountPresetRes.data.data ?? [];
    if (!form.campusKey && campusChoices.value.length > 0) {
      form.campusKey = campusChoices.value[0].key;
    }
    if (paymentMethods.value.length > 0) {
      payments.value[0].paymentMethod = paymentMethods.value[0].methodCode;
    }
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  }
}

async function loadHistoricalOrder() {
  const orderId = Array.isArray(route.query.orderId) ? route.query.orderId[0] : route.query.orderId;
  if (!orderId) {
    return;
  }
  try {
    const res = await fetchEnrollmentOrder(orderId);
    applyHistoricalOrder(res.data.data);
    await printHistoricalFormIfRequested(String(orderId));
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  }
}

async function printHistoricalFormIfRequested(orderId: string) {
  const shouldPrint = Array.isArray(route.query.print) ? route.query.print[0] === '1' : route.query.print === '1';
  if (!shouldPrint || printedOrderId.value === orderId) {
    return;
  }
  printedOrderId.value = orderId;
  await nextTick();
  window.setTimeout(printForm, 100);
}

/**
 * Existing students begin a new registration with their contact profile prefilled.
 * Course rows and payment details always belong to the new registration only.
 */
async function loadExistingStudent() {
  const studentId = Array.isArray(route.query.studentId)
    ? route.query.studentId[0]
    : route.query.studentId
      || (Array.isArray(route.query.renewStudentId) ? route.query.renewStudentId[0] : route.query.renewStudentId);
  if (!studentId) {
    return;
  }
  try {
    const response = await fetchStudent(studentId);
    const student = response.data.data;
    setStudentName(student.studentName || '');
    form.student.grade = student.grade || '';
    form.student.school = student.school || '';
    form.student.guardianName = student.guardianName || '';
    form.student.guardianPhone = student.guardianPhone || student.phone || '';
    form.student.guardianPhone2 = student.guardianPhone2 || '';
    form.student.guardianEmail = student.guardianEmail || '';
    const pickupRequired = Boolean(student.pickupRequired);
    const dropoffRequired = Boolean(student.dropoffRequired);
    form.student.pickupRequired = pickupRequired;
    form.student.pickupAddress = pickupRequired ? student.pickupAddress || '' : '';
    form.student.dropoffRequired = dropoffRequired;
    form.student.dropoffAddress = dropoffRequired ? student.dropoffAddress || '' : '';
    form.pickupFeeCharged = false;
    form.pickupFeeAmount = 0;
    form.dropoffFeeCharged = false;
    form.dropoffFeeAmount = 0;
    if (student.campusId != null) {
      form.campusKey = String(student.campusId);
    }
    reusableStudent.value = student;
    studentReuseStatus.value = 'existing';
    ElMessage.success(`New registration started for ${student.studentName}. Add the course rows and payment for this registration.`);
  } catch (error) {
    ElMessage.error(`Could not load the renewal student: ${extractErrorMessage(error)}`);
  }
}

function applyHistoricalOrder(order: EnrollmentOrderDetailItem) {
  const registrationReceipt = order.receipts.find(receipt => receipt.templateCode === 'DEFAULT_RECEIPT');
  const receiptPayload = parsePayload(registrationReceipt?.receiptPayload);
  const campusId = getString(receiptPayload, 'campusId') || String(order.campusId ?? '');
  const campusName = getString(receiptPayload, 'campusName') || order.campusNameSnapshot || '';
  form.campusKey = resolveCampusKey(campusId, campusName);
  form.signupDate = normalizeDate(getString(receiptPayload, 'signupDate') || order.signupDate || '');
  form.periodStartDate = normalizeDate(getString(receiptPayload, 'periodStartDate') || order.periodStartDate || '');
  form.periodEndDate = normalizeDate(getString(receiptPayload, 'periodEndDate') || order.periodEndDate || '');
  form.handledByName = getString(receiptPayload, 'handledByName') || order.handledByName || '';
  form.pickupFeeAmount = firstPositiveNumber(getNumber(receiptPayload, 'pickupFeeAmount'), order.pickupFeeAmount);
  form.pickupFeeCharged = getBoolean(receiptPayload, 'pickupFeeCharged', form.pickupFeeAmount > 0);
  form.dropoffFeeAmount = firstPositiveNumber(
    getNumber(receiptPayload, 'dropoffFeeAmount'),
    order.dropoffFeeAmount,
  );
  form.dropoffFeeCharged = getBoolean(receiptPayload, 'dropoffFeeCharged', form.dropoffFeeAmount > 0);
  form.remark = order.remark ?? '';
  setStudentName(getString(receiptPayload, 'studentName') || order.studentNameSnapshot || order.studentName || '');
  form.student.grade = getString(receiptPayload, 'grade') || order.gradeSnapshot || '';
  form.student.school = getString(receiptPayload, 'school') || order.schoolSnapshot || '';
  form.student.guardianName = '';
  form.student.guardianPhone = getString(receiptPayload, 'guardianPhone') || order.parentPhoneSnapshot || '';
  form.student.guardianPhone2 = getString(receiptPayload, 'guardianPhone2') || order.parentPhone2Snapshot || '';
  form.student.guardianEmail = '';
  form.student.pickupRequired = getBoolean(receiptPayload, 'pickupRequired', order.pickupRequiredSnapshot);
  form.student.pickupAddress = getString(receiptPayload, 'pickupAddress') || order.pickupAddressSnapshot || '';
  form.student.dropoffRequired = getBoolean(receiptPayload, 'dropoffRequired', order.dropoffRequiredSnapshot);
  form.student.dropoffAddress = getString(receiptPayload, 'dropoffAddress') || order.dropoffAddressSnapshot || '';

  const payloadItems = getArray(receiptPayload, 'items');
  courseLines.value = (payloadItems.length > 0 ? payloadItems : order.items)
    .map((item, index) => toHistoricalCourseLine(item, index, findOrderItemFallback(item, order.items, index)));
  if (courseLines.value.length === 0) {
    courseLines.value = [createCourseLine()];
  }

  const historicalExtraFees = getMoneyLines(receiptPayload, 'extraFees');
  const savedPickupFee = historicalExtraFees.find((fee) => fee.label === 'Pickup service');
  const savedDropoffFee = historicalExtraFees.find((fee) => fee.label === 'Drop-off service');
  if (form.pickupFeeAmount === 0 && savedPickupFee) {
    form.pickupFeeAmount = savedPickupFee.amount;
    form.pickupFeeCharged = true;
  }
  if (form.dropoffFeeAmount === 0 && savedDropoffFee) {
    form.dropoffFeeAmount = savedDropoffFee.amount;
    form.dropoffFeeCharged = true;
  }
  extraFees.value = historicalExtraFees.filter((fee) => !['Pickup service', 'Drop-off service'].includes(fee.label));
  if (extraFees.value.length === 0) {
    extraFees.value = [createMoneyLineWithAmount('Other fee', firstPositiveNumber(getNumber(receiptPayload, 'extraFeeAmount'), order.extraFeeAmount))];
  }
  discounts.value = getMoneyLines(receiptPayload, 'discounts')
    .map((discount) => createDiscountLine(discount.label, discount.amount));
  if (discounts.value.length === 0) {
    discounts.value = [createDiscountLine('Custom discount', firstPositiveNumber(getNumber(receiptPayload, 'discountAmount'), order.discountAmount))];
  }

  const savedPayments = getHistoricalPayments(receiptPayload);
  payments.value = registrationReceipt
    ? savedPayments
    : order.payments.map((payment) => ({
      rowKey: `payment-${payment.id}`,
      paymentMethod: payment.paymentMethod,
      amount: numeric(payment.amount),
      paidAt: normalizeDate(payment.paidAt),
      handledByName: payment.handledByName,
      remark: payment.remark,
    }));
  if (payments.value.length === 0) {
    payments.value = [createPaymentLine()];
  }

  showTaxIdOnForm.value = getBoolean(receiptPayload, 'showTaxId', false);
  receiptTaxId.value = getString(receiptPayload, 'taxId');
  validationErrors.value = [];
  studentReuseStatus.value = 'idle';
  reusableStudent.value = null;
}

function toHistoricalCourseLine(
  item: Record<string, unknown> | EnrollmentOrderDetailItem['items'][number],
  index: number,
  fallback?: EnrollmentOrderDetailItem['items'][number],
): RegistrationCourseLine {
  const periodLabel = getString(item, 'periodLabel') || getString(fallback, 'periodLabel');
  return {
    rowKey: `history-course-${getString(item, 'id') || getString(item, 'orderItemId') || getString(fallback, 'id') || index}`,
    courseType: getString(item, 'courseType') || getString(fallback, 'courseType'),
    gradeBand: getString(item, 'gradeBand') || getString(fallback, 'gradeBand'),
    periodKey: `${periodLabel || 'General'}::Standard`,
    coursePriceOptionId: (getString(item, 'coursePriceOptionId') || getString(fallback, 'coursePriceOptionId')) as EntityId | '',
    courseProductId: getString(item, 'courseProductId') || getString(fallback, 'courseProductId') || undefined,
    courseProductName: getString(item, 'courseProductName') || undefined,
    classId: getString(item, 'classId') || getString(fallback, 'classId') || undefined,
    className: getString(item, 'className') || undefined,
    teacherId: getString(item, 'teacherId') || getString(fallback, 'teacherId') || undefined,
    registerClass: getString(item, 'registerClass') || getString(fallback, 'registerClass'),
    detail: getString(item, 'detail') || getString(fallback, 'detail'),
    periodLabel,
    durationLabel: '',
    timeSlotLabel: getString(item, 'timeSlotLabel') || getString(fallback, 'timeSlotLabel'),
    priceAmount: firstPositiveNumber(getNumber(item, 'priceAmount'), getNumber(fallback, 'priceAmount')),
    originalPriceAmount: optionalNumber(item, 'originalPriceAmount') ?? optionalNumber(fallback, 'originalPriceAmount'),
    manualPriceOverride: getBoolean(item, 'manualPriceOverride', false),
    oneOnOneHours: optionalNumber(item, 'oneOnOneHours') ?? optionalNumber(fallback, 'oneOnOneHours'),
    oneOnOneUnits: optionalNumber(item, 'oneOnOneUnits') ?? optionalNumber(fallback, 'oneOnOneUnits'),
    groupBillingType: getString(item, 'groupBillingType') || undefined,
    groupSessionCount: optionalNumber(item, 'sessions'),
    groupStartMonth: getString(item, 'groupStartMonth') || getString(item, 'startMonth') || undefined,
    groupEndMonth: getString(item, 'groupEndMonth') || getString(item, 'endMonth') || undefined,
    groupRegistrationStartMonth: getString(item, 'startMonth') || undefined,
    groupRegistrationEndMonth: getString(item, 'endMonth') || undefined,
    initialLessonStartAt: getString(item, 'initialLessonStartAt') || undefined,
    initialLessonEndAt: getString(item, 'initialLessonEndAt') || undefined,
    remark: getString(item, 'remark') || undefined,
  };
}

function findOrderItemFallback(
  item: Record<string, unknown> | EnrollmentOrderDetailItem['items'][number],
  orderItems: EnrollmentOrderDetailItem['items'],
  index: number,
) {
  const orderItemId = getString(item, 'orderItemId') || getString(item, 'id');
  const lineNo = getNumber(item, 'lineNo');
  return orderItems.find((candidate) => String(candidate.id) === orderItemId)
    || orderItems.find((candidate) => candidate.lineNo === lineNo)
    || orderItems[index];
}

async function checkReusableStudent() {
  const studentName = fullStudentName.value;
  const guardianPhone = form.student.guardianPhone.replace(/\D/g, '');
  reusableStudent.value = null;
  if (!studentName || !guardianPhone) {
    studentReuseStatus.value = 'idle';
    return;
  }
  studentReuseStatus.value = 'loading';
  try {
    const res = await fetchReusableStudent({ studentName, guardianPhone });
    reusableStudent.value = res.data.data ?? null;
    studentReuseStatus.value = reusableStudent.value ? 'existing' : 'new';
  } catch {
    studentReuseStatus.value = 'idle';
  }
}

function createCourseLine(): RegistrationCourseLine {
  return {
    rowKey: `course-${++rowSeed}`,
    courseType: '',
    gradeBand: '',
    periodKey: '',
    coursePriceOptionId: '',
    registerClass: '',
    detail: '',
    periodLabel: '',
    durationLabel: '',
    timeSlotLabel: '',
    priceAmount: 0,
    manualPriceOverride: false,
    groupBillingType: undefined,
    groupTotalSessions: undefined,
    groupStartMonth: undefined,
    groupEndMonth: undefined,
    groupSessionCount: undefined,
    groupRegistrationStartMonth: undefined,
    groupRegistrationEndMonth: undefined,
    initialLessonStartAt: undefined,
    initialLessonEndAt: undefined,
  };
}

function setStudentName(fullName: string) {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  form.student.firstName = parts.shift() ?? '';
  form.student.lastName = parts.join(' ');
}

function createMoneyLine(label: string): MoneyLine {
  return {
    rowKey: `money-${++rowSeed}`,
    label,
    amount: 0,
  };
}

function createMoneyLineWithAmount(label: string, amount: number): MoneyLine {
  return {
    rowKey: `money-${++rowSeed}`,
    label,
    amount: roundMoney(amount),
  };
}

function createDiscountLine(label = 'Custom discount', amount = 0): DiscountLine {
  return {
    rowKey: `discount-${++rowSeed}`,
    presetId: '__custom__',
    label,
    amount: roundMoney(amount),
  };
}

function createPaymentLine(): PaymentLine {
  return {
    rowKey: `payment-${++rowSeed}`,
    paymentMethod: paymentMethods.value[0]?.methodCode ?? '',
    amount: 0,
    paidAt: today,
    remark: '',
  };
}

function addCourseLine() {
  courseLines.value.push(createCourseLine());
}

function removeCourseLine(index: number) {
  if (courseLines.value.length > 1) {
    courseLines.value.splice(index, 1);
  }
}

function addExtraFee() {
  extraFees.value.push(createMoneyLine(''));
}

function removeExtraFee(index: number) {
  if (extraFees.value.length > 1) {
    extraFees.value.splice(index, 1);
  }
}

function addDiscount() {
  discounts.value.push(createDiscountLine());
}

function removeDiscount(index: number) {
  if (discounts.value.length > 1) {
    discounts.value.splice(index, 1);
  }
}

function isCustomDiscount(discount: DiscountLine) {
  return discount.presetId === '__custom__';
}

function discountPresetLabel(preset: DiscountPresetItem) {
  const value = preset.discountType === 'PERCENTAGE'
    ? `${preset.discountValue}% off`
    : `${formatMoney(preset.discountValue)} off`;
  return `${preset.presetName} (${value})`;
}

function applyDiscountPreset(discount: DiscountLine) {
  if (isCustomDiscount(discount)) {
    discount.label = 'Custom discount';
    return;
  }
  const preset = discountPresets.value.find((item) => item.id === discount.presetId);
  if (!preset) {
    discount.presetId = '__custom__';
    return;
  }
  discount.label = preset.presetName;
  recalculatePresetDiscount(discount);
}

function openDiscountPresetManagement() {
  router.push({ name: 'enrollment-orders', query: { manageDiscountPresets: '1' } });
}

function recalculatePresetDiscount(discount: DiscountLine) {
  if (isCustomDiscount(discount)) {
    return;
  }
  const preset = discountPresets.value.find((item) => item.id === discount.presetId);
  if (!preset) {
    return;
  }
  discount.amount = preset.discountType === 'PERCENTAGE'
    ? roundMoney(discountableAmount.value * numeric(preset.discountValue) / 100)
    : roundMoney(preset.discountValue);
}

function addPaymentLine() {
  payments.value.push(createPaymentLine());
}

function removePaymentLine(index: number) {
  if (payments.value.length > 1) {
    payments.value.splice(index, 1);
  }
}

function handleCourseProductChange(line: RegistrationCourseLine) {
  const product = courseProducts.value.find((item) => String(item.id) === String(line.courseProductId));
  const firstOption = priceOptionsForLineProduct(line)[0];
  line.gradeBand = '';
  line.periodKey = '';
  clearSelectedOption(line);
  line.courseType = product?.courseType || firstOption?.courseType || '';
  line.registerClass = product?.registerClass || firstOption?.registerClass || '';
}

function handleGradeChange(line: RegistrationCourseLine) {
  line.periodKey = '';
  clearSelectedOption(line);
}

function handleOptionChange(line: RegistrationCourseLine) {
  const option = findOption(line.coursePriceOptionId);
  if (!option) {
    clearSelectedOption(line);
    return;
  }
  line.courseType = option.courseType;
  line.registerClass = option.registerClass;
  line.gradeBand = option.gradeBand ?? '';
  line.periodLabel = option.periodLabel ?? '';
  line.durationLabel = option.durationLabel ?? '';
  line.periodKey = buildPeriodKey(option);
  line.timeSlotLabel = option.timeSlotLabel ?? '';
  line.detail = option.detailText;
  line.courseProductId = option.courseProductId;
  line.oneOnOneHours = option.oneOnOneHours;
  line.oneOnOneUnits = option.oneOnOneHours ? Math.round(option.oneOnOneHours * 2) : undefined;
  line.classId = option.classId;
  line.groupBillingType = option.groupBillingType;
  line.groupTotalSessions = option.groupTotalSessions;
  line.groupStartMonth = option.groupStartMonth;
  line.groupEndMonth = option.groupEndMonth;
  if (isOneOnOneLine(line)) {
    line.groupSessionCount = undefined;
    line.groupRegistrationStartMonth = undefined;
    line.groupRegistrationEndMonth = undefined;
  } else if (line.groupBillingType === 'MONTHLY') {
    line.groupSessionCount = undefined;
    const defaultStart = monthWithinRange(line.groupRegistrationStartMonth, line)
      ? line.groupRegistrationStartMonth
      : line.groupStartMonth;
    line.groupRegistrationStartMonth = defaultStart;
    line.groupRegistrationEndMonth = monthWithinRange(line.groupRegistrationEndMonth, line)
      ? line.groupRegistrationEndMonth
      : defaultStart;
  } else {
    line.groupSessionCount = line.groupSessionCount || 1;
    line.groupRegistrationStartMonth = undefined;
    line.groupRegistrationEndMonth = undefined;
  }
  line.manualPriceOverride = false;
  handleGroupDurationChange(line);
}

function handlePriceChange(line: RegistrationCourseLine) {
  const originalPrice = line.originalPriceAmount;
  line.manualPriceOverride = originalPrice !== undefined && roundMoney(line.priceAmount) !== roundMoney(originalPrice);
}

function handleGroupDurationChange(line: RegistrationCourseLine) {
  syncGroupDurationLabel(line);
  recalculateLinePrice(line);
}

function recalculateLinePrice(line: RegistrationCourseLine) {
  const option = findOption(line.coursePriceOptionId);
  if (!option) return;
  const quantity = isOneOnOneLine(line) ? 1 : groupPurchasedQuantity(line);
  const computedPrice = roundMoney(numeric(option.priceAmount) * quantity);
  line.originalPriceAmount = computedPrice;
  if (!line.manualPriceOverride) {
    line.priceAmount = computedPrice;
  }
}

function groupPurchasedQuantity(line: RegistrationCourseLine) {
  if (line.groupBillingType === 'MONTHLY') {
    return groupRegistrationMonthCount(line);
  }
  return numeric(line.groupSessionCount);
}

function syncGroupDurationLabel(line: RegistrationCourseLine) {
  if (isOneOnOneLine(line)) return;
  line.durationLabel = printGroupDurationLabel(line);
}

function clearSelectedOption(line: RegistrationCourseLine) {
  line.coursePriceOptionId = '';
  line.classId = undefined;
  line.teacherId = undefined;
  line.registerClass = '';
  line.detail = '';
  line.periodLabel = '';
  line.durationLabel = '';
  line.timeSlotLabel = '';
  line.priceAmount = 0;
  line.originalPriceAmount = undefined;
  line.manualPriceOverride = false;
  line.oneOnOneHours = undefined;
  line.oneOnOneUnits = undefined;
  line.groupBillingType = undefined;
  line.groupTotalSessions = undefined;
  line.groupStartMonth = undefined;
  line.groupEndMonth = undefined;
  line.groupSessionCount = undefined;
  line.groupRegistrationStartMonth = undefined;
  line.groupRegistrationEndMonth = undefined;
}

function gradeOptionsForLine(line: RegistrationCourseLine) {
  return uniqueValues(
    priceOptionsForLineProduct(line)
      .map((option) => option.gradeBand || 'General')
  );
}

function optionChoicesForLine(line: RegistrationCourseLine) {
  return priceOptionsForLineProduct(line).filter((option) => {
    return !line.gradeBand || (option.gradeBand || 'General') === line.gradeBand;
  });
}

function priceOptionsForLineProduct(line: RegistrationCourseLine) {
  return priceOptionsWithProduct.value.filter((option) => {
    return !line.courseProductId || String(option.courseProductId) === String(line.courseProductId);
  });
}

function productOptionsForLine(line: RegistrationCourseLine) {
  const linkedProductIds = new Set(
    priceOptionsWithProduct.value
      .map((option) => String(option.courseProductId))
  );
  return courseProducts.value.filter((product) => linkedProductIds.has(String(product.id)));
}

function classOptionsForLine(line: RegistrationCourseLine) {
  if (!line.courseProductId) {
    return [];
  }
  return classGroups.value.filter((item) => String(item.productId) === String(line.courseProductId));
}

function detailSelectPlaceholder(line: RegistrationCourseLine) {
  if (!line.courseProductId) {
    return 'Select course first';
  }
  if (!line.gradeBand) {
    return 'Select grade first';
  }
  return optionChoicesForLine(line).length > 0 ? 'Registration duration' : 'No duration options';
}

function courseLineHint(line: RegistrationCourseLine) {
  if (priceOptionsWithProduct.value.length === 0) {
    return isAdmin.value
      ? 'No course prices are set. Complete Back office setup first.'
      : 'No course prices are set. Ask an administrator to complete Back office setup.';
  }
  if (!line.courseProductId) {
    return productOptionsForLine(line).length > 0
      ? 'Select a course name.'
      : 'No courses are ready for registration. Ask an administrator to update Back office setup.';
  }
  if (!line.coursePriceOptionId) {
    return optionChoicesForLine(line).length > 0
      ? 'Select a grade and duration price row.'
      : 'Course pricing is missing for this product.';
  }
  return 'Detail will appear here.';
}

function productSelectPlaceholder(line: RegistrationCourseLine) {
  return productOptionsForLine(line).length > 0 ? 'Course name' : 'No active course';
}

function productOptionLabel(product: CourseProductItem) {
  const labels: string[] = [];
  appendDistinctCourseLabel(labels, product.productName);
  appendDistinctCourseLabel(labels, product.registerClass);
  appendDistinctCourseLabel(labels, formatCourseType(product.courseType || ''));
  return labels.join(' · ') || 'Course';
}

function appendDistinctCourseLabel(labels: string[], value?: string) {
  const label = value?.trim();
  if (!label) {
    return;
  }
  const normalized = normalizeCourseLabel(label);
  const duplicated = labels.some((existing) => {
    const existingNormalized = normalizeCourseLabel(existing);
    return existingNormalized === normalized
      || existingNormalized.includes(normalized)
      || normalized.includes(existingNormalized);
  });
  if (!duplicated) {
    labels.push(label);
  }
}

function normalizeCourseLabel(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function printCourseProductLabel(line: RegistrationCourseLine) {
  const product = courseProducts.value.find((item) => String(item.id) === String(line.courseProductId));
  return product ? productOptionLabel(product) : line.courseProductName || line.registerClass || '—';
}

function printCourseScheduleLabel(line: RegistrationCourseLine) {
  const option = findOption(line.coursePriceOptionId);
  return [
    line.durationLabel || option?.durationLabel,
    line.periodLabel || option?.periodLabel,
    line.timeSlotLabel || option?.timeSlotLabel,
  ].filter(Boolean).join(' · ') || line.detail || '—';
}

function showGroupDurationInput(line: RegistrationCourseLine) {
  return Boolean(line.coursePriceOptionId) && !isOneOnOneLine(line);
}

function groupDurationInputLabel(line: RegistrationCourseLine) {
  return line.groupBillingType === 'MONTHLY' ? 'Registration months' : 'Purchased sessions';
}

function printGroupDurationLabel(line: RegistrationCourseLine) {
  if (line.groupBillingType === 'MONTHLY') {
    const months = groupRegistrationMonthCount(line);
    return months > 0 && line.groupRegistrationStartMonth && line.groupRegistrationEndMonth
      ? `${line.groupRegistrationStartMonth} to ${line.groupRegistrationEndMonth} (${months} months)`
      : '—';
  }
  return line.groupSessionCount ? `${line.groupSessionCount} sessions` : '—';
}

function groupRegistrationMonthCount(line: RegistrationCourseLine) {
  const start = monthIndex(line.groupRegistrationStartMonth);
  const end = monthIndex(line.groupRegistrationEndMonth);
  return start === undefined || end === undefined || end < start ? 0 : end - start + 1;
}

function monthWithinRange(value: string | undefined, line: RegistrationCourseLine) {
  const current = monthIndex(value);
  const start = monthIndex(line.groupStartMonth);
  const end = monthIndex(line.groupEndMonth);
  if (current === undefined || start === undefined || end === undefined) {
    return false;
  }
  return current >= start && current <= end;
}

function printTeacherOrClassLabel(line: RegistrationCourseLine) {
  if (isOneOnOneLine(line)) {
    return teachers.value.find((teacher) => String(teacher.id) === String(line.teacherId))?.teacherName || 'To be assigned';
  }
  const classGroup = classGroups.value.find((item) => String(item.id) === String(line.classId));
  return classGroup ? formatClassLabel(classGroup) : line.className || '—';
}

function durationOptionLabel(option: CoursePriceOptionItem) {
  if (option.courseType !== 'ONE_ON_ONE') {
    const billing = option.groupBillingType === 'MONTHLY'
      ? [option.groupStartMonth, option.groupEndMonth].filter(Boolean).join(' to ') || 'Monthly'
      : option.groupTotalSessions ? `${option.groupTotalSessions} total sessions` : 'Per session';
    return [
      billing,
      option.periodLabel,
      option.timeSlotLabel,
    ].filter(Boolean).join(' · ') || option.detailText;
  }
  return [
    option.durationLabel,
    option.periodLabel,
    option.timeSlotLabel,
  ].filter(Boolean).join(' · ') || option.detailText;
}

function classSelectPlaceholder(line: RegistrationCourseLine) {
  if (!line.coursePriceOptionId) {
    return 'Select option first';
  }
  return classOptionsForLine(line).length > 0 ? 'Class' : 'Class will be created automatically';
}

function classScheduleLabel(line: RegistrationCourseLine) {
  const classGroup = classGroups.value.find((item) => String(item.id) === String(line.classId));
  if (!classGroup) {
    return line.coursePriceOptionId ? 'Class schedule will be assigned from this grade price row.' : 'Select a price row first';
  }
  return formatClassLabel(classGroup);
}

function isOneOnOneLine(line: RegistrationCourseLine) {
  const normalizedRegisterClass = line.registerClass.toUpperCase().replace(/[^A-Z0-9]/g, '');
  return line.courseType === 'ONE_ON_ONE'
    || normalizedRegisterClass === 'ONEONONE'
    || normalizedRegisterClass === '1V1'
    || normalizedRegisterClass === '11';
}

async function submitForm() {
  const errors = validateForm();
  validationErrors.value = errors;
  if (errors.length > 0) {
    ElMessage.error(errors[0]);
    return;
  }
  submitting.value = true;
  try {
    const payload = buildPayload();
    const res = await submitRegistrationIntake(payload);
    const saved = res.data.data;
    notifyRegistrationSaved(saved);
    const scheduleArtifact = saved.courseArtifacts.find((artifact) => artifact.packageId);
    if (scheduleArtifact?.packageId) {
      const teacherId = courseLines.value[scheduleArtifact.lineNo - 1]?.teacherId;
      await router.replace({
        name: 'recurring-schedule',
        query: {
          studentId: saved.studentId,
          packageId: scheduleArtifact.packageId,
          teacherId: teacherId || undefined,
          startSchedule: '1',
        },
      });
      return;
    }
    await router.replace({
      name: 'registration',
      query: { orderId: saved.enrollmentOrderId, saved: '1' },
    });
  } catch (error) {
    ElNotification({
      title: 'Registration failed',
      message: extractErrorMessage(error),
      type: 'error',
      position: 'top-right',
    });
  } finally {
    submitting.value = false;
  }
}

function notifyRegistrationSaved(saved: RegistrationIntakeResult) {
  const account = saved.studentAccount;
  const accountMessage = account
    ? account.created
      ? `Student account created: ${account.username}. The initial password is shown below.`
      : `Existing student account reused: ${account.username}. The password was not changed.`
    : 'Registration saved. No student login was created because this is not a 1-on-1 enrollment.';
  const needsScheduling = saved.courseArtifacts.some((artifact) => artifact.packageId);

  ElNotification({
    title: 'Registration completed',
    message: needsScheduling
      ? `${accountMessage} Continue by choosing weekly lesson times on the calendar.`
      : accountMessage,
    type: 'success',
    position: 'top-right',
    duration: 6500,
  });
}

function printForm() {
  window.print();
}

function openCurrentStudentHistory() {
  router.push(buildStudentContextRoute());
}

function openCourseSettings() {
  router.push({ name: courseProducts.value.length === 0 ? 'course-products' : 'admin-config-course-prices' });
}

function buildStudentContextRoute() {
  const studentId = currentStudentContextId.value;
  return {
    name: 'admin-config-students',
    query: studentId
      ? { studentId }
      : studentLookupQuery.value,
  };
}

function validateForm() {
  const errors: string[] = [];
  if (!selectedCampus.value) {
    errors.push('Select one campus before submitting.');
  }
  if (!form.student.firstName.trim() || !form.student.lastName.trim()) {
    errors.push('First name and last name are required.');
  }
  if (!form.student.guardianPhone.trim()) {
    errors.push('Guardian phone is required.');
  }
  if (!form.handledByName.trim()) {
    errors.push('Handled by is required.');
  }
  if (form.periodStartDate && form.periodEndDate
      && Date.parse(form.periodEndDate) < Date.parse(form.periodStartDate)) {
    errors.push('Registration period end date cannot be earlier than start date.');
  }
  if (form.student.pickupRequired && !form.student.pickupAddress.trim()) {
    errors.push('Pick up address is required when Pick up is checked.');
  }
  if (form.student.dropoffRequired && !form.student.dropoffAddress.trim()) {
    errors.push('Drop off address is required when Drop off is checked.');
  }
  if (form.pickupFeeCharged && form.pickupFeeAmount <= 0) {
    errors.push('Pickup fee is marked for this registration. Enter an amount or uncheck the charge option.');
  }
  if (form.dropoffFeeCharged && form.dropoffFeeAmount <= 0) {
    errors.push('Drop-off fee is marked for this registration. Enter an amount or uncheck the charge option.');
  }
  if (discountTotal.value > courseSubtotal.value + serviceFeeTotal.value + extraFeeTotal.value) {
    errors.push('Discount cannot exceed tuition plus fees.');
  }
  if (paidAmount.value > amountDue.value) {
    errors.push('Paid amount cannot exceed amount due. Credit handling is not supported by the intake API yet.');
  }
  courseLines.value.forEach((line, index) => {
    const label = `Course row ${index + 1}`;
    if (!line.courseProductId) {
      errors.push(`${label}: select a course.`);
    }
    if (!line.coursePriceOptionId) {
      errors.push(`${label}: select a grade and duration price row.`);
    }
    if (!line.detail.trim()) {
      errors.push(`${label}: course detail is missing. Reselect the course option.`);
    }
    const groupDurationInvalid = validateGroupDuration(line, label, errors);
    if (!groupDurationInvalid && (line.originalPriceAmount === undefined || line.originalPriceAmount <= 0)) {
      if (!line.manualPriceOverride || line.priceAmount <= 0 || !line.remark?.trim()) {
        errors.push(`${label}: configured price is missing. Enter a manual price and reason, or ask an administrator to update the course price.`);
      }
    }
    if (!groupDurationInvalid && line.priceAmount <= 0) {
      errors.push(`${label}: price must be greater than zero.`);
    }
    if (line.manualPriceOverride && !line.remark?.trim()) {
      errors.push(`${label}: manual price reason is required.`);
    }
    if (!isOneOnOneLine(line) && classOptionsForLine(line).length > 0 && !line.classId) {
      errors.push(`${label}: select an active class for group registration.`);
    }
  });
  validateMoneyLines(extraFees.value, 'Other fee', errors);
  validateMoneyLines(discounts.value, 'Discount', errors);
  payments.value.forEach((payment, index) => {
    if (payment.amount > 0 && !payment.paymentMethod) {
      errors.push(`Payment row ${index + 1}: payment method is required.`);
    }
    if (payment.amount < 0) {
      errors.push(`Payment row ${index + 1}: amount cannot be negative.`);
    }
    if (payment.amount > 0 && !payment.paidAt) {
      errors.push(`Payment row ${index + 1}: payment date is required.`);
    }
    if (payment.amount > 0 && isOtherPaymentMethod(payment.paymentMethod) && !payment.remark?.trim()) {
      errors.push(`Payment row ${index + 1}: describe the other payment method.`);
    }
  });
  return errors;
}

function validateGroupDuration(line: RegistrationCourseLine, label: string, errors: string[]) {
  if (isOneOnOneLine(line) || !line.coursePriceOptionId) {
    return false;
  }
  let invalid = false;
  if (line.groupBillingType === 'MONTHLY') {
    const start = monthIndex(line.groupRegistrationStartMonth);
    const end = monthIndex(line.groupRegistrationEndMonth);
    const classStart = monthIndex(line.groupStartMonth);
    const classEnd = monthIndex(line.groupEndMonth);
    if (classStart === undefined || classEnd === undefined) {
      errors.push(`${label}: class dates are missing. Ask an administrator to update the course setup.`);
      invalid = true;
    }
    if (start === undefined || end === undefined) {
      errors.push(`${label}: select registration start month and end month.`);
      invalid = true;
    }
    if (start !== undefined && end !== undefined && end < start) {
      errors.push(`${label}: registration end month cannot be earlier than start month.`);
      invalid = true;
    }
    if (start !== undefined && classStart !== undefined && start < classStart) {
      errors.push(`${label}: registration start month cannot be earlier than the grade class start month.`);
      invalid = true;
    }
    if (end !== undefined && classEnd !== undefined && end > classEnd) {
      errors.push(`${label}: registration end month cannot exceed the grade class end month.`);
      invalid = true;
    }
    return invalid;
  }

  const sessions = Math.trunc(numeric(line.groupSessionCount));
  if (sessions < 1) {
    errors.push(`${label}: enter purchased sessions on the registration form.`);
    invalid = true;
  }
  if (line.groupTotalSessions && sessions > line.groupTotalSessions) {
    errors.push(`${label}: purchased sessions cannot exceed the grade class total sessions.`);
    invalid = true;
  }
  return invalid;
}

function validateMoneyLines(lines: MoneyLine[], label: string, errors: string[]) {
  lines.forEach((line, index) => {
    if (line.amount > 0 && !line.label.trim()) {
      errors.push(`${label} row ${index + 1}: label is required.`);
    }
  });
}

function buildPayload(): RegistrationIntakePayload {
  const campus = selectedCampus.value;
  const pickupAddress = form.student.pickupAddress.trim();
  const dropoffAddress = form.student.dropoffAddress.trim();
  const payload: RegistrationIntakePayload = {
    student: {
      studentName: fullStudentName.value,
      grade: form.student.grade.trim() || undefined,
      school: form.student.school.trim() || undefined,
      guardianName: form.student.guardianName.trim() || undefined,
      guardianPhone: form.student.guardianPhone.trim(),
      guardianPhone2: form.student.guardianPhone2.trim() || undefined,
      guardianEmail: form.student.guardianEmail.trim() || undefined,
      pickupRequired: form.student.pickupRequired || Boolean(pickupAddress),
      pickupAddress: pickupAddress || undefined,
      dropoffRequired: form.student.dropoffRequired || Boolean(dropoffAddress),
      dropoffAddress: dropoffAddress || undefined,
    },
    campusId: campus?.campusId,
    campusName: campus?.campusName,
    signupDate: form.signupDate,
    periodStartDate: form.periodStartDate || undefined,
    periodEndDate: form.periodEndDate || undefined,
    handledByName: form.handledByName.trim(),
    currencyCode: 'USD',
    items: courseLines.value.map((line, index) => ({
      lineNo: index + 1,
      registerClass: line.registerClass,
      courseType: line.courseType,
      courseProductId: line.courseProductId,
      coursePriceOptionId: line.coursePriceOptionId || undefined,
      classId: isOneOnOneLine(line) ? undefined : line.classId,
      detail: line.detail,
      gradeBand: line.gradeBand || undefined,
      periodLabel: line.periodLabel || undefined,
      timeSlotLabel: line.timeSlotLabel || undefined,
      priceAmount: roundMoney(line.priceAmount),
      originalPriceAmount: line.originalPriceAmount,
      manualPriceOverride: line.manualPriceOverride,
      oneOnOneHours: isOneOnOneLine(line) ? line.oneOnOneHours : undefined,
      oneOnOneUnits: isOneOnOneLine(line) ? line.oneOnOneUnits : undefined,
      teacherId: isOneOnOneLine(line) ? line.teacherId : undefined,
      sessions: !isOneOnOneLine(line) && line.groupBillingType !== 'MONTHLY'
        ? line.groupSessionCount : undefined,
      startMonth: !isOneOnOneLine(line) && line.groupBillingType === 'MONTHLY'
        ? line.groupRegistrationStartMonth : undefined,
      endMonth: !isOneOnOneLine(line) && line.groupBillingType === 'MONTHLY'
        ? line.groupRegistrationEndMonth : undefined,
      remark: line.remark,
    })),
    pickupFeeCharged: form.pickupFeeCharged,
    pickupFeeAmount: roundMoney(effectivePickupFeeAmount.value),
    dropoffFeeCharged: form.dropoffFeeCharged,
    dropoffFeeAmount: roundMoney(effectiveDropoffFeeAmount.value),
    extraFeeAmount: roundMoney(extraFeeTotal.value),
    extraFees: buildMoneyPayload(extraFees.value),
    discountAmount: roundMoney(discountTotal.value),
    discounts: buildMoneyPayload(discounts.value),
    payments: payments.value
      .filter((payment) => payment.amount > 0)
      .map((payment) => ({
        paymentMethod: payment.paymentMethod,
        amount: roundMoney(payment.amount),
        paidAt: toLocalDateTime(payment.paidAt),
        handledByName: form.handledByName.trim(),
        remark: payment.remark?.trim() || undefined,
      })),
    receiptOptions: {
      issueReceipt: true,
      templateCode: 'DEFAULT_RECEIPT',
      showTaxId: showTaxIdOnForm.value,
      taxId: currentCampusTaxId.value,
      campusAddress: campus?.address,
      campusPhone: campus?.phone,
      brandName: APP_BRAND_NAME,
    },
    remark: form.remark.trim() || undefined,
  };
  return payload;
}

function buildMoneyPayload(lines: MoneyLine[]) {
  return lines
    .filter((line) => line.amount > 0)
    .map((line) => ({
      label: line.label.trim(),
      amount: roundMoney(line.amount),
    }));
}

function resetForNextRegistration() {
  validationErrors.value = [];
  form.signupDate = formatDate(new Date());
  form.periodStartDate = '';
  form.periodEndDate = '';
  form.pickupFeeCharged = false;
  form.pickupFeeAmount = 0;
  form.dropoffFeeCharged = false;
  form.dropoffFeeAmount = 0;
  form.remark = '';
  form.student.firstName = '';
  form.student.lastName = '';
  form.student.grade = '';
  form.student.school = '';
  form.student.guardianName = '';
  form.student.guardianPhone = '';
  form.student.guardianPhone2 = '';
  form.student.guardianEmail = '';
  form.student.pickupRequired = false;
  form.student.pickupAddress = '';
  form.student.dropoffRequired = false;
  form.student.dropoffAddress = '';
  courseLines.value = [createCourseLine()];
  extraFees.value = [createMoneyLine('Other fee')];
  discounts.value = [createDiscountLine()];
  payments.value = [createPaymentLine()];
  studentReuseStatus.value = 'idle';
  reusableStudent.value = null;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function findOption(id: EntityId | '') {
  return priceOptions.value.find((option) => option.id === id);
}

function formatClassLabel(item: ClassGroupItem) {
  const meta = [item.teacherName, item.scheduleDesc].filter(Boolean).join(' / ');
  return meta ? `${item.className} (${meta})` : item.className;
}

function formatCourseType(type: string) {
  const labels: Record<string, string> = {
    AFTER_SCHOOL: 'After school',
    SATURDAY: 'Saturday',
    SUMMER: 'Summer',
    SPRINT: 'Sprint',
    ONE_ON_ONE: '1 on 1',
  };
  return labels[type] ?? type.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}

function buildPeriodKey(option: CoursePriceOptionItem) {
  return `${option.periodLabel ?? 'General'}::${option.durationLabel ?? 'Standard'}`;
}

function resolveCampusProfile(name: string) {
  const normalized = name.toLowerCase();
  if (normalized.includes('north')) {
    return campusFallbacks[1];
  }
  if (normalized.includes('online')) {
    return campusFallbacks[2];
  }
  return campusFallbacks[0];
}

function isCampusProfileSelected(profileKey: string) {
  const campus = selectedCampus.value;
  if (!campus) return false;
  return resolveCampusProfile(campus.campusName).key === profileKey;
}

function selectCampusProfile(profileKey: string) {
  const match = campusChoices.value.find((campus) => {
    return campus.key === profileKey || resolveCampusProfile(campus.campusName).key === profileKey;
  });
  if (match) {
    form.campusKey = match.key;
  }
}

function resolveCampusKey(campusId: string, campusName: string) {
  const byId = campusChoices.value.find((campus) => String(campus.campusId ?? campus.key) === campusId);
  if (byId) {
    return byId.key;
  }
  const normalizedName = campusName.toLowerCase();
  const byName = campusChoices.value.find((campus) => campus.campusName.toLowerCase() === normalizedName);
  if (byName) {
    return byName.key;
  }
  const byProfile = campusChoices.value.find((campus) => normalizedName.includes(resolveCampusProfile(campus.campusName).key.toLowerCase()));
  return byProfile?.key ?? campusChoices.value[0]?.key ?? '';
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)));
}

function sumNumbers(values: number[]) {
  return roundMoney(values.reduce((sum, value) => sum + numeric(value), 0));
}

function numeric(value: unknown) {
  const result = Number(value);
  return Number.isFinite(result) ? result : 0;
}

function roundMoney(value: number) {
  return Math.round((numeric(value) + Number.EPSILON) * 100) / 100;
}

function formatMoney(value: number | undefined) {
  return `$${roundMoney(value ?? 0).toFixed(2)}`;
}

function formatHours(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function formatDateValue(value?: string) {
  if (!value) {
    return '-';
  }
  return value.includes('T') ? value.slice(0, 10) : value;
}

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function toLocalDateTime(date: string) {
  return `${date}T00:00:00`;
}

function isOtherPaymentMethod(methodCode?: string) {
  return methodCode?.toUpperCase() === 'OTHER';
}

function normalizeDate(value?: string) {
  if (!value) {
    return '';
  }
  return value.includes('T') ? value.slice(0, 10) : value;
}

function monthIndex(value?: string) {
  const match = value?.match(/^(\d{4})-(\d{2})$/);
  if (!match) {
    return undefined;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  return month >= 1 && month <= 12 ? year * 12 + month - 1 : undefined;
}

function parsePayload(value: unknown): Record<string, unknown> | null {
  if (isRecord(value)) {
    return value;
  }
  if (typeof value !== 'string' || !value.trim()) {
    return null;
  }
  try {
    const parsed = JSON.parse(value);
    return isRecord(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function getArray(payload: Record<string, unknown> | null, key: string): Record<string, unknown>[] {
  const value = payload?.[key];
  return Array.isArray(value) ? value.filter(isRecord) : [];
}

function getMoneyLines(payload: Record<string, unknown> | null, key: string) {
  return getArray(payload, key)
    .map((line, index) => ({
      rowKey: `${key}-${index}`,
      label: getString(line, 'label') || key,
      amount: getNumber(line, 'amount'),
    }))
    .filter((line) => line.amount > 0);
}

function getHistoricalPayments(payload: Record<string, unknown> | null): PaymentLine[] {
  return getArray(payload, 'payments')
    .map((payment, index) => ({
      rowKey: `payload-payment-${getString(payment, 'paymentId') || index}`,
      paymentMethod: getString(payment, 'paymentMethod'),
      amount: getNumber(payment, 'amount'),
      paidAt: normalizeDate(getString(payment, 'paidAt')) || form.signupDate || today,
      handledByName: getString(payment, 'handledByName') || form.handledByName,
      remark: getString(payment, 'remark'),
    }))
    .filter((payment) => payment.amount > 0);
}

function getString(payload: unknown, key: string) {
  const value = isRecord(payload) ? payload[key] : undefined;
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return '';
}

function getBoolean(payload: unknown, key: string, fallback?: boolean) {
  const value = isRecord(payload) ? payload[key] : undefined;
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'number') {
    return value > 0;
  }
  if (typeof value === 'string') {
    return value === 'true' || value === '1';
  }
  return Boolean(fallback);
}

function getNumber(payload: unknown, key: string) {
  const value = isRecord(payload) ? payload[key] : undefined;
  return numeric(value);
}

function optionalNumber(payload: unknown, key: string) {
  const value = getNumber(payload, key);
  return value > 0 ? value : undefined;
}

function firstPositiveNumber(...values: Array<number | undefined>) {
  return values.find((value) => numeric(value) > 0) ?? 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
</script>

<style scoped>
.registration-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  background: #f3f4f6;
  padding: 18px 16px 40px;
  overflow: auto;
  box-sizing: border-box;
}

.paper-form {
  position: relative;
  width: 100%;
  max-width: 920px;
  min-height: 1188px;
  background: #fff;
  color: #050505;
  padding: 30px 34px 24px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.16);
  box-sizing: border-box;
  font-family: "Arial", "Helvetica Neue", sans-serif;
}

.paper-form *,
.registration-desk *,
.submit-bar * {
  box-sizing: border-box;
}

.paper-form :deep(.el-input),
.paper-form :deep(.el-select),
.paper-form :deep(.el-input-number),
.paper-form :deep(.el-date-editor) {
  max-width: 100%;
  min-width: 0;
}

.registration-desk {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  max-width: 920px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.08);
}

.registration-desk strong {
  display: block;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.registration-desk p {
  margin: 2px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.registration-desk__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.registration-flow {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
  max-width: 920px;
}

.registration-flow article {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 8px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #d8e7ff;
  border-radius: 8px;
  background: #f7fbff;
}

.registration-flow span {
  grid-row: span 2;
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.registration-flow strong {
  color: #1f2937;
  font-size: 13px;
}

.registration-flow small {
  overflow: hidden;
  color: #606266;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-header {
  display: block;
  min-height: 82px;
  padding-right: 142px;
}

.form-title-block h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: 0.02em;
}

.form-title-block h2 {
  margin: 5px 0 0;
  color: #4b5563;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: 0;
}

.form-logo {
  position: absolute;
  top: 30px;
  right: 34px;
  width: 112px;
  height: 112px;
  object-fit: cover;
}

.ein-toggle {
  position: absolute;
  top: 148px;
  right: 34px;
  padding: 6px 12px;
  border: 1px solid #111;
  border-radius: 4px;
  background: #fff;
  color: #111;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.form-tax-id {
  position: absolute;
  top: 184px;
  right: 34px;
  min-width: 112px;
  text-align: center;
  color: #111;
  font-size: 16px;
  font-weight: 800;
}

.contact-block {
  margin-top: 14px;
  font-size: 16px;
  line-height: 1.45;
}

.contact-block strong {
  display: block;
  margin-bottom: 6px;
  font-size: 16px;
}

.phone-line {
  margin-bottom: 5px;
}

.campus-line,
.address-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
}

.campus-line input,
.address-line input {
  width: 17px;
  height: 17px;
  margin: 0;
  accent-color: #111;
}

.student-lines {
  margin-top: 6px;
  font-size: 17px;
}

.full-line,
.two-col-line label,
.address-line,
.payment-line {
  display: flex;
  align-items: center;
  min-height: 37px;
  gap: 10px;
}

.full-line > span,
.two-col-line span,
.address-line span {
  white-space: nowrap;
}

.field-label-with-help {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.registration-help-icon {
  color: var(--el-text-color-secondary);
  cursor: help;
  font-size: 14px;
}

.full-line .line-input {
  flex: 1;
}

.student-name-fields {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  min-width: 0;
}

.two-col-line {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 46px;
}

.signup-period-line {
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 34px;
}

.two-col-line label {
  min-width: 0;
}

.line-input,
.line-date,
.period-line {
  flex: 1;
  min-width: 0;
}

.period-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.period-separator {
  color: #111;
  font-weight: 700;
  line-height: 1;
}

.reuse-tag {
  margin-left: 4px;
}

.student-context-line {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin: -2px 0 4px;
  color: #4b5563;
  font-size: 12px;
}

.student-context-line button {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  font-weight: 700;
  cursor: pointer;
}

:deep(.line-input .el-input__wrapper),
:deep(.line-date .el-input__wrapper),
:deep(.table-select .el-select__wrapper),
:deep(.table-price .el-input__wrapper) {
  box-shadow: none !important;
  border-radius: 0;
  background: transparent;
}

:deep(.line-input .el-input__wrapper),
:deep(.line-date .el-input__wrapper) {
  border-bottom: 2px solid #222;
}

:deep(.line-date.el-date-editor.el-input),
:deep(.line-date.el-date-editor.el-input__wrapper) {
  width: 100%;
  min-width: 0;
}

:deep(.line-input .el-input__inner),
:deep(.line-date .el-input__inner),
:deep(.table-price .el-input__inner) {
  color: #111;
  font-size: 16px;
  font-family: inherit;
}

.address-line .line-input {
  min-width: 0;
}

.payment-line {
  margin-top: 1px;
  border-bottom: 2px solid #222;
}

.payment-line > span:first-child {
  white-space: nowrap;
}

.paper-payment-summary {
  display: inline-flex;
  flex: 1;
  min-width: 0;
  min-height: 30px;
  align-items: center;
  padding: 0 6px;
  border-bottom: 2px solid #222;
  color: #111;
  font-size: 16px;
  font-weight: 500;
}

.paper-payment-summary--amount {
  flex: 0 0 116px;
  justify-content: flex-end;
  font-variant-numeric: tabular-nums;
}

.payment-history {
  display: grid;
  gap: 4px;
  margin-top: 6px;
  padding: 6px 0;
  border-bottom: 1px solid #222;
  font-size: 12px;
}

.payment-history > div {
  display: grid;
  grid-template-columns: 92px 110px 90px 1fr;
  gap: 8px;
  align-items: center;
}

.payment-history small {
  color: #4b5563;
}

.paper-other-method {
  width: 160px;
}

.amount-label {
  margin-left: auto;
}

.course-pricing-alert {
  margin-top: 12px;
}

.course-pricing-alert__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.course-table-wrap {
  margin-top: 18px;
  overflow: visible;
}

.course-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 15px;
}

.course-table th,
.course-table td {
  border: 1px solid #222;
}

.course-table th {
  height: 30px;
  text-align: center;
  font-size: 16px;
  font-weight: 400;
}

.course-table td {
  height: 39px;
  padding: 8px;
  vertical-align: top;
}

.course-entry-row td {
  height: 152px;
}

.course-table th:nth-child(1),
.course-table td:nth-child(1) {
  width: 26%;
}

.course-table th:nth-child(2),
.course-table td:nth-child(2) {
  width: 56%;
}

.course-table th:nth-child(3),
.course-table td:nth-child(3) {
  width: 18%;
}

.table-select {
  width: 100%;
}

.course-class-cell,
.course-detail-cell {
  display: grid;
  gap: 8px;
}

.print-only {
  display: none;
}

.print-course-value,
.print-course-price {
  color: #111;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.receipt-course-display,
.receipt-course-detail {
  color: #111;
  font-size: 13px;
  line-height: 1.45;
}

.receipt-course-display strong,
.receipt-course-detail strong,
.receipt-course-price {
  font-weight: 700;
  overflow-wrap: anywhere;
}

.receipt-course-detail {
  display: grid;
  gap: 6px;
}

.receipt-course-detail > div {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 10px;
}

.receipt-course-detail span {
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
}

.receipt-course-detail p {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 11px;
}

.receipt-course-price {
  display: block;
  text-align: right;
}

.receipt-financial-summary {
  display: none;
}

.detail-row {
  display: grid;
  grid-template-columns: minmax(110px, 0.6fr) minmax(0, 1.2fr) minmax(110px, 0.7fr);
  gap: 10px;
}

.teacher-class-line {
  display: grid;
  gap: 4px;
}

.course-control {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.group-month-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.group-month-range :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

.group-month-range > span {
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
}

.course-control > span {
  color: #6b7280;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.course-control--wide {
  width: 100%;
}

.course-detail-hint {
  color: #4b5563;
  font-size: 11px;
  line-height: 1.3;
}

.one-on-one-scheduling-hint {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.manual-price-line {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 6px;
  align-items: center;
}

:deep(.table-select .el-select__wrapper) {
  min-height: 30px;
  padding: 0 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
}

:deep(.table-select .el-select__wrapper:hover),
:deep(.table-select .el-select__wrapper.is-focused) {
  border-color: #94a3b8;
  background: #f8fafc;
}

:deep(.table-select .el-select__placeholder),
:deep(.table-select .el-select__selected-item) {
  font-size: 13px;
}

.price-option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 520px;
}

.price-option-row span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.price-option-row small {
  flex: none;
  color: var(--el-text-color-secondary);
}

.price-cell {
  position: relative;
  vertical-align: middle !important;
  white-space: nowrap;
}

.table-price {
  width: calc(100% - 22px);
}

:deep(.table-price .el-input__wrapper) {
  padding: 0 2px;
}

.remove-row {
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  color: #b91c1c;
  cursor: pointer;
}

.paper-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.intake-adjustment-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  justify-content: start;
  gap: 8px;
  margin-top: 10px;
  padding: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

.adjustment-column {
  display: grid;
  align-content: start;
  gap: 6px;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.adjustment-column:nth-child(1) {
  border-color: #dbeafe;
  background: #f8fbff;
}

.adjustment-column:nth-child(2) {
  border-color: #dcfce7;
  background: #f8fdf9;
}

.adjustment-column:nth-child(3) {
  border-color: #e9d5ff;
  background: #fcfaff;
}

.adjustment-column--payments {
  grid-column: 1 / -1;
  width: 100%;
  max-width: 100%;
  justify-self: stretch;
}

.adjustment-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  padding-bottom: 5px;
  border-bottom: 1px solid #eef2f7;
}

.adjustment-heading strong {
  color: #111827;
  font-size: 13px;
}

.adjustment-heading span {
  min-width: 0;
  color: #6b7280;
  font-size: 11px;
  overflow-wrap: anywhere;
}

.adjustment-heading__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 0;
}

.adjustment-heading__actions :deep(.el-button) {
  height: 20px;
  padding: 0;
  font-size: 11px;
}

.adjustment-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 116px auto;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.adjustment-row > span {
  min-width: 0;
  color: #374151;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.service-charge-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #374151;
  font-size: 12px;
  line-height: 1.25;
}

.service-charge-toggle input {
  width: 15px;
  height: 15px;
  margin: 0;
  accent-color: #111;
}

.service-charge-toggle span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.remove-adjustment-row {
  width: 22px;
  height: 22px;
  padding: 0;
  font-size: 18px;
  line-height: 1;
}

.adjustment-row--service {
  grid-template-columns: minmax(0, 1fr) 112px;
}

.adjustment-column--fees .adjustment-row:not(.adjustment-row--service),
.adjustment-column--discounts .adjustment-row {
  grid-template-columns: minmax(0, 1fr) 110px auto;
}

.adjustment-column--fees .adjustment-row :deep(.el-input),
.adjustment-column--discounts .adjustment-row :deep(.el-input) {
  width: 100%;
  min-width: 0;
}

.discount-preset-control {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.adjustment-column :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.adjustment-row--service :deep(.el-input-number) {
  width: 112px;
  justify-self: end;
}

.adjustment-column :deep(.el-input__wrapper),
.adjustment-column :deep(.el-select__wrapper) {
  min-height: 34px;
  border: 1px solid #d8e1eb;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.adjustment-column :deep(.el-input__wrapper.is-focus),
.adjustment-column :deep(.el-select__wrapper.is-focused) {
  border-color: #7aa7d9;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.12);
}

.adjustment-column :deep(.el-input__inner) {
  color: #334155;
  font-size: 13px;
}

.adjustment-column :deep(.el-input-number .el-input__inner) {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.adjustment-empty {
  min-height: 0;
  padding: 6px 8px;
  border: 1px dashed #d1d5db;
  border-radius: 6px;
  color: #6b7280;
  font-size: 12px;
  line-height: 16px;
}

.adjustment-row--payment {
  grid-template-columns:
    minmax(72px, 0.7fr)
    minmax(130px, 1.45fr)
    minmax(90px, 0.75fr)
    minmax(122px, 1fr)
    22px;
}

.adjustment-row--payment > * {
  min-width: 0;
}

.adjustment-row--payment :deep(.el-select),
.adjustment-row--payment :deep(.el-input),
.adjustment-row--payment :deep(.el-input-number) {
  width: 100%;
}

.adjustment-row--payment :deep(.el-date-editor) {
  width: 100%;
  max-width: 100%;
}

.payment-other-method {
  grid-column: 2 / 5;
}

@media (max-width: 720px) {
  .intake-adjustment-panel {
    grid-template-columns: 1fr;
  }

  .adjustment-column--payments {
    grid-column: auto;
    width: 100%;
  }

  .adjustment-column--fees .adjustment-row:not(.adjustment-row--service),
  .adjustment-column--discounts .adjustment-row {
    grid-template-columns: minmax(0, 1fr) 110px auto;
  }

  .adjustment-column--fees .adjustment-row :deep(.el-input),
  .adjustment-column--discounts .adjustment-row :deep(.el-input) {
    width: 100%;
  }

  .adjustment-row--service :deep(.el-input-number) {
    width: 112px;
  }

  .adjustment-row--payment {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 22px;
  }

  .adjustment-row--payment > span,
  .payment-other-method {
    grid-column: 1 / -1;
  }

  .adjustment-row--payment > .remove-adjustment-row {
    grid-column: 3;
    grid-row: 2 / span 2;
    align-self: center;
  }

  .adjustment-row--payment :deep(.el-date-editor) {
    grid-column: 1 / 3;
    width: 100%;
  }
}

.policies-and-zelle {
  display: grid;
  grid-template-columns: 1fr 176px;
  gap: 34px;
  align-items: end;
  margin-top: 12px;
}

.policy-copy {
  min-height: 228px;
  font-size: 13px;
  line-height: 1.23;
  overflow: hidden;
}

.policy-copy p {
  margin: 0;
}

.policy-copy strong {
  font-size: 13px;
}

.zelle-placeholder {
  width: 126px;
  height: 126px;
  margin: 0 auto 18px;
  border: 13px solid #050505;
  border-radius: 18px;
  box-shadow:
    -6px 0 0 #f59e0b,
    6px 0 0 #06b6d4,
    0 -6px 0 #7c3aed,
    0 6px 0 #ef4444;
  background: #fff;
}

.submit-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 920px;
  padding: 14px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
  box-sizing: border-box;
}

.fee-totals {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(108px, 1fr));
  gap: 8px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-secondary);
}

.fee-totals span {
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f9fafb;
}

.fee-totals small {
  color: #6b7280;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.fee-totals strong {
  color: #111827;
  font-size: 15px;
}

.fee-totals em {
  grid-column: 1 / -1;
  color: #92400e;
  font-size: 12px;
  font-style: normal;
}

.balance-pill--due {
  border-color: #f59e0b !important;
  background: #fffbeb !important;
}

.balance-pill--due strong {
  color: #b45309;
}

.balance-pill--paid {
  border-color: #86efac !important;
  background: #f0fdf4 !important;
}

.balance-pill--paid strong {
  color: #15803d;
}

.handled-by {
  width: 170px;
}

.remark-input {
  flex: 1;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-header__title {
  font-size: 18px;
  font-weight: 700;
}

.registration-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 2px 16px;
}

.date-range {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.pickup-grid {
  display: grid;
  grid-template-columns: 120px 1fr 120px 1fr;
  align-items: center;
  gap: 14px;
}

.registration-table {
  width: 100%;
}

.option-price {
  float: right;
  color: var(--el-text-color-secondary);
}

.detail-cell {
  display: grid;
  gap: 4px;
}

.detail-cell small {
  color: var(--el-text-color-secondary);
}

.manual-price {
  color: var(--el-color-warning);
}

.money-layout {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 1.2fr) 320px;
  gap: 20px;
  align-items: start;
}

.money-editor h3,
.payments-editor h3,
.summary-card h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.fee-row,
.payment-row {
  display: grid;
  grid-template-columns: 1fr 140px auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.payment-row {
  grid-template-columns: minmax(140px, 1fr) 130px 150px auto;
}

.summary-card {
  display: grid;
  gap: 12px;
  padding: 18px;
  background: #f8fafc;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
}

.summary-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.summary-line--total {
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 16px;
}

.discount-text {
  color: var(--el-color-success);
}

.balance-negative {
  color: var(--el-color-danger);
}

.credit-text {
  color: var(--el-color-success);
}

.validation-alert {
  align-items: flex-start;
}

.validation-list {
  margin: 8px 0 0;
  padding-left: 18px;
}

@media (max-width: 1280px) {
  .paper-form,
  .registration-desk,
  .submit-bar {
    max-width: 920px;
  }
}

@media (max-width: 960px) {
  .registration-desk,
  .registration-flow,
  .submit-bar {
    width: 920px;
    max-width: none;
  }

  .paper-form {
    width: 920px;
    max-width: none;
    overflow-x: auto;
  }
}

@media print {
  .no-print,
  .submit-bar,
  .validation-alert,
  button,
  .el-button,
  .paper-actions,
  .remove-row {
    display: none !important;
  }

  .registration-page {
    background: #fff;
    padding: 0;
  }

  .paper-form {
    width: 100%;
    min-height: auto;
    box-shadow: none;
    padding: 0;
  }

  .course-table .el-select,
  .course-table .el-input-number,
  .course-table .el-date-editor,
  .course-table .course-detail-hint,
  .course-table .manual-price-line {
    display: none !important;
  }

  .course-table .print-only {
    display: block !important;
  }

  .course-table .course-entry-row td {
    height: auto;
    padding: 7px;
  }

  .course-table .detail-row {
    grid-template-columns: 0.4fr 1fr 0.55fr;
    gap: 8px;
    padding: 0;
    background: transparent;
    color: #111;
  }

  .course-table .course-control {
    gap: 3px;
  }

  .receipt-financial-summary {
    display: grid !important;
    grid-template-columns: 1.1fr 0.9fr 1fr;
    gap: 12px;
    margin: 10px 0;
    padding: 8px 10px;
    border: 1px solid #334155;
    color: #111;
    font-size: 11px;
  }

  .receipt-financial-summary__column {
    display: grid;
    align-content: start;
    gap: 3px;
    min-width: 0;
  }

  .receipt-financial-summary__column + .receipt-financial-summary__column {
    padding-left: 12px;
    border-left: 1px solid #cbd5e1;
  }

  .receipt-financial-summary__column > strong {
    margin-bottom: 2px;
    font-size: 12px;
  }

  .receipt-financial-summary__row {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }

  .receipt-financial-summary__row span {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  .receipt-financial-summary__row b {
    flex: none;
    font-variant-numeric: tabular-nums;
  }

  .receipt-financial-summary__row--balance {
    margin-top: 2px;
    padding-top: 3px;
    border-top: 1px solid #94a3b8;
    font-size: 12px;
  }

}
</style>
