<template>
  <div class="page-stack">
    <section class="config-panel">
      <div class="config-panel__header">
        <div>
          <span class="config-panel__eyebrow">Course setup</span>
          <h2>Set Grade Classes & Registration Options</h2>
          <p>Choose a course, set shared registration options by grade, then edit each actual class schedule separately.</p>
        </div>
        <div class="header-actions">
          <el-button @click="loadAll">Reload</el-button>
        </div>
      </div>

      <div class="pricing-mode-tabs">
        <button
          v-for="mode in courseModes"
          :key="mode.value"
          class="pricing-mode-tab"
          :class="{ 'pricing-mode-tab--active': selectedMode === mode.value }"
          type="button"
          @click="switchCourseMode(mode.value)"
        >
          {{ mode.label }}
        </button>
      </div>

      <div class="matrix-layout">
        <aside class="navigation-sidebar">
          <span class="navigation-sidebar__label">{{ selectedModeLabel }} courses</span>
          <el-tooltip
            v-for="product in productChoices"
            :key="product.id"
            :content="product.productName"
            placement="right"
            :show-after="300"
          >
            <button
              :class="['product-nav-item', { 'product-nav-item--active': String(query.courseProductId) === String(product.id) }]"
              type="button"
              @click="selectCourseProduct(product)"
            >
              <strong>{{ product.productName }}</strong>
            </button>
          </el-tooltip>
          <el-empty
            v-if="!loadingProducts && productChoices.length === 0"
            :image-size="44"
            description="No courses"
          />
        </aside>

        <div class="matrix-content">
          <section v-if="!query.courseProductId" class="matrix-empty-guide">
            <span>Step 2</span>
            <strong>Choose a course on the left to set grades, prices, and schedule.</strong>
            <p>{{ emptyGuideDescription }}</p>
            <el-button v-if="fromProductSetup" @click="goToCourseProducts">Back to Courses</el-button>
          </section>

          <template v-else>
            <el-alert
              v-if="selectedProduct?.status !== 'ACTIVE'"
              class="inactive-product-notice"
              type="warning"
              :closable="false"
              show-icon
              title="This copied course is inactive. Finish reviewing its grades, prices, and schedule, then activate it in Courses before accepting registrations."
            />
            <div class="matrix-toolbar">
              <el-button v-if="fromProductSetup" text type="primary" @click="handleContextBack">
                <el-icon><ArrowLeft /></el-icon>
                Back to Courses
              </el-button>
              <div class="matrix-toolbar__actions">
                <el-button @click="openGradeDialog">Manage grades</el-button>
                <el-button type="primary" @click="openDialog()">
                  {{ matrixPrimaryActionLabel }}
                </el-button>
              </div>
            </div>

            <el-empty v-if="!loading && groupedOptions.length === 0" description="No grade classes for this course yet." />

            <section
              v-for="group in groupedOptions"
              :key="group.key"
              class="product-price-group"
            >
              <header class="product-price-group__header">
                <div>
                  <strong>{{ group.gradeLabel }}</strong>
                  <small v-if="selectedMode === 'GROUP'">Actual classes: {{ groupActualClassSummary(group) }}</small>
                  <small>{{ group.rows.length }} registration option{{ group.rows.length === 1 ? '' : 's' }} for this grade</small>
                </div>
                <div class="product-price-group__actions">
                  <el-button
                    v-if="selectedMode === 'GROUP' && query.courseProductId && group.gradeLabel !== 'Grade not set'"
                    size="small"
                    @click="openClassHolidayDialog(group.gradeLabel)"
                  >
                    Manage holidays
                  </el-button>
                  <small v-if="selectedMode === 'GROUP' && groupHalfDayPriceSummary(group)" class="group-halfday-summary">
                    {{ groupHalfDayPriceSummary(group) }}
                  </small>
                  <el-button size="small" type="primary" plain @click="openDialog(undefined, group.gradeLabel)">
                    Add registration option
                  </el-button>
                </div>
              </header>

              <div v-loading="loading || loadingProducts" class="grade-package-card">
                <div v-if="selectedMode === 'ONE_ON_ONE'" class="grade-package-card__summary">
                  <div>
                    <strong>{{ groupPackageTitle(group) }}</strong>
                    <span>Private lesson package</span>
                  </div>
                </div>

                <el-table
                  :data="group.rows"
                  row-key="id"
                  class="config-table price-matrix-table payment-option-table"
                  size="small"
                  border
                >
                  <el-table-column label="Registration option" min-width="180">
                    <template #header>
                      <header-tip label="Registration option" tip="What the student is buying, such as 2 months, 6 months, or Fall 13 sessions." />
                    </template>
                    <template #default="{ row }">
                      <div class="matrix-class-cell">
                        <div class="matrix-class-cell__title">
                          <strong>{{ paymentOptionName(row) }}</strong>
                        </div>
                        <span>{{ packageLengthLabel(row) || '-' }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column :label="selectedMode === 'GROUP' ? 'Full-day price' : 'Price'" min-width="150" align="right">
                    <template #header>
                      <header-tip :label="selectedMode === 'GROUP' ? 'Full-day price' : 'Price'" :tip="priceColumnTip" />
                    </template>
                    <template #default="{ row }">
                      <div class="price-cell">
                        <strong>{{ formatMoney(row.totalPrice ?? row.priceAmount) }}</strong>
                        <span>{{ priceUnitLabel(row) }}</span>
                        <small v-if="row.derivedUnitPrice">
                          {{ formatMoney(row.derivedUnitPrice) }} / {{ packageUnitText(row.packageUnit || row.groupBillingType || 'package').toLowerCase() }}
                        </small>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column v-if="selectedMode === 'GROUP'" label="Half-day price" min-width="140" align="right">
                    <template #header>
                      <header-tip label="Half-day price" tip="Optional price used when a student signs up for morning or afternoon only." />
                    </template>
                    <template #default="{ row }">
                      <div class="price-cell">
                        <strong>{{ row.halfDayPrice ? formatMoney(row.halfDayPrice) : '-' }}</strong>
                        <span v-if="row.halfDayPrice">USD / package</span>
                        <small v-if="row.halfDayPrice && halfDayUnitPrice(row)">
                          {{ formatMoney(halfDayUnitPrice(row)) }} / {{ packageUnitText(row.packageUnit || row.groupBillingType || 'package').toLowerCase() }}
                        </small>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="Usage" width="120">
                    <template #header>
                      <header-tip label="Usage" tip="Whether this registration option has already been used by registrations." />
                    </template>
                    <template #default="{ row }">
                      <div class="usage-cell">
                        <el-tag size="small" :type="row.referenced ? 'warning' : 'info'" effect="plain">
                          {{ row.referenced ? `Used ${row.referenceCount ?? 0}` : 'Not used' }}
                        </el-tag>
                        <el-tag
                          v-if="hasRowIssue(row)"
                          size="small"
                          type="danger"
                          effect="plain"
                        >
                          Needs setup
                        </el-tag>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="Status" width="96" align="center">
                    <template #header>
                      <header-tip label="Status" tip="Only active registration options are available during registration." />
                    </template>
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
                        {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="Actions" width="124" align="center">
                    <template #header>
                      <header-tip label="Actions" tip="Edit, deactivate, or delete this registration option." />
                    </template>
                    <template #default="{ row }">
                      <div class="matrix-actions">
                        <el-button size="small" type="primary" plain @click="openDialog(row)">Edit</el-button>
                        <el-dropdown trigger="click">
                          <el-button size="small" text>More</el-button>
                          <template #dropdown>
                            <el-dropdown-menu>
                              <el-dropdown-item v-if="row.status === 'ACTIVE'" @click="deactivateOption(row)">Deactivate</el-dropdown-item>
                              <el-dropdown-item divided class="danger-menu-item" @click="removeOption(row.id)">Delete</el-dropdown-item>
                            </el-dropdown-menu>
                          </template>
                        </el-dropdown>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>

                <section v-if="selectedMode === 'GROUP'" class="actual-class-schedules">
                  <div class="actual-class-schedules__head">
                    <div>
                      <strong>Actual class schedules</strong>
                      <span>Registration options above are shared by this grade. Classroom and weekly times are set per actual class.</span>
                    </div>
                    <div class="actual-class-schedules__tools">
                      <el-button size="small" type="primary" plain @click="openActualClassInfoDialog(group)">
                        Add class
                      </el-button>
                      <el-dropdown
                        v-if="groupActualClassRows(group).length"
                        trigger="click"
                        @command="(classId) => deleteActualClassById(group, classId)"
                      >
                        <el-button size="small" type="danger" plain>
                          Delete class
                          <el-icon><ArrowDown /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item
                              v-for="actualClass in groupActualClassRows(group)"
                              :key="`delete-${actualClass.id}`"
                              :command="String(actualClass.id)"
                            >
                              Delete {{ actualClassDisplayLabel(actualClass) }}
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </div>
                  <div v-if="groupActualClassRows(group).length" class="actual-class-grid">
                    <article
                      v-for="actualClass in groupActualClassRows(group)"
                      :key="actualClass.id"
                      class="actual-class-card"
                    >
                      <div class="actual-class-card__main">
                        <div class="actual-class-card__heading">
                          <strong>{{ actualClassDisplayLabel(actualClass) }}</strong>
                          <span
                            class="actual-class-card__campus"
                            :class="campusBadgeClass(actualClass.campusName)"
                          >
                            {{ actualClass.campusName || 'Campus not set' }}
                          </span>
                        </div>
                        <span>{{ actualClass.scheduleDesc || 'Schedule not set' }}</span>
                        <small>Class period: {{ actualClassPeriodLabel(actualClass) }}</small>
                      </div>
                      <dl>
                        <div>
                          <dt>Teacher</dt>
                          <el-tooltip
                            :content="actualClassTeacherLabel(actualClass)"
                            placement="top"
                            :disabled="!actualClassTeacherNames(actualClass).length"
                          >
                            <dd class="actual-class-card__teacher">
                              {{ actualClassTeacherLabel(actualClass) }}
                            </dd>
                          </el-tooltip>
                        </div>
                        <div>
                          <dt>Room</dt>
                          <dd>{{ actualClassClassroomLabel(actualClass) }}</dd>
                        </div>
                      </dl>
                      <div class="actual-class-card__actions">
                        <el-button size="small" plain @click="openActualClassInfoDialog(group, actualClass)">
                          Edit info
                        </el-button>
                        <el-button size="small" plain type="primary" @click="openActualClassScheduleDialog(actualClass)">
                          Edit schedule
                        </el-button>
                      </div>
                    </article>
                  </div>
                  <el-empty v-else :image-size="40" description="No actual classes for this grade yet." />
                </section>
              </div>
            </section>

            <div class="pagination-row">
              <el-pagination
                v-model:current-page="query.pageNum"
                v-model:page-size="query.pageSize"
                layout="total, sizes, prev, pager, next"
                :total="total"
                :page-sizes="[20, 50, 100, 200]"
                @current-change="loadOptions"
                @size-change="reloadFirstPage"
              />
            </div>
          </template>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      width="min(1080px, calc(100vw - 48px))"
      :close-on-click-modal="false"
      :title="priceDialogTitle"
      class="registration-option-dialog"
    >
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules" class="registration-option-form">
        <div class="pricing-context">
          <span>{{ formTeachingMode === 'GROUP' ? 'Edit shared registration option for' : 'Add an hour package for' }}</span>
          <strong>{{ selectedFormProductName }} · {{ form.gradeBand || 'selected grade' }}</strong>
          <small>{{ formTeachingMode === 'GROUP'
            ? 'Example: a grade can share choices like 2 months, 6 months, Fall 13 sessions, or Full year.'
            : 'Example: Grade 6 can have packages like 40 hours, 60 hours, or 120 hours.' }}</small>
        </div>

        <section class="price-dialog-section">
          <div class="price-dialog-section__head">
            <span class="section-step">1</span>
            <div>
              <strong>Choose grade group</strong>
              <small>Product + Grade defines shared registration options. Actual classes under this grade keep separate schedules.</small>
            </div>
          </div>
          <div class="form-grid">
            <el-form-item v-if="!query.courseProductId" label="Course" prop="courseProductId">
              <el-select
                v-model="form.courseProductId"
                filterable
                placeholder="Select course"
                style="width: 100%;"
                @change="handleFormProductChange"
              >
                <el-option
                  v-for="product in formProductChoices"
                  :key="product.id"
                  :label="productLabel(product)"
                  :value="product.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="Grade" prop="gradeBand">
              <el-select
                v-model="form.gradeBand"
                filterable
                allow-create
                clearable
                default-first-option
                reserve-keyword
                placeholder="Select or type grade"
                no-match-text="Press Enter to use this grade"
                no-data-text="Type a grade and press Enter"
                style="width: 100%;"
              >
                <el-option
                  v-for="grade in gradeOptionsForForm"
                  :key="grade"
                  :label="grade"
                  :value="grade"
                />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <section v-if="formTeachingMode === 'GROUP'" class="grade-class-settings">
          <div class="grade-class-settings__head">
            <span class="section-step">2</span>
            <div>
              <strong>Set registration option</strong>
              <small>Each row is one way a student can register for this grade class.</small>
            </div>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Count by">
              <el-select v-model="form.packageUnit" style="width: 100%;">
                <el-option
                  v-for="unit in groupPackageUnits"
                  :key="unit.value"
                  :label="unit.label"
                  :value="unit.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="packageQuantityLabel">
              <el-input-number
                v-model="form.packageQuantity"
                :min="1"
                :precision="form.packageUnit === 'SESSION' ? 0 : 1"
                :step="1"
                :controls="false"
                style="width: 100%;"
              />
            </el-form-item>
          </div>
          <div class="registration-choice-preview">
            <span>Registration label</span>
            <strong>{{ registrationChoiceLabel }}</strong>
          </div>
        </section>

        <section v-if="formTeachingMode === 'GROUP'" class="grade-class-settings">
          <div class="grade-class-settings__head">
            <span class="section-step">3</span>
            <div>
              <strong>Set registration price</strong>
              <small>Enter the total amount paid for this choice. The per-unit price is calculated for reference.</small>
            </div>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Full-day price">
              <el-input-number
                v-model="form.totalPrice"
                :min="0"
                :precision="2"
                :controls="false"
                style="width: 100%;"
              />
            </el-form-item>
            <el-form-item label="Half-day price (optional)">
              <el-input-number
                v-model="form.halfDayPrice"
                :min="0"
                :precision="2"
                :controls="false"
                placeholder="Optional"
                style="width: 100%;"
              />
            </el-form-item>
          </div>
          <div class="calculated-price">
            <span>Calculated {{ derivedUnitLabel.toLowerCase() }}</span>
            <strong>{{ formatMoney(derivedUnitPrice) }}</strong>
            <small v-if="form.halfDayPrice && form.halfDayPrice > 0">
              Half-day registration uses {{ formatMoney(form.halfDayPrice) }}.
            </small>
          </div>
        </section>

        <section v-else class="grade-class-settings">
          <div class="grade-class-settings__head">
            <span class="section-step">2</span>
            <div>
              <strong>Set hour registration option</strong>
              <small>Each 1-on-1 hour package is a separate registration option for the selected grade.</small>
            </div>
          </div>
          <div class="form-grid form-grid--two">
            <el-form-item label="Package name">
              <el-input v-model="form.periodLabel" placeholder="e.g. 40 hours, 60 hours, 120 hours" />
            </el-form-item>
            <el-form-item label="Package hours" prop="oneOnOneHours">
              <el-radio-group v-model="form.oneOnOneHoursMode" class="hours-presets" @change="selectOneOnOneHours">
                <el-radio-button v-for="hours in oneOnOneHourChoices" :key="hours" :value="String(hours)">
                  {{ hours }}h
                </el-radio-button>
                <el-radio-button value="CUSTOM">Custom</el-radio-button>
              </el-radio-group>
              <div v-if="form.oneOnOneHoursMode === 'CUSTOM'" class="custom-hours">
                <el-input-number
                  v-model="form.customOneOnOneHours"
                  :min="0.5"
                  :max="400"
                  :step="0.5"
                  :precision="1"
                  :controls="true"
                  @change="applyCustomOneOnOneHours"
                />
                <span>hours</span>
              </div>
            </el-form-item>
            <el-form-item label="Package price" prop="priceAmount">
              <el-input-number v-model="form.priceAmount" :min="0" :precision="2" :controls="false" style="width: 100%;" />
            </el-form-item>
          </div>
        </section>

        <el-collapse class="advanced-settings">
          <el-collapse-item title="Advanced settings" name="advanced">
            <el-form-item v-if="formTeachingMode === 'GROUP'" label="Custom registration label">
              <el-input v-model="form.periodLabel" :placeholder="paymentOptionPlaceholder" />
              <span class="field-help">Leave blank to use “{{ registrationChoiceLabel }}”.</span>
            </el-form-item>
            <el-form-item label="Notes">
              <el-input v-model="form.detailText" type="textarea" :rows="2" placeholder="Optional" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>

        <div class="dialog-hint">
          <strong>Registration result:</strong>
          {{ formTeachingMode === 'ONE_ON_ONE'
            ? 'Registration will show this grade and hour package under the selected course.'
            : 'Registration will show this grade and registration option.' }}
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="saveOption">{{ priceSaveButtonLabel }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="categoryDialogVisible"
      title="Edit course categories"
      width="560px"
      :close-on-click-modal="false"
    >
      <p class="category-dialog__hint">
        Rename the top-level course categories and set their display order. The set of
        categories is fixed; smaller order numbers appear first.
      </p>
      <el-table :data="categoryRows" size="small" class="category-table">
        <el-table-column label="Display name" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.displayName" maxlength="64" />
          </template>
        </el-table-column>
        <el-table-column label="Order" width="120" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.displayOrder" :min="1" :max="99" :controls="false" style="width: 90px;" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingCategories" @click="saveCategories">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="gradeDialogVisible"
      title="Manage grades"
      width="min(900px, calc(100vw - 40px))"
      :close-on-click-modal="false"
    >
      <p class="category-dialog__hint">
        <template v-if="selectedMode === 'GROUP'">
          Set the class period once per grade. All actual classes under this grade share these dates.
          Renaming also updates this course's price rows, classes, holidays, and teacher pay rate scopes.
        </template>
        <template v-else>
          Rename a grade or change its display order. Renaming updates this course's price rows and teacher pay rate scopes.
        </template>
      </p>
      <el-table :data="gradeRows" size="small" class="category-table">
        <el-table-column label="Grade name" min-width="240">
          <template #default="{ row }">
            <el-input v-model="row.newLabel" maxlength="64" />
          </template>
        </el-table-column>
        <el-table-column v-if="selectedMode === 'GROUP'" label="Class start date" min-width="190">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="Select start date"
              style="width: 100%;"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="selectedMode === 'GROUP'" label="Class end date" min-width="190">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.endDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="Select end date"
              style="width: 100%;"
            />
          </template>
        </el-table-column>
        <el-table-column label="Order" width="130" align="center">
          <template #default="{ $index }">
            <div class="grade-order-actions">
              <el-button size="small" text :disabled="$index === 0" @click="moveGradeRow($index, -1)">Up</el-button>
              <el-button size="small" text :disabled="$index === gradeRows.length - 1" @click="moveGradeRow($index, 1)">Down</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="gradeDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingGrades" @click="saveGrades">Save</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="actualClassScheduleDialogVisible"
      width="min(920px, calc(100vw - 48px))"
      :close-on-click-modal="false"
      :title="actualClassScheduleTitle"
      class="actual-class-schedule-dialog"
    >
      <el-alert
        title="Saving rebuilds future unconfirmed lessons for this actual class only. Confirmed lessons stay unchanged."
        type="info"
        :closable="false"
        show-icon
        class="grade-class-settings__alert"
      />
      <div class="weekly-schedule-panel">
        <WeeklyTimeSelector v-model="actualClassScheduleForm.slots" hide-hint />
      </div>
      <div v-if="actualClassScheduleForm.slots.length > 0" class="slot-teacher-list actual-class-slot-list">
        <div
          v-for="(slot, index) in actualClassScheduleForm.slots"
          :key="`${slot.weekday}-${slot.startTime}-${index}`"
          class="actual-class-slot-row"
        >
          <div class="slot-time-cell">
            <small>Time</small>
            <strong>{{ groupSlotLabel(slot) }}</strong>
          </div>
          <label class="slot-field">
            <span>Teacher</span>
            <el-select v-model="slot.teacherId" clearable filterable placeholder="No teacher for this slot">
              <el-option v-for="teacher in sortedTeachers" :key="teacher.id" :label="teacher.teacherName" :value="teacher.id" />
            </el-select>
          </label>
          <label class="slot-field">
            <span>Classroom</span>
            <el-select
              v-model="slot.classroomSelection"
              clearable
              popper-class="actual-classroom-picker"
              filterable
              placeholder="No classroom (optional)"
            >
              <el-option label="Online" value="ONLINE" />
              <el-option-group
                v-for="group in classroomGroups"
                :key="group.key"
                :label="group.label"
                class="actual-classroom-campus-group"
              >
                <el-option
                  v-for="room in group.classrooms"
                  :key="room.id"
                  :label="room.roomName"
                  :value="`ROOM:${room.id}`"
                />
              </el-option-group>
            </el-select>
          </label>
        </div>
      </div>
      <template #footer>
        <el-button @click="actualClassScheduleDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingActualClassSchedule" @click="saveActualClassSchedule">Save schedule</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="actualClassInfoDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      :title="actualClassInfoTitle"
      class="actual-class-info-dialog"
    >
      <el-form label-position="top" class="actual-class-info-form">
        <el-form-item label="Class name">
          <el-input v-model="actualClassInfoForm.className" maxlength="128" placeholder="e.g. G1" />
        </el-form-item>
        <p class="category-dialog__hint">Class dates are managed once for the whole grade in “Manage grades”.</p>
        <el-form-item label="Campus">
          <el-select v-model="actualClassInfoForm.campusId" clearable filterable placeholder="Select campus" style="width: 100%;">
            <el-option v-for="campus in activeCampuses" :key="campus.id" :label="campus.campusName" :value="campus.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actualClassInfoDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="savingActualClassInfo" @click="saveActualClassInfo">
          Save class
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="classHolidayDialogVisible"
      width="min(760px, calc(100vw - 40px))"
      :close-on-click-modal="false"
      :title="`${selectedProduct?.productName ?? 'Course'} · ${classHolidayGradeBand} holidays`"
      class="class-holiday-dialog"
    >
      <el-alert
        :title="`These dates apply to every actual class in ${classHolidayGradeBand} under ${selectedProduct?.productName ?? 'this course'}. Other grades, courses, and one-on-one schedules are not affected.`"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        class="class-holiday-schedule-note"
        title="Lessons that fall during the holiday are skipped. The same weekly schedule continues afterward so this grade keeps its planned number of class days."
        type="success"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="class-holiday-form">
        <el-form-item label="Holiday name">
          <el-input v-model="classHolidayForm.holidayName" maxlength="100" placeholder="e.g. Thanksgiving break" />
        </el-form-item>
        <div class="class-holiday-form__dates">
          <el-form-item label="Start date">
            <el-date-picker
              v-model="classHolidayForm.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="Select start date"
              style="width: 100%;"
            />
          </el-form-item>
          <el-form-item label="End date">
            <el-date-picker
              v-model="classHolidayForm.endDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="Select end date"
              style="width: 100%;"
            />
          </el-form-item>
        </div>
        <div class="class-holiday-form__actions">
          <el-button v-if="editingHolidayId" @click="resetClassHolidayForm">Cancel edit</el-button>
          <el-button type="primary" :loading="savingClassHoliday" @click="saveClassHoliday">
            {{ editingHolidayId ? 'Save changes' : 'Add holiday' }}
          </el-button>
        </div>
      </el-form>
      <el-table v-loading="loadingClassHolidays" :data="classHolidayRows" border>
        <el-table-column prop="holidayName" label="Holiday" min-width="180" />
        <el-table-column label="Dates" min-width="220">
          <template #default="{ row }">{{ row.startDate }} – {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="150" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="editClassHoliday(row)">Edit</el-button>
            <el-button link type="danger" @click="removeClassHoliday(row)">Delete</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="48" description="No class holidays configured." />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="classHolidayDialogVisible = false">Close</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowLeft, InfoFilled } from '@element-plus/icons-vue';
import { ElIcon, ElMessage, ElMessageBox, ElTooltip } from 'element-plus';
import { computed, defineComponent, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { extractErrorMessage } from '@/api/http';
import {
  createCoursePriceOption,
  deleteCoursePriceOption,
  fetchAllClassrooms,
  fetchCampuses,
  fetchAllTeachers,
  fetchCoursePriceOptions,
  fetchCourseCategories,
  fetchCourseProducts,
  fetchProductGradeConfigs,
  saveProductGradeConfigs,
  updateCourseCategory,
  updateCoursePriceOption,
  type CourseCategoryItem,
  type CoursePriceOptionSavePayload,
} from '@/api/base-data';
import {
  createActualClass,
  createClassHoliday,
  deleteClassGroup,
  deleteClassHoliday,
  fetchClassGroups,
  fetchClassHolidays,
  previewClassHoliday,
  updateClassInfo,
  updateClassSchedule,
  updateClassHoliday,
  type ClassHolidayItem,
} from '@/api/schedule';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { SlotDef } from '@/components/WeeklyTimeSelector.vue';
import type { CampusItem, ClassGroupItem, ClassroomItem, CoursePriceOptionItem, CourseProductItem, EntityId, TeacherItem } from '@/types/domain';

type CourseMode = 'ONE_ON_ONE' | 'GROUP';
type CourseTypeValue = 'AFTER_SCHOOL' | 'SATURDAY' | 'SUMMER' | 'SPRINT' | 'ONE_ON_ONE';
type PackageUnit = 'MONTH' | 'WEEK' | 'SESSION' | 'HOUR';

interface CourseTypeDef {
  value: CourseTypeValue;
  label: string;
  mode: CourseMode;
}

interface CourseModeDef {
  value: CourseMode;
  label: string;
}

interface ProductPriceGroup {
  key: string;
  gradeLabel: string;
  rows: CoursePriceOptionItem[];
}

interface ActualClassScheduleRow {
  id: EntityId;
  className: string;
  campusId?: EntityId;
  campusName?: string;
  productId?: EntityId;
  gradeBand?: string;
  actualClass?: string;
  teacherId?: EntityId;
  teacherName?: string;
  classroomId?: EntityId;
  classroomName?: string;
  startDate?: string;
  endDate?: string;
  scheduleDesc?: string;
  scheduleSlots?: ClassGroupItem['scheduleSlots'];
}

const HeaderTip = defineComponent({
  name: 'HeaderTip',
  props: {
    label: { type: String, required: true },
    tip: { type: String, required: true },
  },
  setup(props) {
    return () => h('span', { class: 'table-header-tip' }, [
      h('span', props.label),
      h(ElTooltip, { content: props.tip, placement: 'top', effect: 'dark' }, {
        default: () => h(ElIcon, { class: 'table-header-tip__icon' }, () => h(InfoFilled)),
      }),
    ]);
  },
});

const courseTypeDefs: CourseTypeDef[] = [
  { value: 'AFTER_SCHOOL', label: 'After School', mode: 'GROUP' },
  { value: 'SATURDAY', label: 'Saturday Class', mode: 'GROUP' },
  { value: 'SUMMER', label: 'Summer Class', mode: 'GROUP' },
  { value: 'SPRINT', label: 'Sprint Class', mode: 'GROUP' },
  { value: 'ONE_ON_ONE', label: '1 on 1', mode: 'ONE_ON_ONE' },
];

// Category display name/order are configurable; codes and modes stay fixed.
const categoryConfig = ref<Record<string, { label: string; order: number }>>({});
const categoryDialogVisible = ref(false);
const savingCategories = ref(false);
const categoryRows = ref<CourseCategoryItem[]>([]);

// grade 名称/顺序管理：orderMap 驱动分组排序，rows 驱动管理弹窗。
const DEFAULT_GRADE_ORDER = 999;
const gradeDialogVisible = ref(false);
const savingGrades = ref(false);
const gradeRows = ref<Array<{
  currentLabel: string;
  newLabel: string;
  displayOrder: number;
  startDate?: string;
  endDate?: string;
}>>([]);
const gradeClassPeriodMap = ref(new Map<string, {
  startDate?: string;
  endDate?: string;
  configured: boolean;
}>());
const gradeOrderMap = ref<Map<string, number>>(new Map());

const courseTypes = computed<CourseTypeDef[]>(() =>
  [...courseTypeDefs]
    .map(def => ({
      ...def,
      label: categoryConfig.value[def.value]?.label ?? def.label,
    }))
    .sort((left, right) =>
      (categoryConfig.value[left.value]?.order ?? 99) - (categoryConfig.value[right.value]?.order ?? 99)));

const courseModes: CourseModeDef[] = [
  { value: 'ONE_ON_ONE', label: '1-on-1 Private' },
  { value: 'GROUP', label: 'Group Class' },
];

const route = useRoute();
const router = useRouter();
const oneOnOneHourChoices = [20, 40, 60, 80, 100, 120];
const groupPackageUnits: Array<{ value: Exclude<PackageUnit, 'HOUR'>; label: string }> = [
  { value: 'MONTH', label: 'By month' },
  { value: 'WEEK', label: 'By week' },
  { value: 'SESSION', label: 'By session' },
];
const pptGradeOptions: Record<CourseTypeValue, string[]> = {
  AFTER_SCHOOL: ['G1-G5', 'G6-G8', 'ESL'],
  SATURDAY: ['G1-2', 'G3-G6', 'SHSAT', 'PSAT', 'SAT'],
  SUMMER: ['G1-G6', 'SHSAT', 'PSAT', 'SAT', 'ESL'],
  SPRINT: [],
  ONE_ON_ONE: ['G1-G5', 'G6-G8', 'G9-G12', 'AP'],
};

const loading = ref(false);
const loadingProducts = ref(false);
const saving = ref(false);
const options = ref<CoursePriceOptionItem[]>([]);
const courseProducts = ref<CourseProductItem[]>([]);
const classGroups = ref<ClassGroupItem[]>([]);
const campuses = ref<CampusItem[]>([]);
const activeCampuses = computed(() => campuses.value.filter(campus => campus.status === 'ACTIVE'));
const teachers = ref<TeacherItem[]>([]);
const sortedTeachers = computed(() => [...teachers.value].sort((left, right) =>
  left.teacherName.localeCompare(right.teacherName, undefined, { sensitivity: 'base' })));
const classrooms = ref<ClassroomItem[]>([]);
const classroomGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; classrooms: ClassroomItem[] }>();

  for (const classroom of classrooms.value) {
    const label = classroom.campus?.trim() || 'Unassigned campus';
    const key = classroom.campusId != null
      ? `campus:${classroom.campusId}`
      : `campus-name:${label.toLocaleLowerCase()}`;
    const group = groups.get(key) ?? { key, label, classrooms: [] };
    group.classrooms.push(classroom);
    groups.set(key, group);
  }

  return [...groups.values()]
    .map(group => ({
      ...group,
      classrooms: [...group.classrooms].sort((left, right) =>
        left.roomName.localeCompare(right.roomName, undefined, { numeric: true, sensitivity: 'base' })),
    }))
    .sort((left, right) =>
      left.label.localeCompare(right.label, undefined, { numeric: true, sensitivity: 'base' }));
});
const total = ref(0);
const dialogVisible = ref(false);
const editingId = ref<EntityId | null>(null);
const formRef = ref();
const actualClassScheduleDialogVisible = ref(false);
const savingActualClassSchedule = ref(false);
const editingActualClass = ref<ActualClassScheduleRow | null>(null);
const actualClassInfoDialogVisible = ref(false);
const savingActualClassInfo = ref(false);
const editingActualClassInfo = ref<ActualClassScheduleRow | null>(null);
const actualClassScheduleForm = reactive({
  slots: [] as Array<SlotDef & { teacherId?: EntityId; classroomSelection?: string }>,
});
const actualClassInfoForm = reactive({
  productId: undefined as EntityId | undefined,
  gradeBand: '',
  className: '',
  campusId: undefined as EntityId | undefined,
});
const classHolidayDialogVisible = ref(false);
const classHolidayGradeBand = ref('');
const loadingClassHolidays = ref(false);
const savingClassHoliday = ref(false);
const classHolidayRows = ref<ClassHolidayItem[]>([]);
const editingHolidayId = ref<EntityId | null>(null);
const classHolidayForm = reactive({ holidayName: '', startDate: '', endDate: '' });

