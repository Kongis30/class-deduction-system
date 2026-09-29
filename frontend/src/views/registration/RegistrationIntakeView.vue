<template>
  <div class="registration-page">
    <el-alert
      v-if="receiptEditingEnabled && validationErrors.length"
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
        <p>{{ isReadOnly && !editingCorrection
          ? (canCorrectReceipt
            ? 'This is the saved receipt. Click Edit Receipt to correct all fields and keep the original registration.'
            : 'This is the saved receipt. This account can view and print it.')
          : 'Fill the familiar paper form, save the record, then print the receipt when ready.' }}</p>
      </div>
      <div class="registration-desk__actions">
        <el-button type="primary" @click="startNewRegistration">New Registration</el-button>
        <el-button class="action-secondary" @click="openCurrentStudentHistory">Find student &amp; history</el-button>
        <el-button v-if="isReadOnly && !editingCorrection && canCorrectReceipt" type="primary" :loading="correctionSetupLoading" @click="startCorrection">Edit Receipt</el-button>
        <template v-if="isReadOnly && editingCorrection">
          <el-button @click="cancelCorrection">Cancel</el-button>
          <el-button type="primary" :loading="savingCorrection" @click="saveCorrection">Save changes</el-button>
        </template>
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
        <small>Course, program, duration, teacher/class</small>
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

    <section v-if="hasOneOnOneLine" class="private-lesson-switcher no-print">
      <div class="private-lesson-switcher__header">
        <div>
          <span class="section-kicker">Private lesson</span>
          <strong>Lesson format</strong>
        </div>
        <el-radio-group v-if="receiptEditingEnabled" v-model="privateParticipantCount" class="private-lesson-mode-select" size="small" aria-label="Private lesson student count">
          <el-radio-button :value="1">1-on-1</el-radio-button>
          <el-radio-button :value="2">1-on-2</el-radio-button>
          <el-radio-button :value="3">1-on-3</el-radio-button>
        </el-radio-group>
        <el-tag v-else class="private-lesson-mode-readonly" type="info" effect="plain">1-on-{{ privateParticipantCount }}</el-tag>
      </div>
      <template v-if="privateWorkspaceVisible">
        <div class="private-lesson-tabs" role="tablist" aria-label="Private lesson registration view">
          <button
            v-for="tab in visiblePrivateTabs"
            :key="String(tab)"
            type="button"
            :class="['private-lesson-tab', { 'private-lesson-tab--active': activePrivateWorkspaceTab === tab }]"
            role="tab"
            :aria-selected="activePrivateWorkspaceTab === tab"
            @click="activePrivateWorkspaceTab = tab"
          >
            <span>{{ privateWorkspaceTabLabel(tab) }}</span>
            <small v-if="tab !== 'all'">{{ privateWorkspaceTabStatus(tab) }}</small>
          </button>
        </div>
        <div v-if="activePrivateWorkspaceTab === 'all'" class="private-lesson-shared-summary">
          <span>{{ primaryPrivateCourseLabel || 'Select a 1-on-1 course' }}</span>
          <span>{{ privateParticipantMultiplier.toFixed(1) }}x tuition · {{ formatMoney(privateParticipantShareAmount) }} each</span>
        </div>
      </template>
    </section>

    <section v-if="studentPickerVisible" class="registration-student-picker no-print">
      <div class="registration-student-picker__heading">
        <strong>{{ activeStudentName || 'Existing student' }}</strong>
        <span>Select an existing student or search by name. Leave blank for a new student.</span>
        <span class="student-credit-balance" :class="{ 'student-credit-balance--loading': studentCreditLoading }">
          <small>Student Credit</small>
          <strong>{{ formatMoney(studentCreditBalance) }}</strong>
        </span>
      </div>
      <el-select
        v-model="activeStudentPickerSelection"
        class="registration-student-picker__select"
        filterable
        clearable
        :disabled="!receiptEditingEnabled"
        :loading="activeStudentPickerLoading"
        :filter-method="filterStudentPickerOptions"
        @visible-change="handleStudentPickerVisibility"
        :placeholder="activeStudentPickerPlaceholder"
        no-data-text="No active students available"
        no-match-text="No existing student found; fill the form below for a new student"
      >
        <el-option
          v-for="student in activeStudentPickerOptions"
          :key="String(student.id)"
          :label="student.studentName"
          :value="student.id"
        >
          <div class="registration-student-picker__option">
            <strong>{{ student.studentName }}</strong>
          </div>
        </el-option>
      </el-select>
    </section>

    <section class="paper-form" :class="{ 'paper-form--readonly': isReadOnly && !editingCorrection }">
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
        <div class="phone-line">☎ Ms. Jin (929) 528-1060 / 办公室 (929) 701-9872</div>

        <label :class="['campus-line', { 'campus-line--selected': isCampusProfileSelected('SOUTH') }]">
          <input type="checkbox" :checked="isCampusProfileSelected('SOUTH')" :disabled="contactLocked || isPrivateAllReceiptView" @change="selectCampusProfile('SOUTH')" />
          <span>南校：144–56 Roosevelt Ave, Flushing, NY 11354</span>
        </label>
        <label :class="['campus-line', { 'campus-line--selected': isCampusProfileSelected('NORTH') }]">
          <input type="checkbox" :checked="isCampusProfileSelected('NORTH')" :disabled="contactLocked || isPrivateAllReceiptView" @change="selectCampusProfile('NORTH')" />
          <span>北校：142–18 32nd Ave, Flushing, NY 11354</span>
        </label>
        <label :class="['campus-line', { 'campus-line--selected': isCampusProfileSelected('ONLINE') }]">
          <input type="checkbox" :checked="isCampusProfileSelected('ONLINE')" :disabled="contactLocked || isPrivateAllReceiptView" @change="selectCampusProfile('ONLINE')" />
          <span>线上课 online</span>
        </label>
      </section>

      <section v-if="isPrivateAllReceiptView" class="private-receipt-student-summary" aria-label="Students on this receipt">
        <div class="private-receipt-student-summary__header">
          <span class="private-receipt-student-summary__label">Students on this receipt</span>
          <strong>{{ privateParticipantCount }} students</strong>
        </div>
        <div class="private-receipt-student-summary__list">
          <article v-for="(participant, index) in privateParticipantReceiptRows" :key="participant.key" class="private-receipt-student-summary__row">
            <div class="private-receipt-student-summary__identity">
              <strong>{{ participant.name || 'Name not set' }}</strong>
            </div>
            <div class="private-receipt-student-summary__financials">
              <span>Due <strong>{{ formatMoney(participant.amountDue) }}</strong></span>
              <span>Paid <strong>{{ formatMoney(participant.paidAmount) }}</strong></span>
              <span>Balance <strong>{{ formatMoney(participant.balanceAmount) }}</strong></span>
            </div>
            <div v-if="participant.payments.length" class="private-receipt-student-summary__payments">
              <span
                v-for="payment in participant.payments"
                :key="payment.rowKey"
                :class="{ 'refund-line': payment.amount < 0 || payment.paymentType === 'REFUND' }"
              >
                {{ [paymentRefundTag(payment), displayPaymentMethod(payment.paymentMethod)].filter(Boolean).join(' · ') }} · {{ formatMoney(payment.amount) }} · {{ formatDateValue(payment.paidAt) }}
              </span>
            </div>
          </article>
        </div>
      </section>

      <section v-else-if="!privateWorkspaceVisible || typeof activePrivateWorkspaceTab === 'number'" class="student-lines">
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
          <div class="student-name-fields screen-only">
            <el-input v-model="activeStudentFirstName" :disabled="contactLocked" placeholder="First name" />
            <el-input v-model="activeStudentLastName" :disabled="contactLocked" placeholder="Last name" />
          </div>
          <strong class="printed-student-name">{{ activeStudentName || '—' }}</strong>
          <span v-if="!isPrivateAllReceiptView" class="student-credit-inline">Credit {{ formatMoney(studentCreditBalance) }}</span>
          <template v-if="!privateWorkspaceVisible || activePrivateWorkspaceTab === 1">
            <el-tag v-if="!isReadOnly && studentReuseStatus === 'existing'" size="small" type="success" class="reuse-tag">Reuse</el-tag>
            <el-tag v-else-if="!isReadOnly && studentReuseStatus === 'new'" size="small" type="info" class="reuse-tag">New</el-tag>
          </template>
        </div>
        <div v-if="(!privateWorkspaceVisible || activePrivateWorkspaceTab === 1) && !isReadOnly && reusableStudent" class="student-context-line no-print">
          <span>Existing profile found. Open history before saving when this is a repeat registration.</span>
          <button type="button" @click="openCurrentStudentHistory">View history</button>
        </div>

        <div class="two-col-line">
          <label>
            <span>Grade:</span>
            <el-input v-model="activeStudentGrade" :disabled="contactLocked" class="line-input" />
          </label>
          <label>
            <span>School:</span>
            <el-input v-model="activeStudentSchool" :disabled="contactLocked" class="line-input" />
          </label>
        </div>

        <div class="two-col-line">
          <label>
            <span>Guardian phone:</span>
            <el-input
              v-if="!contactLocked"
              :model-value="guardianPhoneFocused ? activeStudentGuardianPhone : formatUsPhoneDisplay(activeStudentGuardianPhone)"
              class="line-input student-phone-input"
              @update:model-value="activeStudentGuardianPhone = $event"
              @focus="guardianPhoneFocused = true"
              @blur="guardianPhoneFocused = false"
            />
            <span v-else class="line-input student-phone-display">{{ formatUsPhoneDisplay(activeStudentGuardianPhone) || '—' }}</span>
            <span class="print-only student-phone-print">{{ formatUsPhoneDisplay(activeStudentGuardianPhone) || '—' }}</span>
          </label>
          <label>
            <span>Guardian phone 2:</span>
            <el-input
              v-if="!contactLocked"
              :model-value="guardianPhone2Focused ? activeStudentGuardianPhone2 : formatUsPhoneDisplay(activeStudentGuardianPhone2)"
              class="line-input student-phone-input"
              @update:model-value="activeStudentGuardianPhone2 = $event"
              @focus="guardianPhone2Focused = true"
              @blur="guardianPhone2Focused = false"
            />
            <span v-else class="line-input student-phone-display">{{ formatUsPhoneDisplay(activeStudentGuardianPhone2) || '—' }}</span>
            <span class="print-only student-phone-print">{{ formatUsPhoneDisplay(activeStudentGuardianPhone2) || '—' }}</span>
          </label>
        </div>

        <div class="two-col-line">
          <label>
            <span>Sign Up Date:</span>
            <el-date-picker v-model="form.signupDate" type="date" value-format="YYYY-MM-DD" :disabled="contactLocked" class="line-date" />
          </label>
        </div>

      </section>

      <section class="payment-line">
        <span>Pay method:</span>
        <div class="payment-method-block">
          <strong class="paper-payment-summary">{{ activePaymentMethodSummary }}</strong>
          <div v-if="printablePaymentDetails.length" class="payment-details-print print-only">
            <strong>Payment details:</strong>
            <span
              v-for="detail in printablePaymentDetails"
              :key="detail.key"
              :class="{ 'payment-detail-refund': detail.refund }"
            >{{ detail.method }} {{ detail.refund ? '-' : '' }}{{ formatMoney(detail.amount) }}</span>
          </div>
        </div>
        <span class="amount-label">Amount:</span>
        <strong class="paper-payment-summary paper-payment-summary--amount">{{ formatMoney(activePaidAmount) }}</strong>
      </section>

      <section v-if="isReadOnly && !editingCorrection && !isPrivateAllReceiptView && receiptPaymentRows.length > 0" class="payment-history">
        <div
          v-for="payment in receiptPaymentRows"
          :key="payment.rowKey"
          :class="{ 'payment-history__refund': payment.amount < 0 || payment.paymentType === 'REFUND' }"
        >
          <span>{{ formatDateValue(payment.paidAt) }}</span>
          <strong>{{ [paymentRefundTag(payment), paymentParticipantLabel(payment), payment.paymentMethod || 'Payment'].filter(Boolean).join(' · ') }}</strong>
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
        <el-alert
          v-if="!isReadOnly && !isPrivateAllReceiptView && courseLines.some(isOneOnOneLine)"
          type="info"
          :closable="false"
          show-icon
          class="one-on-one-registration-alert no-print"
          title="1-on-1 registration records the package only. Schedule the teacher, subject, and weekly lesson times after saving."
        />
        <table class="course-table">
          <thead>
            <tr>
              <th>1. Course</th>
              <th>2. Program & package details</th>
              <th>3. Price</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, index) in courseLines" :key="line.rowKey" class="course-entry-row">
              <td>
                <div v-if="isPrivateAllReceiptView || (isReadOnly && !editingCorrection)" class="receipt-course-display">
                  <strong>{{ printCourseProductLabel(line) }}</strong>
                </div>
                <div v-else>
                  <div class="course-class-cell course-picker-control screen-only">
                    <el-select
                      v-model="line.courseProductId"
                      class="table-select table-select--primary"
                      popper-class="course-product-picker"
                      :placeholder="productSelectPlaceholder(line)"
                      :disabled="!receiptEditingEnabled || productOptionsForLine(line).length === 0"
                      no-data-text="No linked courses"
                      no-match-text="No matching course"
                      filterable
                      @change="() => handleCourseProductChange(line)"
                    >
                      <el-option-group
                        v-for="group in productOptionGroupsForLine(line)"
                        :key="group.value"
                        :label="group.label"
                        :class="`course-product-group course-product-group--${group.value}`"
                      >
                        <el-option
                          v-for="product in group.options"
                          :key="product.id"
                          :label="productOptionLabel(product)"
                          :value="product.id"
                          :class="`course-product-option course-product-option--${group.value}`"
                        />
                      </el-option-group>
                    </el-select>
                    <button
                      v-if="receiptEditingEnabled && !isPrivateAllReceiptView && !isRenewalMode && (line.courseProductId || courseLines.length > 1)"
                      type="button"
                      class="remove-row no-print"
                      aria-label="Clear course"
                      @click="clearOrRemoveCourseLine(index)"
                    >×</button>
                  </div>
                  <strong class="print-only print-course-value">{{ printCourseProductLabel(line) }}</strong>
                </div>
              </td>
              <td>
                <div v-if="isPrivateAllReceiptView || (isReadOnly && !editingCorrection)" class="receipt-course-detail">
                  <div>
                    <span>Program</span>
                    <strong>{{ line.gradeBand || '—' }}</strong>
                  </div>
                  <div>
                    <span>{{ isOneOnOneLine(line) ? 'Package' : 'Schedule' }}</span>
                    <strong>{{ printCourseScheduleLabel(line) }}</strong>
                  </div>
                  <div v-if="showDurationInput(line)">
                    <span>Duration &amp; schedule</span>
                    <strong>{{ printRegistrationDurationLabel(line) }}</strong>
                  </div>
                  <div>
                    <span>{{ isOneOnOneLine(line) ? 'Teacher' : 'Class' }}</span>
                    <strong>{{ printTeacherOrClassLabel(line) }}</strong>
                  </div>
                  <p v-if="isOneOnOneLine(line)">Arrange lesson times on the weekly calendar after this registration is saved.</p>
                </div>
                <div v-else>
                  <div class="course-detail-cell screen-only">
                    <div :class="['detail-row', { 'detail-row--group': !isOneOnOneLine(line) }]">
                    <label class="course-control">
                      <span>Program</span>
                      <el-select
                        v-model="line.gradeBand"
                        class="table-select"
                        placeholder="Select program"
                        :disabled="!receiptEditingEnabled || !line.courseProductId"
                        no-data-text="No program options"
                        clearable
                        @change="() => handleGradeChange(line)"
                      >
                        <el-option v-for="grade in gradeOptionsForLine(line)" :key="grade" :label="grade" :value="grade" />
                      </el-select>
                      <strong class="print-only print-course-value">{{ line.gradeBand || '—' }}</strong>
                    </label>
                    <label class="course-control">
                      <span>Package</span>
                      <el-select
                        v-model="line.coursePriceOptionId"
                        class="table-select option"
                        :placeholder="detailSelectPlaceholder(line)"
                        :disabled="!receiptEditingEnabled || !line.courseProductId || !line.gradeBand || optionChoicesForLine(line).length === 0"
                        no-data-text="No detail options"
                        no-match-text="No matching detail"
                        filterable
                        @change="() => handleOptionChange(line)"
                      >
                        <el-option
                          v-for="option in optionChoicesForLine(line)"
                          :key="option.id"
                          :label="durationOptionLabel(option)"
                          :value="option.id"
                        >
                          <div class="price-option-row">
                            <span>{{ durationOptionLabel(option) }}</span>
                            <small>{{ formatMoney(wholeDollarForm ? ceilRegistrationAmount(packageBasePrice(option, defaultAttendanceOption(option))) : packageBasePrice(option, defaultAttendanceOption(option))) }}</small>
                          </div>
                        </el-option>
                      </el-select>
                      <strong class="print-only print-course-value">{{ printCourseScheduleLabel(line) }}</strong>
                    </label>
                    <label v-if="showDurationInput(line)" class="course-control">
                      <span>{{ isOneOnOneLine(line) ? 'Package hours' : isSessionPackage(line) ? 'Selected sessions' : 'Selected length' }}</span>
                      <el-input-number
                        v-if="isOneOnOneLine(line)"
                        v-model="line.oneOnOneHours"
                        class="table-price"
                        :min="0.5"
                        :max="400"
                        :step="0.5"
                        :precision="1"
                        :controls="true"
                        :disabled="!receiptEditingEnabled"
                        @change="() => handleRegistrationDurationChange(line)"
                      />
                      <el-select
                        v-else-if="isSessionPackage(line)"
                        v-model="line.groupSessionCount"
                        class="table-select"
                        :disabled="!receiptEditingEnabled"
                        @change="() => handleGroupSessionCountChange(line)"
                      >
                        <el-option
                          v-for="sessionCount in availableSessionCounts(line)"
                          :key="sessionCount"
                          :label="`${sessionCount} sessions`"
                          :value="sessionCount"
                        />
                      </el-select>
                      <div v-else-if="line.packageUnit === 'MONTH'" class="class-schedule-summary">
                        {{ printRegistrationDurationLabel(line) }}
                      </div>
                      <div v-else class="class-schedule-summary">
                        {{ printRegistrationDurationLabel(line) }}
                      </div>
                      <strong class="print-only print-course-value">{{ printRegistrationDurationLabel(line) }}</strong>
                    </label>
                    <label v-if="isOneOnOneLine(line)" class="course-control">
                      <span>Bonus hours</span>
                      <el-input-number
                        v-model="line.oneOnOneBonusHours"
                        class="table-price"
                        :min="0"
                        :max="400"
                        :step="0.5"
                        :precision="1"
                        :controls="true"
                        :disabled="!receiptEditingEnabled || !line.coursePriceOptionId"
                        @change="() => handleRegistrationDurationChange(line)"
                      />
                    </label>
                    <label v-if="!isOneOnOneLine(line) && line.coursePriceOptionId" class="course-control">
                      <span>Start Date</span>
                      <el-date-picker
                        v-model="line.startDate"
                        type="date"
                        value-format="YYYY-MM-DD"
                        :placeholder="classStartDatePlaceholder(line)"
                        :disabled="!receiptEditingEnabled"
                        class="line-date"
                        @change="() => handleGroupStartDateChange(line)"
                      />
                    </label>
                    <label v-if="!isOneOnOneLine(line) && attendanceOptionsForLine(line).length > 0" class="course-control">
                      <span>Attendance</span>
                      <el-select
                        v-model="line.attendanceOption"
                        class="table-select"
                        placeholder="Select attendance"
                        :disabled="!receiptEditingEnabled"
                        @change="() => handleAttendanceChange(line)"
                      >
                        <el-option
                          v-for="option in attendanceOptionsForLine(line)"
                          :key="option.value"
                          :label="option.label"
                          :value="option.value"
                        />
                      </el-select>
                    </label>
                  </div>
                    <div v-if="!isOneOnOneLine(line)" class="teacher-class-line">
                      <label class="course-control course-control--wide">
                        <span>Class</span>
                        <div class="registration-class-select-row">
                          <el-select
                            v-model="line.classId"
                            class="table-select option"
                            :placeholder="classSelectPlaceholder(line)"
                            :disabled="!receiptEditingEnabled || !line.coursePriceOptionId || classOptionsForLine(line).length === 0"
                            filterable
                            @change="() => handleClassChange(line)"
                          >
                            <el-option
                              v-for="classGroup in classOptionsForLine(line)"
                              :key="classGroup.id"
                              :label="formatClassLabel(classGroup)"
                              :value="classGroup.id"
                            />
                          </el-select>
                          <el-button
                            v-if="selectedCampus"
                            class="registration-class-campus-toggle no-print"
                            size="small"
                            text
                            type="primary"
                            @click="showAllCampusClasses = !showAllCampusClasses"
                          >
                            {{ showAllCampusClasses ? 'Selected campus only' : 'All campuses' }}
                          </el-button>
                        </div>
                        <strong class="print-only print-course-value">{{ printTeacherOrClassLabel(line) }}</strong>
                      </label>
                      <span v-if="!line.coursePriceOptionId" class="course-detail-hint">{{ courseLineHint(line) }}</span>
                    </div>
                    <p v-if="isOneOnOneLine(line)" class="one-on-one-scheduling-hint">
                      After saving, open the schedule calendar and assign each time slot its teacher, lesson name, and allocated hours.
                    </p>
                    <div v-if="receiptEditingEnabled && line.manualPriceOverride" class="manual-price-line">
                      <el-tag size="small" type="warning">Manual price</el-tag>
                      <el-input
                        v-model="line.remark"
                        size="small"
                        placeholder="Reason, e.g. mid-term enrollment"
                      />
                    </div>
                  </div>
                  <div class="print-only print-course-summary">
                    <div><span>Program</span><strong>{{ line.gradeBand || '—' }}</strong></div>
                    <div><span>Registration</span><strong>{{ printRegistrationDurationLabel(line) }}</strong></div>
                    <div>
                      <span>{{ isOneOnOneLine(line) ? 'Package' : 'Sessions' }}</span>
                      <strong>{{ printTuitionOptionLabel(line) }}</strong>
                    </div>
                    <div>
                      <span>{{ isOneOnOneLine(line) ? 'Teacher' : 'Schedule' }}</span>
                      <strong>{{ printScheduleSummaryLabel(line) }}</strong>
                    </div>
                  </div>
                </div>
              </td>
              <td class="price-cell">
                <strong v-if="!receiptEditingEnabled || isPrivateAllReceiptView" class="receipt-course-price">{{ formatCourseLinePrice(line) }}</strong>
                <template v-else-if="isSharedPrivateLesson">
                  <MoneyAmountInput
                    :model-value="privateParticipantLineSharePrice(line, Math.max(0, (activePrivateParticipantIndex ?? 1) - 1))"
                    :min="0"
                    class="table-price"
                    disabled
                  />
                  <strong class="print-only print-course-price">{{ formatCourseLinePrice(line) }}</strong>
                </template>
                <template v-else>
                  <MoneyAmountInput v-model="line.priceAmount" :min="0" :whole-dollars="wholeDollarForm" class="table-price" @change="() => handlePriceChange(line)" />
                  <strong class="print-only print-course-price">{{ formatCourseLinePrice(line) }}</strong>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="hasPrintableTransportDetails" class="receipt-transport-summary print-only">
        <article v-for="line in printableCourseTransportLines" :key="`print-transport-${line.rowKey}`" class="receipt-transport-summary__course">
          <div class="receipt-transport-summary__heading">
            <strong>{{ courseLineOptionLabel(line, courseLines.indexOf(line)) }}</strong>
            <span>Course transport &amp; fees</span>
          </div>
          <div class="receipt-transport-summary__grid">
            <div v-if="line.pickupRequired">
              <span>Pickup address</span>
              <strong>{{ effectiveCoursePickupAddress(line) || '—' }}</strong>
            </div>
            <div v-if="line.dropoffRequired">
              <span>Drop-off address</span>
              <strong>{{ effectiveCourseDropoffAddress(line) || '—' }}</strong>
            </div>
            <div v-if="line.pickupRequired && numeric(line.pickupFeeAmount) > 0">
              <span>Pickup fee</span>
              <strong>{{ formatMoney(line.pickupFeeAmount) }}</strong>
            </div>
            <div v-if="line.dropoffRequired && numeric(line.dropoffFeeAmount) > 0">
              <span>Drop-off fee</span>
              <strong>{{ formatMoney(line.dropoffFeeAmount) }}</strong>
            </div>
          </div>
          <div v-if="line.serviceFees.some((fee) => numeric(fee.amount) > 0)" class="receipt-transport-summary__fees">
            <span>Other fees</span>
            <div v-for="fee in line.serviceFees.filter((item) => numeric(item.amount) > 0)" :key="`print-transport-fee-${fee.rowKey}`" class="receipt-financial-summary__row">
              <span>{{ fee.label || 'Other fee' }}</span>
              <b>{{ formatMoney(fee.amount) }}</b>
            </div>
          </div>
        </article>
      </section>

      <section v-if="!isReadOnly && !isPrivateAllReceiptView" class="paper-actions no-print">
        <el-button v-if="!isRenewalMode" class="action-secondary" @click="addCourseLine">Add course row</el-button>
      </section>

      <section v-if="receiptEditingEnabled && !isPrivateAllReceiptView" class="intake-adjustment-panel no-print">
        <div class="adjustment-column adjustment-column--fees">
          <div class="adjustment-heading">
            <strong>Service & other fees</strong>
            <span>Use the registration address by default, or override it for a specific course.</span>
          </div>
          <div class="course-adjustment-list">
            <article v-for="(line, lineIndex) in courseLines" :key="line.rowKey" class="course-adjustment-card course-adjustment-card--fees">
              <header class="course-adjustment-card__header">
                <div class="course-adjustment-card__title">
                  <span>Course {{ lineIndex + 1 }}</span>
                  <strong>{{ courseLineOptionLabel(line, lineIndex) }}</strong>
                </div>
                <span class="course-adjustment-card__context">Fees {{ formatMoney(courseFeeAmount(line, lineIndex)) }}</span>
              </header>
              <div class="course-service-list">
                <div class="course-service-row">
                  <div class="course-service-row__controls">
                    <label class="service-charge-toggle"><input type="checkbox" v-model="line.pickupRequired" @change="syncCourseTransportFee(line, 'pickup')" /><span>Pickup needed</span></label>
                  </div>
                  <div class="course-service-row__fields">
                    <div class="course-service-field">
                      <span>Pickup address</span>
                      <el-input v-model="line.pickupAddress" placeholder="Enter pickup address" :disabled="!line.pickupRequired" />
                    </div>
                    <label class="course-service-field course-service-field--amount">
                      <span>Fee</span>
                      <MoneyAmountInput v-model="line.pickupFeeAmount" :min="0" :whole-dollars="wholeDollarForm" :disabled="!line.pickupRequired" />
                    </label>
                  </div>
                </div>
                <div class="course-service-row">
                  <div class="course-service-row__controls">
                    <label class="service-charge-toggle"><input type="checkbox" v-model="line.dropoffRequired" @change="syncCourseTransportFee(line, 'dropoff')" /><span>Drop-off needed</span></label>
                  </div>
                  <div class="course-service-row__fields">
                    <div class="course-service-field">
                      <span>Drop-off address</span>
                      <el-input v-model="line.dropoffAddress" placeholder="Enter drop-off address" :disabled="!line.dropoffRequired" />
                    </div>
                    <label class="course-service-field course-service-field--amount">
                      <span>Fee</span>
                      <MoneyAmountInput v-model="line.dropoffFeeAmount" :min="0" :whole-dollars="wholeDollarForm" :disabled="!line.dropoffRequired" />
                    </label>
                  </div>
                </div>
              </div>
              <div v-if="line.serviceFees.length" class="course-service-other-title">Other fees</div>
              <div v-for="(fee, feeIndex) in line.serviceFees" :key="fee.rowKey" class="adjustment-row">
                <el-input v-model="fee.label" placeholder="Other fee label" />
                <MoneyAmountInput v-model="fee.amount" :min="0" :whole-dollars="wholeDollarForm" />
                <el-button v-if="line.serviceFees.length > 1" class="remove-adjustment-row" text type="danger" aria-label="Remove fee" @click="removeCourseServiceFee(line, feeIndex)">×</el-button>
              </div>
              <footer class="course-adjustment-card__footer">
                <el-button class="action-secondary" text @click="addCourseServiceFee(line)">Add fee</el-button>
                <strong>Total fees {{ formatMoney(courseFeeAmount(line, lineIndex)) }}</strong>
              </footer>
            </article>
          </div>
        </div>

        <div class="adjustment-column adjustment-column--discounts">
          <div class="adjustment-heading">
            <strong>Discounts</strong>
            <div class="adjustment-heading__actions">
              <span>{{ activeBillingScopeLabel }}</span>
              <el-button text size="small" @click="openDiscountPresetManagement">Manage presets</el-button>
            </div>
          </div>
          <div class="course-adjustment-list">
            <article v-for="(line, lineIndex) in courseLines" :key="line.rowKey" class="course-adjustment-card course-adjustment-card--discount">
              <header class="course-adjustment-card__header">
                <div class="course-adjustment-card__title">
                  <span>Course {{ lineIndex + 1 }}</span>
                  <strong>{{ courseLineOptionLabel(line, lineIndex) }}</strong>
                </div>
                <span class="course-adjustment-card__context">Tuition {{ formatMoney(activeCoursePrice(line)) }}</span>
              </header>
              <div v-if="discountsForCourse(line.rowKey).length" class="course-adjustment-card__lines">
                <div
                  v-for="discount in discountsForCourse(line.rowKey)"
                  :key="discount.rowKey"
                  class="course-adjustment-card__line adjustment-row"
                  :class="{ 'course-adjustment-card__line--custom': isCustomDiscount(discount) }"
                >
                  <el-button class="remove-adjustment-row" text type="danger" aria-label="Remove discount" @click="removeActiveDiscount(discount)">×</el-button>
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
                  <el-input
                    v-if="isCustomDiscount(discount)"
                    v-model="discount.label"
                    class="discount-name-input"
                    placeholder="Discount name"
                  />
                  <MoneyAmountInput
                    v-model="discount.amount"
                    :min="0"
                    :whole-dollars="wholeDollarForm"
                    :disabled="!isCustomDiscount(discount)"
                  />
                </div>
              </div>
              <p v-else class="course-adjustment-card__empty">No discounts assigned to this course.</p>
              <footer class="course-adjustment-card__footer">
                <el-button class="action-secondary" text @click="addActiveDiscount(line.rowKey)">Add discount</el-button>
                <strong>Discount {{ formatMoney(activeCourseDiscountAmount(line.rowKey)) }}</strong>
              </footer>
            </article>
          </div>
        </div>

        <div class="adjustment-column adjustment-column--payments">
          <div class="adjustment-heading">
            <strong>Payments</strong>
            <span>{{ activeBillingScopeLabel }}</span>
          </div>
          <div class="course-adjustment-list course-adjustment-list--payments">
            <template v-for="(line, lineIndex) in courseLines" :key="line.rowKey">
              <article class="course-adjustment-card course-adjustment-card--payment">
                <header class="course-adjustment-card__header">
                  <div class="course-adjustment-card__title">
                    <span>Course {{ lineIndex + 1 }}</span>
                    <strong>{{ courseLineOptionLabel(line, lineIndex) }}</strong>
                  </div>
                  <span class="course-adjustment-card__context">{{ formatMoney(activeCourseAmountDue(line, lineIndex)) }} due</span>
                </header>
                <div v-if="paymentsForCourse(line.rowKey).length" class="course-payment-list">
                  <div
                    v-for="(payment, paymentIndex) in paymentsForCourse(line.rowKey)"
                    :key="payment.rowKey"
                    class="course-payment-entry"
                    :class="{ 'course-payment-entry--historical': payment.isHistorical }"
                  >
                    <div class="course-payment-entry__fields">
                      <span class="course-payment-entry__number">
                        Payment {{ paymentIndex + 1 }}
                        <small v-if="payment.isHistorical">Historical</small>
                      </span>
                      <el-select
                        v-if="canEditCoursePaymentDetails(payment, line.rowKey)"
                        v-model="payment.paymentMethod"
                        placeholder="Method"
                        filterable
                        @change="normalizePaymentAmount(payment, line.rowKey)"
                      >
                        <el-option v-for="method in paymentMethods" :key="method.id" :label="method.methodName" :value="method.methodCode" />
                      </el-select>
                      <span v-else class="course-payment-entry__history">
                        {{ displayPaymentMethod(payment.paymentMethod) }} · {{ formatDateValue(payment.paidAt) }}
                      </span>
                      <div class="payment-amount-field">
                        <MoneyAmountInput
                          :model-value="coursePaymentAmount(payment, line.rowKey)"
                          :min="0"
                          :max="maxCoursePaymentAmount(payment, line.rowKey)"
                          :whole-dollars="wholeDollarForm"
                          @input="handlePaymentInput(payment, line.rowKey, $event)"
                          @update:model-value="setCoursePaymentAmount(payment, line.rowKey, $event)"
                        />
                        <span
                        v-if="paymentLimitHint(payment)"
                        class="payment-limit-hint"
                        role="status"
                      >{{ paymentLimitHint(payment) }}</span>
                      </div>
                      <el-date-picker
                        v-if="canEditCoursePaymentDetails(payment, line.rowKey)"
                        v-model="payment.paidAt"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="Date"
                      />
                      <el-button
                        v-if="activePaymentLines.length > 1 && canEditCoursePaymentDetails(payment, line.rowKey)"
                        class="remove-adjustment-row"
                        text
                        type="danger"
                        aria-label="Remove payment"
                        @click="removeActivePaymentLine(payment)"
                      >×</el-button>
                      <el-input
                        v-if="canEditCoursePaymentDetails(payment, line.rowKey) && isOtherPaymentMethod(payment.paymentMethod)"
                        v-model="payment.remark"
                        class="payment-other-method"
                        placeholder="Describe the payment method"
                      />
                    </div>
                  </div>
                </div>
                <p v-else class="course-adjustment-card__empty">No payments recorded for this course.</p>
                <div v-if="refundPaymentsForCourse(line.rowKey).length" class="course-payment-refund-list" aria-label="Refund history">
                  <div v-for="refund in refundPaymentsForCourse(line.rowKey)" :key="refund.rowKey" class="course-payment-refund-entry">
                    <span>Refund · {{ displayPaymentMethod(refund.paymentMethod) }} · {{ formatDateValue(refund.paidAt) }} · Read-only</span>
                    <strong>-{{ formatMoney(Math.abs(refund.amount)) }}</strong>
                  </div>
                </div>
                <footer class="course-adjustment-card__footer">
                  <span>Paid {{ formatMoney(coursePaymentAllocationTotal(line.rowKey)) }}</span>
                  <span v-if="courseRefundAmount(line.rowKey) > 0" class="course-payment-refund-total">
                    Refunded {{ formatMoney(courseRefundAmount(line.rowKey)) }}
                  </span>
                  <el-button class="action-secondary" text @click="addActivePaymentLine(line.rowKey)">Add payment</el-button>
                  <strong>Balance {{ formatMoney(activeCourseAmountDue(line, lineIndex) - coursePaymentAllocationTotal(line.rowKey)) }}</strong>
                </footer>
              </article>
            </template>
            <article
              v-if="creditTopUpAmount > 0"
              class="course-adjustment-card course-adjustment-card--payment course-adjustment-card--credit-payment"
              :class="{ 'course-adjustment-card--readonly': isReadOnly }"
            >
              <header class="course-adjustment-card__header">
                <div class="course-adjustment-card__title">
                  <span>Student Credit</span>
                  <strong>Credit top-up</strong>
                </div>
                <span class="course-adjustment-card__context">{{ formatMoney(creditTopUpAmount) }} to store</span>
              </header>
              <div class="course-payment-list">
                <div class="course-payment-entry">
                  <div class="course-payment-entry__fields">
                    <span class="course-payment-entry__number">Credit payment</span>
                    <el-select v-model="creditTopUpPayment.paymentMethod" placeholder="Method" filterable :disabled="isReadOnly">
                      <el-option v-for="method in realPaymentMethods" :key="method.id" :label="method.methodName" :value="method.methodCode" />
                    </el-select>
                    <MoneyAmountInput :model-value="creditTopUpAmount" :min="0" disabled />
                    <el-date-picker v-model="creditTopUpPayment.paidAt" type="date" value-format="YYYY-MM-DD" placeholder="Date" :disabled="isReadOnly" />
                    <el-input
                      v-if="isOtherPaymentMethod(creditTopUpPayment.paymentMethod)"
                      v-model="creditTopUpPayment.remark"
                      class="payment-other-method"
                      placeholder="Describe the payment method"
                      :disabled="isReadOnly"
                    />
                  </div>
                </div>
              </div>
              <footer class="course-adjustment-card__footer">
                <span>Paid {{ formatMoney(creditTopUpAmount) }}</span>
                <strong>Credit {{ formatMoney(creditTopUpAmount) }}</strong>
              </footer>
            </article>
          </div>
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
            <span>Total amount due</span>
            <b>{{ formatMoney(printTotalAmountDue) }}</b>
          </div>
          <div class="receipt-financial-summary__row receipt-financial-summary__row--paid">
            <span>Paid</span>
            <b>{{ formatMoney(printPaidThisTime) }}</b>
          </div>
          <div v-if="refundTotalAmount > 0" class="receipt-financial-summary__row receipt-financial-summary__row--refund">
            <span>Refunded</span>
            <b>-{{ formatMoney(refundTotalAmount) }}</b>
          </div>
          <div class="receipt-financial-summary__row receipt-financial-summary__row--balance">
            <span>Balance due</span>
            <b>{{ printBalanceDueLabel }}</b>
          </div>
        </div>
      </section>

      <section v-if="printableCreditTopUpAmount > 0" class="receipt-credit-summary print-only">
        <div class="receipt-credit-summary__heading">
          <strong>Student Credit</strong>
          <span>Stored balance deposit</span>
        </div>
        <div class="receipt-credit-summary__grid">
          <div>
            <span>Credit added</span>
            <strong>{{ formatMoney(printableCreditTopUpAmount) }}</strong>
          </div>
          <div>
            <span>Payment method</span>
            <strong>{{ printableCreditTopUpPayment?.paymentMethod ? displayPaymentMethod(printableCreditTopUpPayment.paymentMethod) : 'Not recorded' }}</strong>
          </div>
          <div>
            <span>Paid date</span>
            <strong>{{ formatDateValue(printableCreditTopUpPayment?.paidAt) }}</strong>
          </div>
        </div>
      </section>

      <section v-if="form.remark.trim()" class="registration-remark-print print-only">
        <strong>Registration remark</strong>
        <p>{{ form.remark.trim() }}</p>
      </section>

      <section class="policies">
        <div class="policies-layout">
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
          <section v-if="receiptEditingEnabled && !isPrivateAllReceiptView" class="credit-top-up-compact no-print">
            <div class="credit-top-up-compact__header">
              <div>
                <span class="section-kicker">Stored balance</span>
                <strong>Student Credit</strong>
              </div>
              <span>Current {{ formatMoney(studentCreditBalance) }}</span>
            </div>
            <p>Deposit money now and use it on a future registration.</p>
            <label class="credit-top-up-compact__input">
              <span>Top-up amount</span>
              <MoneyAmountInput
                v-model="creditTopUpAmount"
                :min="0"
                :whole-dollars="wholeDollarForm"
                :disabled="isReadOnly"
              />
            </label>
            <strong class="credit-top-up-compact__summary">Adds {{ formatMoney(creditTopUpAmount) }} to Credit</strong>
          </section>
        </div>
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
        <span v-if="refundTotalAmount > 0" class="fee-totals__refund">
          <small>Refunded</small>
          <strong>-{{ formatMoney(refundTotalAmount) }}</strong>
        </span>
        <span :class="['balance-pill', { 'balance-pill--due': hasOutstandingBalance, 'balance-pill--paid': isPaidInFull }]">
          <small>Balance</small>
          <strong>{{ balanceLabel }}</strong>
        </span>
        <em v-if="unallocatedPaymentAmount > 0" class="fee-totals__warning">
          {{ formatMoney(unallocatedPaymentAmount) }} payment is not assigned to a course.
        </em>
        <em v-if="hasOutstandingBalance">Balance due. Record the remaining payment from student history later.</em>
        <em v-else-if="isPaidInFull && unallocatedPaymentAmount === 0">Paid in full.</em>
      </div>
      <el-input
        v-if="receiptEditingEnabled && !isPrivateAllReceiptView"
        v-model="form.handledByName"
        placeholder="Handled by"
        class="handled-by"
        :disabled="isManager"
      />
      <el-input v-if="receiptEditingEnabled && !isPrivateAllReceiptView" v-model="form.remark" placeholder="Registration remark" class="remark-input" />
      <el-button v-if="!isReadOnly" class="action-primary" type="primary" size="large" :loading="submitting" @click="submitForm">Save</el-button>
      <el-button class="action-print" size="large" @click="printForm">Print</el-button>
    </section>

  </div>