const query = reactive({
  courseType: 'AFTER_SCHOOL' as CourseTypeValue,
  courseProductId: '' as EntityId | '',
  status: 'ACTIVE',
  pageNum: 1,
  pageSize: 100,
});

const form = reactive({
  optionCode: '',
  courseType: 'AFTER_SCHOOL' as CourseTypeValue,
  registerClass: 'After School',
  gradeBand: '',
  periodLabel: '',
  durationLabel: '',
  timeSlotLabel: '',
  detailText: '',
  oneOnOneHours: undefined as number | undefined,
  oneOnOneHoursMode: '40' as string,
  customOneOnOneHours: undefined as number | undefined,
  priceAmount: 0,
  packageUnit: 'MONTH' as PackageUnit,
  packageQuantity: 2 as number | undefined,
  totalPrice: undefined as number | undefined,
  halfDayPrice: undefined as number | undefined,
  courseProductId: undefined as EntityId | undefined,
  actualClasses: [] as string[],
  groupClassroomId: undefined as EntityId | undefined,
  groupScheduleSlots: [] as Array<{
    teacherId?: EntityId;
    subjectName?: string;
    weekday: number;
    startTime: string;
    endTime: string;
    plannedUnits: number;
    attendanceOptions?: string;
  }>,
  groupStartDate: '',
  groupEndDate: '',
  groupTotalSessions: 13 as number | undefined,
  groupTotalPrice: undefined as number | undefined,
  groupStartMonth: '',
  groupEndMonth: '',
  sortOrder: 0,
  status: 'ACTIVE',
});

const rules = {
  courseProductId: [{ required: true, message: 'Course is required' }],
  gradeBand: [{ required: true, message: 'Grade or level is required' }],
  oneOnOneHours: [{
    validator: (_rule: unknown, value: number | undefined, callback: (error?: Error) => void) => {
      callback(formTeachingMode.value === 'ONE_ON_ONE' && (!value || value <= 0)
        ? new Error('Purchased hours must be greater than zero')
        : undefined);
    },
    trigger: 'change',
  }],
  priceAmount: [{
    validator: (_rule: unknown, value: number, callback: (error?: Error) => void) => {
      callback(value > 0 ? undefined : new Error('Price must be greater than zero'));
    },
    trigger: 'change',
  }],
};

const gradeOptionsForForm = computed(() => {
  // 该产品已有的 grade（含改名后的）排在硬编码建议之前，避免改名后下拉仍引导创建旧名。
  const existing = new Map<string, string>();
  for (const option of options.value) {
    const label = (option.gradeBand ?? '').trim();
    if (label && String(option.courseProductId ?? '') === String(form.courseProductId ?? '')) {
      existing.set(label.toLowerCase(), label);
    }
  }
  const merged = [...existing.values()];
  for (const grade of pptGradeOptions[form.courseType] ?? []) {
    if (!merged.some(item => item.toLowerCase() === grade.toLowerCase())) {
      merged.push(grade);
    }
  }
  return merged;
});
const derivedUnitPrice = computed(() => {
  const totalPrice = Number(form.totalPrice ?? 0);
  const quantity = Number(form.packageQuantity ?? 0);
  return totalPrice > 0 && quantity > 0
    ? Math.round((totalPrice / quantity) * 100) / 100
    : 0;
});
const derivedUnitLabel = computed(() => `${packageUnitText(form.packageUnit)} price`);
const packageQuantityLabel = computed(() => {
  if (form.packageUnit === 'MONTH') {
    return 'Number of months';
  }
  if (form.packageUnit === 'WEEK') {
    return 'Number of weeks';
  }
  return 'Number of sessions';
});
const paymentOptionPlaceholder = computed(() => {
  if (form.packageUnit === 'MONTH') {
    return 'e.g. 2 months, 6 months, Full year';
  }
  if (form.packageUnit === 'WEEK') {
    return 'e.g. 7 weeks, Summer 7 weeks';
  }
  return 'e.g. Fall 13 sessions, Spring 20 sessions';
});
const registrationChoiceLabel = computed(() => {
  const quantity = Number(form.packageQuantity ?? 0);
  return quantity > 0
    ? `${formatQuantity(quantity)} ${packageUnitPlural(form.packageUnit, quantity)}`
    : `Choose a ${packageUnitText(form.packageUnit).toLowerCase()} count`;
});