</template>

<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { fetchActiveCoursePriceOptions, fetchAllStudents, fetchAllTeachers, fetchCampuses, fetchCourseCategories, fetchCourseProducts, fetchDiscountPresets, fetchProductGradeConfigs, fetchReusableStudent, fetchStudent } from '@/api/base-data';
import { extractErrorMessage, preprocessLargeIds } from '@/api/http';
import { fetchActivePaymentMethods, fetchCoursePackage, fetchEnrollmentOrder, fetchGroupClassStudentProgress, submitRegistrationIntake, correctEnrollmentOrder, type EnrollmentOrderCorrectionPayload, type RegistrationIntakePayload, type RegistrationIntakeResult, type RegistrationParticipantBillingPayload } from '@/api/enrollment';
import { fetchStudentCreditBalance } from '@/api/student-credit';
import { fetchClassGroups } from '@/api/schedule';
import { APP_BRAND_NAME } from '@/config/app-config';
import type { CampusItem, ClassGroupItem, CoursePackageItem, CoursePriceOptionItem, CourseProductItem, DiscountPresetItem, EnrollmentOrderDetailItem, EntityId, PaymentMethodItem, StudentItem, TeacherItem } from '@/types/domain';
import { getStoredAuthUser } from '@/utils/auth-storage';
import { isAdminUser, isManagerUser, isStaffUser } from '@/types/auth';
import { formatUsPhoneDisplay } from '@/utils/phone-format';
import { exceedsMoney, formatExactUsd, formatRoundedUsd } from '@/utils/money-display';
import { calculateProratedSessionPrice } from '@/utils/session-price';
import MoneyAmountInput from '@/components/MoneyAmountInput.vue';
import { paymentDraftAmount, sumPaymentAllocations } from './payment-draft';

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
  orderItemId?: EntityId;
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
  startDate?: string;
  packageUnit?: string;
  packageQuantity?: number;
  packageTotalPrice?: number;
  attendanceOption?: 'FULL_DAY' | 'MORNING' | 'AFTERNOON' | 'CUSTOM' | 'NONE';
  attendanceScheduleJson?: string;
  priceAmount: number;
  originalPriceAmount?: number;
  manualPriceOverride: boolean;
  oneOnOneHours?: number;
  oneOnOneBonusHours?: number;
  openingUsedHours?: number;
  oneOnOneUnits?: number;
  groupBillingType?: string;
  groupTotalSessions?: number;
  groupStartMonth?: string;
  groupEndMonth?: string;
  groupSessionCount?: number;
  groupDurationMode?: '2' | '6' | '10' | 'CUSTOM';
  groupPurchasedMonths?: number;
  groupRegistrationStartMonth?: string;
  groupRegistrationEndMonth?: string;
  pickupRequired: boolean;
  pickupAddress: string;
  dropoffRequired: boolean;
  dropoffAddress: string;
  pickupFeeCharged: boolean;
  pickupFeeAmount: number;
  dropoffFeeCharged: boolean;
  dropoffFeeAmount: number;
  serviceFees: MoneyLine[];
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
  courseRowKey: string;
  courseLineNo?: number;
}

interface PaymentLine {
  rowKey: string;
  paymentMethod: string;
  amount: number;
  paidAt: string;
  isCreditTopUp?: boolean;
  /** 历史流水标记：编辑收据时仍允许修改正向付款，但退款流水保持只读。 */
  isHistorical?: boolean;
  courseRowKey?: string;
  participantIndex?: number;
  handledByName?: string;
  remark?: string;
  paymentType?: string;
  courseAllocations: Record<string, number>;
}

interface RegistrationForm {
  campusKey: string;
  signupDate: string;
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
    studentClass: string;
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

interface PrivateParticipantForm {
  studentId?: EntityId;
  studentName: string;
  grade: string;
  studentClass: string;
  school: string;
  guardianName: string;
  guardianPhone: string;
  guardianPhone2: string;
  guardianEmail: string;
  pickupRequired: boolean;
  pickupAddress: string;
  dropoffRequired: boolean;
  dropoffAddress: string;
}

interface ParticipantBillingForm {
  participantIndex: number;
  discounts: DiscountLine[];
  payments: PaymentLine[];
}

type PrivateWorkspaceTab = 'all' | number;

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
    studentClass: '',
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
const studentsForPicker = ref<StudentItem[]>([]);
const studentPickerLoading = ref(false);
const studentPickerQuery = ref('');
const priceOptions = ref<CoursePriceOptionItem[]>([]);
const courseProducts = ref<CourseProductItem[]>([]);
const productGradeOrderMaps = ref<Map<string, Map<string, number>>>(new Map());
const classGroups = ref<ClassGroupItem[]>([]);
const showAllCampusClasses = ref(false);
const discountPresets = ref<DiscountPresetItem[]>([]);
const courseCategoryLabels = ref<Record<string, string>>({});
const reusableStudent = ref<StudentItem | null>(null);
const historicalPrimaryStudentId = ref<EntityId | undefined>();
const studentReuseStatus = ref<ReuseStatus>('idle');
const validationErrors = ref<string[]>([]);
const submitting = ref(false);
const showTaxIdOnForm = ref(false);
const receiptTaxId = ref('');
let rowSeed = 0;
const paymentLimitHints = reactive<Record<string, string>>({});
const paymentLimitHintTimers = new Map<string, number>();
const courseLines = ref<RegistrationCourseLine[]>([createCourseLine()]);
const privateParticipantCount = ref(1);
const privateParticipantForms = ref<PrivateParticipantForm[]>([]);
// 已保存收据中的每位学生收费快照，仅用于按原收据金额展示，不参与重新计费。
const privateParticipantPriceSnapshots = ref<Array<number | undefined>>([]);
const extraFees = ref<MoneyLine[]>([createMoneyLine('Other fee')]);
const discounts = ref<DiscountLine[]>([createDiscountLine()]);
const payments = ref<PaymentLine[]>([createPaymentLine(courseLines.value[0]?.rowKey)]);
const creditTopUpAmount = ref(0);
const creditTopUpPayment = ref<PaymentLine>(createPaymentLine());
creditTopUpPayment.value.isCreditTopUp = true;
// 历史混合报名单的 Credit 充值属于独立交易，编辑课程时不能回填到可编辑付款行，
// 但打印收据仍需展示这笔充值。单独保存打印快照，避免保存更正时重复创建充值。
const historicalCreditTopUpAmount = ref(0);
const historicalCreditTopUpPayment = ref<PaymentLine | null>(null);
const printableCreditTopUpAmount = computed(() => creditTopUpAmount.value > 0
  ? roundMoney(creditTopUpAmount.value)
  : roundMoney(historicalCreditTopUpAmount.value));
const printableCreditTopUpPayment = computed<PaymentLine | null>(() => creditTopUpAmount.value > 0
  ? creditTopUpPayment.value
  : historicalCreditTopUpPayment.value);
const studentCreditBalance = ref(0);
const studentCreditLoading = ref(false);
const printedOrderId = ref<string | null>(null);
const activePrivateWorkspaceTab = ref<PrivateWorkspaceTab>('all');
const participantBillings = ref<ParticipantBillingForm[]>([]);
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
const renewPackageId = computed(() => getQueryValue(route.query.renewPackageId));
const renewClassEnrollmentId = computed(() => getQueryValue(route.query.renewClassEnrollmentId));
const isRenewalMode = computed(() => Boolean(renewPackageId.value || renewClassEnrollmentId.value));
const editingCorrection = ref(false);
const savingCorrection = ref(false);
const correctionSetupLoading = ref(false);
const historicalOrder = ref<EnrollmentOrderDetailItem | null>(null);
const wholeDollarForm = computed(() => !isReadOnly.value || historicalOrder.value?.billingPolicy === 'WHOLE_DOLLAR_V1');
// Contact fields are editable when creating a new registration or when correcting a saved one.
const contactLocked = computed(() => isReadOnly.value && !editingCorrection.value);
// 收据更正态复用报名表控件，但保存时更新原订单，不创建新的报名单。
const receiptEditingEnabled = computed(() => !isReadOnly.value || editingCorrection.value);
const isAdmin = computed(() => isAdminUser(currentUser));
const isManager = computed(() => isManagerUser(currentUser));
const handledByName = computed(() => isManager.value
  ? currentUser?.displayName?.trim() ?? ''
  : form.handledByName.trim());
const canCorrectReceipt = computed(() => isAdmin.value || (isStaffUser(currentUser) && !isManagerUser(currentUser)));
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

const hasOneOnOneLine = computed(() => courseLines.value.some(isOneOnOneLine));
const hasSelectedCourse = computed(() => courseLines.value.some((line) => Boolean(line.courseProductId || line.coursePriceOptionId)));
// 充值金额大于 0 且尚未选课时，提交独立充值订单；选课后同一次提交拆成课程订单和充值订单。
const isCreditTopUpOnly = computed(() => creditTopUpAmount.value > 0 && !hasSelectedCourse.value);
// 历史纯充值单的 Credit 已写入独立台账，编辑收据时不能再次提交付款流水。
// 这里同时检查历史金额，避免用户在编辑界面把充值金额改成 0 后仍被识别成课程收据。
const isStandaloneCreditTopUpReceipt = computed(() =>
  !hasSelectedCourse.value
  && (creditTopUpAmount.value > 0 || historicalCreditTopUpAmount.value > 0),
);
const isSharedPrivateLesson = computed(() => hasOneOnOneLine.value && privateParticipantCount.value > 1);
// 只有 1-on-2 / 1-on-3 才需要多人工作区；1-on-1 沿用原来的单学生注册表。
const privateWorkspaceVisible = computed(() => isSharedPrivateLesson.value);
const isPrivateAllReceiptView = computed(() => isSharedPrivateLesson.value && activePrivateWorkspaceTab.value === 'all');
const visiblePrivateTabs = computed<PrivateWorkspaceTab[]>(() => [
  'all',
  ...Array.from({ length: privateParticipantCount.value }, (_, index) => index + 1),
]);
const activePrivateParticipantIndex = computed(() =>
  typeof activePrivateWorkspaceTab.value === 'number'
    ? activePrivateWorkspaceTab.value
    : hasOneOnOneLine.value && !privateWorkspaceVisible.value
      ? 1
      : null,
);
// 普通课程和 1-on-1 使用主学生；1-on-2/1-on-3 使用当前学生 Tab，All 仅展示汇总收据。
const activeStudentPickerIndex = computed<number | null>(() => {
  if (isSharedPrivateLesson.value) {
    return typeof activePrivateWorkspaceTab.value === 'number'
      ? activePrivateWorkspaceTab.value
      : null;
  }
  return receiptEditingEnabled.value ? 1 : null;
});
const studentPickerVisible = computed(() => receiptEditingEnabled.value && !isPrivateAllReceiptView.value);
const activeStudentPickerOptions = computed(() => {
  const index = activeStudentPickerIndex.value;
  if (index === null) {
    return [];
  }
  const participant = index === 1 ? null : privateParticipantForms.value[index - 2];
  const selectedStudent = index === 1
    ? reusableStudent.value
      ?? (historicalPrimaryStudentId.value && fullStudentName.value
        ? createStudentPickerOption(historicalPrimaryStudentId.value, fullStudentName.value, form.student)
        : null)
    : participant?.studentId
      ? studentsForPicker.value.find((student) => String(student.id) === String(participant.studentId))
        ?? createStudentPickerOption(participant.studentId, participant.studentName, participant)
      : null;
  const students = selectedStudent ? [selectedStudent, ...studentsForPicker.value] : studentsForPicker.value;
  const uniqueStudents = new Map<string, StudentItem>();
  students.forEach((student) => uniqueStudents.set(String(student.id), student));
  const sortedStudents = Array.from(uniqueStudents.values()).sort((left, right) =>
    left.studentName.localeCompare(right.studentName, undefined, { sensitivity: 'base' })
      || String(left.id).localeCompare(String(right.id), undefined, { numeric: true }),
  );
  const query = studentPickerQuery.value.trim().toLowerCase();
  if (!query) {
    return sortedStudents;
  }
  return sortedStudents.filter((student) => {
    if (selectedStudent && String(student.id) === String(selectedStudent.id)) {
      return true;
    }
    return [
      student.studentName,
      student.studentNo,
      student.grade,
      student.guardianPhone,
      student.phone,
    ].filter(Boolean).some((value) => String(value).toLowerCase().includes(query));
  });
});
const activeStudentPickerSelection = computed<EntityId | ''>({
  get: () => {
    const index = activeStudentPickerIndex.value;
    if (index === null) {
      return '';
    }
    return index === 1
      ? reusableStudent.value?.id ?? historicalPrimaryStudentId.value ?? ''
      : privateParticipantForms.value[index - 2]?.studentId ?? '';
  },
  set: (studentId) => {
    const index = activeStudentPickerIndex.value;
    if (index === null) {
      return;
    }
    if (!studentId) {
      clearSelectedStudent(index);
      return;
    }
    const student = activeStudentPickerOptions.value.find((item) => String(item.id) === String(studentId));
    if (!student) {
      return;
    }
    if (index === 1) {
      selectPrimaryPrivateParticipant(student);
    } else {
      selectPrivateParticipant(index, student);
    }
  },
});
const activeStudentPickerLoading = computed(() => {
  return studentPickerLoading.value;
});
const realPaymentMethods = computed(() => paymentMethods.value.filter((method) => method.methodCode !== 'STUDENT_CREDIT'));
const activeStudentPickerPlaceholder = computed(() => {
  const name = activeStudentName.value;
  if (name) {
    return `Select or search ${name}`;
  }
  const ordinal = ['', 'second', 'third'][activeStudentPickerIndex.value ?? 1] ?? '';
  return ordinal ? `Select or search the ${ordinal} student` : 'Select or search a student';
});
const activePrivateParticipant = computed(() => {
  const index = activePrivateParticipantIndex.value;
  if (index === null || index === 1) {
    return null;
  }
  return privateParticipantForms.value[index - 2] ?? null;
});
const privateParticipantMultiplier = computed(() =>
  hasOneOnOneLine.value && privateParticipantCount.value > 1
    ? 1 + (privateParticipantCount.value - 1) * 0.5
    : 1,
);
const privateParticipantTotalPrice = computed(() => {
  return roundMoney(courseLines.value
    .filter(isOneOnOneLine)
    .reduce((total, line) => total + privateParticipantLineTotalPrice(line), 0));
});
const privateParticipantShareAmount = computed(() => {
  const total = privateParticipantTotalPrice.value;
  return privateParticipantCount.value > 0
    ? splitShareAmount(total, privateParticipantCount.value, 0, wholeDollarForm.value)
    : 0;
});
const primaryPrivateCourseLabel = computed(() => {
  const line = courseLines.value.find(isOneOnOneLine);
  return line ? printCourseProductLabel(line) : '';
});
const privateParticipantReceiptRows = computed(() => {
  const names = Array.from({ length: privateParticipantCount.value }, (_, index) =>
    index === 0 ? fullStudentName.value : privateParticipantForms.value[index - 1]?.studentName.trim() || '',
  );
  const count = names.length;
  return names.map((name, index) => {
    const billing = participantBillings.value[index];
    const grossPrice = privateParticipantPriceSnapshots.value.length === count
      && privateParticipantPriceSnapshots.value[index] !== undefined
      ? privateParticipantPriceSnapshots.value[index]!
      : roundMoney(courseLines.value.reduce((total, line) => total + participantCoursePrice(line, index), 0));
    const discount = sumNumbers(billing?.discounts.map((item) => item.amount) ?? []);
    const participantFees = index === 0 ? serviceFeeTotal.value + courseServiceExtraFeeTotal.value : 0;
    const amountDue = Math.max(0, roundMoney(grossPrice + participantFees - discount));
    const paid = appliedPaymentAmountForLines(billing?.payments ?? []);
    return {
    key: `receipt-participant-${index}`,
    name,
    grade: index === 0 ? form.student.grade : privateParticipantForms.value[index - 1]?.grade || '',
    school: index === 0 ? form.student.school : privateParticipantForms.value[index - 1]?.school || '',
    guardianPhone: index === 0 ? form.student.guardianPhone : privateParticipantForms.value[index - 1]?.guardianPhone || '',
    priceAmount: grossPrice,
    discountAmount: discount,
    amountDue,
    paidAmount: paid,
    balanceAmount: roundMoney(amountDue - paid),
    payments: (billing?.payments ?? []).filter((payment) => payment.amount !== 0),
  };
  });
});
const courseSubtotal = computed(() => sumNumbers(courseLines.value.map((line) => {
  return courseAggregatePrice(line);
})));
const extraFeeTotal = computed(() => sumNumbers(extraFees.value.map((line) => line.amount)));
const hasExplicitCourseFees = computed(() => courseLines.value.some((line) =>
  line.pickupRequired || line.dropoffRequired
  || line.serviceFees.some((fee) => numeric(fee.amount) > 0)));
// 新报名按课程行记录接送；只有更正旧订单时才读取订单级接送字段。
const usesLegacyOrderTransportFields = computed(() =>
  isReadOnly.value && editingCorrection.value && !hasExplicitCourseFees.value);
const effectivePickupFeeAmount = computed(() => hasExplicitCourseFees.value
  ? roundMoney(courseLines.value.reduce((total, line) => total + (line.pickupRequired ? numeric(line.pickupFeeAmount) : 0), 0))
  : usesLegacyOrderTransportFields.value && form.student.pickupRequired ? roundMoney(form.pickupFeeAmount) : 0);
const effectiveDropoffFeeAmount = computed(() => hasExplicitCourseFees.value
  ? roundMoney(courseLines.value.reduce((total, line) => total + (line.dropoffRequired ? numeric(line.dropoffFeeAmount) : 0), 0))
  : usesLegacyOrderTransportFields.value && form.student.dropoffRequired ? roundMoney(form.dropoffFeeAmount) : 0);
const courseServiceExtraFeeTotal = computed(() => hasExplicitCourseFees.value
  ? roundMoney(courseLines.value.reduce((total, line) => total + sumNumbers(line.serviceFees.map((fee) => fee.amount)), 0))
  : extraFeeTotal.value);
const serviceFeeTotal = computed(() => roundMoney(effectivePickupFeeAmount.value + effectiveDropoffFeeAmount.value));
const discountableAmount = computed(() => roundMoney(courseSubtotal.value + serviceFeeTotal.value + courseServiceExtraFeeTotal.value));
const printableCourseTransportLines = computed(() => courseLines.value.filter((line) =>
  line.pickupRequired
  || line.dropoffRequired
  || line.serviceFees.some((fee) => numeric(fee.amount) > 0)));
const hasPrintableTransportDetails = computed(() => printableCourseTransportLines.value.length > 0);
const participantDiscountTotal = computed(() => participantBillings.value
  .reduce((total, billing) => total + sumNumbers(billing.discounts.map((line) => line.amount)), 0));
const participantPaidAmount = computed(() => participantBillings.value
  .reduce((total, billing) => total + appliedPaymentAmountForLines(billing.payments), 0));

function effectiveCoursePickupAddress(line: RegistrationCourseLine) {
  return line.pickupAddress.trim();
}

function effectiveCourseDropoffAddress(line: RegistrationCourseLine) {
  return line.dropoffAddress.trim();
}

// 底部 Paid 只累计当前表单的付款行；编辑收据时旧流水已预填为表单，不再单独叠加。
// 退款以负数参与计算，多课程付款的行金额随课程分配同步。
function isRefundPayment(payment: PaymentLine) {
  return payment.paymentType === 'REFUND' || numeric(payment.amount) < 0;
}

function netPaymentAmount(payment: PaymentLine) {
  const amount = Math.abs(numeric(payment.amount));
  return isRefundPayment(payment) ? -amount : amount;
}

const paymentTotalAmount = computed(() => roundMoney(payments.value.reduce(
  (total, payment) => total + netPaymentAmount(payment),
  0,
)));
const unallocatedPaymentAmount = computed(() => {
  if (courseLines.value.length <= 1) {
    return 0;
  }
  return roundMoney(payments.value.reduce((total, payment) => {
    if (payment.isHistorical) {
      return total;
    }
    const rawAmount = numeric(payment.amount);
    const appliedAmount = paymentAppliedAmount(payment);
    return total + Math.max(0, rawAmount - appliedAmount);
  }, 0));
});
const receiptPaymentRows = computed(() => {
  if (!isSharedPrivateLesson.value) {
    return payments.value;
  }
  const activeIndex = activePrivateParticipantIndex.value;
  const billings = activeIndex != null
    ? participantBillings.value.filter((billing) => billing.participantIndex === activeIndex)
    : participantBillings.value;
  return billings.flatMap((billing) => billing.payments.map((payment) => ({
    ...payment,
    participantIndex: billing.participantIndex,
  })));
});
const printTotalAmountDue = computed(() => amountDue.value);
const printPaidThisTime = computed(() => paidAmount.value);
const printBalanceDue = computed(() => balanceAmount.value);
const printBalanceDueLabel = computed(() => printBalanceDue.value < 0
  ? `Credit ${formatMoney(Math.abs(printBalanceDue.value))}`
  : formatMoney(printBalanceDue.value));
const creditPaymentAmount = computed(() => {
  const lines = isSharedPrivateLesson.value
    ? participantBillings.value.flatMap((billing) => billing.payments)
    : payments.value;
  return roundMoney(lines.reduce((total, payment) => {
    if (payment.paymentMethod !== 'STUDENT_CREDIT' || numeric(payment.amount) <= 0) {
      return total;
    }
    return total + numeric(payment.amount);
  }, 0));
});
const discountTotal = computed(() => isSharedPrivateLesson.value
  ? roundMoney(participantDiscountTotal.value)
  : sumNumbers(discounts.value.map((line) => line.amount)));
const paidCourseAmount = computed(() => isCreditTopUpOnly.value
  ? 0
  : isSharedPrivateLesson.value
    ? roundMoney(participantPaidAmount.value)
    : paymentTotalAmount.value);
const paidAmount = computed(() => isCreditTopUpOnly.value && historicalCreditTopUpAmount.value > 0
  // 历史纯 Credit 收据的付款流水本身包含充值和可能的退款，不能再次用充值金额覆盖净支付额。
  ? paymentTotalAmount.value
  : roundMoney(paidCourseAmount.value + printableCreditTopUpAmount.value));
const refundTotalAmount = computed(() => {
  const lines = isSharedPrivateLesson.value
    ? participantBillings.value.flatMap((billing) => billing.payments)
    : payments.value;
  return roundMoney(lines.reduce((total, payment) => (
    isRefundPayment(payment) ? total + Math.abs(numeric(payment.amount)) : total
  ), 0));
});
const paymentMethodSummary = computed(() => {
  if (isCreditTopUpOnly.value) {
    return creditTopUpPayment.value.paymentMethod
      ? displayPaymentMethod(creditTopUpPayment.value.paymentMethod)
      : 'Not recorded';
  }
  const methodCodes = Array.from(new Set(receiptPaymentRows.value
    .filter((payment) => !isRefundPayment(payment) && payment.amount > 0 && Boolean(payment.paymentMethod))
    .map((payment) => payment.paymentMethod)));
  if (methodCodes.length === 0) {
    return 'Not recorded';
  }
  if (methodCodes.length > 1) {
    return 'Mixed';
  }
  return paymentMethods.value.find((method) => method.methodCode === methodCodes[0])?.methodName ?? methodCodes[0];
});
const printablePaymentDetails = computed(() => {
  const totals = new Map<string, { method: string; amount: number; refund: boolean }>();
  const addPayment = (paymentMethod: string | undefined, amount: number, refund = false) => {
    if (Math.abs(amount) <= 0) return;
    const method = paymentMethod ? displayPaymentMethod(paymentMethod) : 'Not recorded';
    const key = `${refund ? 'refund' : 'payment'}:${method}`;
    const current = totals.get(key);
    totals.set(key, {
      method: refund ? `Refund · ${method}` : method,
      amount: roundMoney((current?.amount ?? 0) + Math.abs(amount)),
      refund,
    });
  };

  // Credit-only receipts keep the top-up payment outside the course payment rows.
  if (!isCreditTopUpOnly.value) {
    receiptPaymentRows.value.forEach((payment) => addPayment(
      payment.paymentMethod,
      numeric(payment.amount),
      isRefundPayment(payment),
    ));
  }
  if (printableCreditTopUpAmount.value > 0) {
    addPayment(printableCreditTopUpPayment.value?.paymentMethod, printableCreditTopUpAmount.value);
  }
  return Array.from(totals, ([key, detail]) => ({ key, ...detail }));
});
const activeParticipantBilling = computed(() => {
  const index = activePrivateParticipantIndex.value;
  return isSharedPrivateLesson.value && index !== null
    ? participantBillings.value[index - 1]
    : null;
});
const activeDiscountLines = computed(() => activeParticipantBilling.value?.discounts ?? discounts.value);
const activePaymentLines = computed(() => {
  const lines = activeParticipantBilling.value?.payments ?? payments.value;
  return lines.filter((payment) => !isRefundPayment(payment));
});
const activeBillingScopeLabel = computed(() => activeParticipantBilling.value
  ? `${privateWorkspaceTabLabel(activeParticipantBilling.value.participantIndex)} only`
  : 'Applied to this receipt');
const activePaidAmount = computed(() => activeParticipantBilling.value
  ? appliedPaymentAmountForLines(activeParticipantBilling.value.payments)
  : paidAmount.value);
const activePaymentMethodSummary = computed(() => {
  const billingPayments = activeParticipantBilling.value?.payments;
  if (!billingPayments) {
    return paymentMethodSummary.value;
  }
  const methodCodes = Array.from(new Set(billingPayments
    .filter((payment) => !isRefundPayment(payment) && payment.amount > 0 && Boolean(payment.paymentMethod))
    .map((payment) => payment.paymentMethod)));
  if (methodCodes.length === 0) {
    return 'Not recorded';
  }
  if (methodCodes.length > 1) {
    return 'Mixed';
  }
  return paymentMethods.value.find((method) => method.methodCode === methodCodes[0])?.methodName ?? methodCodes[0];
});
const paymentParticipantLabel = (payment: PaymentLine) => payment.participantIndex
  ? privateWorkspaceTabLabel(payment.participantIndex)
  : '';
const courseRegistrationAmountDue = computed(() => Math.max(0, roundMoney(
  courseSubtotal.value + serviceFeeTotal.value + courseServiceExtraFeeTotal.value - discountTotal.value,
)));
const amountDue = computed(() => roundMoney(courseRegistrationAmountDue.value + printableCreditTopUpAmount.value));
const balanceAmount = computed(() => roundMoney(amountDue.value - paidAmount.value));
const balanceLabel = computed(() => {
  if (balanceAmount.value < 0) {
    return `Credit ${formatMoney(Math.abs(balanceAmount.value))}`;
  }
  return formatMoney(balanceAmount.value);
});
const hasOutstandingBalance = computed(() => balanceAmount.value > 0);
const isPaidInFull = computed(() => amountDue.value > 0 && balanceAmount.value === 0);
const currentStudentContextId = computed(() => reusableStudent.value?.id ?? historicalPrimaryStudentId.value);
const selectedStudentCreditId = computed(() => reusableStudent.value?.id ?? historicalPrimaryStudentId.value);
const fullStudentName = computed(() => [form.student.firstName, form.student.lastName]
  .map((part) => part.trim())
  .filter(Boolean)
  .join(' '));
const activeStudentName = computed(() => activePrivateParticipant.value?.studentName.trim() || fullStudentName.value);

function splitParticipantName(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return {
    firstName: parts.shift() || '',
    lastName: parts.join(' '),
  };
}

function updateActiveParticipantName(firstName: string, lastName: string) {
  const participant = activePrivateParticipant.value;
  if (participant) {
    participant.studentName = [firstName, lastName].map((part) => part.trim()).filter(Boolean).join(' ');
    return;
  }
  form.student.firstName = firstName;
  form.student.lastName = lastName;
}

const activeStudentFirstName = computed({
  get: () => activePrivateParticipant.value
    ? splitParticipantName(activePrivateParticipant.value.studentName).firstName
    : form.student.firstName,
  set: (value: string) => updateActiveParticipantName(value, activeStudentLastName.value),
});
const activeStudentLastName = computed({
  get: () => activePrivateParticipant.value
    ? splitParticipantName(activePrivateParticipant.value.studentName).lastName
    : form.student.lastName,
  set: (value: string) => updateActiveParticipantName(activeStudentFirstName.value, value),
});

type ActiveStudentStringField = 'grade' | 'school' | 'guardianPhone' | 'guardianPhone2';
function createActiveStudentStringField(field: ActiveStudentStringField) {
  return computed({
    get: () => activePrivateParticipant.value?.[field] ?? form.student[field],
    set: (value: string) => {
      if (activePrivateParticipant.value) {
        activePrivateParticipant.value[field] = value;
      } else {
        form.student[field] = value;
      }
    },
  });
}

const activeStudentGrade = createActiveStudentStringField('grade');
const activeStudentSchool = createActiveStudentStringField('school');
const activeStudentGuardianPhone = createActiveStudentStringField('guardianPhone');
const activeStudentGuardianPhone2 = createActiveStudentStringField('guardianPhone2');
const guardianPhoneFocused = ref(false);
const guardianPhone2Focused = ref(false);
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
  form.pickupFeeCharged = pickupRequired;
  if (!pickupRequired) {
    form.pickupFeeAmount = 0;
  } else if (!isReadOnly.value && form.pickupFeeAmount === 0) {
    form.pickupFeeAmount = selectedCampus.value?.defaultPickupFeeAmount ?? 0;
  }
});