const selectedMode = ref<CourseMode>('GROUP');
const selectedModeLabel = computed(() =>
  courseModes.find(item => item.value === selectedMode.value)?.label ?? ''
);
const productMap = computed(() => new Map(courseProducts.value.map(item => [String(item.id), item])));
const selectedProduct = computed(() => query.courseProductId ? productMap.value.get(String(query.courseProductId)) : undefined);
const fromProductSetup = computed(() => queryValue(route.query.from) === 'course-products');
const productChoices = computed(() =>
  sortCourseProducts(courseProducts.value.filter(product => product.teachingMode === selectedMode.value))
);
const visibleOptions = computed(() => {
  return options.value.filter((option) => {
    if (modeForCourseType(option.courseType) !== selectedMode.value) {
      return false;
    }
    if (!query.courseProductId) {
      return false;
    }
    if (query.courseProductId && String(option.courseProductId) !== String(query.courseProductId)) {
      return false;
    }
    return true;
  });
});

const emptyGuideDescription = computed(() =>
  selectedMode.value === 'GROUP'
    ? 'Pick a course, then add a grade. After that, enter the registration choices, prices, and schedule for that grade.'
    : 'Pick a 1-on-1 course, then add the grade and hour package students can buy.'
);

const matrixPrimaryActionLabel = computed(() =>
  selectedMode.value === 'ONE_ON_ONE'
    ? 'Add hour package'
    : groupedOptions.value.length === 0 ? 'Add grade' : 'Add registration option'
);

const priceDialogTitle = computed(() =>
  selectedMode.value === 'ONE_ON_ONE'
    ? 'Add 1-on-1 package'
    : editingId.value ? 'Edit registration option' : 'Add registration option'
);

const priceSaveButtonLabel = computed(() => {
  if (editingId.value) {
    return 'Save changes';
  }
  return selectedMode.value === 'ONE_ON_ONE'
    ? 'Save hour package'
    : 'Save registration option';
});
const canEditActualClassesInOptionForm = computed(() =>
  formTeachingMode.value === 'GROUP' && !editingId.value && !existingGradeClass.value
);
const actualClassScheduleTitle = computed(() => {
  const classLabel = editingActualClass.value?.className || editingActualClass.value?.actualClass || 'Class';
  return `Edit ${classLabel} schedule`;
});
const actualClassInfoTitle = computed(() =>
  editingActualClassInfo.value ? 'Edit actual class' : 'Add actual class'
);

const groupedOptions = computed<ProductPriceGroup[]>(() => {
  const groups = new Map<string, ProductPriceGroup>();
  visibleOptions.value.forEach((option) => {
    const gradeLabel = option.gradeBand || 'Grade not set';
    const key = gradeLabel.toLowerCase();
    const existing = groups.get(key);
    if (existing) {
      existing.rows.push(option);
      return;
    }
    groups.set(key, {
      key,
      gradeLabel,
      rows: [option],
    });
  });

  return Array.from(groups.values())
    .map((group) => ({
      ...group,
      rows: [...group.rows].sort((left, right) =>
        left.sortOrder - right.sortOrder
        || String(left.periodLabel ?? '').localeCompare(String(right.periodLabel ?? ''))
        || String(left.durationLabel ?? '').localeCompare(String(right.durationLabel ?? ''))
      ),
    }))
    .sort((left, right) => {
      // 已配置顺序的 grade 优先按 displayOrder 排；未配置的按字典序排在其后。
      const leftOrder = gradeOrderMap.value.get(left.gradeLabel.toLowerCase());
      const rightOrder = gradeOrderMap.value.get(right.gradeLabel.toLowerCase());
      if (leftOrder !== undefined || rightOrder !== undefined) {
        return (leftOrder ?? DEFAULT_GRADE_ORDER) - (rightOrder ?? DEFAULT_GRADE_ORDER);
      }
      return left.gradeLabel.localeCompare(right.gradeLabel);
    });
});

const existingGradeClass = computed(() => {
  if (selectedMode.value !== 'GROUP' || editingId.value || !form.courseProductId || !form.gradeBand.trim()) {
    return false;
  }
  return options.value.some(option =>
    String(option.courseProductId) === String(form.courseProductId)
      && String(option.gradeBand || '').trim().toLowerCase() === form.gradeBand.trim().toLowerCase()
      && option.classId != null
  );
});

const formTeachingMode = computed<CourseMode>(() => courseTypes.value.find(item => item.value === form.courseType)?.mode ?? 'GROUP');
const fixedDateGroup = computed(() => form.packageUnit === 'WEEK' || form.packageUnit === 'SESSION');
const priceColumnTip = computed(() => selectedMode.value === 'ONE_ON_ONE'
  ? 'Total package price for the selected private lesson hours.'
  : 'Total price for this registration option. Registration uses this configured price directly.');
const formProductChoices = computed(() =>
  sortCourseProducts(courseProducts.value.filter(product => product.teachingMode === formTeachingMode.value))
);
const selectedFormProductName = computed(() =>
  form.courseProductId ? productName(form.courseProductId) : 'Select course'
);
watch(() => form.gradeBand, (grade) => {
  if (formTeachingMode.value !== 'GROUP') {
    return;
  }
  const current = form.actualClasses.map(item => item.trim()).filter(Boolean);
  if (current.length === 0 || current.every(item => expandGradeBand(grade).includes(item))) {
    form.actualClasses = expandGradeBand(grade);
  }
});

watch(() => form.packageUnit, (unit) => {
  if (formTeachingMode.value !== 'GROUP') {
    return;
  }
  if (!form.packageQuantity || form.packageQuantity <= 0) {
    form.packageQuantity = defaultPackageQuantity(unit);
  }
  form.groupTotalSessions = Math.ceil(Number(form.packageQuantity ?? defaultPackageQuantity(unit)));
});

onMounted(initializePage);

async function initializePage() {
  await loadCategoryConfig();
  const requestedType = queryValue(route.query.courseType);
  const requestedTypeDefinition = courseTypes.value.find(item => item.value === requestedType);
  if (requestedTypeDefinition) {
    query.courseType = requestedType as CourseTypeValue;
    selectedMode.value = requestedTypeDefinition.mode;
  } else {
    query.courseType = 'AFTER_SCHOOL';
    selectedMode.value = 'GROUP';
  }
  await loadProductsAndClasses();

  const requestedProductId = queryValue(route.query.courseProductId);
  const requestedProduct = requestedProductId ? productMap.value.get(requestedProductId) : undefined;
  if (requestedProduct) {
    selectedMode.value = requestedProduct.teachingMode;
    query.courseType = requestedProduct.teachingMode === 'ONE_ON_ONE'
      ? 'ONE_ON_ONE'
      : normalizeGroupCourseType(requestedProduct.courseType);
    query.courseProductId = requestedProduct.id;
  } else {
    query.courseProductId = '';
  }
  await loadOptions();
}

async function loadAll() {
  await Promise.all([loadProductsAndClasses(), loadOptions()]);
}

async function openClassHolidayDialog(gradeBand: string) {
  if (!query.courseProductId || !gradeBand.trim()) return;
  classHolidayGradeBand.value = gradeBand.trim();
  classHolidayDialogVisible.value = true;
  resetClassHolidayForm();
  await loadClassHolidays();
}

async function loadClassHolidays() {
  if (!query.courseProductId) {
    classHolidayRows.value = [];
    return;
  }
  loadingClassHolidays.value = true;
  try {
    const response = await fetchClassHolidays(query.courseProductId, classHolidayGradeBand.value);
    classHolidayRows.value = response.data.data ?? [];
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    loadingClassHolidays.value = false;
  }
}

function resetClassHolidayForm() {
  editingHolidayId.value = null;
  classHolidayForm.holidayName = '';
  classHolidayForm.startDate = '';
  classHolidayForm.endDate = '';
}

function editClassHoliday(holiday: ClassHolidayItem) {
  editingHolidayId.value = holiday.id;
  classHolidayForm.holidayName = holiday.holidayName;
  classHolidayForm.startDate = holiday.startDate;
  classHolidayForm.endDate = holiday.endDate;
}

async function saveClassHoliday() {
  const holidayName = classHolidayForm.holidayName.trim();
  if (!holidayName || !classHolidayForm.startDate || !classHolidayForm.endDate) {
    ElMessage.warning('Enter a holiday name and select both dates.');
    return;
  }
  if (classHolidayForm.endDate < classHolidayForm.startDate) {
    ElMessage.warning('End date cannot be earlier than start date.');
    return;
  }
  const payload = {
    holidayName,
    startDate: classHolidayForm.startDate,
    endDate: classHolidayForm.endDate,
  };
  savingClassHoliday.value = true;
  try {
    const courseProductId = query.courseProductId;
    if (!courseProductId) return;
    const gradeBand = classHolidayGradeBand.value;
    const previewResponse = await previewClassHoliday(courseProductId, gradeBand, payload, editingHolidayId.value ?? undefined);
    const preview = previewResponse.data.data;
    if (preview.conflicts.length) {
      await ElMessageBox.alert(preview.conflicts.join('\n'), 'Resolve schedule conflicts first', {
        type: 'warning',
        confirmButtonText: 'Close',
      });
      return;
    }
    const courseName = selectedProduct.value?.productName ?? '当前课程';
    const enrollmentImpact = preview.affectedSessionEnrollmentCount > 0
      ? `将重新计算 ${preview.affectedSessionEnrollmentCount} 条按课次计费的报名记录有效期`
      : '没有按课次计费的报名记录需要重算';
    const impactText = `${courseName} · ${gradeBand}：假期期间将跳过 ${preview.affectedOccurrenceCount} 节排课并顺延补排；${enrollmentImpact}。`;
    await ElMessageBox.confirm(impactText, '确认设置假期', {
      type: 'warning',
      confirmButtonText: editingHolidayId.value ? '保存更改' : '添加假期',
      cancelButtonText: '取消',
    });
    if (editingHolidayId.value) {
      await updateClassHoliday(courseProductId, gradeBand, editingHolidayId.value, payload);
      ElMessage.success('Grade holiday updated.');
    } else {
      await createClassHoliday(courseProductId, gradeBand, payload);
      ElMessage.success('Grade holiday added.');
    }
    resetClassHolidayForm();
    await loadClassHolidays();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(extractErrorMessage(error));
  } finally {
    savingClassHoliday.value = false;
  }
}

async function removeClassHoliday(holiday: ClassHolidayItem) {
  try {
    await ElMessageBox.confirm(
      `Delete “${holiday.holidayName}” (${holiday.startDate} – ${holiday.endDate})? Class lessons will be restored where no other holiday applies.`,
      'Delete grade holiday',
      { type: 'warning', confirmButtonText: 'Delete', cancelButtonText: 'Cancel' },
    );
    const courseProductId = query.courseProductId;
    if (!courseProductId) return;
    await deleteClassHoliday(courseProductId, classHolidayGradeBand.value, holiday.id);
    if (editingHolidayId.value === holiday.id) resetClassHolidayForm();
    ElMessage.success('Grade holiday deleted.');
    await loadClassHolidays();
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(extractErrorMessage(error));
  }
}