watch(() => form.student.dropoffRequired, (dropoffRequired) => {
  form.dropoffFeeCharged = dropoffRequired;
  if (!dropoffRequired) {
    form.dropoffFeeAmount = 0;
  } else if (!isReadOnly.value && form.dropoffFeeAmount === 0) {
    form.dropoffFeeAmount = selectedCampus.value?.defaultDropoffFeeAmount ?? 0;
  }
});

watch(() => form.campusKey, () => {
  showAllCampusClasses.value = false;
  if (isReadOnly.value && !editingCorrection.value) {
    return;
  }
  if (form.student.pickupRequired) {
    form.pickupFeeAmount = selectedCampus.value?.defaultPickupFeeAmount ?? 0;
  }
  if (form.student.dropoffRequired) {
    form.dropoffFeeAmount = selectedCampus.value?.defaultDropoffFeeAmount ?? 0;
  }
  courseLines.value.forEach((line) => {
    if (!isOneOnOneLine(line) && line.classId
        && !classOptionsForLine(line).some((classGroup) => String(classGroup.id) === String(line.classId))) {
      line.classId = undefined;
    }
  });
});

watch(selectedStudentCreditId, () => {
  void refreshStudentCreditBalance();
});

watch(
  () => route.query.orderId,
  async (orderId, previousOrderId) => {
    if (orderId && orderId !== previousOrderId) {
      await loadHistoricalOrder();
    }
  },
);

watch(
  () => route.query.receiptId,
  async (receiptId, previousReceiptId) => {
    if (route.query.orderId && receiptId !== previousReceiptId) {
      await loadHistoricalOrder();
    }
  },
);

watch(discountableAmount, () => {
  discounts.value.forEach(recalculatePresetDiscount);
});

watch([privateParticipantCount, hasOneOnOneLine], () => {
  if (!hasOneOnOneLine.value) {
    privateParticipantCount.value = 1;
    activePrivateWorkspaceTab.value = 'all';
  }
  ensurePrivateParticipantState();
  if (typeof activePrivateWorkspaceTab.value === 'number'
      && activePrivateWorkspaceTab.value > privateParticipantCount.value) {
    activePrivateWorkspaceTab.value = 'all';
  }
});

onMounted(async () => {
  if (isReadOnly.value) {
    await loadHistoricalOrder();
    void loadCourseCategoryLabels();
  } else if (isRenewalMode.value) {
    await loadInitialData();
    await loadRenewalRegistration();
  } else {
    await loadInitialData();
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
    // 支付方式保持后台配置顺序，Student Credit 固定放到最后；新付款行留空，交由用户主动选择。
    const configuredPaymentMethods = paymentRes.data.data ?? [];
    const studentCreditMethod = configuredPaymentMethods.find((method) => method.methodCode === 'STUDENT_CREDIT') ?? {
      id: 'student-credit',
      methodCode: 'STUDENT_CREDIT',
      methodName: 'Student Credit',
      sortOrder: 999,
      status: 'ACTIVE',
    };
    paymentMethods.value = [
      ...configuredPaymentMethods.filter((method) => method.methodCode !== 'STUDENT_CREDIT'),
      studentCreditMethod,
    ];
    priceOptions.value = optionRes.data.data ?? [];
    courseProducts.value = productRes.data.data.records ?? [];
    classGroups.value = classRes.data.data ?? [];
    discountPresets.value = discountPresetRes.data.data ?? [];
    await loadProductGradeOrders(courseProducts.value);
    await loadStudentsForPicker();
    void loadCourseCategoryLabels();
    // 校区默认留空，交由用户主动选择（提交时 validateForm 校验已拦截未选校区的提交）。
    // 编辑既有订单(loadHistoricalOrder) / 续费(loadRenewalRegistration) / 预览学生(loadExistingStudent) 仍会按数据带出校区。
    creditTopUpPayment.value.paymentMethod = '';
    creditTopUpPayment.value.paidAt = today;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  }
}

async function refreshStudentCreditBalance() {
  const studentId = selectedStudentCreditId.value;
  if (!studentId) {
    studentCreditBalance.value = 0;
    return;
  }
  studentCreditLoading.value = true;
  try {
    studentCreditBalance.value = Number((await fetchStudentCreditBalance(studentId)).data.data ?? 0);
  } catch {
    studentCreditBalance.value = 0;
  } finally {
    studentCreditLoading.value = false;
  }
}

async function loadStudentsForPicker() {
  studentPickerLoading.value = true;
  try {
    const response = await fetchAllStudents();
    studentsForPicker.value = response.data.data ?? [];
  } catch (error) {
    studentsForPicker.value = [];
    ElMessage.warning(`Existing student list unavailable: ${extractErrorMessage(error)}`);
  } finally {
    studentPickerLoading.value = false;
  }
}

async function loadCourseCategoryLabels() {
  try {
    const res = await fetchCourseCategories();
    const map: Record<string, string> = {};
    for (const item of res.data.data ?? []) {
      map[item.typeCode] = item.displayName;
    }
    courseCategoryLabels.value = map;
  } catch {
    courseCategoryLabels.value = {};
  }
}

async function loadProductGradeOrders(products: CourseProductItem[]) {
  const entries = await Promise.all(products.map(async (product) => {
    try {
      const res = await fetchProductGradeConfigs(product.id);
      const orderMap = new Map<string, number>();
      for (const item of res.data.data ?? []) {
        orderMap.set(item.gradeLabel.toLowerCase(), item.displayOrder ?? 99);
      }
      return [String(product.id), orderMap] as const;
    } catch {
      // grade 配置加载失败时不阻塞报名页，后续下拉框使用本地稳定排序兜底。
      return [String(product.id), new Map<string, number>()] as const;
    }
  }));
  productGradeOrderMaps.value = new Map(entries);
}

async function loadHistoricalOrder() {
  const orderId = Array.isArray(route.query.orderId) ? route.query.orderId[0] : route.query.orderId;
  if (!orderId) {
    return;
  }
  try {
    // 只读收据依赖订单快照与校区信息；和大批量下拉数据并行加载，避免等待报名表选项。
    const [orderResponse, campusResponse] = await Promise.all([
      fetchEnrollmentOrder(orderId),
      campuses.value.length > 0 ? Promise.resolve(null) : fetchCampuses(),
    ]);
    if (campusResponse) {
      campuses.value = campusResponse.data.data ?? [];
    }
    historicalOrder.value = orderResponse.data.data;
    applyHistoricalOrder(historicalOrder.value);
    await printHistoricalFormIfRequested(String(orderId));
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  }
}

async function startCorrection() {
  if (!canCorrectReceipt.value) return;
  correctionSetupLoading.value = true;
  try {
    await loadInitialData();
    if (historicalOrder.value) {
      // 基础选项加载完成后重建一次编辑模型，把已停用课程与价格映射到可编辑表单。
      applyHistoricalOrder(historicalOrder.value);
      initializeCorrectionPayments();
    }
    editingCorrection.value = true;
  } finally {
    correctionSetupLoading.value = false;
  }
}

// 编辑收据以本次表单的课程付款为准，历史流水金额只用于填充没有课程分配的旧付款。
function initializeCorrectionPayments() {
  const useCourseAmounts = (payment: PaymentLine) => {
    payment.amount = paymentDraftAmount(payment, courseLines.value.length);
  };
  payments.value.forEach(useCourseAmounts);
  participantBillings.value.forEach((billing) => billing.payments.forEach(useCourseAmounts));
}

function cancelCorrection() {
  editingCorrection.value = false;
  void loadHistoricalOrder();
}

async function saveCorrection() {
  const orderId = Array.isArray(route.query.orderId) ? route.query.orderId[0] : route.query.orderId;
  if (!orderId) {
    return;
  }
  if (!fullStudentName.value.trim()) {
    ElMessage.warning('Student name cannot be empty.');
    return;
  }
  savingCorrection.value = true;
  try {
    await correctEnrollmentOrder(orderId, {
      studentName: fullStudentName.value.trim(),
      grade: form.student.grade,
      studentClass: form.student.studentClass,
      school: form.student.school,
      guardianPhone: form.student.guardianPhone,
      guardianPhone2: form.student.guardianPhone2,
      campusId: selectedCampus.value?.campusId,
      campusName: selectedCampus.value?.campusName,
      campusAddress: selectedCampus.value?.address,
      campusPhone: selectedCampus.value?.phone,
      taxId: selectedCampus.value?.taxId ?? '',
      pickupRequired: form.student.pickupRequired,
      pickupAddress: form.student.pickupAddress,
      dropoffRequired: form.student.dropoffRequired,
      dropoffAddress: form.student.dropoffAddress,
      signupDate: form.signupDate || undefined,
      handledByName: form.handledByName || undefined,
      remark: form.remark || undefined,
      ...buildCorrectionPayload(),
    });
    ElMessage.success('Receipt details updated.');
    editingCorrection.value = false;
    await loadHistoricalOrder();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    savingCorrection.value = false;
  }
}

function buildCorrectionPayload(): EnrollmentOrderCorrectionPayload {
  const payload = buildPayload();
  if (isStandaloneCreditTopUpReceipt.value) {
    // 充值交易已经在 Student Credit 台账中落账。纯充值收据的更正只同步
    // 学生资料和收据展示信息，不能把历史付款行再次作为普通课程付款提交。
    return {
      participants: payload.participants,
      receiptOptions: payload.receiptOptions,
    };
  }
  return {
    pickupFeeCharged: payload.pickupFeeCharged,
    pickupFeeAmount: payload.pickupFeeAmount,
    dropoffFeeCharged: payload.dropoffFeeCharged,
    dropoffFeeAmount: payload.dropoffFeeAmount,
    extraFeeAmount: payload.extraFeeAmount,
    extraFees: payload.extraFees,
    discountAmount: payload.discountAmount,
    discounts: payload.discounts,
    items: payload.items,
    payments: payload.payments,
    participants: payload.participants,
    participantBillings: payload.participantBillings,
    receiptOptions: payload.receiptOptions,
  };
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
    applyStudentProfile(student);
    ElMessage.success(`New registration started for ${student.studentName}. Add the course rows and payment for this registration.`);
  } catch (error) {
    ElMessage.error(`Could not load the renewal student: ${extractErrorMessage(error)}`);
  }
}

function applyStudentProfile(student: StudentItem) {
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
}

async function loadRenewalRegistration() {
  try {
    if (renewPackageId.value) {
      await loadPackageRenewal(renewPackageId.value);
      return;
    }
    if (renewClassEnrollmentId.value) {
      await loadGroupClassRenewal(renewClassEnrollmentId.value);
    }
  } catch (error) {
    ElMessage.error(`Could not prepare renewal registration: ${extractErrorMessage(error)}`);
  }
}

async function loadPackageRenewal(packageId: string) {
  const packageResponse = await fetchCoursePackage(packageId);
  const coursePackage = packageResponse.data.data;
  if (coursePackage.teachingMode !== 'ONE_ON_ONE') {
    throw new Error('This renewal entry is not a 1-on-1 course package.');
  }
  const studentResponse = await fetchStudent(coursePackage.studentId);
  applyStudentProfile(studentResponse.data.data);
  const groupParticipants = coursePackage.privateLessonParticipants ?? [];
  if (groupParticipants.length > 1) {
    privateParticipantCount.value = Math.min(3, groupParticipants.length);
    privateParticipantForms.value = groupParticipants.slice(1, 3).map((participant) => ({
      studentId: participant.studentId,
      studentName: participant.studentName || '',
      grade: participant.grade || '',
      studentClass: '',
      school: participant.school || '',
      guardianPhone: '',
    }));
    privateParticipantPriceSnapshots.value = [];
  } else {
    privateParticipantCount.value = 1;
    privateParticipantForms.value = [];
    privateParticipantPriceSnapshots.value = [];
  }
  const line = createCourseLine();
  line.courseProductId = coursePackage.courseProductId;
  handleCourseProductChange(line);
  const originalOption = coursePackage.coursePriceOptionId ? findOption(coursePackage.coursePriceOptionId) : undefined;
  line.gradeBand = originalOption?.gradeBand
    || coursePackage.gradeBand
    || chooseGradeForProduct(line.courseProductId, form.student.grade);
  const option = originalOption
    ?? optionChoicesForLine(line)[0];
  if (!option) {
    throw new Error('Could not find a matching 1-on-1 package option for this renewal.');
  }
  if (String(option.courseProductId) !== String(coursePackage.courseProductId)) {
    throw new Error('The original package option no longer belongs to this 1-on-1 course product.');
  }
  line.coursePriceOptionId = option.id;
  handleOptionChange(line);
  line.oneOnOneBonusHours = 0;
  line.openingUsedHours = 0;
  handleRegistrationDurationChange(line);
  courseLines.value = [line];
  const participantLabel = groupParticipants.length > 1
    ? ` for ${groupParticipants.map((participant) => participant.studentName || 'student').join(' / ')}`
    : ` for ${coursePackage.studentName}`;
  ElMessage.success(`Renewal started${participantLabel}. Confirm the package hours and payment.`);
}

async function loadGroupClassRenewal(enrollmentId: string) {
  const classId = getQueryValue(route.query.classId);
  if (!classId) {
    throw new Error('Class ID is required for group class renewal.');
  }
  const classGroup = classGroups.value.find((item) => String(item.id) === String(classId));
  if (!classGroup) {
    throw new Error('Class group is not active or cannot be loaded.');
  }
  const studentId = getQueryValue(route.query.studentId);
  if (!studentId) {
    throw new Error('Student ID is required for group class renewal.');
  }
  const [studentResponse, studentsResponse] = await Promise.all([
    fetchStudent(studentId),
    fetchGroupClassStudentProgress(classId),
  ]);
  applyStudentProfile(studentResponse.data.data);
  const enrollment = studentsResponse.data.data.find((item) => String(item.enrollmentId) === String(enrollmentId));
  const line = createCourseLine();
  line.courseProductId = classGroup.productId ?? stringToEntityId(getQueryValue(route.query.productId));
  handleCourseProductChange(line);
  line.gradeBand = chooseGradeForProduct(line.courseProductId, form.student.grade);
  const option = optionChoicesForLine(line).find((item) => String(item.classId) === String(classGroup.id))
    ?? optionChoicesForLine(line)[0];
  if (option) {
    line.coursePriceOptionId = option.id;
    handleOptionChange(line);
  }
  line.classId = classGroup.id;
  line.className = classGroup.className;
  line.startDate = nextRenewalStartDate(enrollment?.expiryDate);
  handleRegistrationDurationChange(line);
  courseLines.value = [line];
  ElMessage.success(`Group class renewal started for ${studentResponse.data.data.studentName}. Select the package length and payment.`);
}