async function loadCategoryConfig() {
  try {
    const res = await fetchCourseCategories();
    const map: Record<string, { label: string; order: number }> = {};
    for (const item of res.data.data ?? []) {
      map[item.typeCode] = { label: item.displayName, order: item.displayOrder ?? 99 };
    }
    categoryConfig.value = map;
  } catch {
    // Fall back to built-in labels/order if the config cannot be loaded.
    categoryConfig.value = {};
  }
}

async function openCategoryDialog() {
  try {
    const res = await fetchCourseCategories();
    categoryRows.value = (res.data.data ?? [])
      .slice()
      .sort((left, right) => (left.displayOrder ?? 99) - (right.displayOrder ?? 99))
      .map(item => ({ ...item }));
    categoryDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load course categories.');
  }
}

async function saveCategories() {
  for (const row of categoryRows.value) {
    if (!row.displayName || !row.displayName.trim()) {
      ElMessage.warning('Display name cannot be empty.');
      return;
    }
  }
  savingCategories.value = true;
  try {
    await Promise.all(categoryRows.value.map(row =>
      updateCourseCategory(row.id, {
        displayName: row.displayName.trim(),
        displayOrder: row.displayOrder,
      })));
    await loadCategoryConfig();
    categoryDialogVisible.value = false;
    ElMessage.success('Course categories updated.');
  } catch {
    ElMessage.error('Failed to save course categories.');
  } finally {
    savingCategories.value = false;
  }
}

async function loadGradeConfigs() {
  if (!query.courseProductId) {
    gradeOrderMap.value = new Map();
    gradeClassPeriodMap.value = new Map();
    return;
  }
  try {
    const res = await fetchProductGradeConfigs(query.courseProductId);
    const map = new Map<string, number>();
    const periods = new Map<string, { startDate?: string; endDate?: string; configured: boolean }>();
    for (const item of res.data.data ?? []) {
      const key = item.gradeLabel.trim().toLowerCase();
      map.set(key, item.displayOrder ?? 99);
      periods.set(key, {
        startDate: item.startDate,
        endDate: item.endDate,
        configured: item.classPeriodConfigured === true,
      });
    }
    gradeOrderMap.value = map;
    gradeClassPeriodMap.value = periods;
  } catch {
    // 加载失败时退回字典序排序，不阻塞页面。
    gradeOrderMap.value = new Map();
    gradeClassPeriodMap.value = new Map();
  }
}

watch(() => query.courseProductId, () => {
  void loadGradeConfigs();
}, { immediate: true });

async function openGradeDialog() {
  if (!query.courseProductId) {
    return;
  }
  try {
    const res = await fetchProductGradeConfigs(query.courseProductId);
    gradeRows.value = (res.data.data ?? []).map(item => ({
      currentLabel: item.gradeLabel,
      newLabel: item.gradeLabel,
      displayOrder: item.displayOrder ?? 99,
      startDate: item.startDate,
      endDate: item.endDate,
    }));
    gradeDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load grades.');
  }
}

function moveGradeRow(index: number, direction: -1 | 1) {
  const target = index + direction;
  if (target < 0 || target >= gradeRows.value.length) {
    return;
  }
  const rows = [...gradeRows.value];
  [rows[index], rows[target]] = [rows[target], rows[index]];
  gradeRows.value = rows;
}

async function saveGrades() {
  if (!query.courseProductId) {
    return;
  }
  const seen = new Set<string>();
  for (const row of gradeRows.value) {
    const label = row.newLabel.trim();
    if (!label) {
      ElMessage.warning('Grade name cannot be empty.');
      return;
    }
    const key = label.toLowerCase();
    if (seen.has(key)) {
      ElMessage.warning(`Duplicate grade name: ${label}`);
      return;
    }
    seen.add(key);
  }
  savingGrades.value = true;
  try {
    await saveProductGradeConfigs(query.courseProductId, gradeRows.value.map((row, index) => ({
      currentLabel: row.currentLabel,
      newLabel: row.newLabel.trim() === row.currentLabel ? undefined : row.newLabel.trim(),
      displayOrder: index + 1,
      startDate: selectedMode.value === 'GROUP' ? row.startDate || undefined : undefined,
      endDate: selectedMode.value === 'GROUP' ? row.endDate || undefined : undefined,
      updateClassPeriod: selectedMode.value === 'GROUP',
    })));
    gradeDialogVisible.value = false;
    ElMessage.success('Grades updated.');
    await Promise.all([loadProductsAndClasses(), loadOptions(), loadGradeConfigs()]);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to save grades.');
  } finally {
    savingGrades.value = false;
  }
}

async function loadProductsAndClasses() {
  loadingProducts.value = true;
  try {
    const [productsRes, classRes, teachersRes, classroomsRes, campusesRes] = await Promise.all([
      fetchCourseProducts({ pageNum: 1, pageSize: 200 }),
      fetchClassGroups({ includeInactiveProducts: true }).catch(() => ({ data: { data: [] as ClassGroupItem[] } })),
      fetchAllTeachers(),
      fetchAllClassrooms(),
      fetchCampuses().catch(() => ({ data: { data: [] as CampusItem[] } })),
    ]);
    courseProducts.value = productsRes.data.data.records ?? [];
    classGroups.value = classRes.data.data ?? [];
    teachers.value = teachersRes.data.data ?? [];
    classrooms.value = classroomsRes.data.data ?? [];
    campuses.value = campusesRes.data.data ?? [];
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to load courses.');
  } finally {
    loadingProducts.value = false;
  }
}

async function loadOptions() {
  loading.value = true;
  try {
    const res = await fetchCoursePriceOptions({
      courseProductId: query.courseProductId || undefined,
      status: query.status || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize,
    });
    options.value = res.data.data.records ?? [];
    total.value = query.courseProductId ? visibleOptions.value.length : (res.data.data.total ?? 0);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to load course price options.');
  } finally {
    loading.value = false;
  }
}

function reloadFirstPage() {
  query.pageNum = 1;
  loadOptions();
}

function switchCourseMode(mode: CourseMode) {
  if (selectedMode.value === mode) {
    return;
  }
  selectedMode.value = mode;
  query.courseType = mode === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'AFTER_SCHOOL';
  query.courseProductId = '';
  reloadFirstPage();
}

function chooseAnotherProduct() {
  query.courseProductId = '';
}

function goToCourseProducts() {
  router.push({
    name: 'course-products',
    query: { tab: selectedMode.value === 'ONE_ON_ONE' ? 'one-on-one' : 'group' },
  });
}

function handleContextBack() {
  if (fromProductSetup.value) {
    goToCourseProducts();
    return;
  }
  chooseAnotherProduct();
}

function selectCourseProduct(product: CourseProductItem) {
  query.courseProductId = product.id;
  query.courseType = product.teachingMode === 'ONE_ON_ONE'
    ? 'ONE_ON_ONE'
    : normalizeGroupCourseType(product.courseType);
  reloadFirstPage();
}

function openDialog(option?: CoursePriceOptionItem, gradeLabel = '') {
  const presetGrade = gradeLabel === 'Grade not set' ? '' : gradeLabel;
  editingId.value = option?.id ?? null;
  form.optionCode = option?.optionCode ?? '';
  form.courseType = (option?.courseType as CourseTypeValue) ?? query.courseType;
  form.registerClass = option?.registerClass ?? selectedProduct.value?.registerClass ?? selectedProduct.value?.productName ?? selectedTypeLabelFor(form.courseType);
  form.gradeBand = option?.gradeBand ?? presetGrade;
  form.periodLabel = option?.periodLabel ?? '';
  form.durationLabel = option?.durationLabel ?? '';
  form.timeSlotLabel = option?.timeSlotLabel ?? '';
  form.detailText = option?.detailText ?? '';
  form.oneOnOneHours = option?.packageQuantity ?? option?.oneOnOneHours;
  form.customOneOnOneHours = undefined;
  form.oneOnOneHoursMode = oneOnOneHourChoices.includes(Number(option?.oneOnOneHours))
    ? String(option?.oneOnOneHours)
    : 'CUSTOM';
  if (form.oneOnOneHoursMode === 'CUSTOM') {
    form.customOneOnOneHours = option?.packageQuantity ?? option?.oneOnOneHours;
  }
  if (!option) {
    if (formTeachingMode.value === 'ONE_ON_ONE') {
      form.oneOnOneHours = 40;
      form.oneOnOneHoursMode = '40';
      syncOneOnOneDuration();
    }
  }
  form.priceAmount = option?.totalPrice ?? option?.priceAmount ?? 0;
  form.packageUnit = normalizePackageUnit(option?.packageUnit, formTeachingMode.value);
  form.packageQuantity = option?.packageQuantity
    ?? (formTeachingMode.value === 'GROUP' ? option?.groupTotalSessions : option?.oneOnOneHours)
    ?? defaultPackageQuantity(form.packageUnit);
  form.totalPrice = option?.totalPrice ?? option?.groupTotalPrice ?? option?.priceAmount;
  form.halfDayPrice = option?.halfDayPrice;
  form.courseProductId = option?.courseProductId ?? (query.courseProductId || undefined);
  form.actualClasses = option?.actualClasses?.map(item => item.actualClass || item.className).filter(Boolean) as string[]
    ?? existingActualClassNames(form.courseProductId, form.gradeBand)
    ?? expandGradeBand(form.gradeBand);
  form.groupClassroomId = option?.groupClassroomId;
  form.groupScheduleSlots = (option?.groupScheduleSlots ?? []).map(slot => ({
    ...slot,
    plannedUnits: Math.round((timeToMinutes(slot.endTime) - timeToMinutes(slot.startTime)) / 30),
    attendanceOptions: normalizeSlotAttendance(slot.attendanceOptions),
  }));
  form.groupStartDate = option?.groupStartDate ?? '';
  form.groupEndDate = option?.groupEndDate ?? '';
  form.groupTotalSessions = Number(form.packageQuantity ?? option?.groupTotalSessions ?? 13);
  form.groupTotalPrice = option?.totalPrice ?? option?.groupTotalPrice;
  form.groupStartMonth = option?.groupStartMonth ?? '';
  form.groupEndMonth = option?.groupEndMonth ?? '';
  form.sortOrder = option?.sortOrder ?? 0;
  form.status = option?.status ?? 'ACTIVE';
  dialogVisible.value = true;
}

function handleFormProductChange() {
  const product = form.courseProductId ? productMap.value.get(String(form.courseProductId)) : undefined;
  if (product) {
    form.courseType = product.teachingMode === 'ONE_ON_ONE'
      ? 'ONE_ON_ONE'
      : normalizeGroupCourseType(product.courseType);
    form.registerClass = product.registerClass || product.productName;
    if (product.teachingMode === 'ONE_ON_ONE') {
      form.packageUnit = 'HOUR';
    } else if (form.packageUnit === 'HOUR') {
      form.packageUnit = 'MONTH';
      form.packageQuantity = defaultPackageQuantity(form.packageUnit);
    }
  }
}

function normalizePackageUnit(unit: string | undefined, mode: CourseMode): PackageUnit {
  if (mode === 'ONE_ON_ONE') {
    return 'HOUR';
  }
  const normalized = String(unit || '').toUpperCase();
  return normalized === 'WEEK' || normalized === 'SESSION' ? normalized : 'MONTH';
}

function normalizeGroupCourseType(courseType?: string): CourseTypeValue {
  const normalized = String(courseType || '').toUpperCase();
  return courseTypes.value.some(item => item.value === normalized && item.mode === 'GROUP')
    ? normalized as CourseTypeValue
    : 'AFTER_SCHOOL';
}

function defaultPackageQuantity(unit: string) {
  const normalized = String(unit || '').toUpperCase();
  if (normalized === 'WEEK') return 13;
  if (normalized === 'SESSION') return 13;
  if (normalized === 'HOUR') return 40;
  return 2;
}

function optionalPositive(value?: number) {
  return value != null && value > 0 ? value : undefined;
}

function selectOneOnOneHours(value: string) {
  if (value === 'CUSTOM') {
    form.oneOnOneHours = undefined;
    form.customOneOnOneHours = undefined;
    form.durationLabel = '';
    return;
  }
  form.oneOnOneHours = Number(value);
  form.customOneOnOneHours = undefined;
  syncOneOnOneDuration();
}

function applyCustomOneOnOneHours() {
  const hours = form.customOneOnOneHours;
  if (hours == null || hours <= 0) {
    form.oneOnOneHours = undefined;
    form.durationLabel = '';
  } else {
    form.oneOnOneHours = hours;
    syncOneOnOneDuration();
  }
}

function syncOneOnOneDuration() {
  if (form.oneOnOneHours == null || form.oneOnOneHours <= 0) {
    form.durationLabel = '';
  } else {
    form.durationLabel = `${formatHours(form.oneOnOneHours)} hours`;
  }
}

function buildPayload(status = form.status): CoursePriceOptionSavePayload {
  const packageUnit = formTeachingMode.value === 'ONE_ON_ONE' ? 'HOUR' : form.packageUnit;
  const packageQuantity = formTeachingMode.value === 'ONE_ON_ONE'
    ? form.oneOnOneHours
    : form.packageQuantity;
  const packageTotalPrice = formTeachingMode.value === 'ONE_ON_ONE'
    ? form.priceAmount
    : form.totalPrice;
  return {
    optionCode: form.optionCode.trim() || undefined,
    courseType: form.courseType,
    registerClass: form.registerClass.trim(),
    gradeBand: form.gradeBand.trim() || undefined,
    periodLabel: form.periodLabel.trim() || undefined,
    durationLabel: formTeachingMode.value === 'ONE_ON_ONE' ? form.durationLabel.trim() || undefined : undefined,
    timeSlotLabel: undefined,
    detailText: form.detailText.trim(),
    packageUnit,
    packageQuantity,
    totalPrice: packageTotalPrice,
    fullDayPrice: formTeachingMode.value === 'GROUP' ? packageTotalPrice : undefined,
    halfDayPrice: formTeachingMode.value === 'GROUP' ? optionalPositive(form.halfDayPrice) : undefined,
    oneOnOneHours: formTeachingMode.value === 'ONE_ON_ONE' ? form.oneOnOneHours : undefined,
    priceAmount: packageTotalPrice ?? 0,
    currencyCode: 'USD',
    courseProductId: form.courseProductId,
    actualClasses: resolvePayloadActualClasses(),
    groupTeacherId: undefined,
    groupClassroomId: canEditActualClassesInOptionForm.value ? form.groupClassroomId : undefined,
    groupScheduleSlots: canEditActualClassesInOptionForm.value
      ? form.groupScheduleSlots.map(({ teacherId, subjectName, weekday, startTime, endTime, attendanceOptions }) => ({
          teacherId,
          subjectName: subjectName?.trim() || undefined,
          weekday,
          startTime,
          endTime,
          attendanceOptions: normalizeSlotAttendance(attendanceOptions),
        }))
      : undefined,
    groupBillingType: formTeachingMode.value === 'GROUP' ? packageUnit : undefined,
    groupStartDate: formTeachingMode.value === 'GROUP' && fixedDateGroup.value ? form.groupStartDate || undefined : undefined,
    groupEndDate: formTeachingMode.value === 'GROUP' && fixedDateGroup.value ? form.groupEndDate || undefined : undefined,
    groupTotalSessions: formTeachingMode.value === 'GROUP' ? Math.ceil(Number(packageQuantity ?? 0)) : undefined,
    groupTotalPrice: formTeachingMode.value === 'GROUP' ? packageTotalPrice : undefined,
    groupStartMonth: formTeachingMode.value === 'GROUP' ? form.groupStartMonth || undefined : undefined,
    groupEndMonth: formTeachingMode.value === 'GROUP' ? form.groupEndMonth || undefined : undefined,
    sortOrder: form.sortOrder,
    status,
  };
}

function payloadFromRow(row: CoursePriceOptionItem, status: string): CoursePriceOptionSavePayload {
  return {
    optionCode: row.optionCode,
    courseType: row.courseType,
    registerClass: row.registerClass,
    gradeBand: row.gradeBand,
    periodLabel: row.periodLabel,
    durationLabel: row.durationLabel,
    timeSlotLabel: row.timeSlotLabel,
    detailText: row.detailText,
    packageUnit: row.packageUnit,
    packageQuantity: row.packageQuantity,
    totalPrice: row.totalPrice,
    fullDayPrice: row.fullDayPrice,
    halfDayPrice: row.halfDayPrice,
    oneOnOneHours: row.oneOnOneHours,
    priceAmount: row.priceAmount,
    currencyCode: row.currencyCode || 'USD',
    courseProductId: row.courseProductId,
    classId: row.classId,
    actualClasses: row.actualClasses?.map(item => item.actualClass || item.className).filter(Boolean),
    groupTeacherId: undefined,
    groupClassroomId: row.groupClassroomId,
    groupScheduleSlots: row.groupScheduleSlots,
    groupBillingType: row.groupBillingType,
    groupStartDate: row.groupStartDate,
    groupEndDate: row.groupEndDate,
    groupTotalSessions: row.groupTotalSessions,
    groupTotalPrice: row.groupTotalPrice,
    groupStartMonth: row.groupStartMonth,
    groupEndMonth: row.groupEndMonth,
    sortOrder: row.sortOrder,
    status,
    notes: row.notes,
  };
}

async function saveOption() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (formTeachingMode.value === 'GROUP' && resolvePayloadActualClasses()?.length === 0) {
    ElMessage.warning('Add at least one actual class, for example G1.');
    return;
  }
  if (formTeachingMode.value === 'GROUP'
      && (!form.totalPrice || form.totalPrice <= 0
        || !form.packageQuantity || form.packageQuantity <= 0)) {
    ElMessage.warning('Add a package total price and package count.');
    return;
  }
  if (formTeachingMode.value === 'ONE_ON_ONE' && (!form.oneOnOneHours || form.oneOnOneHours <= 0)) {
    ElMessage.error('1 on 1 hours must be greater than zero.');
    return;
  }
  saving.value = true;
  try {
    if (editingId.value) {
      await updateCoursePriceOption(editingId.value, buildPayload());
    } else {
      await createCoursePriceOption(buildPayload());
    }
    ElMessage.success(editingId.value ? 'Registration option updated.' : 'Registration option created.');
    dialogVisible.value = false;
    await loadAll();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Save failed.');
  } finally {
    saving.value = false;
  }
}

function openActualClassScheduleDialog(actualClass: ActualClassScheduleRow) {
  prepareActualClassScheduleEdit(actualClass);
}

function prepareActualClassScheduleEdit(actualClass: ActualClassScheduleRow) {
  const classDetail = classGroups.value.find(item => String(item.id) === String(actualClass.id)) ?? actualClass;
  editingActualClass.value = actualClass;
  actualClassScheduleForm.slots = normalizeActualClassScheduleSlots(
    classDetail.scheduleSlots,
    classDetail.teacherId,
    classDetail.classroomId,
  );
  actualClassScheduleDialogVisible.value = true;
}

function openActualClassInfoDialog(group: ProductPriceGroup, actualClass?: ActualClassScheduleRow) {
  const productId = actualClass?.productId ?? group.rows[0]?.courseProductId ?? query.courseProductId ?? undefined;
  editingActualClassInfo.value = actualClass ?? null;
  actualClassInfoForm.productId = productId;
  actualClassInfoForm.gradeBand = actualClass?.gradeBand || group.gradeLabel;
  actualClassInfoForm.className = actualClass?.className || actualClass?.actualClass || '';
  actualClassInfoForm.campusId = actualClass?.campusId;
  actualClassInfoDialogVisible.value = true;
}

async function saveActualClassInfo() {
  if (!actualClassInfoForm.productId || !actualClassInfoForm.gradeBand.trim()) {
    ElMessage.warning('Select a course and grade before adding a class.');
    return;
  }
  const className = actualClassInfoForm.className.trim();
  if (!className) {
    ElMessage.warning('Class name is required.');
    return;
  }
  savingActualClassInfo.value = true;
  try {
    const payload = {
      productId: actualClassInfoForm.productId,
      gradeBand: actualClassInfoForm.gradeBand.trim(),
      actualClass: className,
      className,
      campusId: actualClassInfoForm.campusId,
    };
    if (editingActualClassInfo.value) {
      await updateClassInfo(editingActualClassInfo.value.id, payload);
      ElMessage.success('Actual class updated.');
    } else {
      await createActualClass(payload);
      ElMessage.success('Actual class created.');
    }
    actualClassInfoDialogVisible.value = false;
    await loadAll();
  } catch (err) {
    ElMessage.error(extractErrorMessage(err));
  } finally {
    savingActualClassInfo.value = false;
  }
}

async function deleteActualClass(actualClass: ActualClassScheduleRow) {
  const className = actualClass.className || actualClass.actualClass || 'this actual class';
  try {
    await ElMessageBox.confirm(
      `Delete ${className}? Active students will be removed from this class, future lessons will be cancelled, and its weekly schedule will be cleared. Student profiles, orders, and completed lesson history will be kept.`,
      'Delete actual class',
      {
        type: 'warning',
        confirmButtonText: 'Delete class',
        cancelButtonText: 'Cancel',
        confirmButtonClass: 'el-button--danger',
      },
    );
    await deleteClassGroup(actualClass.id);
    ElMessage.success('Actual class deleted.');
    await loadAll();
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') {
      return;
    }
    ElMessage.error(extractErrorMessage(err));
  }
}

function deleteActualClassById(group: ProductPriceGroup, classId: string | number) {
  const actualClass = groupActualClassRows(group).find(item => String(item.id) === String(classId));
  if (actualClass) {
    void deleteActualClass(actualClass);
  }
}

function normalizeActualClassScheduleSlots(
  slots: ClassGroupItem['scheduleSlots'],
  defaultTeacherId?: EntityId,
  defaultClassroomId?: EntityId,
): Array<SlotDef & { teacherId?: EntityId; classroomSelection?: string }> {
  const hasSlotTeacher = (slots ?? []).some(slot => slot.teacherId != null);
  const hasConfiguredSlotClassroom = (slots ?? []).some(slot => slot.classroomId != null || slot.deliveryMode === 'ONLINE');
  return (slots ?? []).map(slot => ({
    weekday: slot.weekday,
    teacherId: hasSlotTeacher ? slot.teacherId : defaultTeacherId,
    classroomSelection: slot.deliveryMode === 'ONLINE'
      ? 'ONLINE'
      : (hasConfiguredSlotClassroom ? slot.classroomId : slot.classroomId ?? defaultClassroomId) != null
        ? `ROOM:${hasConfiguredSlotClassroom ? slot.classroomId : slot.classroomId ?? defaultClassroomId}`
        : '',
    startTime: slot.startTime.slice(0, 5),
    endTime: slot.endTime.slice(0, 5),
    plannedUnits: slot.plannedUnits ?? plannedUnitsForSlot(slot.startTime, slot.endTime),
  }));
}

async function saveActualClassSchedule() {
  if (!editingActualClass.value || savingActualClassSchedule.value) {
    return;
  }
  if (actualClassScheduleForm.slots.length === 0) {
    ElMessage.warning('Add at least one weekly class time.');
    return;
  }
  savingActualClassSchedule.value = true;
  try {
    await updateClassSchedule(editingActualClass.value.id, {
      preserveStartedLessons: false,
      slots: actualClassScheduleForm.slots.map(({ teacherId, classroomSelection, weekday, startTime, endTime, plannedUnits }) => ({
        teacherId,
        deliveryMode: classroomSelection === 'ONLINE' ? 'ONLINE' : 'IN_PERSON',
        classroomId: classroomSelection?.startsWith('ROOM:') ? classroomSelection.slice(5) : undefined,
        weekday,
        startTime,
        endTime,
        plannedUnits,
      })),
    });
    ElMessage.success('Class schedule updated.');
    actualClassScheduleDialogVisible.value = false;
    await loadAll();
  } catch (err) {
    ElMessage.error({ message: extractErrorMessage(err), duration: 10000, showClose: true });
  } finally {
    savingActualClassSchedule.value = false;
  }
}

async function deactivateOption(row: CoursePriceOptionItem) {
  try {
    await ElMessageBox.confirm('Deactivate this registration option?', 'Confirm', { type: 'warning' });
    await updateCoursePriceOption(row.id, payloadFromRow(row, 'INACTIVE'));
    ElMessage.success('Registration option deactivated.');
    await loadOptions();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
    }
  }
}