function applyHistoricalOrder(order: EnrollmentOrderDetailItem) {
  const requestedReceiptId = Array.isArray(route.query.receiptId)
    ? route.query.receiptId[0]
    : route.query.receiptId;
  const selectedReceipt = requestedReceiptId
    ? order.receipts.find(receipt => String(receipt.id) === String(requestedReceiptId))
    : undefined;
  const registrationReceipt = selectedReceipt
    ?? order.receipts.find(receipt => receipt.templateCode === 'DEFAULT_RECEIPT');
  const isStandaloneReceipt = registrationReceipt?.templateCode !== undefined
    && registrationReceipt.templateCode !== 'DEFAULT_RECEIPT';
  const receiptPayload = parsePayload(registrationReceipt?.receiptPayload);
  const campusId = getString(receiptPayload, 'campusId') || String(order.campusId ?? '');
  const campusName = getString(receiptPayload, 'campusName') || order.campusNameSnapshot || '';
  form.campusKey = resolveCampusKey(campusId, campusName);
  form.signupDate = normalizeDate(getString(receiptPayload, 'signupDate') || order.signupDate || '');
  form.handledByName = getString(receiptPayload, 'handledByName') || order.handledByName || '';
  form.pickupFeeAmount = firstPositiveNumber(getNumber(receiptPayload, 'pickupFeeAmount'), order.pickupFeeAmount);
  form.pickupFeeCharged = getBoolean(receiptPayload, 'pickupFeeCharged', form.pickupFeeAmount > 0);
  form.dropoffFeeAmount = firstPositiveNumber(
    getNumber(receiptPayload, 'dropoffFeeAmount'),
    order.dropoffFeeAmount,
  );
  form.dropoffFeeCharged = getBoolean(receiptPayload, 'dropoffFeeCharged', form.dropoffFeeAmount > 0);
  form.remark = order.remark ?? '';
  historicalPrimaryStudentId.value = order.studentId;
  setStudentName(getString(receiptPayload, 'studentName') || order.studentNameSnapshot || order.studentName || '');
  form.student.grade = getString(receiptPayload, 'grade') || order.gradeSnapshot || '';
  form.student.studentClass = getString(receiptPayload, 'studentClass') || order.studentClassSnapshot || '';
  form.student.school = getString(receiptPayload, 'school') || order.schoolSnapshot || '';
  form.student.guardianName = '';
  form.student.guardianPhone = getString(receiptPayload, 'guardianPhone') || order.parentPhoneSnapshot || '';
  form.student.guardianPhone2 = getString(receiptPayload, 'guardianPhone2') || order.parentPhone2Snapshot || '';
  form.student.guardianEmail = '';
  form.student.pickupRequired = getBoolean(receiptPayload, 'pickupRequired', order.pickupRequiredSnapshot);
  form.student.pickupAddress = getString(receiptPayload, 'pickupAddress') || order.pickupAddressSnapshot || '';
  form.student.dropoffRequired = getBoolean(receiptPayload, 'dropoffRequired', order.dropoffRequiredSnapshot);
  form.student.dropoffAddress = getString(receiptPayload, 'dropoffAddress') || order.dropoffAddressSnapshot || '';
  const historicalParticipants: Record<string, unknown>[] = order.participants?.length
    ? order.participants.map((participant) => ({
      studentId: participant.studentId,
      studentNo: participant.studentNo,
      participantIndex: participant.participantIndex,
      studentName: participant.studentName,
      grade: participant.grade,
      school: participant.school,
      studentClass: participant.studentClass,
      guardianName: participant.guardianName,
      guardianPhone: participant.guardianPhone,
      guardianPhone2: participant.guardianPhone2,
      guardianEmail: participant.guardianEmail,
      pickupRequired: participant.pickupRequired,
      pickupAddress: participant.pickupAddress,
      dropoffRequired: participant.dropoffRequired,
      dropoffAddress: participant.dropoffAddress,
      priceAmount: participant.grossAmount,
      grossAmount: participant.grossAmount,
      discountAmount: participant.discountAmount,
      amountDue: participant.amountDue,
      paidAmount: participant.paidAmount,
      balanceAmount: participant.balanceAmount,
      discounts: parseMoneyDetail(participant.discountDetail),
    }))
    : getArray(receiptPayload, 'participants');
  privateParticipantCount.value = Math.min(3, Math.max(1, historicalParticipants.length || 1));
  privateParticipantPriceSnapshots.value = historicalParticipants.slice(0, 3).map((item) =>
    item.priceAmount == null ? undefined : getNumber(item, 'priceAmount'));
  privateParticipantForms.value = historicalParticipants.slice(1, 3).map((item) => ({
    studentId: item.studentId == null ? undefined : String(item.studentId),
    studentName: getString(item, 'studentName'),
    grade: getString(item, 'grade'),
    studentClass: getString(item, 'studentClass'),
    school: getString(item, 'school'),
    guardianName: getString(item, 'guardianName'),
    guardianPhone: getString(item, 'guardianPhone'),
    guardianPhone2: getString(item, 'guardianPhone2'),
    guardianEmail: getString(item, 'guardianEmail'),
    pickupRequired: getBoolean(item, 'pickupRequired', false),
    pickupAddress: getString(item, 'pickupAddress'),
    dropoffRequired: getBoolean(item, 'dropoffRequired', false),
    dropoffAddress: getString(item, 'dropoffAddress'),
  }));
  participantBillings.value = historicalParticipants.slice(0, 3).map((item, index) => {
    const discounts = getMoneyLines(item, 'discounts');
    const paymentsForParticipant = (order.payments ?? [])
      .filter((payment) =>
        Number(payment.participantIndex) === index + 1
        || (index === 0 && (payment.participantIndex == null || payment.participantIndex === '')))
      .map((payment) => ({
        rowKey: `payment-${payment.id}`,
        paymentMethod: payment.paymentMethod,
        amount: numeric(payment.amount),
        paidAt: normalizeDate(payment.paidAt),
        isHistorical: true,
        courseAllocations: Object.fromEntries((payment.allocations ?? []).map((allocation) => [
          String(allocation.enrollmentOrderItemId), numeric(allocation.amount),
        ])),
        participantIndex: index + 1,
        handledByName: payment.handledByName,
        remark: payment.remark,
        paymentType: payment.paymentType,
      }));
    return {
      participantIndex: index + 1,
      discounts: getArray(item, 'discounts').length > 0
        ? getArray(item, 'discounts').map((discount) => {
          const line = createDiscountLine(
            getString(discount, 'label') || 'Discount',
            getNumber(discount, 'amount'),
            '',
          );
          line.courseLineNo = getNumber(discount, 'orderItemLineNo') || undefined;
          return line;
        })
        : discounts.length > 0
          ? discounts.map((discount) => createDiscountLine(discount.label, discount.amount, ''))
          : [createDiscountLine('Custom discount', getNumber(item, 'discountAmount'), '')],
      payments: paymentsForParticipant.length > 0 ? paymentsForParticipant : [createPaymentLine()],
    };
  });
  ensurePrivateParticipantState();

  const payloadItems = getArray(receiptPayload, 'items');
  const historicalTopUpAmount = getNumber(receiptPayload, 'creditTopUpAmount');
  const historicalTopUpOnly = historicalTopUpAmount > 0 && payloadItems.length === 0 && order.items.length === 0;
  const historicalTopUpPayment = isRecord(receiptPayload.creditTopUpPayment)
    ? receiptPayload.creditTopUpPayment
    : null;
  historicalCreditTopUpAmount.value = historicalTopUpAmount;
  historicalCreditTopUpPayment.value = historicalTopUpPayment
    ? {
      ...createPaymentLine(),
      isCreditTopUp: true,
      paymentMethod: getString(historicalTopUpPayment, 'paymentMethod'),
      amount: historicalTopUpAmount,
      paidAt: normalizeDate(getString(historicalTopUpPayment, 'paidAt'))
        || normalizeDate(getString(receiptPayload, 'signupDate')),
      handledByName: getString(historicalTopUpPayment, 'handledByName'),
      remark: getString(historicalTopUpPayment, 'remark'),
    }
    : null;
  // 混合报名的 Credit 已落在独立充值订单中，课程收据编辑页不重复展示不可编辑的充值付款。
  creditTopUpAmount.value = historicalTopUpOnly ? historicalTopUpAmount : 0;
  courseLines.value = (payloadItems.length > 0 ? payloadItems : order.items)
    .map((item, index) => toHistoricalCourseLine(item, index, findOrderItemFallback(item, order.items, index)));
  normalizeHistoricalLineReferences();
  mapHistoricalParticipantBillingCourseReferences();
  // 历史报名单（收据无 items、订单无明细行时）用订单课包兜底还原课程行，
  // 课程名取课包快照，避免依赖已删除的 course_product 联表。
  if (courseLines.value.length === 0 && !historicalTopUpOnly) {
    courseLines.value = buildCourseLinesFromPackages(order.packages);
  }
  if (courseLines.value.length === 0 && !historicalTopUpOnly) {
    courseLines.value = [createCourseLine()];
  }

  const historicalExtraFees = getMoneyLines(receiptPayload, 'extraFees');
  const savedPickupFee = historicalExtraFees.find((fee) => fee.label === 'Pickup service');
  const savedDropoffFee = historicalExtraFees.find((fee) => fee.label === 'Drop-off service');
  if (form.pickupFeeAmount === 0 && savedPickupFee) {
    form.pickupFeeAmount = savedPickupFee.amount;
    form.pickupFeeCharged = true;
  }
  // 早期收据只有服务费金额，没有同步保存 pickupRequired 标记；金额本身就是历史上
  // 已收费的事实，编辑时要把它恢复成当前按课程编辑器可见的接送需求。
  if (!form.student.pickupRequired && form.pickupFeeAmount > 0) {
    form.student.pickupRequired = true;
  }
  if (form.dropoffFeeAmount === 0 && savedDropoffFee) {
    form.dropoffFeeAmount = savedDropoffFee.amount;
    form.dropoffFeeCharged = true;
  }
  if (!form.student.dropoffRequired && form.dropoffFeeAmount > 0) {
    form.student.dropoffRequired = true;
  }
  applyHistoricalRegistrationTransportFallback();
  extraFees.value = historicalExtraFees.filter((fee) => !['Pickup service', 'Drop-off service'].includes(fee.label));
  if (extraFees.value.length === 0) {
    extraFees.value = [createMoneyLineWithAmount('Other fee', firstPositiveNumber(getNumber(receiptPayload, 'extraFeeAmount'), order.extraFeeAmount))];
  }
  discounts.value = historicalDiscountLines(receiptPayload, order);
  if (discounts.value.length === 0) {
    discounts.value = [createDiscountLine('Custom discount')];
  }

  // 参与人账单需要等课程行完成历史兜底后再关联一次，否则旧订单没有 items 时
  // 会因为当时没有 rowKey 而丢失课程级折扣和付款分摊。
  mapHistoricalParticipantBillingCourseReferences();

  // 报名收据使用订单实时账本；退款/付款收据必须展示被点击凭证自身的快照流水。
  const livePayments = (order.payments ?? [])
    .filter((payment) => numeric(payment.amount) !== 0 && payment.paymentStatus !== 'VOIDED')
    .slice()
    .sort((left, right) => String(left.paidAt ?? '').localeCompare(String(right.paidAt ?? ''))
      || String(left.id).localeCompare(String(right.id), undefined, { numeric: true }))
    .map((payment) => {
      const courseAllocations = Object.fromEntries((payment.allocations ?? []).flatMap((allocation) => {
        const course = courseLines.value.find((line) => String(line.orderItemId) === String(allocation.enrollmentOrderItemId));
        return course ? [[course.rowKey, numeric(allocation.amount)]] : [];
      }));
      const allocatedCourses = courseLines.value.filter((line) => numeric(courseAllocations[line.rowKey]) > 0);
      const onlyCourseAllocation = allocatedCourses[0];
      const allocationMatchesPayment = onlyCourseAllocation
        && Math.abs(roundMoney(numeric(payment.amount) - numeric(courseAllocations[onlyCourseAllocation.rowKey]))) <= 0.01;
      return {
        rowKey: `payment-${payment.id}`,
        paymentMethod: payment.paymentMethod,
        amount: numeric(payment.amount),
        paidAt: normalizeDate(payment.paidAt),
        isHistorical: true,
        courseRowKey: allocatedCourses.length === 1 && allocationMatchesPayment ? onlyCourseAllocation?.rowKey : undefined,
        participantIndex: payment.participantIndex,
        handledByName: payment.handledByName,
        remark: payment.remark,
        paymentType: payment.paymentType,
        courseAllocations,
      };
  });
  const savedPayments = getHistoricalPayments(receiptPayload);
  payments.value = isStandaloneReceipt
    ? savedPayments
    : livePayments.length > 0 ? livePayments : savedPayments;
  if (historicalTopUpOnly) {
    const topUpPayment = payments.value.find((payment) => payment.amount > 0);
    creditTopUpPayment.value = {
      ...(topUpPayment ?? createPaymentLine()),
      isCreditTopUp: true,
      paymentMethod: topUpPayment?.paymentMethod ?? '',
      paidAt: topUpPayment?.paidAt ?? normalizeDate(getString(receiptPayload, 'signupDate')),
    };
    historicalCreditTopUpPayment.value ??= creditTopUpPayment.value;
  }
  if (payments.value.length === 0) {
    payments.value = [createPaymentLine(courseLines.value[0]?.rowKey)];
  }

  showTaxIdOnForm.value = getBoolean(receiptPayload, 'showTaxId', false);
  receiptTaxId.value = getString(receiptPayload, 'taxId');
  validationErrors.value = [];
  studentReuseStatus.value = 'idle';
  reusableStudent.value = null;
}

/**
 * 兼容早期只保存整单接送费的收据：编辑时把整单快照迁移到课程行，
 * 保证现行按课程编辑器能看到原来的地址和金额，同时不重复计费。
 */
function applyHistoricalRegistrationTransportFallback() {
  const line = courseLines.value.find((candidate) => candidate.pickupRequired || numeric(candidate.pickupFeeAmount) > 0)
    ?? courseLines.value[0];
  const dropoffLine = courseLines.value.find((candidate) => candidate.dropoffRequired || numeric(candidate.dropoffFeeAmount) > 0)
    ?? courseLines.value[0];
  if (!line) return;

  if (form.student.pickupRequired) {
    line.pickupRequired = true;
    line.pickupAddress ||= form.student.pickupAddress;
    if (numeric(line.pickupFeeAmount) <= 0) {
      line.pickupFeeAmount = roundMoney(form.pickupFeeAmount);
    }
    line.pickupFeeCharged = true;
  }
  if (dropoffLine && form.student.dropoffRequired) {
    dropoffLine.dropoffRequired = true;
    dropoffLine.dropoffAddress ||= form.student.dropoffAddress;
    if (numeric(dropoffLine.dropoffFeeAmount) <= 0) {
      dropoffLine.dropoffFeeAmount = roundMoney(form.dropoffFeeAmount);
    }
    dropoffLine.dropoffFeeCharged = true;
  }
}

/**
 * 历史收据里的 ID 来自 JSON，通常会被解析成字符串；Element Plus 下拉选项使用
 * 基础数据里的 number/EntityId。统一回填成当前选项的原始 ID，避免下拉框把 ID 当作显示文本。
 */
function normalizeHistoricalLineReferences() {
  courseLines.value.forEach((line) => {
    let product = courseProducts.value.find((item) => String(item.id) === String(line.courseProductId));
    if (!product && line.courseProductId) {
      product = {
        id: line.courseProductId,
        productCode: `HISTORICAL-${line.courseProductId}`,
        productName: line.courseProductName || line.detail || 'Historical course',
        courseType: line.courseType,
        registerClass: line.registerClass,
        teachingMode: line.courseType === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'GROUP',
        status: 'ACTIVE',
        sortOrder: 999,
      };
      courseProducts.value = [...courseProducts.value, product];
    }
    if (product) {
      line.courseProductId = product.id;
      line.courseProductName = product.productName;
    }
    let option = priceOptions.value.find((item) => String(item.id) === String(line.coursePriceOptionId));
    if (!option && line.coursePriceOptionId) {
      // 已停用/历史价格选项不在 active 列表中时，保留一条只读历史选项，避免下拉框显示原始 ID。
      option = {
        id: line.coursePriceOptionId,
        optionCode: `HISTORICAL-${line.coursePriceOptionId}`,
        courseType: line.courseType || 'GROUP',
        registerClass: line.registerClass || '',
        gradeBand: line.gradeBand,
        periodLabel: line.periodLabel,
        durationLabel: line.durationLabel || (line.packageQuantity ? `${line.packageQuantity} ${line.packageUnit ?? ''}`.trim() : ''),
        timeSlotLabel: line.timeSlotLabel,
        detailText: line.detail || 'Historical package',
        packageUnit: line.packageUnit,
        packageQuantity: line.packageQuantity,
        totalPrice: line.priceAmount,
        priceAmount: line.priceAmount,
        currencyCode: 'USD',
        courseProductId: line.courseProductId,
        actualClasses: line.classId ? [{
          id: line.classId,
          className: line.className || line.classId.toString(),
          actualClass: line.gradeBand,
        }] : [],
        sortOrder: 999,
        status: 'ACTIVE',
      } as CoursePriceOptionItem;
      priceOptions.value = [...priceOptions.value, option];
    }
    if (option) {
      line.coursePriceOptionId = option.id;
      line.courseProductId = product?.id ?? option.courseProductId ?? line.courseProductId;
      line.courseProductName = product?.productName ?? line.courseProductName;
    }
    const classGroup = classGroups.value.find((item) => String(item.id) === String(line.classId));
    if (classGroup) {
      line.classId = classGroup.id;
      line.className = classGroup.className;
    }
  });
}

/** 将历史账单里的行号/订单项 ID 重新关联到当前课程行 key，供同一表单继续编辑。 */
function mapHistoricalParticipantBillingCourseReferences() {
  for (const billing of participantBillings.value) {
    for (const discount of billing.discounts) {
      const line = discount.courseLineNo
        ? courseLines.value[discount.courseLineNo - 1]
        : courseLines.value.length === 1 ? courseLines.value[0] : undefined;
      if (line) {
        discount.courseRowKey = line.rowKey;
      }
      delete discount.courseLineNo;
    }
    for (const payment of billing.payments) {
      const savedAllocations = payment.courseAllocations;
      const mappedAllocations: Record<string, number> = {};
      courseLines.value.forEach((line) => {
        if (!line.orderItemId) return;
        const amount = numeric(savedAllocations[String(line.orderItemId)]);
        if (amount > 0) {
          mappedAllocations[line.rowKey] = amount;
        }
      });
      if (courseLines.value.length === 1 && Object.keys(mappedAllocations).length === 0 && payment.amount > 0) {
        mappedAllocations[courseLines.value[0].rowKey] = payment.amount;
      }
      payment.courseAllocations = mappedAllocations;
      if (courseLines.value.length === 1) {
        payment.courseRowKey = courseLines.value[0].rowKey;
      }
    }
  }
}