async function removeOption(id: EntityId) {
  try {
    await ElMessageBox.confirm('Delete this registration option?', 'Confirm', { type: 'warning' });
    await deleteCoursePriceOption(id);
    ElMessage.success('Deleted.');
    await loadOptions();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.response?.data?.message ?? 'Delete failed.');
    }
  }
}

function hasClassGroup(productId: EntityId) {
  return classGroups.value.some(item => String(item.productId) === String(productId));
}

function productName(id?: EntityId) {
  if (!id) {
    return 'No product linked';
  }
  return productMap.value.get(String(id))?.productName ?? `Product ${id}`;
}

function productLabel(product: CourseProductItem) {
  return product.productName;
}

function sortCourseProducts(products: CourseProductItem[]) {
  return [...products].sort((left, right) =>
    (left.sortOrder ?? 99) - (right.sortOrder ?? 99)
    || left.productName.localeCompare(right.productName)
  );
}

function selectedTypeLabelFor(value: string) {
  return courseTypes.value.find(item => item.value === value)?.label ?? formatCourseType(value);
}

function modeForCourseType(value: string): CourseMode {
  return value === 'ONE_ON_ONE' ? 'ONE_ON_ONE' : 'GROUP';
}

function productMatchesCourseType(product: CourseProductItem, courseType: CourseTypeValue) {
  return product.teachingMode === modeForCourseType(courseType);
}

function queryValue(value: unknown) {
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '');
}

function formatCourseType(value: string) {
  return value ? value.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (char: string) => char.toUpperCase()) : '-';
}

function groupPackageTitle(group: ProductPriceGroup) {
  if (selectedMode.value === 'ONE_ON_ONE') {
    return 'Hour packages';
  }
  const first = group.rows[0];
  const unit = packageUnitText(first?.packageUnit || first?.groupBillingType || 'MONTH');
  return `${group.gradeLabel} package (${unit.toLowerCase()} payment)`;
}

function groupActualClassSummary(group: ProductPriceGroup) {
  const classes = groupActualClassRows(group).map(actualClassDisplayLabel);
  return classes.length > 0 ? classes.join(', ') : 'Actual classes not set';
}

function actualClassDisplayLabel(actualClass: ActualClassScheduleRow) {
  const className = actualClass.className?.trim();
  const classCode = actualClass.actualClass?.trim();
  return className || classCode || actualClass.gradeBand || 'Class';
}

function campusBadgeClass(campusName?: string) {
  const normalized = campusName?.trim().toLowerCase() || '';
  if (!normalized) return 'actual-class-card__campus--unassigned';
  if (normalized.includes('north') || normalized.includes('北')) return 'actual-class-card__campus--north';
  if (normalized.includes('south') || normalized.includes('南')) return 'actual-class-card__campus--south';
  return 'actual-class-card__campus--assigned';
}

function actualClassPeriodLabel(actualClass: ActualClassScheduleRow) {
  if (actualClass.startDate && actualClass.endDate) {
    return `${actualClass.startDate} to ${actualClass.endDate}`;
  }
  return actualClass.startDate || actualClass.endDate || 'Not set';
}

// 卡片上的教师以排课 slot 为准，汇总同一 class 已分配的老师并去重；没有 slot 老师时兼容旧的 class 默认老师。
function actualClassTeacherNames(actualClass: ActualClassScheduleRow): string[] {
  const names = (actualClass.scheduleSlots ?? [])
    .map(slot => slot.teacherId)
    .filter((teacherId): teacherId is EntityId => teacherId !== undefined && teacherId !== null)
    .map(teacherId => teachers.value.find(item => String(item.id) === String(teacherId))?.teacherName?.trim())
    .filter((name): name is string => Boolean(name));

  const uniqueSlotTeacherNames = [...new Set(names)];
  if (uniqueSlotTeacherNames.length > 0) {
    return uniqueSlotTeacherNames;
  }

  const defaultTeacherName = actualClass.teacherName?.trim();
  return defaultTeacherName ? [defaultTeacherName] : [];
}

function actualClassTeacherLabel(actualClass: ActualClassScheduleRow): string {
  return actualClassTeacherNames(actualClass).join(', ') || 'Assign later';
}

function actualClassClassroomNames(actualClass: ActualClassScheduleRow): string[] {
  const names = (actualClass.scheduleSlots ?? [])
    .map(slot => slot.classroomId)
    .filter((classroomId): classroomId is EntityId => classroomId !== undefined && classroomId !== null)
    .map(classroomId => classrooms.value.find(item => String(item.id) === String(classroomId))?.roomName?.trim())
    .filter((name): name is string => Boolean(name));
  const uniqueSlotClassroomNames = [...new Set(names)];
  if (uniqueSlotClassroomNames.length > 0) {
    return uniqueSlotClassroomNames;
  }
  const defaultClassroomName = actualClass.classroomName?.trim();
  return defaultClassroomName ? [defaultClassroomName] : [];
}

function actualClassClassroomLabel(actualClass: ActualClassScheduleRow): string {
  return actualClassClassroomNames(actualClass).join(', ') || 'Assign later';
}

function groupActualClassRows(group: ProductPriceGroup): ActualClassScheduleRow[] {
  const productId = group.rows[0]?.courseProductId;
  const gradePeriod = gradeClassPeriodMap.value.get(group.gradeLabel.trim().toLowerCase());
  const byId = new Map<string, ActualClassScheduleRow>();
  const matchingClassGroups = classGroups.value.filter(item =>
    String(item.productId ?? '') === String(productId ?? '')
      && String(item.gradeBand ?? '').trim().toLowerCase() === group.gradeLabel.trim().toLowerCase());

  for (const classGroup of matchingClassGroups) {
    byId.set(String(classGroup.id), {
      id: classGroup.id,
      className: classGroup.className,
      campusId: classGroup.campusId,
      campusName: classGroup.campusName,
      productId: classGroup.productId,
      gradeBand: classGroup.gradeBand,
      actualClass: classGroup.actualClass,
      teacherId: classGroup.teacherId,
      teacherName: classGroup.teacherName,
      classroomId: classGroup.classroomId,
      classroomName: classGroup.classroomName,
      startDate: gradePeriod?.configured ? gradePeriod.startDate : classGroup.startDate,
      endDate: gradePeriod?.configured ? gradePeriod.endDate : classGroup.endDate,
      scheduleDesc: classGroup.scheduleDesc,
      scheduleSlots: classGroup.scheduleSlots,
    });
  }

  for (const actualClass of group.rows.flatMap(row => row.actualClasses ?? [])) {
    if (byId.has(String(actualClass.id))) {
      continue;
    }
    byId.set(String(actualClass.id), {
      ...actualClass,
      productId,
      gradeBand: group.gradeLabel,
      startDate: gradePeriod?.configured ? gradePeriod.startDate : actualClass.startDate,
      endDate: gradePeriod?.configured ? gradePeriod.endDate : actualClass.endDate,
    });
  }

  return [...byId.values()].sort((left, right) =>
    String(left.actualClass || left.className).localeCompare(String(right.actualClass || right.className), undefined, {
      numeric: true,
      sensitivity: 'base',
    }));
}

function existingActualClassNames(productId?: EntityId, gradeBand = '') {
  const names = classGroups.value
    .filter(item => String(item.productId ?? '') === String(productId ?? '')
      && String(item.gradeBand ?? '').trim().toLowerCase() === gradeBand.trim().toLowerCase())
    .map(item => item.actualClass || item.gradeBand || item.className)
    .filter(Boolean) as string[];
  return names.length > 0 ? uniqueValues(names) : undefined;
}

function resolvePayloadActualClasses() {
  if (formTeachingMode.value !== 'GROUP') {
    return undefined;
  }
  if (canEditActualClassesInOptionForm.value) {
    return uniqueValues(form.actualClasses.map(item => item.trim()).filter(Boolean));
  }
  return existingActualClassNames(form.courseProductId, form.gradeBand)
    ?? uniqueValues(form.actualClasses.map(item => item.trim()).filter(Boolean));
}

function packageDisplayName(row: CoursePriceOptionItem) {
  return row.periodLabel || row.durationLabel || packageLengthLabel(row) || 'Standard package';
}

function paymentOptionName(row: CoursePriceOptionItem) {
  return row.periodLabel || packageLengthLabel(row) || 'Standard payment';
}

function packageLengthLabel(row: CoursePriceOptionItem) {
  const quantity = row.packageQuantity ?? row.oneOnOneHours ?? row.groupTotalSessions;
  const unit = row.packageUnit || row.groupBillingType || (row.courseType === 'ONE_ON_ONE' ? 'HOUR' : 'MONTH');
  if (!quantity || quantity <= 0) {
    return row.durationLabel || '';
  }
  return `${formatQuantity(quantity)} ${packageUnitPlural(unit, quantity)}`;
}

function packageUnitText(unit?: string) {
  const normalized = String(unit || '').toUpperCase();
  if (normalized === 'MONTH' || normalized === 'MONTHLY') return 'Month';
  if (normalized === 'WEEK') return 'Week';
  if (normalized === 'SESSION' || normalized === 'PER_SESSION') return 'Session';
  if (normalized === 'HOUR') return 'Hour';
  return 'Package';
}

function packageUnitPlural(unit: string, quantity: number) {
  const text = packageUnitText(unit).toLowerCase();
  return Number(quantity) === 1 ? text : `${text}s`;
}

function formatQuantity(value: number) {
  return Number.isInteger(Number(value)) ? String(Number(value)) : Number(value).toFixed(1);
}

function groupSlotLabel(slot: { weekday: number; startTime: string; endTime: string }) {
  const weekday = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][slot.weekday] ?? '';
  return `${weekday} ${slot.startTime}-${slot.endTime}`;
}

function expandGradeBand(gradeBand: string) {
  const normalized = String(gradeBand || '').trim().toUpperCase();
  const match = normalized.match(/^G(\d{1,2})\s*-\s*G?(\d{1,2})$/);
  if (!match) {
    return normalized ? [normalized] : [];
  }
  const start = Number(match[1]);
  const end = Number(match[2]);
  if (!Number.isInteger(start) || !Number.isInteger(end) || end < start || end - start > 20) {
    return [normalized];
  }
  return Array.from({ length: end - start + 1 }, (_item, index) => `G${start + index}`);
}

function groupHalfDayPriceSummary(group: ProductPriceGroup) {
  const prices = group.rows
    .map(row => Number(row.halfDayPrice ?? 0))
    .filter(price => price > 0);
  if (prices.length === 0) {
    return '';
  }
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  return min === max
    ? `Half-day: ${formatMoney(min)}`
    : `Half-day: ${formatMoney(min)}–${formatMoney(max)}`;
}

function teacherName(id?: EntityId) {
  if (!id) return 'Assign later';
  return teachers.value.find(item => String(item.id) === String(id))?.teacherName ?? 'Assign later';
}

function classroomName(id?: EntityId) {
  if (!id) return 'Assign later';
  return classrooms.value.find(item => String(item.id) === String(id))?.roomName ?? 'Assign later';
}

function hasRowIssue(row: CoursePriceOptionItem) {
  const product = row.courseProductId ? productMap.value.get(String(row.courseProductId)) : undefined;
  if (!product) {
    return true;
  }
  return product.teachingMode !== modeForCourseType(row.courseType)
    || row.totalPrice === undefined
    || row.totalPrice === null
    || Number(row.totalPrice) === 0
    || (product.teachingMode === 'GROUP' && !hasClassGroup(product.id));
}

function priceUnitLabel(row: CoursePriceOptionItem) {
  const currency = row.currencyCode || 'USD';
  if (modeForCourseType(row.courseType) === 'GROUP') {
    return `${currency} / package`;
  }
  return `${currency} / package`;
}

function halfDayUnitPrice(row: CoursePriceOptionItem) {
  const totalPrice = Number(row.halfDayPrice ?? 0);
  const quantity = Number(row.packageQuantity ?? row.groupTotalSessions ?? 0);
  return totalPrice > 0 && quantity > 0
    ? Math.round((totalPrice / quantity) * 100) / 100
    : 0;
}

function formatMoney(value?: number) {
  return `$${Number(value ?? 0).toFixed(2)}`;
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)));
}

function formatHours(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function timeToMinutes(value: string) {
  const [hours, minutes] = value.split(':').map(Number);
  return (hours * 60) + minutes;
}

function plannedUnitsForSlot(startTime: string, endTime: string) {
  return Math.max(1, Math.round((timeToMinutes(endTime) - timeToMinutes(startTime)) / 30));
}

function normalizeSlotAttendance(value?: string) {
  const normalized = String(value || '').trim().toUpperCase();
  return normalized === 'MORNING' || normalized === 'AFTERNOON' ? normalized : 'ALL';
}
</script>

<style scoped>
.category-dialog__hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6b7684;
}

.category-table {
  width: 100%;
}

.grade-order-actions {
  display: inline-flex;
  gap: 4px;
}

.config-panel {
  padding: 18px 20px;
  border: 1px solid #e8edf2;
  border-radius: 10px;
  background: #fff;
}

.config-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.config-panel__header h2 {
  margin: 4px 0 6px;
  font-size: 20px;
}

.config-panel__header p,
.cell-stack span {
  margin: 0;
  color: #6b7280;
}

.config-panel__eyebrow {
  color: #18a058;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.pricing-mode-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 14px;
  border-bottom: 2px solid #e8edf2;
}

.pricing-mode-tab {
  margin-bottom: -2px;
  padding: 10px 24px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #909399;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.pricing-mode-tab:hover {
  color: #18b58a;
}

.pricing-mode-tab--active {
  border-bottom-color: #18b58a;
  color: #18b58a;
}

.matrix-layout {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 12px;
}

.navigation-sidebar {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding-right: 10px;
  border-right: 1px solid #edf0f3;
}

.navigation-sidebar__label {
  margin: 2px 3px 4px;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.product-nav-item {
  display: block;
  width: 100%;
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.product-nav-item:hover,
.product-nav-item--active {
  border-color: #c9ead5;
  background: #effaf3;
  color: #0f7a3a;
}

.product-nav-item strong {
  display: block;
  width: 100%;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.matrix-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin: 0 0 10px;
}

.matrix-toolbar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-left: auto;
  flex-wrap: wrap;
}

.matrix-toolbar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.matrix-empty-guide {
  display: grid;
  gap: 7px;
  padding: 18px;
  border: 1px dashed #bfdbfe;
  border-radius: 10px;
  background: #eff6ff;
}

.matrix-empty-guide span {
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.matrix-empty-guide strong {
  color: #1f2937;
  font-size: 15px;
}

.matrix-empty-guide p {
  max-width: 640px;
  margin: 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.config-table {
  width: 100%;
}

.price-matrix-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
}

.table-header-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 0;
}

.table-header-tip__icon {
  color: #94a3b8;
  font-size: 13px;
  cursor: help;
}

.table-header-tip__icon:hover {
  color: #409eff;
}

.price-matrix-table :deep(.el-table__cell) {
  vertical-align: top;
}

.product-price-group {
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.product-price-group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 12px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.product-price-group__header > div:first-child {
  display: grid;
  gap: 2px;
}

.product-price-group__header strong {
  color: #1e293b;
  font-size: 13px;
}

.product-price-group__header span {
  color: #64748b;
  font-size: 12px;
}

.product-price-group__header small {
  color: #64748b;
  font-size: 12px;
}

.product-price-group__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}

.grade-package-card {
  padding: 12px;
  background: #fff;
}

.grade-package-card__summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fbfdff;
}

.grade-package-card__summary div {
  display: grid;
  gap: 3px;
}

.grade-package-card__summary strong {
  color: #0f172a;
  font-size: 14px;
}

.grade-package-card__summary span,
.grade-package-card__summary small {
  color: #64748b;
  font-size: 12px;
}

.payment-option-table {
  border-radius: 6px;
  overflow: hidden;
}

.actual-class-schedules {
  display: grid;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.actual-class-schedules__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.actual-class-schedules__tools {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.actual-class-schedules__head div {
  display: grid;
  gap: 3px;
}

.actual-class-schedules__head strong {
  color: #1e293b;
  font-size: 13px;
}

.actual-class-schedules__head span {
  color: #64748b;
  font-size: 12px;
}

.actual-class-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}

.actual-class-card {
  display: grid;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #fbfdff;
}

.actual-class-card__main {
  display: grid;
  gap: 4px;
}

.actual-class-card__main strong {
  color: #0f172a;
  font-size: 14px;
}

.actual-class-card__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.actual-class-card__heading strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actual-class-card__campus {
  flex: none;
  max-width: 52%;
  overflow: hidden;
  padding: 2px 8px;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actual-class-card__campus--north {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.actual-class-card__campus--south {
  border-color: #a7f3d0;
  background: #ecfdf5;
  color: #047857;
}

.actual-class-card__campus--unassigned {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: #64748b;
}

.actual-class-card__main span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.actual-class-card dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
}

.actual-class-card dt {
  color: #94a3b8;
  font-size: 11px;
}

.actual-class-card dd {
  overflow: hidden;
  margin: 2px 0 0;
  color: #334155;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actual-class-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.actual-class-info-dialog :deep(.el-dialog__body) {
  padding-top: 10px;
}

.actual-class-info-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.cell-stack strong {
  color: #1e293b;
}

.matrix-class-cell {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.matrix-class-cell__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.matrix-class-cell__title strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.matrix-class-cell span,
.matrix-class-cell small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-cell,
.usage-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.price-cell {
  align-items: flex-end;
}

.price-cell strong {
  color: #111827;
  font-size: 14px;
}

.price-cell span {
  color: #94a3b8;
  font-size: 11px;
}

.price-cell small {
  color: #64748b;
  font-size: 10px;
}

.usage-cell {
  align-items: flex-start;
}

.matrix-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.matrix-actions :deep(.el-button) {
  margin-left: 0;
}

:deep(.danger-menu-item) {
  color: #f56c6c;
}

.dialog-hint {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f7fa;
  color: #4b5563;
  font-size: 13px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.registration-option-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 180px);
  overflow: auto;
  padding: 16px 20px;
}

.class-holiday-dialog :deep(.el-dialog__body) {
  display: grid;
  gap: 16px;
  max-height: calc(100vh - 180px);
  overflow: auto;
}

.class-holiday-form {
  padding: 14px;
  border: 1px solid #dbe4ee;
  border-radius: 10px;
  background: #f8fafc;
}

.class-holiday-form__dates {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.class-holiday-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.registration-option-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #e5e7eb;
  padding: 12px 20px;
}

.registration-option-form {
  min-width: 0;
}

.form-grid {
  display: grid;
  gap: 14px;
}

.form-grid--two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.calculated-price {
  display: grid;
  grid-template-columns: auto auto 1fr;
  align-items: baseline;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.calculated-price span,
.calculated-price small {
  color: #64748b;
  font-size: 12px;
}

.calculated-price strong {
  color: #1d4ed8;
  font-size: 18px;
}

.calculated-price small {
  text-align: right;
}

.grade-class-settings {
  display: grid;
  gap: 12px;
  margin: 16px 0;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.price-dialog-section {
  display: grid;
  gap: 12px;
  margin: 14px 0;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.price-dialog-section__head,
.grade-class-settings__head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.price-dialog-section__head div,
.grade-class-settings__head div {
  display: grid;
  gap: 3px;
}

.section-step {
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.price-dialog-section__head strong,
.grade-class-settings__head strong {
  color: #1f2937;
  font-size: 13px;
}

.price-dialog-section__head small,
.grade-class-settings__head small {
  color: #64748b;
  font-size: 12px;
}

.slot-teacher-list {
  display: grid;
  gap: 8px;
}

.weekly-schedule-panel {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 4px;
}

.weekly-schedule-panel :deep(.weekly-time-selector) {
  min-width: 760px;
}

.slot-teacher-row {
  display: grid;
  grid-template-columns: minmax(130px, 0.8fr) minmax(130px, 0.75fr) minmax(160px, 1fr) minmax(160px, 1fr) minmax(112px, 0.65fr);
  align-items: end;
  gap: 10px;
  min-width: 0;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #fbfdff;
}

.actual-class-schedule-dialog :deep(.el-dialog__body) {
  display: grid;
  gap: 12px;
  max-height: calc(100vh - 180px);
  overflow: auto;
}

.actual-class-slot-list {
  margin-top: 2px;
}

.actual-class-slot-row {
  display: grid;
  grid-template-columns: minmax(140px, 0.8fr) repeat(2, minmax(220px, 1fr));
  align-items: end;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #fbfdff;
}

.slot-time-cell,
.slot-field {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.slot-time-cell small,
.slot-field span {
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.slot-field__label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.slot-field__tip {
  color: #909399;
  cursor: help;
  font-size: 13px;
  line-height: 1;
}

.slot-field__tip:hover {
  color: #409eff;
}

.slot-time-cell strong {
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slot-field :deep(.el-select),
.slot-field :deep(.el-input),
.slot-field :deep(.el-input-number) {
  width: 100%;
}

.slot-field :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

.custom-hours {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.hours-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.hours-presets :deep(.el-radio-button__inner) {
  border-left: 1px solid var(--el-border-color) !important;
  border-radius: 4px !important;
  box-shadow: none !important;
}

.custom-hours {
  margin-top: 10px;
}

.custom-hours :deep(.el-input-number) {
  width: 160px;
}

.custom-hours span,
.field-help {
  color: #6b7280;
  font-size: 12px;
}

.field-help {
  display: block;
  margin-top: 6px;
  line-height: 1.45;
}

.registration-choice-preview {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 6px;
  background: #f8fafc;
}

.registration-choice-preview span {
  color: #64748b;
  font-size: 12px;
}

.registration-choice-preview strong {
  color: #1e293b;
  font-size: 13px;
}

.pricing-context {
  display: grid;
  gap: 3px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.pricing-context span,
.pricing-context small {
  color: #6b7280;
  font-size: 12px;
}

.pricing-context strong {
  color: #111827;
  font-size: 15px;
}

.advanced-settings {
  margin-top: 4px;
  border-top: 1px solid #e5e7eb;
  border-bottom: 1px solid #e5e7eb;
}

@media (max-width: 980px) {
  .matrix-layout {
    grid-template-columns: 1fr;
  }

  .navigation-sidebar {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: 0;
    border-right: 0;
  }

  .form-grid--two {
    grid-template-columns: 1fr;
  }

  .slot-teacher-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .slot-time-cell {
    grid-column: 1 / -1;
  }

  .actual-class-slot-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .registration-option-dialog :deep(.el-dialog__body) {
    padding: 12px;
  }

  .slot-teacher-row {
    grid-template-columns: 1fr;
  }
}
</style>

<!-- 教室下拉会 teleport 到 body，校区卡片样式必须用全局选择器。 -->
<style>
.actual-classroom-picker .el-select-group__wrap {
  margin: 7px 8px;
  overflow: hidden;
  border: 1px solid #9bb5d1 !important;
  border-left: 4px solid #3974a8 !important;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(28 55 85 / 8%);
}

.actual-classroom-picker .el-select-group__wrap + .el-select-group__wrap {
  margin-top: 10px;
}

.actual-classroom-picker .el-select-group__title {
  height: 36px;
  padding: 0 12px;
  border-bottom: 1px solid #9bb5d1 !important;
  background: #dceaf7 !important;
  color: #173f65 !important;
  font-size: 12px;
  font-weight: 800;
  line-height: 36px;
  letter-spacing: 0.035em;
}

.actual-classroom-picker .el-select-group__wrap .el-select-dropdown__item {
  min-height: 35px;
  padding: 0 14px 0 24px;
  color: #35475b;
  line-height: 35px;
}

.actual-classroom-picker .el-select-group__wrap .el-select-dropdown__item.is-hovering {
  background: #edf5fc;
  color: #173f65;
}

.actual-classroom-picker .el-select-group__wrap .el-select-dropdown__item.is-selected {
  background: #e2effb;
  color: #15538a;
}
</style>