function toHistoricalCourseLine(
  item: Record<string, unknown> | EnrollmentOrderDetailItem['items'][number],
  index: number,
  fallback?: EnrollmentOrderDetailItem['items'][number],
): RegistrationCourseLine {
  const periodLabel = getString(item, 'periodLabel') || getString(fallback, 'periodLabel');
  const pickupFeeAmount = firstPositiveNumber(getNumber(item, 'pickupFeeAmount'), getNumber(fallback, 'pickupFeeAmount'));
  const dropoffFeeAmount = firstPositiveNumber(getNumber(item, 'dropoffFeeAmount'), getNumber(fallback, 'dropoffFeeAmount'));
  const pickupRequired = getBoolean(
    item,
    'pickupRequiredSnapshot',
    getBoolean(item, 'pickupRequired', getBoolean(fallback, 'pickupRequiredSnapshot', getBoolean(fallback, 'pickupRequired', false))),
  ) || pickupFeeAmount > 0;
  const dropoffRequired = getBoolean(
    item,
    'dropoffRequiredSnapshot',
    getBoolean(item, 'dropoffRequired', getBoolean(fallback, 'dropoffRequiredSnapshot', getBoolean(fallback, 'dropoffRequired', false))),
  ) || dropoffFeeAmount > 0;
  const itemServiceFees = getMoneyLines(item, 'extraFees');
  const fallbackServiceFees = parseMoneyDetail(getString(fallback, 'extraFeeDetail'))
    .filter(isRecord)
    .map((fee, feeIndex) => ({
      rowKey: `history-fee-${index}-${feeIndex}`,
      label: getString(fee, 'label') || 'Other fee',
      amount: getNumber(fee, 'amount'),
    }))
    .filter((fee) => fee.amount > 0);
  return {
    rowKey: `history-course-${getString(item, 'id') || getString(item, 'orderItemId') || getString(fallback, 'id') || index}`,
    // 订单明细是业务主数据；收据 payload 是历史快照，内部 JSON 的 Snowflake ID
    // 可能已经被原生 JSON.parse 转成不精确的 number，因此 ID 优先取订单明细。
    orderItemId: getString(fallback, 'id') || getString(item, 'orderItemId') || getString(item, 'id') || undefined,
    courseType: getString(item, 'courseType') || getString(fallback, 'courseType'),
    gradeBand: getString(item, 'gradeBand') || getString(fallback, 'gradeBand'),
    periodKey: `${periodLabel || 'General'}::Standard`,
    coursePriceOptionId: (getString(fallback, 'coursePriceOptionId') || getString(item, 'coursePriceOptionId')) as EntityId | '',
    courseProductId: getString(fallback, 'courseProductId') || getString(item, 'courseProductId') || undefined,
    courseProductName: getString(item, 'courseProductName') || undefined,
    classId: getString(fallback, 'classId') || getString(item, 'classId') || undefined,
    className: getString(item, 'className') || undefined,
    teacherId: getString(fallback, 'teacherId') || getString(item, 'teacherId') || undefined,
    registerClass: getString(item, 'registerClass') || getString(fallback, 'registerClass'),
    detail: getString(item, 'detail') || getString(fallback, 'detail'),
    periodLabel,
    durationLabel: '',
    timeSlotLabel: getString(item, 'timeSlotLabel') || getString(fallback, 'timeSlotLabel'),
    startDate: normalizeDate(getString(item, 'startDate') || getString(fallback, 'startDate')) || undefined,
    packageUnit: getString(item, 'packageUnit') || getString(fallback, 'packageUnit') || undefined,
    packageQuantity: optionalNumber(item, 'packageQuantity') ?? optionalNumber(fallback, 'packageQuantity'),
    packageTotalPrice: optionalNumber(item, 'packageTotalPrice') ?? optionalNumber(fallback, 'packageTotalPrice'),
    attendanceOption: (getString(item, 'attendanceOption') || getString(fallback, 'attendanceOption') || 'NONE') as RegistrationCourseLine['attendanceOption'],
    attendanceScheduleJson: getString(item, 'attendanceScheduleJson') || getString(fallback, 'attendanceScheduleJson') || undefined,
    priceAmount: firstPositiveNumber(getNumber(item, 'priceAmount'), getNumber(fallback, 'priceAmount')),
    originalPriceAmount: optionalNumber(item, 'originalPriceAmount') ?? optionalNumber(fallback, 'originalPriceAmount'),
    manualPriceOverride: getBoolean(item, 'manualPriceOverride', false),
    oneOnOneHours: optionalNumber(item, 'oneOnOneHours') ?? optionalNumber(fallback, 'oneOnOneHours'),
    oneOnOneBonusHours: optionalNumber(item, 'oneOnOneBonusHours') ?? optionalNumber(fallback, 'oneOnOneBonusHours'),
    openingUsedHours: optionalNumber(item, 'openingUsedHours') ?? optionalNumber(fallback, 'openingUsedHours') ?? 0,
    oneOnOneUnits: optionalNumber(item, 'oneOnOneUnits') ?? optionalNumber(fallback, 'oneOnOneUnits'),
    groupBillingType: getString(item, 'groupBillingType') || undefined,
    groupSessionCount: optionalNumber(item, 'sessions')
      ?? optionalNumber(fallback, 'sessions')
      ?? ((getString(item, 'packageUnit') || getString(fallback, 'packageUnit'))?.toUpperCase() === 'SESSION'
        ? optionalNumber(item, 'packageQuantity') ?? optionalNumber(fallback, 'packageQuantity')
        : undefined),
    groupPurchasedMonths: optionalNumber(item, 'purchasedMonths') ?? groupMonthCountFromSnapshot(item),
    groupDurationMode: durationModeForMonths(optionalNumber(item, 'purchasedMonths') ?? groupMonthCountFromSnapshot(item)),
    groupStartMonth: getString(item, 'groupStartMonth') || getString(item, 'startMonth') || undefined,
    groupEndMonth: getString(item, 'groupEndMonth') || getString(item, 'endMonth') || undefined,
    groupRegistrationStartMonth: getString(item, 'startMonth') || undefined,
    groupRegistrationEndMonth: getString(item, 'endMonth') || undefined,
    pickupRequired,
    pickupAddress: getString(item, 'pickupAddressSnapshot')
      || getString(item, 'pickupAddress')
      || getString(fallback, 'pickupAddressSnapshot')
      || getString(fallback, 'pickupAddress'),
    dropoffRequired,
    dropoffAddress: getString(item, 'dropoffAddressSnapshot')
      || getString(item, 'dropoffAddress')
      || getString(fallback, 'dropoffAddressSnapshot')
      || getString(fallback, 'dropoffAddress'),
    pickupFeeCharged: pickupRequired,
    pickupFeeAmount,
    dropoffFeeCharged: dropoffRequired,
    dropoffFeeAmount,
    serviceFees: itemServiceFees.length > 0 ? itemServiceFees : fallbackServiceFees,
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

/**
 * 从订单课包还原课程行。用于历史报名单——收据 payload 无 items 数组、订单也无明细行
 * （enrollment_order_item）时的兜底。课程名优先取 courseProductName 快照，
 * 该字段来自 course_product 联表（产品被删后为 null），故回退到 package_name 快照。
 */
function buildCourseLinesFromPackages(packages: CoursePackageItem[]): RegistrationCourseLine[] {
  return (packages ?? []).map((pkg) => {
    const line = createCourseLine();
    line.courseType = pkg.teachingMode || '';
    line.courseProductId = pkg.courseProductId;
    line.courseProductName = pkg.courseProductName || pkg.packageName;
    line.detail = pkg.packageName;
    line.packageQuantity = pkg.totalUnits;
    line.oneOnOneUnits = pkg.teachingMode === 'ONE_ON_ONE' ? pkg.totalUnits : undefined;
    line.priceAmount = 0;
    line.startDate = pkg.startDate || undefined;
    return line;
  });
}

async function checkReusableStudent() {
  // 下拉框选中的学生已经由 studentId 标识。姓名或电话的修改只更新该学生资料，
  // 不能因为重新执行模糊匹配没有找到新姓名就丢失原来的身份。
  if (reusableStudent.value) {
    studentReuseStatus.value = 'existing';
    return;
  }
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
    // 查询返回前用户可能已经从下拉框选中了学生，保留这次明确选择。
    if (reusableStudent.value) {
      studentReuseStatus.value = 'existing';
      return;
    }
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
    startDate: undefined,
    packageUnit: undefined,
    packageQuantity: undefined,
    packageTotalPrice: undefined,
    attendanceOption: 'NONE',
    attendanceScheduleJson: undefined,
    priceAmount: 0,
    manualPriceOverride: false,
    oneOnOneBonusHours: 0,
    openingUsedHours: 0,
    groupBillingType: undefined,
    groupTotalSessions: undefined,
    groupStartMonth: undefined,
    groupEndMonth: undefined,
    groupSessionCount: undefined,
    groupDurationMode: '2',
    groupPurchasedMonths: 2,
    groupRegistrationStartMonth: undefined,
    groupRegistrationEndMonth: undefined,
    pickupRequired: false,
    pickupAddress: '',
    dropoffRequired: false,
    dropoffAddress: '',
    pickupFeeCharged: false,
    pickupFeeAmount: 0,
    dropoffFeeCharged: false,
    dropoffFeeAmount: 0,
    serviceFees: [],
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

function createDiscountLine(label = 'Custom discount', amount = 0, courseRowKey = courseLines.value[0]?.rowKey ?? ''): DiscountLine {
  return {
    rowKey: `discount-${++rowSeed}`,
    presetId: '__custom__',
    label,
    amount: roundMoney(amount),
    courseRowKey,
  };
}

function createPaymentLine(courseRowKey?: string): PaymentLine {
  return {
    rowKey: `payment-${++rowSeed}`,
    paymentMethod: '',
    amount: 0,
    paidAt: today,
    courseRowKey,
    remark: '',
    courseAllocations: courseRowKey ? { [courseRowKey]: 0 } : {},
  };
}

function addCourseLine() {
  courseLines.value.push(createCourseLine());
}

function clearOrRemoveCourseLine(index: number) {
  if (courseLines.value.length > 1) {
    removeCourseLine(index);
    return;
  }
  const line = courseLines.value[index];
  if (!line) return;
  const hasDiscount = discounts.value.some((discount) =>
    discount.courseRowKey === line.rowKey && numeric(discount.amount) > 0);
  const hasPayment = payments.value.some((payment) =>
    numeric(payment.courseAllocations?.[line.rowKey]) > 0 ||
    (payment.courseRowKey === line.rowKey && numeric(payment.amount) > 0));
  const hasParticipantBilling = participantBillings.value.some((billing) =>
    billing.discounts.some((discount) => discount.courseRowKey === line.rowKey && numeric(discount.amount) > 0)
    || billing.payments.some((payment) =>
      numeric(payment.courseAllocations?.[line.rowKey]) > 0
      || (payment.courseRowKey === line.rowKey && numeric(payment.amount) > 0)));
  if (hasDiscount || hasPayment || hasParticipantBilling) {
    ElMessage.warning('Move this course’s discount and payment allocations before clearing it.');
    return;
  }

  const rowKey = line.rowKey;
  Object.assign(line, createCourseLine(), { rowKey });
  discounts.value.forEach((discount) => {
    if (discount.courseRowKey === rowKey) {
      discount.presetId = '__custom__';
      discount.label = 'Custom discount';
      discount.amount = 0;
    }
  });
  payments.value.forEach((payment) => {
    if (payment.courseRowKey === rowKey || payment.courseAllocations?.[rowKey] !== undefined) {
      payment.amount = 0;
      payment.courseRowKey = rowKey;
      payment.courseAllocations = { [rowKey]: 0 };
      clearPaymentLimitHint(payment);
    }
  });
  participantBillings.value.forEach((billing) => {
    billing.discounts.forEach((discount) => {
      if (discount.courseRowKey === rowKey) {
        discount.presetId = '__custom__';
        discount.label = 'Custom discount';
        discount.amount = 0;
      }
    });
    billing.payments.forEach((payment) => {
      if (payment.courseRowKey === rowKey || payment.courseAllocations?.[rowKey] !== undefined) {
        payment.amount = 0;
        payment.courseRowKey = rowKey;
        payment.courseAllocations = { [rowKey]: 0 };
        clearPaymentLimitHint(payment);
      }
    });
  });
}

function removeCourseLine(index: number) {
  if (courseLines.value.length <= 1) {
    return;
  }
  const line = courseLines.value[index];
  if (!line) return;
  const hasDiscount = discounts.value.some((discount) =>
    discount.courseRowKey === line.rowKey && numeric(discount.amount) > 0);
  const hasPayment = payments.value.some((payment) =>
    numeric(payment.courseAllocations?.[line.rowKey]) > 0);
  const hasParticipantBilling = participantBillings.value.some((billing) =>
    billing.discounts.some((discount) => discount.courseRowKey === line.rowKey && numeric(discount.amount) > 0)
    || billing.payments.some((payment) => numeric(payment.courseAllocations?.[line.rowKey]) > 0));
  if (hasDiscount || hasPayment || hasParticipantBilling) {
    ElMessage.warning('Move this course’s discount and payment allocations before removing it.');
    return;
  }
  const nextCourse = courseLines.value.find((candidate) => candidate.rowKey !== line.rowKey);
  payments.value.forEach((payment) => {
    if (payment.courseRowKey === line.rowKey && nextCourse) {
      delete payment.courseAllocations[line.rowKey];
      payment.courseRowKey = nextCourse.rowKey;
      payment.courseAllocations[nextCourse.rowKey] = 0;
      return;
    }
    delete payment.courseAllocations[line.rowKey];
  });
  participantBillings.value.forEach((billing) => {
    billing.discounts.forEach((discount) => {
      if (discount.courseRowKey === line.rowKey && nextCourse) {
        discount.courseRowKey = nextCourse.rowKey;
      }
    });
    billing.payments.forEach((payment) => {
      const amount = numeric(payment.courseAllocations[line.rowKey]);
      delete payment.courseAllocations[line.rowKey];
      if (payment.courseRowKey === line.rowKey && nextCourse) {
        payment.courseRowKey = nextCourse.rowKey;
        payment.courseAllocations[nextCourse.rowKey] = amount;
      }
    });
  });
  courseLines.value.splice(index, 1);
}

function addExtraFee() {
  extraFees.value.push(createMoneyLine(''));
}

function addCourseServiceFee(line: RegistrationCourseLine) {
  line.serviceFees.push(createMoneyLine(''));
}

function removeCourseServiceFee(line: RegistrationCourseLine, index: number) {
  if (line.serviceFees.length > 1) {
    line.serviceFees.splice(index, 1);
  }
}

function removeExtraFee(index: number) {
  if (extraFees.value.length > 1) {
    extraFees.value.splice(index, 1);
  }
}

function addDiscount(courseRowKey?: string) {
  discounts.value.push(createDiscountLine('Custom discount', 0, courseRowKey));
}

function removeDiscount(index: number) {
  if (discounts.value[index]) {
    discounts.value.splice(index, 1);
  }
}

function addActiveDiscount(courseRowKey?: string) {
  const participantIndex = activeParticipantBilling.value?.participantIndex;
  if (participantIndex) {
    addParticipantDiscount(participantIndex, courseRowKey);
    return;
  }
  addDiscount(courseRowKey);
}

function removeActiveDiscount(discount: DiscountLine) {
  const lineIndex = activeDiscountLines.value.indexOf(discount);
  if (lineIndex < 0) return;
  const participantIndex = activeParticipantBilling.value?.participantIndex;
  if (participantIndex) {
    removeParticipantDiscount(participantIndex, lineIndex);
    return;
  }
  removeDiscount(lineIndex);
}

function isCustomDiscount(discount: DiscountLine) {
  return discount.presetId === '__custom__';
}

function discountPresetLabel(preset: DiscountPresetItem) {
  const value = preset.discountType === 'PERCENTAGE'
    ? `${preset.discountValue}% off`
    : `${formatMoney(wholeDollarForm.value ? ceilRegistrationAmount(numeric(preset.discountValue)) : preset.discountValue)} off`;
  return `${preset.presetName} (${value})`;
}

function applyDiscountPreset(discount: DiscountLine) {
  if (isCustomDiscount(discount)) {
    discount.label = discount.label.trim() || 'Custom discount';
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
  const courseLine = courseLines.value.find((line) => line.rowKey === discount.courseRowKey);
  const participantIndex = activeParticipantBilling.value?.participantIndex;
  const baseAmount = courseLine
    ? participantIndex
      ? participantCoursePrice(courseLine, participantIndex - 1)
      : courseAggregatePrice(courseLine)
    : discountableAmount.value;
  const amount = preset.discountType === 'PERCENTAGE'
    ? baseAmount * numeric(preset.discountValue) / 100 : numeric(preset.discountValue);
  discount.amount = wholeDollarForm.value ? ceilRegistrationAmount(amount) : roundMoney(amount);
}

function courseLineOptionLabel(line: RegistrationCourseLine, index: number) {
  return printCourseProductLabel(line) || line.detail || `Course ${index + 1}`;
}

function courseDiscountAmount(courseRowKey: string) {
  return roundMoney(discounts.value
    .filter((discount) => discount.courseRowKey === courseRowKey)
    .reduce((total, discount) => total + numeric(discount.amount), 0));
}

/** 当前学生课程级折扣；非多人报名时回退到整单课程折扣。 */
function activeCourseDiscountAmount(courseRowKey: string) {
  if (activeParticipantBilling.value) {
    return roundMoney(activeParticipantBilling.value.discounts
      .filter((discount) => discount.courseRowKey === courseRowKey)
      .reduce((total, discount) => total + numeric(discount.amount), 0));
  }
  return isSharedPrivateLesson.value
    ? roundMoney(participantBillings.value.reduce((total, billing) => total + billing.discounts
      .filter((discount) => discount.courseRowKey === courseRowKey)
      .reduce((sum, discount) => sum + numeric(discount.amount), 0), 0))
    : courseDiscountAmount(courseRowKey);
}

function activeCoursePrice(line: RegistrationCourseLine) {
  const participantIndex = activeParticipantBilling.value?.participantIndex;
  return participantIndex
    ? participantCoursePrice(line, participantIndex - 1)
    : courseAggregatePrice(line);
}

/** 报名页按课程分组展示折扣，但仍复用同一份账单折扣状态。 */
function discountsForCourse(courseRowKey: string) {
  return activeDiscountLines.value.filter((discount) => discount.courseRowKey === courseRowKey);
}

function paymentCourseAllocationTotal(payment: PaymentLine) {
  return roundMoney(Object.values(payment.courseAllocations ?? {})
    .reduce((total, amount) => total + numeric(amount), 0));
}

function isUnallocatedOrderPayment(payment: PaymentLine) {
  // 历史订单可能没有保存课程分摊；单课程付款也要归到唯一课程，不能在编辑页被隐藏。
  return !payment.courseRowKey
    && paymentCourseAllocationTotal(payment) === 0;
}

function paymentAllocationRemainder(payment: PaymentLine) {
  return roundMoney(numeric(payment.amount) - paymentAppliedAmount(payment));
}

/** 计算一笔付款真正计入当前课程订单的金额。 */
function paymentAppliedAmount(payment: PaymentLine) {
  if (courseLines.value.length === 1) {
    return netPaymentAmount(payment);
  }
  const allocationTotal = paymentCourseAllocationTotal(payment);
  if (allocationTotal !== 0) {
    return allocationTotal;
  }
  // 兼容早期单课程归属付款：没有分摊明细时，courseRowKey 仍能确定归属。
  return payment.courseRowKey ? netPaymentAmount(payment) : 0;
}

function appliedPaymentAmountForLines(lines: PaymentLine[]) {
  return roundMoney(lines.reduce((total, payment) => total + paymentAppliedAmount(payment), 0));
}

/**
 * 课程卡片只展示本课付款。
 * 历史上没有课程归属的付款只能暂挂在第一门课，不能复制到其他课程后造成锁定的假付款行。
 */
function paymentsForCourse(courseRowKey: string) {
  return activePaymentLines.value.filter((payment) => {
    if (payment.courseRowKey) {
      return payment.courseRowKey === courseRowKey;
    }
    const courseAllocation = numeric(payment.courseAllocations?.[courseRowKey]);
    if (courseAllocation > 0) {
      return true;
    }
    // 历史整笔付款没有课程归属时，只在第一门课展示一次，避免同一笔交易在多门课重复出现。
    return isUnallocatedOrderPayment(payment) && courseRowKey === courseLines.value[0]?.rowKey;
  });
}

/** 编辑收据时退款流水不可编辑，但必须在课程付款区明确展示并参与 Paid/Balance 汇总。 */
function refundPaymentsForCourse(courseRowKey: string) {
  const lines = activeParticipantBilling.value?.payments ?? payments.value;
  return lines.filter((payment) => {
    if (!isRefundPayment(payment)) {
      return false;
    }
    if (courseLines.value.length === 1) {
      return true;
    }
    const allocation = numeric(payment.courseAllocations?.[courseRowKey]);
    if (allocation < 0) {
      return true;
    }
    if (payment.courseRowKey === courseRowKey) {
      return true;
    }
    return isUnallocatedOrderPayment(payment) && courseRowKey === courseLines.value[0]?.rowKey;
  });
}

function courseRefundAmount(courseRowKey: string) {
  return roundMoney(refundPaymentsForCourse(courseRowKey)
    .reduce((total, payment) => total + Math.abs(numeric(payment.amount)), 0));
}

function coursePaymentAmount(payment: PaymentLine, courseRowKey: string) {
  if (payment.courseRowKey === courseRowKey || courseLines.value.length === 1) {
    return payment.amount;
  }
  if (isUnallocatedOrderPayment(payment) && courseRowKey === courseLines.value[0]?.rowKey) {
    return payment.amount;
  }
  return numeric(payment.courseAllocations?.[courseRowKey]);
}

function creditPaymentLinesForActiveScope() {
  return activeParticipantBilling.value?.payments ?? payments.value;
}

/**
 * 当前付款行可输入的 Credit 上限；先扣除同一账单中其他 Credit 付款，避免多付款行合计超额。
 * 多课程历史付款还要保留本付款行分配到其他课程的金额，再计算当前课程可用额度。
 */
function maxStudentCreditPaymentAmount(payment: PaymentLine, courseRowKey: string) {
  if (payment.paymentMethod !== 'STUDENT_CREDIT') {
    return undefined;
  }
  const otherCreditPayments = creditPaymentLinesForActiveScope().reduce((total, candidate) => {
    if (candidate === payment || candidate.paymentMethod !== 'STUDENT_CREDIT') {
      return total;
    }
    return total + numeric(candidate.amount);
  }, 0);
  const otherCourseAllocations = courseLines.value.length > 1
    ? Object.entries(payment.courseAllocations ?? {})
      .filter(([rowKey]) => rowKey !== courseRowKey)
      .reduce((total, [, amount]) => total + numeric(amount), 0)
    : 0;
  return roundMoney(Math.max(0, studentCreditBalance.value - otherCreditPayments - otherCourseAllocations));
}

function remainingCoursePaymentAmount(payment: PaymentLine, courseRowKey: string) {
  const otherPayments = creditPaymentLinesForActiveScope().reduce((total, candidate) => {
    if (candidate === payment) {
      return total;
    }
    return total + numeric(candidate.amount);
  }, 0);
  const otherCourseAllocations = courseLines.value.length > 1
    ? Object.entries(payment.courseAllocations ?? {})
      .filter(([rowKey]) => rowKey !== courseRowKey)
      .reduce((total, [, amount]) => total + numeric(amount), 0)
    : 0;
  return roundMoney(Math.max(0, courseRegistrationAmountDue.value - otherPayments - otherCourseAllocations));
}

function maxCoursePaymentAmount(payment: PaymentLine, courseRowKey: string) {
  const remainingAmount = remainingCoursePaymentAmount(payment, courseRowKey);
  const creditAmount = maxStudentCreditPaymentAmount(payment, courseRowKey);
  return creditAmount === undefined ? remainingAmount : Math.min(remainingAmount, creditAmount);
}

function normalizePaymentAmount(payment: PaymentLine, courseRowKey: string) {
  const maxAmount = maxCoursePaymentAmount(payment, courseRowKey);
  if (maxAmount === undefined) {
    clearPaymentLimitHint(payment);
    return;
  }
  const currentAmount = coursePaymentAmount(payment, courseRowKey);
  if (currentAmount > maxAmount) {
    showPaymentLimitHint(payment, maxAmount);
    setCoursePaymentAmount(payment, courseRowKey, maxAmount);
    return;
  }
  clearPaymentLimitHint(payment);
}

function handlePaymentInput(
  payment: PaymentLine,
  courseRowKey: string,
  value: number | string | null | undefined,
) {
  const maxAmount = maxCoursePaymentAmount(payment, courseRowKey);
  if (maxAmount === undefined || numeric(value) <= maxAmount) {
    clearPaymentLimitHint(payment);
    return;
  }
  showPaymentLimitHint(payment, maxAmount);
  setCoursePaymentAmount(payment, courseRowKey, maxAmount);
}

function paymentLimitHint(payment: PaymentLine) {
  return paymentLimitHints[payment.rowKey] ?? '';
}

function clearPaymentLimitHint(payment: PaymentLine) {
  delete paymentLimitHints[payment.rowKey];
  const timer = paymentLimitHintTimers.get(payment.rowKey);
  if (timer !== undefined) {
    window.clearTimeout(timer);
    paymentLimitHintTimers.delete(payment.rowKey);
  }
}

function showPaymentLimitHint(payment: PaymentLine, maxAmount: number) {
  const rowKey = payment.rowKey;
  paymentLimitHints[rowKey] = `Max payable: ${formatExactUsd(maxAmount)}`;
  const previousTimer = paymentLimitHintTimers.get(rowKey);
  if (previousTimer !== undefined) {
    window.clearTimeout(previousTimer);
  }
  paymentLimitHintTimers.set(rowKey, window.setTimeout(() => {
    delete paymentLimitHints[rowKey];
    paymentLimitHintTimers.delete(rowKey);
  }, 2400));
}

/** 修改课程付款后同步当前付款行总额，使底部 Paid 与各课程输入框保持一致。 */
function setCoursePaymentAmount(payment: PaymentLine, courseRowKey: string, value: number | undefined) {
  const requestedAmount = roundMoney(Math.max(0, numeric(value)));
  const maxAmount = maxCoursePaymentAmount(payment, courseRowKey);
  if (maxAmount !== undefined && requestedAmount > maxAmount) {
    showPaymentLimitHint(payment, maxAmount);
  }
  const amount = maxAmount === undefined
    ? requestedAmount
    : Math.min(requestedAmount, maxAmount);
  payment.courseAllocations ??= {};
  payment.courseAllocations[courseRowKey] = amount;
  payment.amount = courseLines.value.length === 1
    ? amount
    : sumPaymentAllocations(payment.courseAllocations);
}

function canEditCoursePaymentDetails(payment: PaymentLine, courseRowKey: string) {
  if (!receiptEditingEnabled.value || isRefundPayment(payment)) {
    return false;
  }
  if (payment.courseRowKey) {
    return payment.courseRowKey === courseRowKey;
  }
  // 未绑定课程的历史付款在所有课程卡片中都代表同一笔交易，允许分别编辑各课程的分摊金额。
  // 完成分配后只保留已有分摊的课程可编辑，避免无意间给付款增加新的归属。
  const allocationTotal = paymentCourseAllocationTotal(payment);
  if (allocationTotal < numeric(payment.amount)) {
    return courseLines.value.length === 1 || courseRowKey === courseLines.value[0]?.rowKey;
  }
  return numeric(payment.courseAllocations?.[courseRowKey]) > 0;
}

/** 汇总课程已收金额；单课报名无需显式分配，付款总额自然归属该课程。 */
function coursePaymentAllocationTotal(courseRowKey: string) {
  const lines = activeParticipantBilling.value?.payments ?? payments.value;
  return roundMoney(lines.reduce((total, payment) => {
    if (courseLines.value.length === 1) {
      return total + netPaymentAmount(payment);
    }
    const allocation = payment.courseAllocations?.[courseRowKey];
    if (allocation !== undefined) {
      return total + numeric(allocation);
    }
    if (payment.courseRowKey === courseRowKey) {
      return total + netPaymentAmount(payment);
    }
    return total + (isUnallocatedOrderPayment(payment) && courseRowKey === courseLines.value[0]?.rowKey
      ? netPaymentAmount(payment)
      : 0);
  }, 0));
}

function buildCoursePaymentAllocations(payment: PaymentLine) {
  if (courseLines.value.length === 1) {
    return [{ orderItemLineNo: 1, amount: roundMoney(payment.amount) }];
  }
  return courseLines.value
    .map((line, index) => ({
      orderItemLineNo: index + 1,
      amount: roundMoney(payment.courseAllocations?.[line.rowKey] ?? 0),
    }))
    .filter((allocation) => allocation.amount > 0);
}

function courseAmountDue(line: RegistrationCourseLine, index: number) {
  const fees = serviceFeeTotal.value + courseServiceExtraFeeTotal.value;
  const feeShare = courseFeeShare(index, fees, line);
  return roundMoney(courseAggregatePrice(line) + feeShare - activeCourseDiscountAmount(line.rowKey));
}

function activeCourseAmountDue(line: RegistrationCourseLine, index: number) {
  const billing = activeParticipantBilling.value;
  if (!billing) {
    return courseAmountDue(line, index);
  }
  const fees = billing.participantIndex === 1
    ? serviceFeeTotal.value + courseServiceExtraFeeTotal.value
    : 0;
  return roundMoney(participantCoursePrice(line, billing.participantIndex - 1)
    + courseFeeShare(index, fees, line)
    - activeCourseDiscountAmount(line.rowKey));
}

function participantCourseAmountDue(
  line: RegistrationCourseLine,
  index: number,
  billing: ParticipantBillingForm,
  afterDiscount: boolean,
) {
  const fees = billing.participantIndex === 1
    ? serviceFeeTotal.value + courseServiceExtraFeeTotal.value
    : 0;
  const discount = afterDiscount
    ? billing.discounts.filter((item) => item.courseRowKey === line.rowKey)
      .reduce((total, item) => total + numeric(item.amount), 0)
    : 0;
  return roundMoney(participantCoursePrice(line, billing.participantIndex - 1)
    + courseFeeShare(index, fees, line)
    - discount);
}

function courseFeeShare(index: number, totalFees: number, line?: RegistrationCourseLine) {
  if (hasExplicitCourseFees.value && line) {
    return courseFeeAmount(line, index);
  }
  const weights = courseLines.value.map(courseAggregatePrice);
  const weightTotal = sumNumbers(weights);
  const shareAt = (lineIndex: number) => weightTotal > 0
    ? Math.floor((totalFees * weights[lineIndex] / weightTotal + 1e-8) * 100) / 100
    : Math.floor((totalFees / Math.max(1, weights.length) + 1e-8) * 100) / 100;
  return index === weights.length - 1
    ? roundMoney(totalFees - weights.slice(0, index).reduce((total, _weight, lineIndex) => total + shareAt(lineIndex), 0))
    : shareAt(index);
}

function courseFeeAmount(line: RegistrationCourseLine, _index = 0) {
  if (!hasExplicitCourseFees.value) {
    return 0;
  }
  return roundMoney(
    (line.pickupRequired ? numeric(line.pickupFeeAmount) : 0)
    + (line.dropoffRequired ? numeric(line.dropoffFeeAmount) : 0)
    + sumNumbers(line.serviceFees.map((fee) => fee.amount)),
  );
}

function historicalDiscountLines(
  receiptPayload: Record<string, unknown> | null,
  order: EnrollmentOrderDetailItem,
) {
  const receiptDiscountRecords = getArray(receiptPayload, 'discounts');
  const orderDiscountRecords = parseMoneyDetail(order.discountDetail).filter(isRecord);
  const savedWithCourse = receiptDiscountRecords.length > 0 ? receiptDiscountRecords : orderDiscountRecords;
  const saved = savedWithCourse
    .map((discount, index) => ({
      rowKey: `historical-discount-${index}`,
      label: getString(discount, 'label') || 'Discount',
      amount: getNumber(discount, 'amount'),
    }))
    .filter((discount) => discount.amount > 0);
  const itemDiscountTotal = (order.items ?? []).reduce((total, item) => total + numeric(item.discountAmount), 0);
  const totalDiscount = firstPositiveNumber(
    getNumber(receiptPayload, 'discountAmount'),
    order.discountAmount,
    itemDiscountTotal,
  );
  if (courseLines.value.length <= 1) {
    const lines = saved.map((discount) => createDiscountLine(discount.label, discount.amount));
    return lines.length > 0 || totalDiscount <= 0
      ? lines
      : [createDiscountLine('Custom discount', totalDiscount)];
  }

  if (savedWithCourse.some((discount) => {
    const lineNo = getNumber(discount, 'orderItemLineNo');
    return Number.isInteger(lineNo) && lineNo >= 1;
  })) {
    return savedWithCourse
      .map((discount) => {
        const lineNo = getNumber(discount, 'orderItemLineNo');
        const line = courseLines.value.find((course, index) => index + 1 === lineNo);
        return line
          ? createDiscountLine(getString(discount, 'label') || 'Discount', getNumber(discount, 'amount'), line.rowKey)
          : null;
      })
      .filter((discount): discount is DiscountLine => discount !== null);
  }

  const itemDiscounts = order.items
    .filter((item) => numeric(item.discountAmount) > 0)
    .flatMap((item) => {
      const line = courseLines.value.find((course) => String(course.orderItemId) === String(item.id));
      return line ? [createDiscountLine('Discount', numeric(item.discountAmount), line.rowKey)] : [];
    });
  if (itemDiscounts.length > 0
      && Math.abs(roundMoney(itemDiscounts.reduce((sum, line) => sum + line.amount, 0)) - totalDiscount) <= 0.01) {
    return itemDiscounts;
  }

  // 旧收据没有课程归属时，按课程价格沿用旧的比例分配，避免编辑后把整笔优惠误挂到首课。
  const tuition = courseLines.value.reduce((total, line) => total + numeric(line.priceAmount), 0);
  const sourceDiscounts = saved.length > 0 ? saved : totalDiscount > 0
    ? [{ label: 'Custom discount', amount: totalDiscount }]
    : [];
  return sourceDiscounts.flatMap((discount) => {
    let assigned = 0;
    return courseLines.value.map((line, index) => {
      const amount = index === courseLines.value.length - 1
        ? roundMoney(discount.amount - assigned)
        : roundMoney(tuition > 0
          ? discount.amount * numeric(line.priceAmount) / tuition
          : discount.amount / courseLines.value.length);
      assigned = roundMoney(assigned + amount);
      return createDiscountLine(discount.label, amount, line.rowKey);
    }).filter((line) => line.amount > 0);
  });
}

function addPaymentLine(courseRowKey?: string) {
  payments.value.push(createPaymentLine(courseRowKey));
}

function removePaymentLine(index: number) {
  if (payments.value.length <= 1) return;
  const editable = payments.value.filter((payment) => !isRefundPayment(payment));
  const target = editable[index];
  if (!target) return;
  const actualIndex = payments.value.indexOf(target);
  if (actualIndex >= 0) {
    payments.value.splice(actualIndex, 1);
  }
}

function addActivePaymentLine(courseRowKey?: string) {
  if (courseLines.value.length > 1
      && activePaymentLines.value.some((payment) =>
        !payment.isHistorical
        && !payment.courseRowKey
        && numeric(payment.amount) > paymentCourseAllocationTotal(payment))) {
    ElMessage.warning('Assign the existing payment to all courses before adding a new payment.');
    return;
  }
  const participantIndex = activeParticipantBilling.value?.participantIndex;
  if (participantIndex) {
    addParticipantPayment(participantIndex, courseRowKey ?? courseLines.value[0]?.rowKey);
    return;
  }
  addPaymentLine(courseRowKey ?? courseLines.value[0]?.rowKey);
}

function removeActivePaymentLine(payment: PaymentLine) {
  const index = activePaymentLines.value.indexOf(payment);
  if (index < 0) return;
  const participantIndex = activeParticipantBilling.value?.participantIndex;
  if (participantIndex) {
    removeParticipantPayment(participantIndex, index);
    return;
  }
  removePaymentLine(index);
}

/** 接送开关记录本次课程需求，费用按输入金额计入；同步旧字段以兼容历史收据和后端 DTO。 */
function syncCourseTransportFee(line: RegistrationCourseLine, type: 'pickup' | 'dropoff') {
  if (type === 'pickup') {
    line.pickupFeeCharged = line.pickupRequired;
    if (!line.pickupRequired) {
      line.pickupFeeAmount = 0;
    } else {
      if (!line.pickupAddress.trim()) {
        line.pickupAddress = form.student.pickupAddress;
      }
      if (!isReadOnly.value && numeric(line.pickupFeeAmount) === 0) {
        line.pickupFeeAmount = wholeDollarForm.value
          ? ceilRegistrationAmount(selectedCampus.value?.defaultPickupFeeAmount ?? 0)
          : selectedCampus.value?.defaultPickupFeeAmount ?? 0;
      }
    }
    return;
  }
  line.dropoffFeeCharged = line.dropoffRequired;
  if (!line.dropoffRequired) {
    line.dropoffFeeAmount = 0;
  } else {
    if (!line.dropoffAddress.trim()) {
      line.dropoffAddress = form.student.dropoffAddress;
    }
    if (!isReadOnly.value && numeric(line.dropoffFeeAmount) === 0) {
      line.dropoffFeeAmount = wholeDollarForm.value
        ? ceilRegistrationAmount(selectedCampus.value?.defaultDropoffFeeAmount ?? 0)
        : selectedCampus.value?.defaultDropoffFeeAmount ?? 0;
    }
  }
}

function handleCourseProductChange(line: RegistrationCourseLine) {
  const product = courseProducts.value.find((item) => String(item.id) === String(line.courseProductId));
  const firstOption = priceOptionsForLineProduct(line)[0];
  line.gradeBand = '';
  line.periodKey = '';
  clearSelectedOption(line);
  line.courseType = product?.teachingMode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : (firstOption?.courseType || 'GROUP');
  line.registerClass = product?.registerClass || firstOption?.registerClass || '';
  if ((isReadOnly.value || isRenewalMode.value)
      && !line.pickupAddress && !line.dropoffAddress && !line.pickupRequired && !line.dropoffRequired) {
    line.pickupRequired = form.student.pickupRequired;
    line.pickupAddress = form.student.pickupAddress;
    line.dropoffRequired = form.student.dropoffRequired;
    line.dropoffAddress = form.student.dropoffAddress;
    syncCourseTransportFee(line, 'pickup');
    syncCourseTransportFee(line, 'dropoff');
  }
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
  line.packageUnit = option.packageUnit;
  line.packageQuantity = option.packageQuantity;
  line.packageTotalPrice = packageBasePrice(option, line.attendanceOption);
  line.detail = option.detailText;
  line.courseProductId = option.courseProductId;
  line.oneOnOneHours = option.oneOnOneHours;
  line.openingUsedHours = Math.min(numeric(line.openingUsedHours), numeric(option.oneOnOneHours));
  line.oneOnOneUnits = option.oneOnOneHours ? Math.round(option.oneOnOneHours * 2) : undefined;
  const actualClasses = actualClassOptionsForOption(option);
  line.classId = actualClasses.length === 1 ? actualClasses[0].id : undefined;
  line.groupBillingType = option.groupBillingType;
  line.groupTotalSessions = option.groupTotalSessions;
  line.groupStartMonth = option.groupStartMonth;
  line.groupEndMonth = option.groupEndMonth;
  if (isOneOnOneLine(line)) {
    line.startDate = undefined;
    line.groupSessionCount = undefined;
    line.groupPurchasedMonths = undefined;
    line.groupDurationMode = undefined;
    line.groupRegistrationStartMonth = undefined;
    line.groupRegistrationEndMonth = undefined;
  } else {
    line.groupSessionCount = line.packageUnit?.toUpperCase() === 'SESSION'
      ? packageSessionCount(line) || undefined
      : undefined;
    line.groupPurchasedMonths = resolveGroupPackageMonths(option);
    line.groupDurationMode = durationModeForMonths(line.groupPurchasedMonths);
    line.startDate = line.startDate || defaultGroupEnrollmentStartDate(line);
    line.groupRegistrationStartMonth = undefined;
    line.groupRegistrationEndMonth = undefined;
  }
  if (!isOneOnOneLine(line)) {
    line.attendanceOption = defaultAttendanceOption(option);
    line.packageTotalPrice = packageBasePrice(option, line.attendanceOption);
  }
  line.manualPriceOverride = false;
  handleRegistrationDurationChange(line);
  if (line.classId) {
    handleClassChange(line);
  }
}

function handleClassChange(line: RegistrationCourseLine) {
  const classGroup = selectedClassGroup(line);
  if (!classGroup) {
    return;
  }
  form.student.studentClass = classGroup.actualClass || classGroup.gradeBand || classGroup.className;
  line.className = classGroup.className;
}

function handlePriceChange(line: RegistrationCourseLine) {
  const originalPrice = line.originalPriceAmount;
  line.manualPriceOverride = originalPrice !== undefined && roundMoney(line.priceAmount) !== roundMoney(originalPrice);
}

function handleRegistrationDurationChange(line: RegistrationCourseLine) {
  if (isOneOnOneLine(line)) {
    const totalHours = totalOneOnOneHours(line);
    line.oneOnOneUnits = totalHours > 0 ? Math.round(totalHours * 2) : undefined;
    line.durationLabel = totalHours > 0 ? `${formatHours(totalHours)} hours` : '';
  } else {
    syncGroupDurationFromSelectedPackage(line);
    line.durationLabel = printRegistrationDurationLabel(line);
  }
  recalculateLinePrice(line);
}

/** 开课日期只改变课程时段说明；编辑历史收据时不得因改日期重算已成交价格。 */
function handleGroupStartDateChange(line: RegistrationCourseLine) {
  line.durationLabel = printRegistrationDurationLabel(line);
}

function handleGroupSessionCountChange(line: RegistrationCourseLine) {
  // Changing the chargeable session count should restore the configured per-session price calculation.
  line.manualPriceOverride = false;
  handleRegistrationDurationChange(line);
}

function selectedClassGroup(line: RegistrationCourseLine) {
  return classGroups.value.find((item) => String(item.id) === String(line.classId));
}

function classStartDatePlaceholder(line: RegistrationCourseLine) {
  return defaultGroupEnrollmentStartDate(line);
}

function defaultGroupEnrollmentStartDate(line: RegistrationCourseLine) {
  const signup = form.signupDate || today;
  const classStart = selectedClassGroup(line)?.startDate;
  return classStart && signup < classStart ? classStart : signup;
}

function resolveGroupPackageMonths(option?: CoursePriceOptionItem) {
  if (!option || option.packageUnit !== 'MONTH') {
    return undefined;
  }
  const months = numeric(option.packageQuantity);
  return months > 0 ? months : undefined;
}

function syncGroupDurationFromSelectedPackage(line: RegistrationCourseLine) {
  if (isOneOnOneLine(line) || line.packageUnit !== 'MONTH') {
    return;
  }
  const option = findOption(line.coursePriceOptionId);
  const months = resolveGroupPackageMonths(option);
  line.groupPurchasedMonths = months;
  line.groupDurationMode = durationModeForMonths(months);
  if (months !== undefined) {
    line.packageQuantity = months;
  }
}

function recalculateLinePrice(line: RegistrationCourseLine) {
  const option = findOption(line.coursePriceOptionId);
  if (!option) return;
  const configuredHours = numeric(option.oneOnOneHours);
  const fullPackagePrice = packageBasePrice(option, line.attendanceOption);
  const sessionCount = isSessionPackage(line) ? selectedSessionCount(line) : 0;
  const configuredSessionCount = isSessionPackage(line) ? packageSessionCount(line) : 0;
  const computedPrice = isOneOnOneLine(line)
    ? configuredHours > 0
      ? wholeDollarForm.value
        ? ceilRegistrationAmount(numeric(fullPackagePrice) / configuredHours * numeric(line.oneOnOneHours))
        : roundMoney(numeric(fullPackagePrice) / configuredHours * numeric(line.oneOnOneHours))
      : 0
    : isSessionPackage(line) && configuredSessionCount > 0
      ? wholeDollarForm.value
        ? calculateProratedSessionPrice(fullPackagePrice, sessionCount, configuredSessionCount)
        : Math.floor(Math.round(fullPackagePrice * 100) * sessionCount / (configuredSessionCount * 100))
      : wholeDollarForm.value ? ceilRegistrationAmount(fullPackagePrice) : fullPackagePrice;
  // Keep the configured full-package price as the proration basis for receipt edits and historical display.
  line.packageTotalPrice = isSessionPackage(line) ? fullPackagePrice : computedPrice;
  line.originalPriceAmount = computedPrice;
  if (!line.manualPriceOverride) {
    line.priceAmount = computedPrice;
  }
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
  line.startDate = undefined;
  line.packageUnit = undefined;
  line.packageQuantity = undefined;
  line.packageTotalPrice = undefined;
  line.attendanceOption = 'NONE';
  line.attendanceScheduleJson = undefined;
  line.priceAmount = 0;
  line.originalPriceAmount = undefined;
  line.manualPriceOverride = false;
  line.oneOnOneHours = undefined;
  line.oneOnOneBonusHours = 0;
  line.oneOnOneUnits = undefined;
  line.groupBillingType = undefined;
  line.groupTotalSessions = undefined;
  line.groupStartMonth = undefined;
  line.groupEndMonth = undefined;
  line.groupSessionCount = undefined;
  line.groupDurationMode = '2';
  line.groupPurchasedMonths = 2;
  line.groupRegistrationStartMonth = undefined;
  line.groupRegistrationEndMonth = undefined;
}

function gradeOptionsForLine(line: RegistrationCourseLine) {
  const gradeOptions = uniqueValues(
    priceOptionsForLineProduct(line)
      .map((option) => option.gradeBand || 'General')
  );
  return sortGradeOptionsForProduct(line.courseProductId, gradeOptions);
}

function sortGradeOptionsForProduct(productId: EntityId | undefined, gradeOptions: string[]) {
  if (!productId) {
    return gradeOptions;
  }
  const orderMap = productGradeOrderMaps.value.get(String(productId));
  if (!orderMap || orderMap.size === 0) {
    return gradeOptions.slice().sort((left, right) => left.localeCompare(right));
  }
  return gradeOptions.slice().sort((left, right) => {
    const leftOrder = orderMap.get(left.toLowerCase());
    const rightOrder = orderMap.get(right.toLowerCase());
    if (leftOrder !== undefined || rightOrder !== undefined) {
      return (leftOrder ?? Number.MAX_SAFE_INTEGER) - (rightOrder ?? Number.MAX_SAFE_INTEGER)
        || left.localeCompare(right);
    }
    return left.localeCompare(right);
  });
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
  return sortCourseProducts(courseProducts.value.filter((product) => linkedProductIds.has(String(product.id))));
}

function productOptionGroupsForLine(line: RegistrationCourseLine) {
  const options = productOptionsForLine(line);
  const oneOnOne = options.filter((product) => product.teachingMode === 'ONE_ON_ONE');
  const groupClass = options.filter((product) => product.teachingMode === 'GROUP');
  const other = options.filter((product) => product.teachingMode !== 'ONE_ON_ONE' && product.teachingMode !== 'GROUP');
  return [
    { label: 'Group class', value: 'group', options: groupClass },
    { label: '1 on 1', value: 'one-on-one', options: oneOnOne },
    { label: 'Other', value: 'other', options: other },
  ].filter((group) => group.options.length > 0);
}

function classOptionsForLine(line: RegistrationCourseLine) {
  if (!line.courseProductId || !line.gradeBand) {
    return [];
  }
  const campusId = classCampusFilterId();
  const currentClasses = classGroupsForProductGrade(line.courseProductId, line.gradeBand, campusId);
  if (currentClasses.length > 0) {
    return currentClasses;
  }
  const option = findOption(line.coursePriceOptionId);
  if (option?.actualClasses && option.actualClasses.length > 0) {
    return option.actualClasses
      .filter(item => campusId === undefined
        || (campusId === null ? item.campusId == null : String(item.campusId ?? '') === String(campusId)))
      .map(item => classGroupFromActualClassOption(item, line.courseProductId, line.gradeBand));
  }
  return [];
}

function actualClassOptionsForOption(option: CoursePriceOptionItem) {
  const campusId = classCampusFilterId();
  const currentClasses = classGroupsForProductGrade(option.courseProductId, option.gradeBand || '', campusId);
  if (currentClasses.length > 0) {
    return currentClasses;
  }
  if (option.actualClasses && option.actualClasses.length > 0) {
    return option.actualClasses
      .filter(item => campusId === undefined
        || (campusId === null ? item.campusId == null : String(item.campusId ?? '') === String(campusId)))
      .map(item => classGroupFromActualClassOption(item, option.courseProductId, option.gradeBand || ''));
  }
  return [];
}

function classCampusFilterId(): EntityId | null | undefined {
  if (!selectedCampus.value || showAllCampusClasses.value) {
    return undefined;
  }
  return selectedCampus.value.campusId ?? null;
}

function classGroupsForProductGrade(productId?: EntityId, gradeBand = '', campusId?: EntityId | null) {
  if (!productId || !gradeBand) {
    return [];
  }
  return classGroups.value.filter((item) =>
    String(item.productId) === String(productId)
      && String(item.gradeBand || '').toLowerCase() === String(gradeBand || '').toLowerCase()
      && (campusId === undefined
        || (campusId === null ? item.campusId == null : String(item.campusId ?? '') === String(campusId))));
}

function classGroupFromActualClassOption(
  item: NonNullable<CoursePriceOptionItem['actualClasses']>[number],
  productId?: EntityId,
  gradeBand = '',
) {
  const currentClass = classGroups.value.find((classGroup) => String(classGroup.id) === String(item.id));
  if (currentClass) {
    return currentClass;
  }
  return {
    id: item.id,
    className: item.className,
    actualClass: item.actualClass,
    campusId: item.campusId,
    campusName: item.campusName,
    gradeBand,
    productId,
    teacherId: item.teacherId,
    teacherName: item.teacherName,
    classroomId: item.classroomId,
    classroomName: item.classroomName,
    scheduleDesc: item.scheduleDesc,
    activeStudentCount: 0,
  } as ClassGroupItem;
}

function detailSelectPlaceholder(line: RegistrationCourseLine) {
  if (!line.courseProductId) {
    return 'Select course first';
  }
  if (!line.gradeBand) {
    return 'Select program first';
  }
  return optionChoicesForLine(line).length > 0
    ? 'Select package'
    : 'No package';
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
      ? isOneOnOneLine(line)
        ? 'Select a program and package.'
        : 'Select a package.'
      : 'Course pricing is missing for this course.';
  }
  return 'Detail will appear here.';
}

function productSelectPlaceholder(line: RegistrationCourseLine) {
  return productOptionsForLine(line).length > 0 ? 'Course name' : 'No active course';
}

function productOptionLabel(product: CourseProductItem) {
  return product.productName || 'Course';
}

function sortCourseProducts(products: CourseProductItem[]) {
  return [...products].sort((left, right) =>
    (left.sortOrder ?? 99) - (right.sortOrder ?? 99)
    || left.productName.localeCompare(right.productName)
  );
}

function printCourseProductLabel(line: RegistrationCourseLine) {
  const product = courseProducts.value.find((item) => String(item.id) === String(line.courseProductId));
  return product ? productOptionLabel(product) : line.courseProductName || line.registerClass || '—';
}

function formatCourseLinePrice(line: RegistrationCourseLine) {
  if (!isSharedPrivateLesson.value) {
    return formatMoney(line.priceAmount);
  }
  const activeIndex = activePrivateParticipantIndex.value;
  const amount = isPrivateAllReceiptView.value || activeIndex === null
    ? courseAggregatePrice(line)
    : participantCoursePrice(line, activeIndex - 1);
  return formatMoney(amount);
}

/** 共享报名的订单课程金额：私教按倍率计价，班课按参与人数乘原价。 */
function courseAggregatePrice(line: RegistrationCourseLine) {
  if (!isSharedPrivateLesson.value) {
    return numeric(line.priceAmount);
  }
  if (isReadOnly.value && privateParticipantPriceSnapshots.value.length > 1) {
    return numeric(line.priceAmount);
  }
  const aggregate = numeric(line.priceAmount)
    * (isOneOnOneLine(line) ? privateParticipantMultiplier.value : privateParticipantCount.value);
  return wholeDollarForm.value ? ceilRegistrationAmount(aggregate) : roundMoney(aggregate);
}

/** 单个参与人的课程金额，用于学生标签页、折扣和付款上限。 */
function participantCoursePrice(line: RegistrationCourseLine, participantIndex: number) {
  if (!isSharedPrivateLesson.value) {
    return numeric(line.priceAmount);
  }
  if (isReadOnly.value && privateParticipantPriceSnapshots.value.length > 1) {
    return splitShareAmount(numeric(line.priceAmount), privateParticipantCount.value, participantIndex, wholeDollarForm.value);
  }
  if (!isOneOnOneLine(line)) {
    return numeric(line.priceAmount);
  }
  return splitShareAmount(courseAggregatePrice(line), privateParticipantCount.value, participantIndex, wholeDollarForm.value);
}

function privateParticipantLineTotalPrice(line: RegistrationCourseLine) {
  return courseAggregatePrice(line);
}

function privateParticipantLineSharePrice(line: RegistrationCourseLine, participantIndex: number) {
  return participantCoursePrice(line, participantIndex);
}

function printCourseScheduleLabel(line: RegistrationCourseLine) {
  const option = findOption(line.coursePriceOptionId);
  return [
    option?.periodLabel || line.periodLabel,
    option?.timeSlotLabel || line.timeSlotLabel,
  ].filter(Boolean).join(' · ') || line.detail || '—';
}

function showDurationInput(line: RegistrationCourseLine) {
  return Boolean(line.coursePriceOptionId);
}

function printRegistrationDurationLabel(line: RegistrationCourseLine) {
  if (isOneOnOneLine(line)) {
    const totalHours = totalOneOnOneHours(line);
    return totalHours > 0 ? `${formatHours(totalHours)} hours` : '—';
  }
  if (isSessionPackage(line)) {
    const selected = selectedSessionCount(line);
    return selected > 0 ? `${selected} sessions` : '—';
  }
  if (line.packageQuantity && line.packageUnit) {
    const unit = line.packageUnit.toLowerCase();
    return `${line.packageQuantity} ${Number(line.packageQuantity) === 1 ? unit : `${unit}s`}`;
  }
  return line.periodLabel || '—';
}

function isSessionPackage(line: RegistrationCourseLine) {
  return !isOneOnOneLine(line) && line.packageUnit?.toUpperCase() === 'SESSION';
}

function packageSessionCount(line: RegistrationCourseLine) {
  return Math.ceil(numeric(line.packageQuantity));
}

function selectedSessionCount(line: RegistrationCourseLine) {
  return Math.floor(numeric(line.groupSessionCount));
}

function availableSessionCounts(line: RegistrationCourseLine) {
  const total = packageSessionCount(line);
  return Array.from({ length: Math.max(0, total) }, (_, index) => total - index);
}

function printTuitionOptionLabel(line: RegistrationCourseLine) {
  const option = findOption(line.coursePriceOptionId);
  return option ? durationOptionLabel(option) : '—';
}

function printScheduleSummaryLabel(line: RegistrationCourseLine) {
  if (isOneOnOneLine(line)) {
    return printTeacherOrClassLabel(line);
  }
  const classGroup = classGroups.value.find((item) => String(item.id) === String(line.classId));
  if (!classGroup) {
    return line.className || line.timeSlotLabel || '—';
  }
  return [classGroup.teacherName, classGroup.scheduleDesc].filter(Boolean).join(' / ')
    || classGroup.className
    || '—';
}

function durationModeForMonths(months?: number): '2' | '6' | '10' | 'CUSTOM' {
  return months === 2 || months === 6 || months === 10 ? String(months) as '2' | '6' | '10' : 'CUSTOM';
}

function groupMonthCountFromSnapshot(item: Record<string, unknown> | EnrollmentOrderDetailItem['items'][number]) {
  const start = monthIndex(getString(item, 'startMonth'));
  const end = monthIndex(getString(item, 'endMonth'));
  return start === undefined || end === undefined || end < start ? undefined : end - start + 1;
}

function printTeacherOrClassLabel(line: RegistrationCourseLine) {
  if (isOneOnOneLine(line)) {
    return teachers.value.find((teacher) => String(teacher.id) === String(line.teacherId))?.teacherName || '—';
  }
  const classGroup = classGroups.value.find((item) => String(item.id) === String(line.classId));
  return classGroup ? formatClassLabel(classGroup) : line.className || '—';
}

function durationOptionLabel(option: CoursePriceOptionItem) {
  if (option.courseType !== 'ONE_ON_ONE') {
    return [
      option.periodLabel,
      packageLengthLabel(option),
    ].filter(Boolean).join(' · ') || 'Class package';
  }
  return [
    option.durationLabel,
    option.periodLabel,
    option.timeSlotLabel,
  ].filter(Boolean).join(' · ') || option.detailText;
}

function packageLengthLabel(option: CoursePriceOptionItem) {
  if (!option.packageUnit || !option.packageQuantity) {
    return option.durationLabel;
  }
  const unit = option.packageUnit.toLowerCase();
  const plural = Number(option.packageQuantity) === 1 ? unit : `${unit}s`;
  return `${option.packageQuantity} ${plural}`;
}

function defaultAttendanceOption(option?: CoursePriceOptionItem): RegistrationCourseLine['attendanceOption'] {
  if (!option || option.courseType === 'ONE_ON_ONE') {
    return 'NONE';
  }
  if (option.fullDayPrice != null) {
    return 'FULL_DAY';
  }
  if (option.halfDayPrice != null) {
    return 'MORNING';
  }
  return 'NONE';
}

function packageBasePrice(option: CoursePriceOptionItem, attendanceOption?: string) {
  const normalized = attendanceOption || 'NONE';
  if ((normalized === 'MORNING' || normalized === 'AFTERNOON' || normalized === 'CUSTOM') && option.halfDayPrice != null) {
    return roundMoney(option.halfDayPrice);
  }
  if (normalized === 'FULL_DAY' && option.fullDayPrice != null) {
    return roundMoney(option.fullDayPrice);
  }
  return roundMoney(option.totalPrice ?? option.groupTotalPrice ?? option.priceAmount);
}

function attendanceOptionsForLine(line: RegistrationCourseLine) {
  const option = findOption(line.coursePriceOptionId);
  if (!option || isOneOnOneLine(line)) {
    return [];
  }
  const options: Array<{ label: string; value: RegistrationCourseLine['attendanceOption'] }> = [];
  if (option.fullDayPrice != null) {
    options.push({ label: `Full day (${formatMoney(option.fullDayPrice)})`, value: 'FULL_DAY' });
  }
  if (option.halfDayPrice != null) {
    options.push({ label: `Morning half day (${formatMoney(option.halfDayPrice)})`, value: 'MORNING' });
    options.push({ label: `Afternoon half day (${formatMoney(option.halfDayPrice)})`, value: 'AFTERNOON' });
  }
  return options;
}

function handleAttendanceChange(line: RegistrationCourseLine) {
  recalculateLinePrice(line);
}

function classSelectPlaceholder(line: RegistrationCourseLine) {
  if (!line.coursePriceOptionId) {
    return 'Select option first';
  }
  if (classOptionsForLine(line).length > 0) {
    return 'Select actual class';
  }
  return hasUnassignedClassForLine(line) ? 'Assign class campus in Prices > Edit Info' : 'No class for this campus';
}

function hasUnassignedClassForLine(line: RegistrationCourseLine) {
  if (!line.courseProductId || !line.gradeBand) {
    return false;
  }
  return classGroups.value.some((item) =>
    String(item.productId) === String(line.courseProductId)
      && String(item.gradeBand || '').toLowerCase() === String(line.gradeBand || '').toLowerCase()
      && item.campusId == null);
}

function classScheduleLabel(line: RegistrationCourseLine) {
  const classGroup = classGroups.value.find((item) => String(item.id) === String(line.classId));
  if (!classGroup) {
    return line.coursePriceOptionId ? 'Class schedule will be assigned from this package.' : 'Select a package first';
  }
  return formatClassLabel(classGroup);
}

function isOneOnOneLine(line: RegistrationCourseLine) {
  const product = courseProducts.value.find((item) => String(item.id) === String(line.courseProductId));
  if (product) {
    return product.teachingMode === 'ONE_ON_ONE';
  }
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
    let response: Awaited<ReturnType<typeof submitRegistrationIntake>>;
    try {
      response = await submitRegistrationIntake(payload);
    } catch (error) {
      if (!isStudentScheduleConflict(error)) {
        throw error;
      }
      try {
        await ElMessageBox.confirm(
          `${extractErrorMessage(error)} Registration has not been saved. Continue anyway?`,
          'Schedule conflict detected',
          {
            confirmButtonText: 'Continue anyway',
            cancelButtonText: 'Cancel registration',
            distinguishCancelAndClose: true,
            type: 'warning',
          },
        );
      } catch {
        return;
      }
      response = await submitRegistrationIntake({ ...payload, ignoreStudentConflicts: true });
    }
    const saved = response.data.data;
    notifyRegistrationSaved(saved);
    // 报名保存后先回到只读收据页，方便立即打印；排课由后续的学生历史/课包流程单独进入。
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

/** 仅对学生已有课程冲突开放确认流程；教师、教室等排课错误仍按原规则阻断。 */
function isStudentScheduleConflict(error: unknown) {
  if (!error || typeof error !== 'object' || !('response' in error)) {
    return false;
  }
  const response = (error as { response?: { data?: unknown } }).response;
  const body = response?.data;
  if (!body || typeof body !== 'object') {
    return false;
  }
  const apiError = body as { code?: unknown; data?: unknown };
  return apiError.code === 'SCHEDULE_CONFLICT'
    && Array.isArray(apiError.data)
    && apiError.data.length > 0
    && apiError.data.every((detail) => (
      Boolean(detail)
      && typeof detail === 'object'
      && (detail as { resourceType?: unknown }).resourceType === 'STUDENT'
    ));
}

function notifyRegistrationSaved(saved: RegistrationIntakeResult) {
  if (saved.creditTopUpAmount && saved.courseArtifacts.length > 0) {
    ElNotification({
      title: 'Registration and Credit top-up completed',
      message: `${saved.studentName} was registered and ${formatMoney(saved.creditTopUpAmount)} was added to Student Credit.`,
      type: 'success',
      position: 'top-right',
      duration: 6500,
    });
    return;
  }
  if (saved.courseArtifacts.length === 0 && saved.amountDue > 0) {
    ElNotification({
      title: 'Credit top-up completed',
      message: `${saved.studentName} now has a new Credit deposit of ${formatMoney(saved.amountDue)}.`,
      type: 'success',
      position: 'top-right',
      duration: 6500,
    });
    return;
  }
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
      ? `${accountMessage} The registration is saved. Schedule the weekly lesson times later from the student history or course package.`
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
  if (wholeDollarForm.value) {
    const requireWhole = (amount: number, label: string) => {
      if (!Number.isInteger(roundMoney(amount))) errors.push(`${label} must be a whole dollar amount.`);
    };
    requireWhole(creditTopUpAmount.value, 'Credit top-up');
    courseLines.value.forEach((line, index) => {
      requireWhole(line.priceAmount, `Course ${index + 1} price`);
      requireWhole(line.pickupFeeAmount, `Course ${index + 1} pickup fee`);
      requireWhole(line.dropoffFeeAmount, `Course ${index + 1} drop-off fee`);
      line.serviceFees.forEach((fee) => requireWhole(fee.amount, 'Course fee'));
    });
    extraFees.value.forEach((fee) => requireWhole(fee.amount, 'Extra fee'));
    [...discounts.value, ...participantBillings.value.flatMap((billing) => billing.discounts)]
      .forEach((discount) => requireWhole(discount.amount, 'Discount'));
    [...payments.value, ...participantBillings.value.flatMap((billing) => billing.payments)]
      .forEach((payment) => {
        requireWhole(payment.amount, 'Payment');
        Object.values(payment.courseAllocations ?? {}).forEach((amount) => requireWhole(numeric(amount), 'Course payment allocation'));
      });
  }
  if (!selectedCampus.value && !isCreditTopUpOnly.value) {
    errors.push('Select one campus before submitting.');
  }
  if (!form.student.firstName.trim() || !form.student.lastName.trim()) {
    errors.push('First name and last name are required.');
  }
  if (!form.student.guardianPhone.trim()) {
    errors.push('Guardian phone is required.');
  }
  if (creditTopUpAmount.value > 0) {
    if (!creditTopUpPayment.value.paymentMethod || creditTopUpPayment.value.paymentMethod === 'STUDENT_CREDIT') {
      errors.push('Select a real payment method for the Credit top-up.');
    }
    if (!creditTopUpPayment.value.paidAt) {
      errors.push('Payment date is required for the Credit top-up.');
    }
  }
  if (creditPaymentAmount.value > 0) {
    if (studentCreditLoading.value) {
      errors.push('Student Credit balance is still loading. Please wait and try again.');
    } else if (!selectedStudentCreditId.value) {
      errors.push('Student Credit can only be used for an existing student with a Credit balance.');
    } else if (exceedsMoney(creditPaymentAmount.value, studentCreditBalance.value)) {
      errors.push(`Student Credit payment ${formatExactUsd(creditPaymentAmount.value)} exceeds the available balance of ${formatExactUsd(studentCreditBalance.value)}.`);
    }
  }
  if (isCreditTopUpOnly.value) {
    if (creditTopUpAmount.value <= 0) {
      errors.push('Enter a Credit top-up amount greater than zero.');
    }
    return errors;
  }
  if (privateParticipantCount.value > 1) {
    if (!hasOneOnOneLine.value) {
      errors.push('Additional students are available only for a 1-on-1 course.');
    }
    if (courseLines.value.filter(isOneOnOneLine).length !== 1) {
      errors.push('A shared registration must contain exactly one 1-on-1 course. Other course rows can be group classes.');
    }
    if (isRenewalMode.value && courseLines.value.length !== 1) {
      errors.push('A shared private lesson renewal must contain one course line.');
    }
    if (isRenewalMode.value && courseLines.value.filter(isOneOnOneLine).length > 1) {
      errors.push('A shared private lesson renewal can renew one private course at a time.');
    }
    privateParticipantForms.value.forEach((participant, index) => {
      if (!participant.studentName.trim()) {
        errors.push(`${privateWorkspaceTabLabel(index + 2)}: name is required.`);
      }
      if (participant.studentId && privateParticipantForms.value.some((other, otherIndex) =>
        otherIndex !== index && String(other.studentId) === String(participant.studentId))) {
        errors.push(`${privateWorkspaceTabLabel(index + 2)}: the same existing student cannot be added twice.`);
      }
    });
    participantBillings.value.slice(0, privateParticipantCount.value).forEach((billing, index) => {
      validateMoneyLines(billing.discounts, `${privateWorkspaceTabLabel(index + 1)} discount`, errors);
      billing.payments.forEach((payment, paymentIndex) => {
        if (payment.amount <= 0) return;
        if (!payment.paymentMethod) {
          errors.push(`${privateWorkspaceTabLabel(index + 1)} payment ${paymentIndex + 1}: payment method is required.`);
        }
        if (!payment.paidAt) {
          errors.push(`${privateWorkspaceTabLabel(index + 1)} payment ${paymentIndex + 1}: payment date is required.`);
        }
      });
    });
  }
  if (!form.handledByName.trim()) {
    errors.push('Handled by is required.');
  }
  if (exceedsMoney(discountTotal.value, courseSubtotal.value + serviceFeeTotal.value + extraFeeTotal.value)) {
    errors.push('Discount cannot exceed tuition plus fees.');
  }
  if (exceedsMoney(paidAmount.value, amountDue.value)) {
    errors.push('Paid amount cannot exceed amount due.');
  }
  courseLines.value.forEach((line, index) => {
    const label = `Course row ${index + 1}`;
    if (!line.courseProductId) {
      errors.push(`${label}: select a course.`);
    }
    if (!line.coursePriceOptionId) {
      errors.push(isOneOnOneLine(line)
        ? `${label}: select a program and package.`
        : `${label}: select a package.`);
    }
    const durationInvalid = validateRegistrationDuration(line, label, errors);
    if (!durationInvalid && (line.originalPriceAmount === undefined || line.originalPriceAmount <= 0)) {
      if (!line.manualPriceOverride || line.priceAmount <= 0 || !line.remark?.trim()) {
        errors.push(`${label}: configured price is missing. Enter a manual price and reason, or ask an administrator to update the course price.`);
      }
    }
    if (!durationInvalid && line.priceAmount <= 0) {
      errors.push(`${label}: price must be greater than zero.`);
    }
    if (line.manualPriceOverride && !line.remark?.trim()) {
      errors.push(`${label}: manual price reason is required.`);
    }
    if (line.pickupRequired && !effectiveCoursePickupAddress(line)) {
      errors.push(`${label}: pickup address is required when pickup is checked.`);
    }
    if (line.dropoffRequired && !effectiveCourseDropoffAddress(line)) {
      errors.push(`${label}: drop-off address is required when drop-off is checked.`);
    }
    if (!isOneOnOneLine(line)) {
      const availableClasses = classOptionsForLine(line);
      const unchangedLegacyClass = isReadOnly.value && editingCorrection.value && Boolean(line.classId);
      if (availableClasses.length === 0 && !unchangedLegacyClass) {
        errors.push(hasUnassignedClassForLine(line)
          ? `${label}: assign a campus to this class in Prices > Edit Info first.`
          : `${label}: no class is configured for the selected campus and grade.`);
      } else if (availableClasses.length > 0 && !line.classId) {
        errors.push(`${label}: select an active class for group registration.`);
      } else if (availableClasses.length > 0 && line.classId
          && !availableClasses.some(classGroup => String(classGroup.id) === String(line.classId))) {
        errors.push(`${label}: select a class for the selected campus.`);
      }
    }
    if (!isOneOnOneLine(line) && line.coursePriceOptionId && !line.startDate) {
      errors.push(`${label}: select the enrollment start date.`);
    }
    if (!isOneOnOneLine(line) && line.startDate) {
      const classGroup = selectedClassGroup(line);
      if (classGroup?.startDate && line.startDate < classGroup.startDate) {
        errors.push(`${label}: start date cannot be earlier than the class start date.`);
      }
      if (classGroup?.endDate && line.startDate > classGroup.endDate) {
        errors.push(`${label}: start date cannot be later than the class end date.`);
      }
    }
  });
  validateMoneyLines(extraFees.value, 'Other fee', errors);
  if (isSharedPrivateLesson.value) {
    participantBillings.value.slice(0, privateParticipantCount.value).forEach((billing) => {
      validateMoneyLines(billing.discounts, `${privateWorkspaceTabLabel(billing.participantIndex)} discount`, errors);
      validatePaymentLines(billing.payments, `${privateWorkspaceTabLabel(billing.participantIndex)} payment`, errors);
      billing.discounts.forEach((discount, discountIndex) => {
        if (discount.amount > 0 && !courseLines.value.some((line) => line.rowKey === discount.courseRowKey)) {
          errors.push(`${privateWorkspaceTabLabel(billing.participantIndex)} discount ${discountIndex + 1}: assign the discount to a course.`);
        }
      });
      if (courseLines.value.length > 1) {
        billing.payments.forEach((payment, paymentIndex) => {
          if (payment.amount > 0 && !payment.isHistorical && paymentAllocationRemainder(payment) !== 0) {
            errors.push(`${privateWorkspaceTabLabel(billing.participantIndex)} payment ${paymentIndex + 1}: assign the full payment amount to courses.`);
          }
        });
      }
      courseLines.value.forEach((line, lineIndex) => {
        const discount = billing.discounts
          .filter((item) => item.courseRowKey === line.rowKey)
          .reduce((total, item) => total + numeric(item.amount), 0);
        if (exceedsMoney(discount, participantCourseAmountDue(line, lineIndex, billing, false))) {
          errors.push(`${privateWorkspaceTabLabel(billing.participantIndex)}: discount cannot exceed ${courseLineOptionLabel(line, lineIndex)} tuition.`);
        }
        const allocated = billing.payments.reduce((total, payment) => total + numeric(
          courseLines.value.length === 1 ? payment.amount : payment.courseAllocations?.[line.rowKey],
        ), 0);
        if (exceedsMoney(allocated, participantCourseAmountDue(line, lineIndex, billing, true))) {
          errors.push(`${privateWorkspaceTabLabel(billing.participantIndex)}: payments exceed ${courseLineOptionLabel(line, lineIndex)} amount due.`);
        }
      });
    });
  } else {
    validateMoneyLines(discounts.value, 'Discount', errors);
    validatePaymentLines(payments.value, 'Payment', errors);
    discounts.value.forEach((discount, index) => {
      if (discount.amount > 0 && !courseLines.value.some((line) => line.rowKey === discount.courseRowKey)) {
        errors.push(`Discount row ${index + 1}: assign the discount to a course.`);
      }
    });
    courseLines.value.forEach((line, index) => {
      if (courseAmountDue(line, index) < 0) {
        errors.push(`Course row ${index + 1}: discount cannot exceed this course's amount due.`);
      }
    });
    if (courseLines.value.length > 1) {
      payments.value.forEach((payment, index) => {
        if (payment.amount > 0 && !payment.isHistorical && paymentAllocationRemainder(payment) !== 0) {
          errors.push(`Payment row ${index + 1}: assign the full payment amount to courses.`);
        }
      });
      courseLines.value.forEach((line, lineIndex) => {
        const allocated = payments.value.reduce((sum, payment) =>
          sum + numeric(payment.courseAllocations?.[line.rowKey]), 0);
        if (exceedsMoney(allocated, courseAmountDue(line, lineIndex))) {
          errors.push(`Course row ${lineIndex + 1}: allocated payments exceed this course's amount due.`);
        }
      });
    }
  }
  return errors;
}

function validatePaymentLines(lines: PaymentLine[], label: string, errors: string[]) {
  lines.forEach((payment, index) => {
    if (payment.amount <= 0) return;
    const rowLabel = `${label} row ${index + 1}`;
    if (!payment.paymentMethod) {
      errors.push(`${rowLabel}: payment method is required.`);
    }
    if (!payment.paidAt) {
      errors.push(`${rowLabel}: payment date is required.`);
    }
    if (isOtherPaymentMethod(payment.paymentMethod) && !payment.remark?.trim()) {
      errors.push(`${rowLabel}: describe the other payment method.`);
    }
  });
}

function validateRegistrationDuration(line: RegistrationCourseLine, label: string, errors: string[]) {
  if (!line.coursePriceOptionId) {
    return false;
  }
  if (isOneOnOneLine(line)) {
    const hours = numeric(line.oneOnOneHours);
    const bonusHours = numeric(line.oneOnOneBonusHours);
    const invalid = hours < 0.5 || hours > 400 || !Number.isInteger(hours * 2);
    if (invalid) {
      errors.push(`${label}: enter 0.5 to 400 hours using 0.5-hour increments.`);
    }
    const invalidBonus = bonusHours < 0 || bonusHours > 400 || !Number.isInteger(bonusHours * 2);
    if (invalidBonus) {
      errors.push(`${label}: bonus hours must be between 0 and 400 using 0.5-hour increments.`);
    }
    return invalid || invalidBonus;
  }

  const quantity = numeric(line.packageQuantity);
  const invalid = quantity <= 0;
  if (invalid) {
    errors.push(`${label}: select a package with a valid duration.`);
  }
  if (isSessionPackage(line)) {
    const totalSessions = packageSessionCount(line);
    const selectedSessions = numeric(line.groupSessionCount);
    if (!Number.isInteger(selectedSessions) || selectedSessions < 1 || selectedSessions > totalSessions) {
      errors.push(`${label}: selected sessions must be between 1 and ${totalSessions}.`);
      return true;
    }
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

function createPrivateParticipantForm(): PrivateParticipantForm {
  return {
    studentName: '',
    grade: '',
    studentClass: '',
    school: '',
    guardianName: '',
    guardianPhone: '',
    guardianPhone2: '',
    guardianEmail: '',
    pickupRequired: false,
    pickupAddress: '',
    dropoffRequired: false,
    dropoffAddress: '',
  };
}

function createEmptyStudentProfile(): RegistrationForm['student'] {
  return {
    firstName: '',
    lastName: '',
    grade: '',
    studentClass: '',
    school: '',
    guardianName: '',
    guardianPhone: '',
    guardianPhone2: '',
    guardianEmail: '',
    pickupRequired: false,
    pickupAddress: '',
    dropoffRequired: false,
    dropoffAddress: '',
  };
}

function createStudentPickerOption(
  studentId: EntityId,
  studentName: string,
  profile: {
    grade?: string;
    school?: string;
    guardianName?: string;
    guardianPhone?: string;
    guardianPhone2?: string;
    guardianEmail?: string;
    pickupRequired?: boolean;
    pickupAddress?: string;
    dropoffRequired?: boolean;
    dropoffAddress?: string;
  },
): StudentItem {
  return {
    id: studentId,
    studentNo: '',
    studentName,
    gender: '',
    guardianName: profile.guardianName || '',
    phone: profile.guardianPhone || '',
    guardianPhone: profile.guardianPhone || '',
    guardianPhone2: profile.guardianPhone2 || '',
    guardianEmail: profile.guardianEmail || '',
    grade: profile.grade || '',
    school: profile.school || '',
    pickupRequired: profile.pickupRequired,
    pickupAddress: profile.pickupAddress || '',
    dropoffRequired: profile.dropoffRequired,
    dropoffAddress: profile.dropoffAddress || '',
    status: 'ACTIVE',
  };
}

function createParticipantBilling(participantIndex: number): ParticipantBillingForm {
  const firstCourseRowKey = courseLines.value[0]?.rowKey ?? '';
  return {
    participantIndex,
    discounts: [createDiscountLine('Custom discount', 0, firstCourseRowKey)],
    payments: [createPaymentLine(firstCourseRowKey)],
  };
}

function ensurePrivateParticipantState() {
  const requiredExtraStudents = Math.max(0, privateParticipantCount.value - 1);
  while (privateParticipantForms.value.length < requiredExtraStudents) {
    privateParticipantForms.value.push(createPrivateParticipantForm());
  }
  if (privateParticipantForms.value.length > requiredExtraStudents) {
    privateParticipantForms.value.splice(requiredExtraStudents);
  }
  while (participantBillings.value.length < privateParticipantCount.value) {
    participantBillings.value.push(createParticipantBilling(participantBillings.value.length + 1));
  }
  if (participantBillings.value.length > privateParticipantCount.value) {
    participantBillings.value.splice(privateParticipantCount.value);
  }
}

function privateWorkspaceTabLabel(tab: PrivateWorkspaceTab) {
  if (tab === 'all') {
    return 'All';
  }
  return privateWorkspaceParticipantName(tab) || `Student ${tab}`;
}

function privateWorkspaceParticipantName(tab: PrivateWorkspaceTab) {
  if (tab === 'all') {
    return '';
  }
  if (tab === 1) {
    return fullStudentName.value;
  }
  return privateParticipantForms.value[tab - 2]?.studentName.trim() || '';
}

function privateWorkspaceTabStatus(tab: PrivateWorkspaceTab) {
  if (tab === 'all') {
    return '';
  }
  const participant = tab === 1 ? null : privateParticipantForms.value[tab - 2];
  const name = tab === 1 ? fullStudentName.value : participant?.studentName.trim();
  return name ? 'Ready' : 'Needs profile';
}

function displayPaymentMethod(methodCode: string) {
  return (paymentMethods.value.find((method) => method.methodCode === methodCode)?.methodName ?? methodCode) || 'Payment';
}

function paymentRefundTag(payment: PaymentLine) {
  return payment.amount < 0 || payment.paymentType === 'REFUND' ? 'Refund' : '';
}

function filterStudentPickerOptions(query: string) {
  studentPickerQuery.value = query;
}

function handleStudentPickerVisibility(visible: boolean) {
  if (!visible) {
    studentPickerQuery.value = '';
  }
}

function clearSelectedStudent(index: number) {
  if (index === 1) {
    Object.assign(form.student, createEmptyStudentProfile());
    form.pickupFeeCharged = false;
    form.pickupFeeAmount = 0;
    form.dropoffFeeCharged = false;
    form.dropoffFeeAmount = 0;
    reusableStudent.value = null;
    historicalPrimaryStudentId.value = undefined;
    studentReuseStatus.value = 'idle';
    return;
  }
  const participant = privateParticipantForms.value[index - 2];
  if (participant) {
    Object.assign(participant, createPrivateParticipantForm());
  }
}

function selectPrimaryPrivateParticipant(student: StudentItem) {
  applyStudentProfile(student);
}

function selectPrivateParticipant(index: number, student: StudentItem) {
  const participant = privateParticipantForms.value[index - 2];
  if (!participant) {
    return;
  }
  participant.studentId = student.id;
  participant.studentName = student.studentName || '';
  participant.grade = student.grade || '';
  participant.studentClass = student.studentClass || '';
  participant.school = student.school || '';
  participant.guardianName = student.guardianName || '';
  participant.guardianPhone = student.guardianPhone || student.phone || '';
  participant.guardianPhone2 = student.guardianPhone2 || '';
  participant.guardianEmail = student.guardianEmail || '';
  participant.pickupRequired = Boolean(student.pickupRequired);
  participant.pickupAddress = student.pickupAddress || '';
  participant.dropoffRequired = Boolean(student.dropoffRequired);
  participant.dropoffAddress = student.dropoffAddress || '';
}

function addParticipantDiscount(index: number, courseRowKey?: string) {
  const billing = participantBillings.value[index - 1];
  billing?.discounts.push(createDiscountLine('Custom discount', 0, courseRowKey ?? courseLines.value[0]?.rowKey ?? ''));
}

function removeParticipantDiscount(index: number, lineIndex: number) {
  const billing = participantBillings.value[index - 1];
  if (billing?.discounts[lineIndex]) {
    billing.discounts.splice(lineIndex, 1);
  }
}

function addParticipantPayment(index: number, courseRowKey?: string) {
  const billing = participantBillings.value[index - 1];
  billing?.payments.push(createPaymentLine(courseRowKey));
}

function removeParticipantPayment(index: number, lineIndex: number) {
  const billing = participantBillings.value[index - 1];
  if (!billing || billing.payments.length <= 1) return;
  const editable = billing.payments.filter((payment) => !isRefundPayment(payment));
  const target = editable[lineIndex];
  if (!target) return;
  const actualIndex = billing.payments.indexOf(target);
  if (actualIndex >= 0) {
    billing.payments.splice(actualIndex, 1);
  }
}

function toPrivateParticipantPayload(formValue: PrivateParticipantForm) {
  const pickupAddress = formValue.pickupAddress.trim();
  const dropoffAddress = formValue.dropoffAddress.trim();
  return {
    studentId: formValue.studentId,
    studentName: formValue.studentName.trim(),
    grade: formValue.grade.trim() || undefined,
    studentClass: formValue.studentClass.trim() || undefined,
    school: formValue.school.trim() || undefined,
    guardianName: formValue.guardianName.trim() || undefined,
    guardianPhone: formValue.guardianPhone.trim() || undefined,
    guardianPhone2: formValue.guardianPhone2.trim() || undefined,
    guardianEmail: formValue.guardianEmail.trim() || undefined,
    pickupRequired: formValue.pickupRequired || Boolean(pickupAddress),
    pickupAddress: pickupAddress || undefined,
    dropoffRequired: formValue.dropoffRequired || Boolean(dropoffAddress),
    dropoffAddress: dropoffAddress || undefined,
  };
}

function toPrimaryParticipantPayload() {
  const pickupAddress = form.student.pickupAddress.trim();
  const dropoffAddress = form.student.dropoffAddress.trim();
  return {
    studentId: reusableStudent.value?.id ?? historicalPrimaryStudentId.value,
    studentName: fullStudentName.value,
    grade: form.student.grade.trim() || undefined,
    studentClass: form.student.studentClass.trim() || undefined,
    school: form.student.school.trim() || undefined,
    guardianName: form.student.guardianName.trim() || undefined,
    guardianPhone: form.student.guardianPhone.trim() || undefined,
    guardianPhone2: form.student.guardianPhone2.trim() || undefined,
    guardianEmail: form.student.guardianEmail.trim() || undefined,
    pickupRequired: form.student.pickupRequired || undefined,
    pickupAddress: pickupAddress || undefined,
    dropoffRequired: form.student.dropoffRequired || undefined,
    dropoffAddress: dropoffAddress || undefined,
  };
}

function buildPayload(): RegistrationIntakePayload {
  const campus = selectedCampus.value;
  const pickupAddress = form.student.pickupAddress.trim();
  const dropoffAddress = form.student.dropoffAddress.trim();
  const useLegacyOrderTransportFields = usesLegacyOrderTransportFields.value && !isCreditTopUpOnly.value;
  const payload: RegistrationIntakePayload = {
    student: {
      studentId: reusableStudent.value?.id ?? historicalPrimaryStudentId.value,
      studentName: fullStudentName.value,
      // 下拉框选中的学生仍沿用主档案字段；注册页未编辑的字段不能因重新提交而被清空。
      englishName: reusableStudent.value?.englishName || undefined,
      gender: reusableStudent.value?.gender || undefined,
      birthDate: reusableStudent.value?.birthDate || undefined,
      sourceChannel: reusableStudent.value?.sourceChannel || undefined,
      guardianRelationType: reusableStudent.value?.guardianRelationType || undefined,
      grade: form.student.grade.trim() || undefined,
      studentClass: form.student.studentClass.trim() || undefined,
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
    participants: hasOneOnOneLine.value && privateParticipantCount.value > 1
      ? [
        toPrimaryParticipantPayload(),
        ...privateParticipantForms.value.map(toPrivateParticipantPayload),
      ]
      : undefined,
    participantBillings: isSharedPrivateLesson.value
      ? participantBillings.value.map((billing) => toParticipantBillingPayload(billing))
      : undefined,
    campusId: campus?.campusId,
    campusName: campus?.campusName,
    signupDate: form.signupDate,
    renewPackageId: stringToEntityId(renewPackageId.value),
    renewClassEnrollmentId: stringToEntityId(renewClassEnrollmentId.value),
    handledByName: handledByName.value,
    currencyCode: 'USD',
    creditTopUpAmount: creditTopUpAmount.value > 0 ? roundMoney(creditTopUpAmount.value) : undefined,
    items: isCreditTopUpOnly.value ? [] : courseLines.value.map((line, index) => ({
      orderItemId: line.orderItemId,
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
      startDate: isOneOnOneLine(line) ? undefined : line.startDate,
      packageUnit: line.packageUnit,
      packageQuantity: line.packageQuantity,
      packageTotalPrice: line.packageTotalPrice,
      attendanceOption: line.attendanceOption,
      attendanceScheduleJson: line.attendanceScheduleJson,
      priceAmount: roundMoney(line.priceAmount),
      originalPriceAmount: line.originalPriceAmount,
      manualPriceOverride: line.manualPriceOverride,
      discountAmount: isSharedPrivateLesson.value ? undefined : courseDiscountAmount(line.rowKey),
      oneOnOneHours: isOneOnOneLine(line) ? line.oneOnOneHours : undefined,
      oneOnOneBonusHours: isOneOnOneLine(line) && numeric(line.oneOnOneBonusHours) > 0 ? line.oneOnOneBonusHours : undefined,
      openingUsedHours: isOneOnOneLine(line) && numeric(line.openingUsedHours) > 0 ? line.openingUsedHours : undefined,
      oneOnOneUnits: isOneOnOneLine(line) ? line.oneOnOneUnits : undefined,
      sessions: isOneOnOneLine(line) ? undefined : line.groupSessionCount,
      purchasedMonths: !isOneOnOneLine(line) && line.packageUnit === 'MONTH' ? line.groupPurchasedMonths : undefined,
      startMonth: isOneOnOneLine(line) ? undefined : line.groupRegistrationStartMonth,
      endMonth: isOneOnOneLine(line) ? undefined : line.groupRegistrationEndMonth,
      teacherId: undefined,
      // Boolean false 必须随 edit receipt 一起提交，后端需要用它清除旧课程接送快照。
      pickupRequired: line.pickupRequired,
      pickupAddress: effectiveCoursePickupAddress(line) || undefined,
      dropoffRequired: line.dropoffRequired,
      dropoffAddress: effectiveCourseDropoffAddress(line) || undefined,
      pickupFeeCharged: line.pickupRequired,
      pickupFeeAmount: line.pickupRequired ? roundMoney(line.pickupFeeAmount) : undefined,
      dropoffFeeCharged: line.dropoffRequired,
      dropoffFeeAmount: line.dropoffRequired ? roundMoney(line.dropoffFeeAmount) : undefined,
      serviceFees: line.serviceFees.filter((fee) => fee.amount > 0).map((fee) => ({
        label: fee.label.trim(),
        amount: roundMoney(fee.amount),
      })),
      remark: line.remark,
    })),
    pickupFeeCharged: useLegacyOrderTransportFields ? form.student.pickupRequired : undefined,
    pickupFeeAmount: useLegacyOrderTransportFields ? roundMoney(effectivePickupFeeAmount.value) : undefined,
    dropoffFeeCharged: useLegacyOrderTransportFields ? form.student.dropoffRequired : undefined,
    dropoffFeeAmount: useLegacyOrderTransportFields ? roundMoney(effectiveDropoffFeeAmount.value) : undefined,
    extraFeeAmount: isCreditTopUpOnly.value || hasExplicitCourseFees.value ? undefined : roundMoney(extraFeeTotal.value),
    extraFees: isCreditTopUpOnly.value || hasExplicitCourseFees.value ? [] : buildMoneyPayload(extraFees.value),
    discountAmount: isCreditTopUpOnly.value ? 0 : roundMoney(discountTotal.value),
    discounts: isCreditTopUpOnly.value || isSharedPrivateLesson.value
      ? []
      : buildCourseDiscountPayload(),
    payments: [
      ...(isSharedPrivateLesson.value ? [] : payments.value
        .filter((payment) => payment.amount > 0 && !isRefundPayment(payment))
        .map((payment) => ({
          paymentMethod: payment.paymentMethod,
          amount: roundMoney(payment.amount),
          paidAt: toLocalDateTime(payment.paidAt),
          handledByName: handledByName.value,
          remark: payment.remark?.trim() || undefined,
          allocations: buildCoursePaymentAllocations(payment),
        }))),
      ...(creditTopUpAmount.value > 0 ? [{
        paymentMethod: creditTopUpPayment.value.paymentMethod,
        amount: roundMoney(creditTopUpAmount.value),
        paidAt: toLocalDateTime(creditTopUpPayment.value.paidAt),
        handledByName: handledByName.value,
        remark: creditTopUpPayment.value.remark?.trim() || form.remark.trim() || 'Student Credit top-up',
        creditTopUp: true,
      }] : []),
    ],
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

function toParticipantBillingPayload(billing: ParticipantBillingForm): RegistrationParticipantBillingPayload {
  return {
    participantIndex: billing.participantIndex,
    discountAmount: roundMoney(sumNumbers(billing.discounts.map((line) => line.amount))),
    discounts: buildParticipantDiscountPayload(billing.discounts),
    payments: billing.payments
      .filter((payment) => payment.amount > 0 && !isRefundPayment(payment))
      .map((payment) => ({
        paymentMethod: payment.paymentMethod,
        amount: roundMoney(payment.amount),
        paidAt: toLocalDateTime(payment.paidAt),
        participantIndex: billing.participantIndex,
        handledByName: handledByName.value,
        remark: payment.remark?.trim() || undefined,
        allocations: buildCoursePaymentAllocations(payment),
      })),
  };
}

function buildParticipantDiscountPayload(lines: DiscountLine[]) {
  return lines
    .filter((line) => line.amount > 0)
    .map((line) => ({
      label: line.label.trim(),
      amount: roundMoney(line.amount),
      orderItemLineNo: courseLines.value.findIndex((course) => course.rowKey === line.courseRowKey) + 1,
    }));
}

function buildMoneyPayload(lines: MoneyLine[]) {
  return lines
    .filter((line) => line.amount > 0)
    .map((line) => ({
      label: line.label.trim(),
      amount: roundMoney(line.amount),
    }));
}

function buildCourseDiscountPayload() {
  return discounts.value
    .filter((discount) => discount.amount > 0)
    .map((discount) => {
      const lineNo = courseLines.value.findIndex((line) => line.rowKey === discount.courseRowKey) + 1;
      return {
        label: discount.label.trim(),
        amount: roundMoney(discount.amount),
        orderItemLineNo: lineNo > 0 ? lineNo : undefined,
      };
    });
}

/**
 * 点击「New Registration」：不离开页面，清空 URL 模式参数（只读收据/续费），
 * 重置全部表单状态并重新拉取基础数据，等价于离开再切回报名页的效果。
 */
async function startNewRegistration() {
  if (formHasUnsavedContent()) {
    try {
      await ElMessageBox.confirm(
        'The current form will be cleared. Start a new registration?',
        'New Registration',
        { confirmButtonText: 'Start new', cancelButtonText: 'Cancel', type: 'warning' },
      );
    } catch {
      return;
    }
  }
  editingCorrection.value = false;
  await router.replace({ name: 'registration' });
  resetForNextRegistration();
  await loadInitialData();
}

/** 表单里是否有值得保护的填写内容（学生名、课程行、收据更正中、查看历史订单）。 */
function formHasUnsavedContent() {
  if (editingCorrection.value || isReadOnly.value || isRenewalMode.value) {
    return true;
  }
  if (fullStudentName.value.trim()
      || form.student.guardianPhone.trim()
      || reusableStudent.value
      || creditTopUpAmount.value > 0) {
    return true;
  }
  return courseLines.value.some((line) => line.courseProductId || line.coursePriceOptionId);
}

function resetForNextRegistration() {
  validationErrors.value = [];
  form.campusKey = '';
  form.signupDate = formatDate(new Date());
  form.pickupFeeCharged = false;
  form.pickupFeeAmount = 0;
  form.dropoffFeeCharged = false;
  form.dropoffFeeAmount = 0;
  form.remark = '';
  creditTopUpAmount.value = 0;
  historicalCreditTopUpAmount.value = 0;
  historicalCreditTopUpPayment.value = null;
  creditTopUpPayment.value = {
    ...createPaymentLine(),
    isCreditTopUp: true,
    paymentMethod: '',
    paidAt: today,
  };
  studentCreditBalance.value = 0;
  form.student.firstName = '';
  form.student.lastName = '';
  form.student.grade = '';
  form.student.studentClass = '';
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
  privateParticipantCount.value = 1;
  privateParticipantForms.value = [];
  privateParticipantPriceSnapshots.value = [];
  participantBillings.value = [];
  activePrivateWorkspaceTab.value = 'all';
  studentPickerQuery.value = '';
  extraFees.value = [createMoneyLine('Other fee')];
  discounts.value = [createDiscountLine()];
  payments.value = [createPaymentLine(courseLines.value[0]?.rowKey)];
  studentReuseStatus.value = 'idle';
  reusableStudent.value = null;
  historicalPrimaryStudentId.value = undefined;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function findOption(id: EntityId | '') {
  return priceOptions.value.find((option) => String(option.id) === String(id));
}

function getQueryValue(value: unknown): string | undefined {
  if (Array.isArray(value)) {
    return value[0] == null ? undefined : String(value[0]);
  }
  return value == null || value === '' ? undefined : String(value);
}

function stringToEntityId(value?: string): EntityId | undefined {
  return value || undefined;
}

function chooseGradeForProduct(productId: EntityId | undefined, preferredGrade?: string) {
  const options = sortGradeOptionsForProduct(productId, uniqueValues(
    priceOptionsWithProduct.value
      .filter((option) => !productId || String(option.courseProductId) === String(productId))
      .map((option) => option.gradeBand || 'General'),
  ));
  if (preferredGrade && options.includes(preferredGrade)) {
    return preferredGrade;
  }
  return options[0] ?? '';
}

function nextRenewalStartDate(expiryDate?: string) {
  if (!expiryDate) {
    return today;
  }
  const expiry = new Date(`${expiryDate}T00:00:00`);
  const current = new Date(`${today}T00:00:00`);
  if (Number.isNaN(expiry.getTime()) || expiry < current) {
    return today;
  }
  expiry.setDate(expiry.getDate() + 1);
  return formatDate(expiry);
}

function formatClassLabel(item: ClassGroupItem) {
  const actualClass = item.actualClass?.trim();
  const className = item.className?.trim();
  return className || actualClass || item.gradeBand || 'Class';
}

function formatCourseType(type: string) {
  const configured = courseCategoryLabels.value[type];
  if (configured) {
    return configured;
  }
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

function totalOneOnOneHours(line: RegistrationCourseLine) {
  return numeric(line.oneOnOneHours) + numeric(line.oneOnOneBonusHours);
}

function roundMoney(value: number) {
  return Math.round((numeric(value) + Number.EPSILON) * 100) / 100;
}

/** 前端仅预览自动报价；后端用 BigDecimal 重算并校验同一整美元规则。 */
function ceilRegistrationAmount(value: number) {
  return Math.ceil(numeric(value) - 1e-9);
}

// 与后端 splitSharedTuition 一致：新订单按整美元、历史订单按分均分，最后一位承接余数。
function splitShareAmount(total: number, count: number, index: number, wholeDollars = false) {
  const share = wholeDollars
    ? Math.floor(numeric(total) / Math.max(1, count))
    : Math.floor((numeric(total) * 100) / Math.max(1, count)) / 100;
  if (index >= count - 1) {
    return roundMoney(numeric(total) - share * (count - 1));
  }
  return share;
}

function formatMoney(value: number | undefined) {
  return formatRoundedUsd(numeric(value ?? 0));
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
    const parsed = JSON.parse(preprocessLargeIds(value));
    return isRecord(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function parseMoneyDetail(value: unknown): unknown[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (typeof value !== 'string' || !value.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(preprocessLargeIds(value));
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
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
      isHistorical: true,
      courseAllocations: {},
      handledByName: getString(payment, 'handledByName') || form.handledByName,
      remark: getString(payment, 'remark'),
      paymentType: getString(payment, 'paymentType'),
    }))
    .filter((payment) => payment.amount !== 0);
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

.paper-form--readonly,
.paper-form--readonly * {
  color: #000 !important;
}

.paper-form--readonly {
  --el-disabled-text-color: #000;
  --el-text-color-placeholder: #000;
  --el-input-text-color: #000;
  --el-input-placeholder-color: #000;
}

:deep(.paper-form--readonly input),
:deep(.paper-form--readonly textarea),
:deep(.paper-form--readonly .el-input__inner),
:deep(.paper-form--readonly .el-select__selected-item),
:deep(.paper-form--readonly .el-select__placeholder),
:deep(.paper-form--readonly .el-checkbox__label) {
  color: #000 !important;
  -webkit-text-fill-color: #000 !important;
  opacity: 1 !important;
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

.campus-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
}

.campus-line input {
  width: 17px;
  height: 17px;
  margin: 0;
  accent-color: #111;
}

.student-lines {
  margin-top: 6px;
  font-size: 17px;
}

.private-receipt-student-summary {
  display: grid;
  gap: 8px;
  margin-top: 6px;
  padding: 10px 12px;
  border-top: 1px solid #d8e3f0;
  border-bottom: 1px solid #d8e3f0;
  background: #fbfdff;
}

.private-receipt-student-summary__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.private-receipt-student-summary__label {
  color: #60758f;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.private-receipt-student-summary__header strong {
  color: #1f3552;
  font-size: 12px;
}

.private-receipt-student-summary__list {
  display: grid;
  gap: 0;
}

.private-receipt-student-summary__row {
  display: grid;
  grid-template-columns: minmax(190px, 1.15fr) minmax(300px, 1.85fr);
  gap: 12px;
  align-items: center;
  padding: 7px 0;
  border-top: 1px solid #edf2f7;
}

.private-receipt-student-summary__identity {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.private-receipt-student-summary__identity > span {
  flex: 0 0 auto;
  color: #8a9bb0;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
}

.private-receipt-student-summary__identity strong {
  min-width: 0;
  overflow: hidden;
  color: #1f3552;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.private-receipt-student-summary__financials {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  color: #70839a;
  font-size: 11px;
}

.private-receipt-student-summary__financials strong {
  color: #314a68;
  font-size: 12px;
  font-weight: 700;
}

.private-receipt-student-summary__payments {
  grid-column: 2;
  display: block;
  color: #74879d;
  font-size: 13px;
  line-height: 1.7;
}

.private-receipt-student-summary__payments span {
  display: block;
  overflow-wrap: anywhere;
}

.private-lesson-switcher {
  width: 100%;
  max-width: 920px;
  margin: 0 0 10px;
  overflow: hidden;
  border: 1px solid #d8e3f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.045);
}

.private-lesson-switcher__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 54px;
  padding: 9px 12px;
  background: #f8fbff;
}

.private-lesson-switcher__header > div {
  display: grid;
  gap: 3px;
}

.private-lesson-switcher__header strong {
  color: #1f3552;
  font-size: 14px;
  font-weight: 700;
}

.private-lesson-switcher__header small {
  color: #6e819a;
  font-size: 12px;
}

.section-kicker {
  color: #3182ce;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.private-lesson-mode-select {
  display: inline-flex !important;
  flex: 0 0 auto;
  flex-wrap: nowrap !important;
  align-items: stretch;
  white-space: nowrap;
}

.private-lesson-mode-select :deep(.el-radio-button) {
  display: inline-flex !important;
  flex: 0 0 auto;
}

.private-lesson-mode-select :deep(.el-radio-button__inner) {
  min-width: 76px;
  height: 30px;
  padding: 6px 12px;
  border-color: #cbd8e6;
  background: #fff;
  color: #526a84;
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
  box-shadow: none;
}

.private-lesson-mode-select :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: 5px 0 0 5px;
}

.private-lesson-mode-select :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 5px 5px 0;
}

.private-lesson-mode-select :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: #2f80c9;
  background: #2f80c9;
  color: #fff;
  box-shadow: -1px 0 0 0 #2f80c9;
}

.private-lesson-mode-select :deep(.el-radio-button__inner:hover) {
  color: #216ba8;
}

.private-lesson-mode-readonly {
  flex: 0 0 auto;
}

.private-lesson-tabs {
  display: flex;
  gap: 4px;
  margin: 0;
  overflow-x: auto;
  padding: 0 8px;
  border-bottom: 1px solid #d8e3f0;
  background: #fff;
}

.private-lesson-tab {
  display: grid;
  gap: 2px;
  min-width: 76px;
  padding: 6px 10px 7px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #6e819a;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.private-lesson-tab span {
  overflow: hidden;
  color: inherit;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.private-lesson-tab small {
  color: #9aa9ba;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
  overflow: hidden;
}

.private-lesson-tab--active {
  border-bottom-color: #409eff;
  color: #1f67a8;
  background: #eef6ff;
}

.private-lesson-tab--active small {
  color: #409eff;
}

.private-lesson-shared-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding: 7px 0 8px;
  color: #627895;
  font-size: 12px;
}

.private-lesson-shared-summary span + span::before {
  margin-right: 18px;
  color: #c1cfdd;
  content: '|';
}

.registration-student-picker {
  width: 100%;
  max-width: 920px;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #d8e3f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.045);
}

.registration-student-picker__heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}

.registration-student-picker__heading strong {
  color: #1f3552;
  font-size: 13px;
}

.registration-student-picker__heading span {
  color: #7f91a7;
  font-size: 11px;
}

.student-credit-balance {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  margin-left: auto;
  padding: 5px 9px;
  border: 1px solid #c7d9ef;
  border-radius: 7px;
  background: #f3f8ff;
  color: #1f4f82 !important;
  white-space: nowrap;
}

.student-credit-balance small {
  color: #6282a6 !important;
  font-size: 10px !important;
  font-weight: 700;
  text-transform: uppercase;
}

.student-credit-balance strong {
  color: #174a80 !important;
  font-size: 14px !important;
  font-variant-numeric: tabular-nums;
}

.student-credit-balance--loading {
  opacity: 0.65;
}

.student-credit-inline {
  margin-left: 4px;
  padding: 3px 7px;
  border-radius: 999px;
  background: #eef6ff;
  color: #2c5d8f;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.policies-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(250px, 0.48fr);
  gap: 18px;
  align-items: stretch;
}

.credit-top-up-compact {
  display: grid;
  align-content: start;
  gap: 9px;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid #c7d9ef;
  border-radius: 8px;
  background: linear-gradient(135deg, #f7fbff, #eef6ff);
  color: #5e7692;
}

.credit-top-up-compact__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #d6e4f3;
}

.credit-top-up-compact__header > div {
  display: grid;
  gap: 2px;
}

.credit-top-up-compact__header strong {
  color: #173b63;
  font-size: 14px;
}

.credit-top-up-compact__header > span {
  color: #557594;
  font-size: 11px;
  white-space: nowrap;
}

.credit-top-up-compact p {
  margin: 0;
  color: #6c829b;
  font-size: 11px;
  line-height: 1.35;
}

.credit-top-up-compact__input {
  display: grid;
  gap: 4px;
  color: #5e7692;
  font-size: 11px;
  font-weight: 700;
}

.credit-top-up-compact__input :deep(.el-input-number) {
  width: 100%;
}

.credit-top-up-compact__summary {
  color: #174a80;
  font-size: 12px;
  font-weight: 700;
}

.registration-student-picker__select {
  width: 100%;
}

.registration-student-picker__option {
  line-height: 1.2;
}

.registration-student-picker__option strong {
  color: #1f3552;
}

.full-line,
.two-col-line label,
.payment-line {
  display: flex;
  align-items: center;
  min-height: 37px;
  gap: 10px;
}

.full-line > span,
.two-col-line span {
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

.two-col-line label {
  min-width: 0;
}

.line-input,
.line-date,
.line-select {
  flex: 1;
  min-width: 0;
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
:deep(.line-select .el-select__wrapper),
:deep(.table-select .el-select__wrapper),
:deep(.table-price .el-input__wrapper) {
  box-shadow: none !important;
  border-radius: 0;
  background: transparent;
}

:deep(.line-input .el-input__wrapper),
:deep(.line-date .el-input__wrapper),
:deep(.line-select .el-select__wrapper) {
  border-bottom: 2px solid #222;
}

:deep(.line-date.el-date-editor.el-input),
:deep(.line-date.el-date-editor.el-input__wrapper) {
  width: 100%;
  min-width: 0;
}

:deep(.line-select.el-select) {
  width: 100%;
}

:deep(.line-input .el-input__inner),
:deep(.line-date .el-input__inner),
:deep(.line-select .el-select__placeholder),
:deep(.table-price .el-input__inner) {
  color: #111;
  font-size: 16px;
  font-family: inherit;
}

.payment-line {
  margin-top: 1px;
  border-bottom: 2px solid #222;
}

.payment-method-block {
  display: grid;
  flex: 1;
  min-width: 0;
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

.payment-details-print {
  display: none;
}

.payment-history {
  display: grid;
  gap: 8px;
  margin-top: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #222;
  font-size: 14px;
}

.payment-history > div {
  display: grid;
  grid-template-columns: minmax(86px, 1fr) minmax(150px, 1.6fr) minmax(78px, .9fr) minmax(88px, 1fr);
  gap: 4px 12px;
  align-items: start;
  padding: 2px 0;
  line-height: 1.35;
}

.payment-history small {
  color: #4b5563;
}

.payment-history__refund {
  color: #b42318;
}

.payment-history__refund strong {
  color: #b42318;
}

.refund-line {
  color: #b42318;
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

.course-picker-control {
  display: flex;
  align-items: center;
  gap: 4px;
}

.course-picker-control :deep(.el-select) {
  flex: 1;
  min-width: 0;
}

.course-picker-control .remove-row {
  flex: 0 0 18px;
}

.print-only {
  display: none;
}

.student-phone-display {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 4px;
  border-bottom: 2px solid #222;
}

.print-course-value,
.print-course-price {
  color: #111;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.printed-student-name {
  display: none;
  flex: 1;
  min-height: 28px;
  padding: 0 4px;
  border-bottom: 2px solid #111;
  color: #111;
  font-size: 17px;
  font-weight: 600;
  line-height: 28px;
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

.detail-row--group {
  grid-template-columns: minmax(120px, 0.8fr) minmax(150px, 1fr);
}

.teacher-class-line {
  display: grid;
  gap: 4px;
}

.registration-class-select-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.registration-class-campus-toggle {
  padding: 0 4px;
  font-size: 11px;
  white-space: nowrap;
}

.course-control {
  display: grid;
  gap: 3px;
  min-width: 0;
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
  grid-template-columns: minmax(0, 1fr);
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

.service-charge-toggle--muted {
  color: #64748b;
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

.adjustment-column--fees .adjustment-row:not(.adjustment-row--service) {
  grid-template-columns: minmax(0, 1fr) 110px auto;
}

.adjustment-column--discounts .adjustment-row {
  grid-template-columns: 22px minmax(0, 1fr) 110px;
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

.adjustment-line-list,
.payment-record-list {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.course-adjustment-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
  gap: 8px;
  min-width: 0;
}

.adjustment-column--fees .course-adjustment-list {
  grid-template-columns: minmax(0, 1fr);
}

.adjustment-column--discounts .course-adjustment-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.adjustment-column--discounts {
  container-type: inline-size;
}

.course-adjustment-card {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid #dbe3ec;
  border-radius: 8px;
  background: #fbfdff;
}

.course-adjustment-card--discount {
  border-color: #d6e6da;
  background: #fbfefb;
}

.course-adjustment-card--payment {
  border-color: #ddd7ed;
  background: #fdfcff;
}

.course-adjustment-card--payment.course-adjustment-card--readonly {
  background: #f7f7fb;
  opacity: 0.82;
}

.course-adjustment-card--fees {
  border-color: #d7e2ef;
  background: #fbfdff;
}

.course-service-list {
  display: grid;
  gap: 0;
}

.course-service-row {
  display: grid;
  gap: 7px;
  padding: 8px 0 10px;
  border-bottom: 1px solid #e8edf2;
}

.course-service-row:last-child {
  border-bottom: 0;
  padding-bottom: 4px;
}

.course-service-row__controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.course-service-row__fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 86px;
  gap: 7px;
  min-width: 0;
}

.course-service-field {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.course-service-field span {
  color: #7b8795;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.course-service-field--amount :deep(.el-input-number) {
  width: 100%;
}

.paper-form :deep(.money-amount-input) {
  width: 100%;
  min-width: 0;
}

.course-service-other-title {
  padding: 8px 0 2px;
  color: #64748b;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.course-service-field :deep(.el-input),
.course-service-field :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.course-adjustment-card__header,
.course-adjustment-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.course-adjustment-card__header {
  padding-bottom: 7px;
  border-bottom: 1px solid #e8edf2;
}

.course-adjustment-card__title {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.course-adjustment-card__title > span {
  color: #718096;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.course-adjustment-card__title strong {
  overflow: hidden;
  color: #253247;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-adjustment-card__context {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.course-adjustment-card__lines {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.course-adjustment-card__line {
  grid-template-columns: 22px minmax(0, 1fr) 100px;
}

.course-adjustment-card__line--custom {
  grid-template-columns: 22px minmax(0, 1.2fr) minmax(0, 1fr) 100px;
}

.adjustment-column--discounts .course-adjustment-card__line--custom {
  grid-template-columns: 22px minmax(0, 1.2fr) minmax(0, 1fr) 100px;
}

.course-adjustment-card__line .discount-preset-control,
.course-adjustment-card__line .discount-name-input,
.course-adjustment-card__line :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.course-adjustment-card__empty {
  margin: 0;
  padding: 4px 0;
  color: #7b8795;
  font-size: 11px;
}

.course-adjustment-card__footer {
  padding-top: 6px;
  border-top: 1px solid #e8edf2;
  color: #475569;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.course-adjustment-card__footer :deep(.el-button) {
  min-height: 22px;
  margin-left: -4px;
}

.payment-record-list {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.course-adjustment-list--payments {
  grid-template-columns: 1fr;
}

.course-payment-list {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.course-payment-entry {
  min-width: 0;
  padding: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #fff;
}

.course-payment-entry__fields {
  display: grid;
  grid-template-columns:
    minmax(66px, 0.55fr)
    minmax(120px, 1.3fr)
    minmax(90px, 0.85fr)
    minmax(120px, 1fr)
    22px;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.course-payment-entry__fields > * {
  min-width: 0;
}

.course-payment-entry__number {
  color: #334155;
  font-size: 11px;
  font-weight: 600;
}

.course-payment-entry__number small {
  margin-left: 4px;
  color: #64748b;
  font-size: 10px;
  font-weight: 500;
}

.course-payment-entry--historical {
  border-color: #cbd5e1;
}

.course-payment-entry__history {
  grid-column: 2 / 3;
  overflow: hidden;
  color: #7b8795;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-payment-entry__fields :deep(.el-select),
.course-payment-entry__fields :deep(.el-input-number),
.course-payment-entry__fields :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}

.course-payment-entry__fields :deep(.el-date-editor) {
  max-width: 100%;
}

.course-payment-refund-list {
  display: grid;
  gap: 4px;
  margin-top: 8px;
  padding: 7px 8px;
  border: 1px dashed #f0b4b4;
  border-radius: 6px;
  background: #fff8f8;
  color: #9f4141;
  font-size: 11px;
}

.course-payment-refund-total {
  color: #b42318;
}

.course-payment-refund-entry {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.course-payment-refund-entry span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-payment-refund-entry strong {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}

.payment-amount-field {
  position: relative;
  min-width: 0;
}

.payment-limit-hint {
  position: absolute;
  top: calc(100% + 2px);
  left: 0;
  z-index: 3;
  padding: 2px 5px;
  border: 1px solid #f5c2c7;
  border-radius: 4px;
  background: #fff5f5;
  color: #b42318;
  font-size: 10px;
  line-height: 1.3;
  white-space: nowrap;
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

@container (max-width: 900px) {
  .adjustment-column--discounts .course-adjustment-list {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 1100px) {
  .adjustment-column--discounts .course-adjustment-list {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .private-lesson-switcher__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .private-lesson-tabs {
    margin-right: -8px;
    margin-left: -8px;
  }

  .private-lesson-tab {
    min-width: 88px;
    padding-right: 10px;
    padding-left: 10px;
  }

  .private-receipt-student-summary {
    gap: 5px;
    margin-top: 3px;
    padding: 6px 8px;
  }

  .private-receipt-student-summary__row {
    grid-template-columns: 1fr;
    gap: 4px;
    padding: 5px 0;
  }

  .private-receipt-student-summary__payments {
    grid-column: auto;
  }

  .intake-adjustment-panel {
    grid-template-columns: 1fr;
  }

  .policies-layout {
    grid-template-columns: 1fr;
  }

  .adjustment-column--payments {
    grid-column: auto;
    width: 100%;
  }

  .adjustment-column--discounts .course-adjustment-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .adjustment-column--fees .adjustment-row:not(.adjustment-row--service) {
    grid-template-columns: minmax(0, 1fr) 110px auto;
  }

  .adjustment-column--discounts .adjustment-row {
    grid-template-columns: 22px minmax(0, 1fr) 110px;
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

  .course-adjustment-card__line {
    grid-template-columns: 22px minmax(0, 1fr) 92px;
  }

  .adjustment-column--discounts .course-adjustment-card__line--custom {
    grid-template-columns: 22px minmax(0, 1fr) minmax(0, 1fr) 92px;
  }

  .course-adjustment-card__header,
  .course-adjustment-card__footer {
    align-items: flex-start;
  }

  .adjustment-row--payment :deep(.el-date-editor) {
    grid-column: 1 / 3;
    width: 100%;
  }

  .course-payment-entry__fields {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 22px;
  }

  .course-payment-entry__number {
    grid-column: 1 / -1;
  }

  .course-payment-entry__history {
    grid-column: 1 / 3;
  }

  .course-payment-entry__fields :deep(.el-date-editor) {
    grid-column: 1 / 3;
  }

  .course-payment-entry__fields > .remove-adjustment-row {
    grid-column: 3;
    grid-row: 2 / span 2;
    align-self: center;
  }

  .course-payment-entry .payment-other-method {
    grid-column: 1 / -1;
  }
}

.policies {
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

.fee-totals__refund strong {
  color: #b42318;
}

.payment-detail-refund {
  color: #b42318;
}

.fee-totals em {
  grid-column: 1 / -1;
  color: #92400e;
  font-size: 12px;
  font-style: normal;
}

.fee-totals__warning {
  color: #b45309 !important;
  font-weight: 600;
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
  .registration-student-picker,
  .submit-bar {
    max-width: 920px;
  }
}

@media (max-width: 960px) {
  .registration-desk,
  .registration-flow,
  .registration-student-picker,
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

@page {
  size: Letter portrait;
  margin: 0.3in 0.3in 0.3in 0.6in;
}

@media print {
  .no-print,
  .screen-only,
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
    color: #111;
    print-color-adjust: exact;
  }

  .paper-form,
  .paper-form * {
    color: #000 !important;
  }

  .paper-form {
    width: 100%;
    min-height: auto;
    box-shadow: none;
    padding: 0;
    font-size: 11.5px;
  }

  .form-header {
    min-height: 48px;
    padding-right: 84px;
  }

  .form-title-block h1 {
    font-size: 20px;
  }

  .form-title-block h2 {
    margin-top: 2px;
    font-size: 11px;
  }

  .form-logo {
    top: 0;
    right: 0;
    width: 70px;
    height: 70px;
  }

  .form-tax-id {
    top: 72px;
    right: 0;
    min-width: 70px;
    font-size: 10px;
  }

  .contact-block {
    margin-top: 2px;
    font-size: 10px;
    line-height: 1.15;
  }

  .contact-block strong {
    margin-bottom: 2px;
    font-size: 11px;
  }

  .student-phone-input {
    display: none !important;
  }

  .student-phone-print {
    display: inline-flex !important;
    align-items: center;
    min-height: 22px;
    padding: 0 3px;
    border-bottom: 1px solid #222;
    font-size: 11px;
  }

  .phone-line {
    margin-bottom: 1px;
  }

  .campus-line {
    display: flex;
    min-height: 17px;
  }

  .campus-line input {
    width: 11px;
    height: 11px;
  }

  .student-lines {
    margin-top: 3px;
    font-size: 11px;
  }

  .private-receipt-student-summary {
    gap: 3px;
    margin-top: 3px;
    padding: 4px 6px;
    border-top-width: 1px;
    border-bottom-width: 1px;
  }

  .private-receipt-student-summary__label,
  .private-receipt-student-summary__identity > span {
    font-size: 7px;
  }

  .private-receipt-student-summary__header strong,
  .private-receipt-student-summary__financials strong {
    font-size: 9px;
  }

  .private-receipt-student-summary__identity strong {
    font-size: 10px;
  }

  .private-receipt-student-summary__financials,
  .private-receipt-student-summary__payments {
    gap: 2px 8px;
    font-size: 8px;
  }

  .printed-student-name {
    display: block !important;
    min-height: 22px;
    border-bottom-width: 1px;
    font-size: 11px;
    line-height: 22px;
  }

  .registration-help-icon {
    display: none !important;
  }

  .student-credit-inline,
  .reuse-tag {
    display: none !important;
  }

  .full-line,
  .two-col-line label,
  .payment-line {
    min-height: 25px;
    gap: 5px;
  }

  .two-col-line {
    gap: 20px;
  }

  :deep(.line-input .el-input__inner),
  :deep(.line-date .el-input__inner),
  :deep(.line-select .el-select__placeholder) {
    font-size: 11px;
  }

  :deep(.line-input .el-input__wrapper),
  :deep(.line-date .el-input__wrapper),
  :deep(.line-select .el-select__wrapper) {
    min-height: 22px;
    padding: 0 3px;
    border-bottom-width: 1px;
  }

  :deep(.line-date .el-input__prefix) {
    display: none !important;
  }

  .paper-payment-summary {
    min-height: 22px;
    padding: 0 3px;
    border-bottom-width: 1px;
    font-size: 11px;
  }

  .payment-line {
    align-items: flex-start;
    min-height: 25px;
  }

  .payment-method-block {
    align-self: stretch;
  }

  .payment-details-print {
    display: flex !important;
    flex-wrap: wrap;
    gap: 2px 10px;
    padding: 2px 3px 1px;
    color: #111;
    font-size: 9px;
    line-height: 1.2;
  }

  .payment-details-print strong {
    flex-basis: 100%;
    font-size: 9px;
  }

  .payment-history {
    gap: 6px;
    margin-top: 6px;
    padding: 6px 0;
    font-size: 10px;
  }

  .payment-history > div {
    grid-template-columns: minmax(86px, 1fr) minmax(150px, 1.6fr) minmax(78px, .9fr) minmax(88px, 1fr);
    gap: 4px 12px;
    padding: 2px 0;
  }

  .course-table-wrap {
    margin-top: 8px;
  }

  .course-table {
    font-size: 10.5px;
  }

  .course-table th {
    height: 24px;
    font-size: 10.5px;
    font-weight: 700;
  }

  .course-table .el-select,
  .course-table .el-input-number,
  .course-table .money-amount-input,
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
    min-height: 48px;
    padding: 7px 8px;
    vertical-align: middle;
  }

  .course-table .course-entry-row {
    break-inside: avoid;
  }

  .print-course-value,
  .print-course-price {
    font-size: 10.5px;
    line-height: 1.3;
  }

  .print-course-price {
    padding-top: 2px;
    text-align: right;
  }

  .course-table .print-course-summary {
    display: grid !important;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px 12px;
    align-items: start;
  }

  .print-course-summary > div {
    display: grid;
    gap: 1px;
    min-width: 0;
  }

  .print-course-summary span {
    min-width: 0;
    color: #64748b;
    font-size: 7.5px;
    font-weight: 700;
    overflow-wrap: anywhere;
    text-transform: uppercase;
  }

  .print-course-summary strong {
    min-width: 0;
    font-size: 10px;
    line-height: 1.25;
    overflow-wrap: anywhere;
  }

  .course-table th:nth-child(1),
  .course-table td:nth-child(1) {
    width: 28%;
  }

  .course-table th:nth-child(2),
  .course-table td:nth-child(2) {
    width: 58%;
  }

  .course-table th:nth-child(3),
  .course-table td:nth-child(3) {
    width: 14%;
  }

  .receipt-course-detail {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 2px 8px;
    font-size: 9px;
    line-height: 1.2;
  }

  .receipt-course-detail > div {
    grid-template-columns: 52px minmax(0, 1fr);
    gap: 4px;
  }

  .receipt-course-detail span,
  .receipt-course-detail p {
    font-size: 8px;
  }

  .receipt-course-detail p {
    display: none;
  }

  .receipt-transport-summary {
    display: grid !important;
    gap: 6px;
    margin: 8px 0 6px;
    padding: 7px 10px;
    border: 1px solid #334155;
    break-inside: avoid;
    color: #111;
    font-size: 10px;
  }

  .receipt-transport-summary__course + .receipt-transport-summary__course {
    padding-top: 5px;
    border-top: 1px solid #cbd5e1;
  }

  .receipt-transport-summary__heading {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    padding-bottom: 3px;
  }

  .receipt-transport-summary__heading strong {
    font-size: 10.5px;
  }

  .receipt-transport-summary__heading span,
  .receipt-transport-summary__grid span,
  .receipt-transport-summary__fees > span {
    color: #64748b;
    font-size: 8px;
    font-weight: 700;
    text-transform: uppercase;
  }

  .receipt-transport-summary__grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px 12px;
  }

  .receipt-transport-summary__grid > div {
    display: grid;
    gap: 1px;
    min-width: 0;
  }

  .receipt-transport-summary__grid strong {
    overflow-wrap: anywhere;
    font-size: 9.5px;
    font-weight: 600;
  }

  .receipt-transport-summary__fees {
    display: grid;
    gap: 2px;
    margin-top: 4px;
    padding-top: 4px;
    border-top: 1px solid #e2e8f0;
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
    gap: 10px;
    margin: 8px 0 6px;
    padding: 8px 10px;
    border: 1px solid #334155;
    color: #111;
    font-size: 10px;
  }

  .receipt-financial-summary__column {
    display: grid;
    align-content: start;
    gap: 3px;
    min-width: 0;
  }

  .receipt-financial-summary__column + .receipt-financial-summary__column {
    padding-left: 8px;
    border-left: 1px solid #cbd5e1;
  }

  .receipt-financial-summary__column > strong {
    margin-bottom: 2px;
    font-size: 10.5px;
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
    font-size: 10.5px;
  }

  .receipt-financial-summary__row--paid {
    padding-bottom: 2px;
    border-bottom: 1px solid #cbd5e1;
  }

  .receipt-financial-summary__row--refund {
    color: #991b1b;
  }

  .receipt-credit-summary {
    display: grid !important;
    gap: 6px;
    margin: 6px 0;
    padding: 7px 10px;
    border: 1px solid #334155;
    break-inside: avoid;
    color: #111;
    font-size: 10px;
  }

  .receipt-credit-summary__heading {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    padding-bottom: 4px;
    border-bottom: 1px solid #cbd5e1;
  }

  .receipt-credit-summary__heading strong {
    font-size: 10.5px;
  }

  .receipt-credit-summary__heading span {
    color: #64748b;
    font-size: 8px;
  }

  .receipt-credit-summary__grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px 12px;
  }

  .receipt-credit-summary__grid > div {
    display: grid;
    gap: 1px;
    min-width: 0;
  }

  .receipt-credit-summary__grid span {
    color: #64748b;
    font-size: 7.5px;
    font-weight: 700;
    text-transform: uppercase;
  }

  .receipt-credit-summary__grid strong {
    min-width: 0;
    font-size: 10.5px;
    overflow-wrap: anywhere;
  }

  .registration-remark-print {
    display: block !important;
    margin: 6px 0;
    padding: 6px 8px;
    border: 1px solid #334155;
    break-inside: avoid;
    color: #111;
  }

  .registration-remark-print strong {
    display: block;
    margin-bottom: 3px;
    font-size: 10px;
  }

  .registration-remark-print p {
    margin: 0;
    font-size: 9px;
    line-height: 1.25;
    white-space: pre-wrap;
  }

  .policies {
    margin-top: 8px;
    break-inside: avoid;
  }

  .policies-layout {
    display: block;
  }

  .policy-copy {
    min-height: 0;
    font-size: 8.4px;
    line-height: 1.12;
  }

  .policy-copy strong {
    font-size: 8.5px;
  }

}
</style>

<!--
  el-select 下拉面板默认 teleport 到 body，scoped 样式无法命中，
  故课程产品下拉的分组/选项样式放在全局（非 scoped）样式中。

  Element Plus 2.11.5 的 el-option-group 渲染结构：
    ul.el-select-group__wrap.course-product-group--<value>   ← 分组卡片（自定义 class 合并在此）
      li.el-select-group__title                              ← 一级分组标题
      li > ul.el-select-group > li.el-select-dropdown__item  ← 二级选项
-->
<style>
/* 一级：分组卡片（标题+选项整体），带边框圆角，与相邻组明显分隔 */
.course-product-picker .el-select-group__wrap {
  margin: 6px 8px;
  overflow: hidden;
  border: 1px solid #dde5ee;
  border-radius: 6px;
  background: #fff;
}

.course-product-picker .el-select-group__wrap + .el-select-group__wrap {
  margin-top: 10px;
}

/* 一级标题：彩色加粗大写条带，突出"分组"层级 */
.course-product-picker .el-select-group__title {
  height: 30px;
  padding: 0 12px;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  font-size: 11px;
  font-weight: 800;
  line-height: 30px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.course-product-picker .course-product-group--group .el-select-group__title {
  background: #eef6ff;
  color: #1d4ed8;
}

.course-product-picker .course-product-group--one-on-one .el-select-group__title {
  background: #f4f7f2;
  color: #3f6212;
}

.course-product-picker .course-product-group--other .el-select-group__title {
  background: #f8fafc;
  color: #64748b;
}

/* 二级：选项缩进 + 更小字号，与标题形成清晰层级；选中态沿用默认蓝色加粗 */
.course-product-picker .el-select-group__wrap .el-select-dropdown__item {
  height: auto;
  min-height: 34px;
  padding: 6px 12px 6px 28px;
  font-size: 12px;
  line-height: 1.25;
  white-space: normal;
}
</style>
