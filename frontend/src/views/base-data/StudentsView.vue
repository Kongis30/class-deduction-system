<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Students</span>
        <div class="panel-header__actions">
          <button
            v-if="appStore.isMobileViewport"
            type="button"
            class="mobile-search-trigger"
            :aria-expanded="mobileSearchExpanded"
            aria-label="Open student search and filters"
            @click="mobileSearchExpanded = !mobileSearchExpanded"
          >
            <el-icon><Search /></el-icon>
            <span v-if="activeAdvancedFilterCount" class="mobile-search-trigger__count">{{ activeAdvancedFilterCount }}</span>
          </button>
          <el-button text size="small" aria-label="View deleted students" @click="openDeletedDialog">
            <el-icon><Delete /></el-icon>
            Deleted
          </el-button>
          <el-button
            v-if="appStore.isMobileViewport"
            type="primary"
            size="small"
            class="student-add-trigger"
            @click="openStudentDialog()"
          >
            Add student
          </el-button>
        </div>
      </div>

      <div v-show="!appStore.isMobileViewport || mobileSearchExpanded" class="mobile-search-panel">
        <div v-if="appStore.isMobileViewport" class="mobile-search-panel__header">
          <span>Search students</span>
          <button type="button" aria-label="Close search" @click="mobileSearchExpanded = false">
            <el-icon><Close /></el-icon>
          </button>
        </div>
        <div class="student-search-bar">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="Search name, guardian, phone, or ID"
            @keyup.enter="handleFilterChange"
            @clear="handleFilterChange"
          />
          <el-button class="student-search-bar__button" type="primary" @click="handleFilterChange">Search</el-button>
          <el-button
            v-if="!appStore.isMobileViewport"
            class="student-search-bar__filters"
            text
            @click="advancedFiltersVisible = !advancedFiltersVisible"
          >
            <el-icon><Filter /></el-icon>
            <span>Filters</span>
            <span v-if="activeAdvancedFilterCount" class="student-search-bar__filter-count">
              {{ activeAdvancedFilterCount }}
            </span>
          </el-button>
        </div>

        <div v-if="advancedFiltersVisible || (appStore.isMobileViewport && mobileSearchExpanded)" class="student-advanced-filters">
          <el-select
            v-model="filters.campusId"
            clearable
            placeholder="All campuses"
            @change="handleFilterChange"
          >
            <el-option v-for="campus in campusOptions" :key="campus.id" :label="campus.campusName" :value="campus.id" />
          </el-select>
          <el-select
            v-model="filters.paymentStatus"
            clearable
            placeholder="Payment status"
            @change="handleFilterChange"
          >
            <el-option label="Unpaid" value="UNPAID" />
            <el-option label="Partial payment" value="PARTIAL" />
            <el-option label="Paid in full" value="PAID" />
          </el-select>
          <el-button v-if="activeAdvancedFilterCount" text @click="clearAdvancedFilters">Clear filters</el-button>
        </div>
      </div>

      <p v-if="appStore.isMobileViewport" class="mobile-table-hint">Swipe to view more fields. Tap a student for details; use More for actions.</p>
      <div ref="peopleTableScrollRef" class="people-table-zoom-scroll mobile-pinch-scroll">
        <div v-if="!studentProducts.length && !loading" class="student-groups-empty">No students found.</div>
        <details v-for="product in studentProducts" :key="product.key" class="student-product" :open="openProducts.has(product.key)" @toggle="setOpen(openProducts, product.key, $event)">
          <summary>{{ product.label }} <span>{{ product.studentCount }} students</span></summary>
          <details v-for="project in product.projects" :key="project.key" class="student-project" :open="openProjects.has(project.key)" @toggle="setOpen(openProjects, project.key, $event)">
            <summary>{{ project.label }}</summary>
            <details v-for="group in project.classes" :key="group.key" class="student-group" :open="openGroups.has(group.key)" @toggle="setOpen(openGroups, group.key, $event)">
              <summary>{{ group.label }} <span>{{ group.students.length }} students</span></summary>
        <el-table
          v-if="openGroups.has(group.key)"
          v-loading="loading"
          :data="group.students"
          row-key="id"
          class="product-table people-main-table"
          :style="{ zoom: appStore.isMobileViewport ? mobileTableScale : undefined }"
          :show-empty="true"
          @row-click="openStudentDetail"
        >
        <el-table-column label="Student" :min-width="appStore.isMobileViewport ? 150 : 190" fixed="left">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.studentName }}</strong>
              <span>{{ row.studentNo }} · {{ row.status === 'ACTIVE' ? 'Active' : 'Inactive' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="appStore.isMobileViewport" label="Phone" width="128">
          <template #default="{ row }">{{ formatUsPhoneDisplay(row.guardianPhone || row.phone) || '-' }}</template>
        </el-table-column>
        <el-table-column v-else label="Guardian contact" min-width="180">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.guardianName || '-' }}</strong>
              <span>{{ formatUsPhoneDisplay(row.guardianPhone || row.phone) || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Orders" :width="appStore.isMobileViewport ? 82 : undefined" :min-width="appStore.isMobileViewport ? undefined : 150">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ row.enrollmentCount ?? 0 }} order{{ (row.enrollmentCount ?? 0) === 1 ? '' : 's' }}</strong>
              <span v-if="!appStore.isMobileViewport">Latest {{ formatDate(row.latestSignupDate) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Balance" :min-width="appStore.isMobileViewport ? 104 : 180">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong :class="{ 'balance-due': numeric(row.enrollmentBalanceAmount) > 0 }">
                {{ numeric(row.enrollmentBalanceAmount) > 0 ? `${formatMoney(row.enrollmentBalanceAmount)} due` : 'No balance' }}
              </strong>
              <el-tag v-if="!appStore.isMobileViewport" size="small" :type="paymentStatusType(row.aggregatePaymentStatus)">
                {{ paymentStatusLabel(row.aggregatePaymentStatus) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Login" :min-width="appStore.isMobileViewport ? 132 : 170">
          <template #default="{ row }">
            <div class="cell-stack">
              <strong>{{ accountForStudent(row)?.username || 'No login' }}</strong>
              <span v-if="appStore.isMobileViewport">{{ accountForStudent(row) ? accountStatusLabel(accountForStudent(row)!.status) : 'Not enabled' }}</span>
              <el-tag v-if="!appStore.isMobileViewport" size="small" :type="accountForStudent(row)?.status === 'ACTIVE' ? 'success' : 'info'">
                {{ accountForStudent(row) ? accountStatusLabel(accountForStudent(row)!.status) : 'Not enabled' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          :label="appStore.isMobileViewport ? 'More' : 'Actions'"
          :width="appStore.isMobileViewport ? 56 : 320"
          :fixed="appStore.isMobileViewport ? false : 'right'"
          align="center"
        >
          <template #default="{ row }">
            <el-dropdown
              v-if="appStore.isMobileViewport"
              class="mobile-table-actions"
              trigger="click"
              placement="bottom-end"
              @click.stop
            >
              <el-button text circle aria-label="More student actions" @click.stop>
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openStudentDetail(row)">Student details</el-dropdown-item>
                  <el-dropdown-item @click="startRegistrationForStudent(row)">Register</el-dropdown-item>
                  <el-dropdown-item @click="openStudentAccountDialog(row)">
                    {{ accountForStudent(row) ? 'Edit login' : 'Create login' }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="openStudentDialog(row)">Edit student</el-dropdown-item>
                  <el-dropdown-item divided @click="handleDeleteStudent(row)">Delete student</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <div v-else class="table-actions">
              <el-button text type="primary" @click.stop="openStudentDetail(row)">Details</el-button>
              <el-button text type="primary" @click.stop="startRegistrationForStudent(row)">Register</el-button>
              <el-button text type="primary" @click.stop="openStudentAccountDialog(row)">
                {{ accountForStudent(row) ? 'Edit login' : 'Create login' }}
              </el-button>
              <el-button text type="primary" @click.stop="openStudentDialog(row)">Edit</el-button>
              <el-button text type="danger" @click.stop="handleDeleteStudent(row)">Delete</el-button>
            </div>
          </template>
        </el-table-column>
        </el-table>
            </details>
          </details>
        </details>
      </div>

      <div class="table-footer">{{ tableData.length }} students{{ loading ? ' · Loading…' : '' }}</div>
    </section>

    <!-- Student Form Dialog -->
    <el-dialog
      v-model="studentDialogVisible"
      width="760px"
      :fullscreen="appStore.isMobileViewport"
      :close-on-click-modal="false"
      class="student-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <h3 class="dialog-header__title">{{ editingId ? 'Edit student' : 'Add student' }}</h3>
        </div>
      </template>

      <el-form ref="studentFormRef" label-position="top" :model="studentForm" :rules="studentFormRules" :validate-on-rule-change="false" class="student-form">
        <h4 class="student-form__section-title">Student profile</h4>
        <div class="student-form__row">
          <el-form-item label="Student name" prop="studentName" class="student-form__half">
            <el-input v-model="studentForm.studentName" placeholder="e.g. Sophia Lin" />
          </el-form-item>
          <el-form-item label="English name" class="student-form__half">
            <el-input v-model="studentForm.englishName" placeholder="Optional English name" />
          </el-form-item>
        </div>
        <div class="student-form__row student-form__row--three">
          <el-form-item label="Gender" prop="gender" class="student-form__third">
            <el-select v-model="studentForm.gender" placeholder="Select gender" style="width: 100%;">
              <el-option label="Male" value="Male" />
              <el-option label="Female" value="Female" />
            </el-select>
          </el-form-item>
          <el-form-item label="Birth date" class="student-form__third">
            <el-date-picker
              v-model="studentForm.birthDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="Select date"
              style="width: 100%;"
            />
          </el-form-item>
          <el-form-item label="Campus" class="student-form__third">
            <el-select v-model="studentForm.campusId" placeholder="Select campus" clearable style="width: 100%;">
              <el-option v-for="c in campusOptions" :key="c.id" :label="c.campusName" :value="c.id" />
            </el-select>
          </el-form-item>
        </div>
        <div class="student-form__row student-form__row--three">
          <el-form-item label="Grade" class="student-form__third">
            <el-input v-model="studentForm.grade" placeholder="e.g. Grade 6" />
          </el-form-item>
          <el-form-item label="Class" class="student-form__third">
            <el-select v-model="studentForm.studentClass" placeholder="Select class" clearable style="width: 100%;">
              <el-option v-for="option in studentClassOptions" :key="option" :label="option" :value="option" />
            </el-select>
          </el-form-item>
          <el-form-item label="School" class="student-form__third">
            <el-input v-model="studentForm.school" placeholder="Current school" />
          </el-form-item>
        </div>

        <h4 class="student-form__section-title">Guardian contact</h4>
        <div class="student-form__row student-form__row--three">
          <el-form-item label="Guardian name" prop="guardianName" class="student-form__third">
            <el-input v-model="studentForm.guardianName" placeholder="Primary guardian" />
          </el-form-item>
          <el-form-item label="Relation" class="student-form__third">
            <el-select v-model="studentForm.guardianRelationType" placeholder="Select relation" clearable style="width: 100%;">
              <el-option label="Mother" value="MOTHER" />
              <el-option label="Father" value="FATHER" />
              <el-option label="Grandparent" value="GRANDPARENT" />
              <el-option label="Other" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="Email" class="student-form__third">
            <el-input v-model="studentForm.guardianEmail" placeholder="Optional email" />
          </el-form-item>
        </div>
        <div class="student-form__row">
          <el-form-item label="Phone" prop="guardianPhone" class="student-form__half">
            <el-input v-model="studentForm.guardianPhone" placeholder="(626) 555-0101" />
          </el-form-item>
          <el-form-item label="Phone #2" class="student-form__half">
            <el-input v-model="studentForm.guardianPhone2" placeholder="Optional second phone" />
          </el-form-item>
        </div>

        <h4 class="student-form__section-title">Pickup and drop-off</h4>
        <div class="student-form__row">
          <el-form-item label="Pickup needed" class="student-form__half">
            <el-switch v-model="studentForm.pickupRequired" active-text="Required" inactive-text="Not required" />
          </el-form-item>
          <el-form-item label="Drop-off needed" class="student-form__half">
            <el-switch v-model="studentForm.dropoffRequired" active-text="Required" inactive-text="Not required" />
          </el-form-item>
        </div>
        <div class="student-form__row">
          <el-form-item label="Pickup address" class="student-form__half">
            <el-input v-model="studentForm.pickupAddress" :disabled="!studentForm.pickupRequired" placeholder="Pickup address" />
          </el-form-item>
          <el-form-item label="Drop-off address" class="student-form__half">
            <el-input v-model="studentForm.dropoffAddress" :disabled="!studentForm.dropoffRequired" placeholder="Drop-off address" />
          </el-form-item>
        </div>

        <h4 class="student-form__section-title">Administration</h4>
        <div class="student-form__row student-form__row--three">
          <el-form-item label="Status" prop="status" class="student-form__third">
            <el-select v-model="studentForm.status" placeholder="Select status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="Source" class="student-form__third">
            <el-input v-model="studentForm.sourceChannel" placeholder="e.g. Referral" />
          </el-form-item>
          <el-form-item label="Notes" class="student-form__full">
            <el-input v-model="studentForm.notes" type="textarea" :rows="2" placeholder="Optional internal notes" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="studentDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleStudentSubmit">Save</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Student login dialog -->
    <el-dialog
      v-model="studentAccountDialogVisible"
      width="480px"
      :fullscreen="appStore.isMobileViewport"
      :close-on-click-modal="false"
      class="student-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <h3 class="dialog-header__title">{{ studentAccountMode === 'create' ? 'Create student login' : 'Edit student login' }}</h3>
        </div>
      </template>

      <div v-if="studentAccountTarget" class="payment-detail">
        <div class="metric-grid">
          <div>
            <label>Student</label>
            <strong>{{ studentAccountTarget.studentName }}</strong>
          </div>
          <div>
            <label>Student no.</label>
            <strong>{{ studentAccountTarget.studentNo }}</strong>
          </div>
        </div>
        <el-form label-position="top" class="student-form">
          <el-form-item label="Username">
            <el-input v-model="studentAccountForm.username" :readonly="studentAccountMode === 'create'" placeholder="Login username" />
          </el-form-item>
          <el-form-item label="Display name">
            <el-input v-model="studentAccountForm.displayName" placeholder="Defaults to student name" />
          </el-form-item>
          <el-form-item label="Email">
            <el-input v-model="studentAccountForm.email" placeholder="Login email" />
          </el-form-item>
          <el-form-item label="Status">
            <el-select v-model="studentAccountForm.status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item :label="studentAccountMode === 'create' ? 'Initial password' : 'New password'">
            <el-input
              v-model="studentAccountForm.password"
              type="password"
              :readonly="studentAccountMode === 'create'"
              :placeholder="studentAccountMode === 'create' ? 'Min 8 characters' : 'Leave blank to keep current'"
              show-password
            />
          </el-form-item>
          <el-form-item label="Confirm password">
            <el-input v-model="studentAccountForm.confirmPassword" type="password" placeholder="Re-enter password" show-password />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="studentAccountDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="studentAccountSaving" @click="submitStudentAccount">Save</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Student operation drawer -->
    <el-drawer
      v-model="detailDrawerVisible"
      :size="appStore.isMobileViewport ? '100%' : '72%'"
      :with-header="false"
      class="student-detail-drawer"
    >
      <div v-loading="detailLoading" class="student-detail">
        <div class="detail-header">
          <div>
            <h2>{{ selectedStudentForDisplay?.studentName || 'Student detail' }}</h2>
            <p>
              {{ selectedStudentForDisplay?.studentNo || '-' }}
              <span v-if="selectedStudentForDisplay?.campusName"> · {{ selectedStudentForDisplay?.campusName }}</span>
            </p>
          </div>
          <div class="detail-header__actions">
            <el-button type="primary" @click="startNewRegistration">New registration</el-button>
            <el-button @click="detailDrawerVisible = false">Close</el-button>
          </div>
        </div>

        <section class="detail-card">
          <h3>Profile</h3>
          <div class="detail-grid">
            <div>
              <label>Status</label>
              <strong>{{ selectedStudentForDisplay?.status === 'ACTIVE' ? 'Active' : 'Inactive' }}</strong>
            </div>
            <div>
              <label>Campus</label>
              <strong>{{ selectedStudentForDisplay?.campusName || '-' }}</strong>
            </div>
            <div>
              <label>Gender</label>
              <strong>{{ selectedStudentForDisplay?.gender || '-' }}</strong>
            </div>
            <div>
              <label>Guardian</label>
              <strong>{{ selectedStudentForDisplay?.guardianName || '-' }}</strong>
            </div>
            <div>
              <label>Phone</label>
              <strong>{{ formatUsPhoneDisplay(selectedStudentForDisplay?.guardianPhone || selectedStudentForDisplay?.phone) || '-' }}</strong>
            </div>
            <div>
              <label>Phone #2</label>
              <strong>{{ formatUsPhoneDisplay(selectedStudentForDisplay?.guardianPhone2) || '-' }}</strong>
            </div>
            <div>
              <label>Email</label>
              <strong>{{ selectedStudentForDisplay?.guardianEmail || '-' }}</strong>
            </div>
            <div>
              <label>Grade</label>
              <strong>{{ selectedStudentForDisplay?.grade || '-' }}</strong>
            </div>
            <div>
              <label>School</label>
              <strong>{{ selectedStudentForDisplay?.school || '-' }}</strong>
            </div>
            <div>
              <label>Guardian email</label>
              <strong>{{ selectedStudentForDisplay?.guardianEmail || '-' }}</strong>
            </div>
            <div>
              <label>Relation</label>
              <strong>{{ guardianRelationLabel(selectedStudentForDisplay?.guardianRelationType) }}</strong>
            </div>
          </div>
        </section>

        <section class="detail-card">
          <h3>Pickup / dropoff</h3>
          <div class="detail-grid">
            <div>
              <label>Pickup</label>
              <strong>{{ selectedStudentForDisplay?.pickupRequired ? 'Required' : 'Not required' }}</strong>
            </div>
            <div class="detail-grid__wide">
              <label>Pickup address</label>
              <strong>{{ selectedStudentForDisplay?.pickupRequired ? selectedStudentForDisplay?.pickupAddress || '-' : '-' }}</strong>
            </div>
            <div>
              <label>Dropoff</label>
              <strong>{{ selectedStudentForDisplay?.dropoffRequired ? 'Required' : 'Not required' }}</strong>
            </div>
            <div class="detail-grid__wide">
              <label>Dropoff address</label>
              <strong>{{ selectedStudentForDisplay?.dropoffRequired ? selectedStudentForDisplay?.dropoffAddress || '-' : '-' }}</strong>
            </div>
          </div>
        </section>

        <section class="detail-card">
          <h3>Payment progress</h3>
          <div class="metric-grid">
            <div>
              <label>Latest signup</label>
              <strong>{{ formatDate(selectedStudent?.latestSignupDate) }}</strong>
            </div>
            <div>
              <label>Enrollment count</label>
              <strong>{{ selectedStudent?.enrollmentCount ?? 0 }}</strong>
            </div>
            <div>
              <label>Amount due</label>
              <strong>{{ formatMoney(selectedStudent?.enrollmentAmountDue) }}</strong>
            </div>
            <div>
              <label>Paid</label>
              <strong>{{ formatMoney(selectedStudent?.enrollmentPaidAmount) }}</strong>
            </div>
            <div>
              <label>Balance</label>
              <strong :class="{ 'balance-due': numeric(selectedStudent?.enrollmentBalanceAmount) > 0 }">
                {{ formatMoney(selectedStudent?.enrollmentBalanceAmount) }}
              </strong>
            </div>
            <div>
              <label>Status</label>
              <el-tag size="small" :type="paymentStatusType(selectedStudent?.aggregatePaymentStatus)">
                {{ paymentStatusLabel(selectedStudent?.aggregatePaymentStatus) }}
              </el-tag>
            </div>
          </div>
        </section>

        <section class="detail-card">
          <div class="section-title-row">
            <h3>Registration history</h3>
            <span>{{ studentOrders.length }} order(s)</span>
          </div>
          <el-table :data="studentOrders" row-key="id" class="product-table" empty-text="No enrollment orders.">
            <el-table-column label="Order no." min-width="150" prop="enrollmentNo" />
            <el-table-column label="Signup date" min-width="110">
              <template #default="{ row }">{{ formatDate(row.signupDate) }}</template>
            </el-table-column>
            <el-table-column label="Courses" min-width="220">
              <template #default="{ row }">{{ summarizeOrderItems(row) }}</template>
            </el-table-column>
            <el-table-column label="Amount due" min-width="100" align="right">
              <template #default="{ row }">{{ formatMoney(row.amountDue) }}</template>
            </el-table-column>
            <el-table-column label="Paid" min-width="90" align="right">
              <template #default="{ row }">{{ formatMoney(row.receivedAmount) }}</template>
            </el-table-column>
            <el-table-column label="Balance" min-width="90" align="right">
              <template #default="{ row }">
                <strong :class="{ 'balance-due': numeric(row.balanceAmount) > 0 }">
                  {{ formatMoney(row.balanceAmount) }}
                </strong>
              </template>
            </el-table-column>
            <el-table-column label="Payment" min-width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="paymentStatusType(row.paymentStatus)">
                  {{ paymentStatusLabel(row.paymentStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Payment timeline" min-width="520">
              <template #default="{ row }">
                <div v-if="paymentTimeline(row).length > 0" class="payment-timeline">
                  <div class="payment-timeline__header">
                    <span>Date</span>
                    <span>Method</span>
                    <span>Type</span>
                    <span>Amount</span>
                    <span>Handler</span>
                    <span>Notes</span>
                  </div>
                  <div v-for="payment in paymentTimeline(row)" :key="payment.id" class="payment-timeline__row">
                    <span>{{ formatDateTime(payment.paidAt) }}</span>
                    <span>{{ paymentMethodName(payment.paymentMethod) }}</span>
                    <span>{{ paymentTypeLabel(payment.paymentType) }}</span>
                    <strong>{{ formatMoney(payment.amount) }}</strong>
                    <span>{{ payment.handledByName || '-' }}</span>
                    <span>{{ payment.remark || '-' }}</span>
                  </div>
                </div>
                <span v-else class="muted-text">No payment records.</span>
              </template>
            </el-table-column>
            <el-table-column label="Status" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="orderStatusType(row.orderStatus)">
                  {{ orderStatusLabel(row.orderStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" min-width="230" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button text type="primary" @click="openReceipt(row)">View receipt</el-button>
                  <el-button text type="primary" :disabled="!canRecordPayment(row)" @click="openPaymentDialog(row)">Record payment</el-button>
                  <el-button text type="warning" :disabled="!canRefundOrder(row)" @click="openRefundDialog(row)">Refund</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
    </el-drawer>

    <!-- Record payment dialog -->
    <el-dialog
      v-model="paymentDialogVisible"
      width="760px"
      :fullscreen="appStore.isMobileViewport"
      :close-on-click-modal="false"
      class="student-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <h3 class="dialog-header__title">Record payment</h3>
        </div>
      </template>

      <div v-if="paymentOrder" class="payment-detail">
        <div class="metric-grid">
          <div>
            <label>Order no.</label>
            <strong>{{ paymentOrder.enrollmentNo }}</strong>
          </div>
          <div>
            <label>Amount due</label>
            <strong>{{ formatMoney(paymentOrder.amountDue) }}</strong>
          </div>
          <div>
            <label>Paid</label>
            <strong>{{ formatMoney(paymentOrder.receivedAmount) }}</strong>
          </div>
          <div>
            <label>Balance</label>
            <strong class="balance-due">{{ formatMoney(paymentOrder.balanceAmount) }}</strong>
          </div>
        </div>

        <h4>Existing payments</h4>
        <el-table :data="paymentOrder.payments" size="small" class="product-table" empty-text="No payment records.">
          <el-table-column label="Paid at" min-width="150">
            <template #default="{ row }">{{ formatDateTime(row.paidAt) }}</template>
          </el-table-column>
          <el-table-column label="Method" min-width="120">
            <template #default="{ row }">{{ paymentMethodName(row.paymentMethod) }}</template>
          </el-table-column>
          <el-table-column label="Amount" min-width="90" align="right">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="Handler" min-width="120" prop="handledByName" />
          <el-table-column label="Notes" min-width="160" prop="remark" />
        </el-table>

        <h4>New payments</h4>
        <div class="payment-rows">
          <div v-for="(row, index) in paymentRows" :key="row.rowKey" class="payment-row">
            <el-select v-model="row.paymentMethod" placeholder="Method">
              <el-option v-for="method in paymentMethods" :key="method.methodCode" :label="method.methodName" :value="method.methodCode" />
            </el-select>
            <el-input-number v-model="row.amount" :min="0.01" :precision="2" :controls="false" placeholder="Amount" />
            <el-date-picker v-model="row.paidAt" type="date" value-format="YYYY-MM-DD" placeholder="Date" />
            <el-input v-model="row.handledByName" placeholder="Handler" />
            <el-input v-model="row.remark" placeholder="Notes" />
            <el-button v-if="paymentRows.length > 1" text type="danger" @click="removePaymentRow(index)">Remove</el-button>
          </div>
          <el-button text type="primary" @click="addPaymentRow">Add payment</el-button>
        </div>
        <div class="payment-total">
          <span>Total {{ formatMoney(paymentDraftTotal) }}</span>
          <span v-if="paymentDraftRemaining >= 0">Remaining {{ formatMoney(paymentDraftRemaining) }}</span>
          <span v-else class="balance-due">Over by {{ formatMoney(Math.abs(paymentDraftRemaining)) }}</span>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="paymentDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="paymentSubmitting" @click="submitPayments">Record payment</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Refund dialog -->
    <el-dialog
      v-model="refundDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      class="student-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <h3 class="dialog-header__title">Refund</h3>
        </div>
      </template>

      <div v-if="refundOrder" class="refund-detail">
        <div class="metric-grid">
          <div>
            <label>Order no.</label>
            <strong>{{ refundOrder.enrollmentNo }}</strong>
          </div>
          <div>
            <label>Currently received</label>
            <strong>{{ formatMoney(refundOrder.receivedAmount) }}</strong>
          </div>
          <div>
            <label>Balance</label>
            <strong>{{ formatMoney(refundOrder.balanceAmount) }}</strong>
          </div>
        </div>
        <p class="refund-warning">
          Partial refund keeps current courses active. Full refund cancels this order, future lessons, and class eligibility.
          If lessons were already recorded, the backend will block full refund and require manual review.
        </p>
        <el-form label-position="top" class="student-form">
          <el-form-item label="Refund amount">
            <el-input-number
              v-model="refundForm.refundAmount"
              :min="0.01"
              :max="refundOrder.receivedAmount ?? 0"
              :precision="2"
              :controls="false"
              style="width: 100%;"
            />
          </el-form-item>
          <el-form-item label="Refund handling">
            <el-radio-group v-model="refundForm.disposition">
              <el-radio value="CREDIT">Convert to Student Credit</el-radio>
              <el-radio value="REFUND">Continue refund</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="Refund method">
            <el-select v-model="refundForm.paymentMethod" placeholder="Select method" style="width: 100%;" :disabled="refundForm.disposition === 'CREDIT'">
              <el-option v-for="method in paymentMethods" :key="method.methodCode" :label="method.methodName" :value="method.methodCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="Handled by">
            <el-input v-model="refundForm.handledByName" placeholder="Your name" />
          </el-form-item>
          <el-form-item label="Reason / Notes">
            <el-input v-model="refundForm.remark" type="textarea" :rows="2" placeholder="Optional reason" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="refundDialogVisible = false">Cancel</el-button>
          <el-button type="warning" :loading="refunding" @click="submitRefund">Confirm refund</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="deleteDialogVisible"
      width="520px"
      :close-on-click-modal="false"
      class="student-dialog"
      @closed="resetDeleteDialog"
    >
      <template #header>
        <div class="dialog-header">
          <h3 class="dialog-header__title">Delete student</h3>
        </div>
      </template>

      <div v-if="deleteTarget" class="delete-confirm">
        <p class="delete-warning-text">
          This action is only for mistaken student profiles. The system will remove login access, close active
          operational records, and cancel future schedules. Use Inactive when you need to preserve operations.
        </p>
        <div class="metric-grid">
          <div>
            <label>Student</label>
            <strong>{{ deleteTarget.studentName }}</strong>
          </div>
          <div>
            <label>Managed courses</label>
            <strong>{{ deleteManagedCourseCount }}</strong>
          </div>
          <div>
            <label>Login access</label>
            <strong>{{ accountForStudent(deleteTarget) ? 'Enabled' : 'No login' }}</strong>
          </div>
        </div>
        <p v-if="deleteConfirmRemaining > 0" class="delete-cooldown">
          Please wait {{ deleteConfirmRemaining }} second(s) before confirming.
        </p>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="deleteDialogVisible = false">Cancel</el-button>
          <el-button
            type="danger"
            :disabled="deleteConfirmRemaining > 0"
            :loading="deletingStudent"
            @click="confirmDeleteStudent"
          >
            {{ deleteConfirmRemaining > 0 ? `Delete (${deleteConfirmRemaining}s)` : 'Delete student' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Deleted Students Dialog -->
    <el-dialog
      v-model="deletedDialogVisible"
      title="Deleted Students"
      width="720px"
      :fullscreen="appStore.isMobileViewport"
      class="student-dialog"
    >
      <p class="deleted-hint">These student profiles have been deleted. This view is read-only.</p>
      <el-table v-loading="deletedLoading" :data="deletedRows" row-key="id" class="product-table" max-height="420">
        <el-table-column label="Student name" min-width="140" prop="studentName" />
        <el-table-column label="Guardian" min-width="140">
          <template #default="{ row }">{{ row.guardianName || '-' }}</template>
        </el-table-column>
        <el-table-column label="Guardian Email" min-width="160">
          <template #default="{ row }">{{ row.guardianEmail || '-' }}</template>
        </el-table-column>
        <el-table-column label="Campus" min-width="120">
          <template #default="{ row }">{{ row.campusName || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!deletedLoading && deletedRows.length === 0" description="No deleted students." />
      <template #footer>
        <el-button @click="deletedDialogVisible = false">Close</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Close, Delete, Filter, MoreFilled, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createStudentAccount,
  fetchStudentAccounts,
  updateStudentAccount,
  type AccountUpdatePayload,
  type StudentAccountCreatePayload,
} from '@/api/auth';
import {
  createStudent,
  fetchCampuses,
  fetchDeletedStudents,
  fetchStudent,
  fetchStudents,
  fetchStudentCourseMemberships,
  deleteStudent,
  updateStudent,
  type StudentQuery,
  type StudentSavePayload,
  type StudentCourseMembership,
} from '@/api/base-data';
import {
  createPaymentRecord,
  fetchActivePaymentMethods,
  fetchEnrollmentOrder,
  fetchEnrollmentOrders,
  refundEnrollmentOrder,
} from '@/api/enrollment';
import { extractErrorMessage } from '@/api/http';
import { useAppStore } from '@/stores/app';
import type { CampusItem, EnrollmentOrderDetailItem, EnrollmentOrderItem, PaymentMethodItem, PaymentRecordItem, StudentAccountItem, StudentItem } from '@/types/domain';
import { DEFAULT_INITIAL_PASSWORD, usernameFromName } from '@/utils/account-credentials';
import { formatUsPhoneDisplay } from '@/utils/phone-format';
import { useMobileTablePinchZoom } from '@/composables/useMobileTablePinchZoom';

const loading = ref(false);
const submitting = ref(false);
const studentDialogVisible = ref(false);
const editingId = ref<string | null>(null);
const studentFormRef = ref();
const router = useRouter();
const route = useRoute();
const appStore = useAppStore();
const peopleTableScrollRef = ref<HTMLElement | null>(null);
const { scale: mobileTableScale } = useMobileTablePinchZoom(
  peopleTableScrollRef,
  computed(() => appStore.isMobileViewport),
  0.72,
);

// ─── Campus options for dropdown ────────────────────
const campusOptions = ref<CampusItem[]>([]);
const paymentMethods = ref<PaymentMethodItem[]>([]);

async function loadCampuses() {
  try {
    const res = await fetchCampuses();
    campusOptions.value = res.data.data ?? [];
  } catch {
    campusOptions.value = [];
  }
}
loadCampuses();

async function loadPaymentMethods() {
  try {
    const res = await fetchActivePaymentMethods();
    paymentMethods.value = res.data.data ?? [];
    if (!paymentMethods.value.some((method) => method.methodCode === 'STUDENT_CREDIT')) {
      paymentMethods.value.unshift({ id: 'student-credit', methodCode: 'STUDENT_CREDIT', methodName: 'Student Credit', sortOrder: -1, status: 'ACTIVE' });
    }
  } catch {
    paymentMethods.value = [];
  }
}
loadPaymentMethods();

// ─── Filters & Pagination ───────────────────────────
const filters = reactive({
  keyword: '',
  campusId: '',
  paymentStatus: '',
});
const advancedFiltersVisible = ref(false);
const mobileSearchExpanded = ref(false);
const activeAdvancedFilterCount = computed(() =>
  Number(Boolean(filters.campusId)) + Number(Boolean(filters.paymentStatus))
);

const tableData = ref<StudentItem[]>([]);
const courseMemberships = ref<StudentCourseMembership[]>([]);
const openProducts = ref(new Set<string>());
const openProjects = ref(new Set<string>());
const openGroups = ref(new Set<string>());
let lastSearchKeyword = '';

// 同一学生可以属于多个实际班级；使用实体 ID 避免同名班级混合。
const studentProducts = computed(() => {
  type ClassGroup = { key: string; label: string; students: StudentItem[] };
  type Project = { key: string; label: string; classes: ClassGroup[] };
  type Product = { key: string; label: string; projects: Project[]; studentCount: number };
  const products = new Map<string, Product>();
  const students = new Map(tableData.value.map((student) => [String(student.id), student]));
  const assigned = new Set<string>();
  const add = (student: StudentItem, productKey: string, productLabel: string, projectKey: string, projectLabel: string, classKey: string, classLabel: string) => {
    let product = products.get(productKey);
    if (!product) { product = { key: productKey, label: productLabel, projects: [], studentCount: 0 }; products.set(productKey, product); }
    let project = product.projects.find((item) => item.key === projectKey);
    if (!project) { project = { key: projectKey, label: projectLabel, classes: [] }; product.projects.push(project); }
    let group = project.classes.find((item) => item.key === classKey);
    if (!group) { group = { key: classKey, label: classLabel, students: [] }; project.classes.push(group); }
    if (!group.students.some((item) => item.id === student.id)) group.students.push(student);
  };
  for (const membership of courseMemberships.value) {
    const student = students.get(String(membership.studentId));
    if (!student) continue;
    assigned.add(String(student.id));
    const productKey = `product:${membership.productId}`;
    const projectKey = `${productKey}:project:${membership.projectId || membership.projectName}`;
    const classKey = `${projectKey}:class:${membership.classId || 'private'}`;
    add(student, productKey, membership.productName, projectKey, membership.projectName, classKey,
      [membership.className, membership.campusName].filter(Boolean).join(' · '));
  }
  for (const student of students.values()) {
    if (!assigned.has(String(student.id))) add(student, 'unassigned', 'No course', 'unassigned-project', 'No project', 'unassigned-class', 'No class');
  }
  for (const product of products.values()) {
    product.projects.sort((a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }));
    for (const project of product.projects) project.classes.sort((a, b) => a.label.localeCompare(b.label, undefined, { numeric: true }));
    product.studentCount = new Set(product.projects.flatMap((project) => project.classes.flatMap((group) => group.students.map((student) => student.id)))).size;
  }
  return [...products.values()].sort((a, b) => a.key === 'unassigned' ? 1 : b.key === 'unassigned' ? -1 : a.label.localeCompare(b.label, undefined, { numeric: true }));
});

function setOpen(openKeys: Set<string>, key: string, event: Event) {
  if ((event.target as HTMLDetailsElement).open) openKeys.add(key); else openKeys.delete(key);
}

// 搜索完成后展开所有匹配路径；清空搜索时恢复折叠，避免显示整页学生表格。
function expandSearchResults(keyword: string) {
  if (keyword) {
    openProducts.value = new Set(studentProducts.value.map((product) => product.key));
    openProjects.value = new Set(studentProducts.value.flatMap((product) => product.projects.map((project) => project.key)));
    openGroups.value = new Set(studentProducts.value.flatMap((product) => product.projects.flatMap((project) => project.classes.map((group) => group.key))));
  } else if (lastSearchKeyword) {
    openProducts.value = new Set();
    openProjects.value = new Set();
    openGroups.value = new Set();
  }
  lastSearchKeyword = keyword;
}
const selectedStudent = ref<StudentItem | null>(null);
const studentDetail = ref<StudentItem | null>(null);
const detailDrawerVisible = ref(false);
const detailLoading = ref(false);
const studentOrders = ref<EnrollmentOrderItem[]>([]);
const studentOrderPayments = ref<Record<string, PaymentRecordItem[]>>({});
const studentAccounts = ref<StudentAccountItem[]>([]);
const studentAccountDialogVisible = ref(false);
const studentAccountSaving = ref(false);
const studentAccountMode = ref<'create' | 'edit'>('create');
const studentAccountTarget = ref<StudentItem | null>(null);
const editingStudentAccount = ref<StudentAccountItem | null>(null);
const studentAccountForm = reactive({
  username: '',
  displayName: '',
  email: '',
  status: 'ACTIVE',
  password: '',
  confirmPassword: '',
});

const paymentDialogVisible = ref(false);
const paymentSubmitting = ref(false);
const paymentOrder = ref<EnrollmentOrderDetailItem | null>(null);
const paymentRows = ref<PaymentDraft[]>([]);
const refundDialogVisible = ref(false);
const refunding = ref(false);
const refundOrder = ref<EnrollmentOrderItem | null>(null);
const refundForm = reactive({
  refundAmount: 0,
  paymentMethod: 'CASH',
  handledByName: '',
  remark: '',
  disposition: 'CREDIT' as 'REFUND' | 'CREDIT',
});

const DELETE_COOLDOWN_SECONDS = 10;
const deleteDialogVisible = ref(false);
const deleteTarget = ref<StudentItem | null>(null);
const deleteConfirmRemaining = ref(DELETE_COOLDOWN_SECONDS);
const deletingStudent = ref(false);
let deleteCooldownTimer: number | undefined;
let deleteCooldownReleaseTimer: number | undefined;
let deleteCooldownDeadline = 0;

interface PaymentDraft {
  rowKey: string;
  paymentMethod: string;
  amount: number;
  paidAt: string;
  handledByName: string;
  remark: string;
}

const selectedStudentForDisplay = computed(() => studentDetail.value ?? selectedStudent.value);
const paymentBalance = computed(() => roundMoney(paymentOrder.value?.balanceAmount ?? 0));
const paymentDraftTotal = computed(() => roundMoney(paymentRows.value.reduce((sum, row) => sum + numeric(row.amount), 0)));
const paymentDraftRemaining = computed(() => roundMoney(paymentBalance.value - paymentDraftTotal.value));
function handleFilterChange() {
  loadData();
}

function applyRouteFilters() {
  const keyword = getQueryValue(route.query.keyword);
  const guardian = getQueryValue(route.query.guardian);
  const paymentStatus = getQueryValue(route.query.paymentStatus);
  if (keyword !== undefined) {
    filters.keyword = keyword;
  }
  if (guardian !== undefined) {
    filters.keyword = guardian;
  }
  if (paymentStatus !== undefined) {
    filters.paymentStatus = paymentStatus;
    advancedFiltersVisible.value = true;
  }
}

function clearAdvancedFilters() {
  filters.campusId = '';
  filters.paymentStatus = '';
  handleFilterChange();
}

function accountForStudent(row: StudentItem) {
  return studentAccounts.value.find((account) => String(account.studentId) === String(row.id));
}

function accountStatusLabel(status?: string) {
  return status === 'ACTIVE' ? 'Active' : 'Inactive';
}

function openStudentAccountDialog(row: StudentItem) {
  const account = accountForStudent(row);
  studentAccountTarget.value = row;
  editingStudentAccount.value = account ?? null;
  studentAccountMode.value = account ? 'edit' : 'create';
  Object.assign(studentAccountForm, {
    username: account?.username ?? usernameFromName(row.studentName),
    displayName: account?.displayName ?? row.studentName,
    email: account?.email ?? row.guardianEmail ?? '',
    status: account?.status ?? 'ACTIVE',
    password: account ? '' : DEFAULT_INITIAL_PASSWORD,
    confirmPassword: account ? '' : DEFAULT_INITIAL_PASSWORD,
  });
  studentAccountDialogVisible.value = true;
}

function validateStudentAccountForm() {
  if (!studentAccountForm.username.trim()) {
    ElMessage.warning('Username is required.');
    return false;
  }
  if (studentAccountMode.value === 'create' && studentAccountForm.password.length < 8) {
    ElMessage.warning('Password must be at least 8 characters.');
    return false;
  }
  if (studentAccountForm.password && studentAccountForm.password.length < 8) {
    ElMessage.warning('Password must be at least 8 characters.');
    return false;
  }
  if (studentAccountForm.password !== studentAccountForm.confirmPassword) {
    ElMessage.warning('Passwords do not match.');
    return false;
  }
  return true;
}

async function submitStudentAccount() {
  if (!studentAccountTarget.value || !validateStudentAccountForm()) {
    return;
  }
  studentAccountSaving.value = true;
  try {
    if (studentAccountMode.value === 'create') {
      const payload: StudentAccountCreatePayload = {
        studentId: studentAccountTarget.value.id,
        studentName: studentAccountTarget.value.studentName,
        guardianName: studentAccountTarget.value.guardianName ?? undefined,
        guardianPhone: studentAccountTarget.value.guardianPhone ?? studentAccountTarget.value.phone ?? undefined,
        guardianEmail: studentAccountTarget.value.guardianEmail ?? undefined,
        username: studentAccountForm.username.trim(),
        password: studentAccountForm.password,
        displayName: studentAccountForm.displayName.trim() || undefined,
        email: studentAccountForm.email.trim() || undefined,
        status: studentAccountForm.status,
      };
      await createStudentAccount(payload);
      ElMessage.success('Student login created.');
    } else if (editingStudentAccount.value) {
      const payload: AccountUpdatePayload = {
        username: studentAccountForm.username.trim(),
        displayName: studentAccountForm.displayName.trim() || undefined,
        email: studentAccountForm.email.trim() || undefined,
        status: studentAccountForm.status,
        password: studentAccountForm.password || undefined,
      };
      await updateStudentAccount(editingStudentAccount.value.userId, payload);
      ElMessage.success('Student login updated.');
    }
    studentAccountDialogVisible.value = false;
    await loadStudentAccounts();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    studentAccountSaving.value = false;
  }
}

// ─── Load Data ────────────────────────────────────
let loadVersion = 0;
async function loadData() {
  const version = ++loadVersion;
  loading.value = true;
  try {
    const params: StudentQuery = {
      keyword: filters.keyword || undefined,
      campusId: filters.campusId || undefined,
      paymentStatus: filters.paymentStatus || undefined,
      sortByName: true,
      pageNum: 1,
      pageSize: 200,
    };
    const [memberships, firstPage] = await Promise.all([fetchStudentCourseMemberships(), fetchStudents(params), loadStudentAccounts()]);
    if (version !== loadVersion) return;
    courseMemberships.value = memberships.data.data ?? [];
    tableData.value = firstPage.data.data.records;
    const total = firstPage.data.data.total;
    for (let page = 2; tableData.value.length < total; page += 1) {
      const response = await fetchStudents({ ...params, pageNum: page });
      if (version !== loadVersion) return;
      const records = response.data.data.records;
      if (!records.length) break;
      tableData.value = [...tableData.value, ...records];
    }
    expandSearchResults(params.keyword?.trim() ?? '');
  } catch {
    if (version === loadVersion) ElMessage.error('Failed to load students. Please try again.');
  } finally {
    if (version === loadVersion) loading.value = false;
  }
}

async function loadStudentAccounts() {
  try {
    const accounts: StudentAccountItem[] = [];
    for (let page = 1; ; page += 1) {
      const res = await fetchStudentAccounts({ pageNum: page, pageSize: 200 });
      const data = res.data.data;
      accounts.push(...(data.records ?? []));
      if (!data.records?.length || accounts.length >= data.total) break;
    }
    studentAccounts.value = accounts;
  } catch {
    studentAccounts.value = [];
  }
}

// ─── Form ──────────────────────────────────────────
const studentClassOptions = ['G1', 'G2', 'G3', 'G4', 'G5', 'G6', 'G7', 'G8', 'SHSAT', 'PSAT', 'SAT', 'ESL', 'TBD'];

const studentForm = reactive<StudentSavePayload>({
  studentName: '',
  gender: 'Female',
  guardianName: '',
  guardianPhone: '',
  guardianPhone2: '',
  guardianEmail: '',
  guardianRelationType: '',
  grade: '',
  studentClass: '',
  school: '',
  status: 'ACTIVE',
  notes: '',
  englishName: '',
  birthDate: '',
  sourceChannel: '',
  campusId: undefined,
  pickupRequired: false,
  pickupAddress: '',
  dropoffRequired: false,
  dropoffAddress: '',
});

const studentFormRules = {
  studentName: [{ required: true, message: 'Name is required' }],
  guardianName: [{ required: true, message: 'Guardian name is required' }],
  guardianPhone: [{ required: true, message: 'Phone is required' }],
};

watch(() => studentForm.pickupRequired, (required) => {
  if (!required) {
    studentForm.pickupAddress = '';
  }
});

watch(() => studentForm.dropoffRequired, (required) => {
  if (!required) {
    studentForm.dropoffAddress = '';
  }
});

function openStudentDialog(row?: StudentItem) {
  editingId.value = row?.id ?? null;
  Object.assign(studentForm, {
    studentName: row?.studentName ?? '',
    gender: row?.gender ?? '',
    guardianName: row?.guardianName ?? '',
    guardianPhone: row?.guardianPhone ?? row?.phone ?? '',
    guardianPhone2: row?.guardianPhone2 ?? '',
    guardianEmail: row?.guardianEmail ?? '',
    guardianRelationType: row?.guardianRelationType ?? '',
    grade: row?.grade ?? '',
    studentClass: row?.studentClass ?? '',
    school: row?.school ?? '',
    status: row?.status ?? 'ACTIVE',
    notes: row?.notes ?? '',
    englishName: row?.englishName ?? '',
    birthDate: row?.birthDate ?? '',
    sourceChannel: row?.sourceChannel ?? '',
    campusId: row?.campusId ?? undefined,
    pickupRequired: Boolean(row?.pickupRequired),
    pickupAddress: row?.pickupRequired ? row.pickupAddress ?? '' : '',
    dropoffRequired: Boolean(row?.dropoffRequired),
    dropoffAddress: row?.dropoffRequired ? row.dropoffAddress ?? '' : '',
  });
  // Clear any previous validation errors when opening dialog
  studentFormRef.value?.clearValidate();
  studentDialogVisible.value = true;
}

async function handleStudentSubmit() {
  const valid = await studentFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    const payload = {
      ...studentForm,
      pickupAddress: studentForm.pickupRequired ? studentForm.pickupAddress : '',
      dropoffAddress: studentForm.dropoffRequired ? studentForm.dropoffAddress : '',
    };
    if (editingId.value) {
      await updateStudent(editingId.value, payload);
      ElMessage.success('Student updated.');
    } else {
      await createStudent(payload);
      ElMessage.success('Student added.');
    }
    studentDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

defineExpose({ openCreateDialog: () => openStudentDialog() });

async function handleDeleteStudent(row: StudentItem) {
  deleteTarget.value = row;
  deleteDialogVisible.value = true;
  startDeleteCooldown();
}

const deleteManagedCourseCount = computed(() => deleteTarget.value?.enrollmentCount ?? 0);

function startDeleteCooldown() {
  clearDeleteCooldown();
  deleteCooldownDeadline = Date.now() + DELETE_COOLDOWN_SECONDS * 1000;
  deleteCooldownTimer = window.setInterval(() => {
    deleteConfirmRemaining.value = Math.max(0, Math.ceil((deleteCooldownDeadline - Date.now()) / 1000));
    if (deleteConfirmRemaining.value === 0) {
      clearDeleteCooldown();
    }
  }, 250);
  deleteCooldownReleaseTimer = window.setTimeout(() => {
    deleteConfirmRemaining.value = 0;
    clearDeleteCooldown();
  }, DELETE_COOLDOWN_SECONDS * 1000);
  deleteConfirmRemaining.value = DELETE_COOLDOWN_SECONDS;
}

function clearDeleteCooldown() {
  if (deleteCooldownTimer != null) {
    window.clearInterval(deleteCooldownTimer);
    deleteCooldownTimer = undefined;
  }
  if (deleteCooldownReleaseTimer != null) {
    window.clearTimeout(deleteCooldownReleaseTimer);
    deleteCooldownReleaseTimer = undefined;
  }
  deleteCooldownDeadline = 0;
}

function resetDeleteDialog() {
  clearDeleteCooldown();
  deleteTarget.value = null;
  deleteConfirmRemaining.value = DELETE_COOLDOWN_SECONDS;
  deletingStudent.value = false;
}

async function confirmDeleteStudent() {
  if (!deleteTarget.value || deleteConfirmRemaining.value > 0) {
    return;
  }
  deletingStudent.value = true;
  try {
    await deleteStudent(deleteTarget.value.id);
    ElMessage.success('Student deleted.');
    deleteDialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    deletingStudent.value = false;
  }
}

// ─── Student detail and enrollment history ─────────
async function openStudentDetail(row: StudentItem) {
  selectedStudent.value = row;
  studentDetail.value = null;
  studentOrders.value = [];
  studentOrderPayments.value = {};
  detailDrawerVisible.value = true;
  detailLoading.value = true;
  try {
    const [studentRes] = await Promise.all([
      fetchStudent(row.id),
      loadStudentOrderHistory(row.id),
    ]);
    studentDetail.value = studentRes.data.data;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    detailLoading.value = false;
  }
}

async function openStudentFromRoute() {
  const studentId = getQueryValue(route.query.studentId);
  if (!studentId) {
    return;
  }
  try {
    const row = tableData.value.find((item) => String(item.id) === studentId);
    if (row) {
      await openStudentDetail(row);
    } else {
      const res = await fetchStudent(studentId);
      await openStudentDetail(res.data.data);
    }
    await openPaymentFromRouteAction();
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  }
}

async function openPaymentFromRouteAction() {
  if (getQueryValue(route.query.action) !== 'payment') {
    return;
  }
  const payableOrder = studentOrders.value.find(canRecordPayment);
  if (!payableOrder) {
    ElMessage.info('No unpaid confirmed order is available for this student.');
    return;
  }
  await openPaymentDialog(payableOrder);
}

async function reloadCurrentStudentContext() {
  await loadData();
  if (!selectedStudent.value) {
    return;
  }
  const latest = tableData.value.find((item) => item.id === selectedStudent.value?.id);
  if (latest) {
    selectedStudent.value = latest;
  }
  await loadStudentOrderHistory(selectedStudent.value.id);
}

async function loadStudentOrderHistory(studentId: StudentItem['id']) {
  const ordersRes = await fetchEnrollmentOrders({ studentId, pageNum: 1, pageSize: 100 });
  const orders = ordersRes.data.data.records ?? [];
  studentOrders.value = orders;
  if (orders.length === 0) {
    studentOrderPayments.value = {};
    return;
  }

  const detailResponses = await Promise.all(orders.map((order) => fetchEnrollmentOrder(order.id)));
  studentOrderPayments.value = Object.fromEntries(
    detailResponses.map((res, index) => [String(orders[index].id), res.data.data.payments ?? []])
  );
}

function openReceipt(row: EnrollmentOrderItem) {
  openRegistrationReceipt(row.id);
}

// 支付或退款成功后复用 View Receipt 的注册页，显示和打印始终保持同一套收据实现。
function openRegistrationReceipt(orderId: string | number) {
  void router.push({ name: 'registration', query: { orderId: String(orderId) } });
}

function startNewRegistration() {
  if (!selectedStudent.value) {
    return;
  }
  detailDrawerVisible.value = false;
  startRegistrationForStudent(selectedStudent.value);
}

function startRegistrationForStudent(student: StudentItem) {
  router.push({
    name: 'registration',
    query: { studentId: student.id },
  });
}

function canRecordPayment(row: EnrollmentOrderItem) {
  return row.orderStatus === 'CONFIRMED'
    && row.paymentStatus !== 'PAID'
    && row.paymentStatus !== 'REFUNDED'
    && numeric(row.balanceAmount) > 0;
}

function canRefundOrder(row: EnrollmentOrderItem) {
  return row.paymentStatus !== 'REFUNDED' && numeric(row.receivedAmount) > 0;
}

async function openPaymentDialog(row: EnrollmentOrderItem) {
  try {
    const res = await fetchEnrollmentOrder(row.id);
    paymentOrder.value = res.data.data;
    paymentRows.value = [createPaymentDraft(Math.max(0, numeric(paymentOrder.value.balanceAmount)))];
    paymentDialogVisible.value = true;
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  }
}

function createPaymentDraft(amount = 0): PaymentDraft {
  return {
    rowKey: `payment-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    paymentMethod: paymentMethods.value[0]?.methodCode ?? 'CASH',
    amount: roundMoney(amount),
    paidAt: currentDate(),
    handledByName: '',
    remark: '',
    disposition: 'CREDIT',
  };
}

function addPaymentRow() {
  paymentRows.value.push(createPaymentDraft());
}

function removePaymentRow(index: number) {
  paymentRows.value.splice(index, 1);
}

async function submitPayments() {
  if (!paymentOrder.value) {
    return;
  }
  const rows = paymentRows.value
    .map((row) => ({
      ...row,
      amount: roundMoney(row.amount),
      handledByName: row.handledByName.trim(),
      remark: row.remark.trim(),
    }))
    .filter((row) => row.amount > 0);
  if (rows.length === 0) {
    ElMessage.warning('Add at least one payment amount.');
    return;
  }
  const missing = rows.find((row) => !row.paymentMethod || !row.paidAt || !row.handledByName);
  if (missing) {
    ElMessage.warning('Payment method, date, and handler are required for every payment.');
    return;
  }
  if (paymentDraftTotal.value > paymentBalance.value) {
    ElMessage.warning('New payments cannot exceed the current balance.');
    return;
  }

  paymentSubmitting.value = true;
  try {
    await createPaymentRecord({
      enrollmentOrderId: paymentOrder.value.id,
      splits: rows.map((row) => ({
        paymentMethod: row.paymentMethod,
        amount: row.amount,
        paidAt: toLocalDateTime(row.paidAt),
        handledByName: row.handledByName,
        remark: row.remark || undefined,
      })),
    });
    ElMessage.success('Payment recorded.');
    const orderId = paymentOrder.value.id;
    const updatedOrder = await fetchEnrollmentOrder(orderId);
    paymentOrder.value = updatedOrder.data.data;
    await reloadCurrentStudentContext();
    paymentDialogVisible.value = false;
    openRegistrationReceipt(updatedOrder.data.data.id);
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    paymentSubmitting.value = false;
  }
}

function openRefundDialog(row: EnrollmentOrderItem) {
  refundOrder.value = row;
  Object.assign(refundForm, {
    refundAmount: roundMoney(row.receivedAmount ?? 0),
    paymentMethod: paymentMethods.value[0]?.methodCode ?? 'CASH',
    handledByName: '',
    remark: '',
  });
  refundDialogVisible.value = true;
}

async function submitRefund() {
  if (!refundOrder.value) {
    return;
  }
  const refundAmount = roundMoney(refundForm.refundAmount);
  if (refundAmount <= 0) {
    ElMessage.warning('Refund amount must be greater than zero.');
    return;
  }
  if (refundAmount > numeric(refundOrder.value.receivedAmount)) {
    ElMessage.warning('Refund amount cannot exceed the currently received amount.');
    return;
  }
  if ((refundForm.disposition === 'REFUND' && !refundForm.paymentMethod) || !refundForm.handledByName.trim()) {
    ElMessage.warning('Refund method and handler are required.');
    return;
  }

  refunding.value = true;
  try {
    await refundEnrollmentOrder(refundOrder.value.id, {
      refundAmount,
      paymentMethod: refundForm.paymentMethod,
      handledByName: refundForm.handledByName.trim(),
      remark: refundForm.remark.trim() || undefined,
      disposition: refundForm.disposition,
    });
    ElMessage.success('Refund completed.');
    refundDialogVisible.value = false;
    const updatedOrder = await fetchEnrollmentOrder(refundOrder.value.id);
    await reloadCurrentStudentContext();
    openRegistrationReceipt(updatedOrder.data.data.id);
  } catch (error) {
    ElMessage.error(extractErrorMessage(error));
  } finally {
    refunding.value = false;
  }
}

function summarizeOrderItems(row: EnrollmentOrderItem) {
  const items = row.items ?? [];
  if (items.length === 0) {
    return row.courseProductName ?? row.className ?? '-';
  }
  return items
    .slice(0, 2)
    .map((item) => item.detail || item.registerClass || item.courseType || 'Course')
    .join('; ')
    + (items.length > 2 ? ` +${items.length - 2}` : '');
}

function paymentMethodName(code: string) {
  return paymentMethods.value.find((item) => item.methodCode === code)?.methodName ?? code;
}

function paymentTypeLabel(type?: string) {
  return type === 'REFUND' ? 'REFUND' : 'PAYMENT';
}

function paymentTimeline(row: EnrollmentOrderItem) {
  return studentOrderPayments.value[String(row.id)] ?? [];
}

function paymentStatusType(status?: string) {
  if (status === 'PAID') return 'success';
  if (status === 'PARTIAL') return 'warning';
  if (status === 'REFUNDED') return 'danger';
  if (status === 'NO_ENROLLMENT' || !status) return 'info';
  return 'info';
}

function paymentStatusLabel(status?: string) {
  const map: Record<string, string> = {
    UNPAID: 'Unpaid',
    PARTIAL: 'Partial',
    PAID: 'Paid',
    REFUNDED: 'Refunded',
    NO_ENROLLMENT: 'No enrollment',
  };
  return map[status ?? 'NO_ENROLLMENT'] ?? status ?? 'No enrollment';
}

function guardianRelationLabel(value?: string) {
  const map: Record<string, string> = {
    MOTHER: 'Mother',
    FATHER: 'Father',
    GRANDPARENT: 'Grandparent',
    OTHER: 'Other',
  };
  return value ? map[value] ?? value : '-';
}

function orderStatusType(status?: string) {
  if (status === 'CONFIRMED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  if (status === 'CLOSED') return 'info';
  return 'warning';
}

function orderStatusLabel(status?: string) {
  const map: Record<string, string> = {
    DRAFT: 'Draft',
    CONFIRMED: 'Confirmed',
    CANCELLED: 'Cancelled',
    CLOSED: 'Closed',
  };
  return status ? map[status] ?? status : '-';
}

function numeric(value: unknown) {
  const result = Number(value);
  return Number.isFinite(result) ? result : 0;
}

function roundMoney(value: number) {
  return Math.round((numeric(value) + Number.EPSILON) * 100) / 100;
}

function formatMoney(value: unknown) {
  return `$${roundMoney(numeric(value)).toFixed(2)}`;
}

function formatDate(value?: string) {
  if (!value) {
    return '-';
  }
  return value.includes('T') ? value.slice(0, 10) : value;
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 16);
}

function currentDate() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function toLocalDateTime(date: string) {
  return `${date}T00:00:00`;
}

function getQueryValue(value: unknown) {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : undefined;
  }
  return typeof value === 'string' ? value : undefined;
}

// ─── Deleted Students Dialog ───────────────────────
const deletedDialogVisible = ref(false);
const deletedLoading = ref(false);
const deletedRows = ref<StudentItem[]>([]);

async function openDeletedDialog() {
  deletedDialogVisible.value = true;
  deletedLoading.value = true;
  deletedRows.value = [];
  try {
    const res = await fetchDeletedStudents();
    deletedRows.value = res.data.data ?? [];
  } catch {
    ElMessage.error('Failed to load deleted students.');
  } finally {
    deletedLoading.value = false;
  }
}

// ─── Init ──────────────────────────────────────────
async function initializeStudentPage() {
  applyRouteFilters();
  await loadData();
  await openStudentFromRoute();
}

watch(
  () => route.query,
  () => {
    void initializeStudentPage();
  }
);

void initializeStudentPage();

onBeforeUnmount(() => {
  clearDeleteCooldown();
});
</script>

<style scoped>
.course-product-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header__title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.panel-header__actions {
  display: flex;
  gap: 8px;
}

.student-search-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.student-search-bar :deep(.el-input) {
  width: min(440px, 100%);
}

.student-search-bar__filters {
  color: #606266;
}

.student-search-bar__filter-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 2px;
  padding: 0 5px;
  border-radius: 9px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 11px;
}

.student-advanced-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -4px 0 12px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafcff;
}

.student-advanced-filters :deep(.el-select) {
  width: 190px;
}

@media (min-width: 641px) {
  .panel-header__actions .student-add-trigger { display: none; }
}

@media (max-width: 640px) {
  .course-product-panel { padding: 12px; }

  .panel-header {
    align-items: center;
    flex-direction: row;
    gap: 8px;
    margin-bottom: 10px;
  }

  .panel-header__title { display: none; }

  .panel-header__actions {
    justify-content: flex-end;
    width: auto;
    margin-left: auto;
  }

  .panel-header__actions > .el-button {
    min-height: 36px;
    flex: 0 0 auto;
    margin: 0;
    padding: 0 8px;
    font-size: 12px;
  }

  .panel-header__actions > .student-add-trigger {
    padding: 0 12px;
    font-weight: 600;
  }

  .student-search-bar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 76px;
    align-items: center;
    gap: 8px;
    margin: 0;
  }

  .student-search-bar :deep(.el-input) {
    width: 100%;
    min-width: 0;
  }

  .student-search-bar :deep(.el-input__wrapper),
  .student-search-bar__button {
    min-height: 44px;
    margin: 0;
  }

  .student-search-bar :deep(.el-input__inner) {
    font-size: 13px;
  }

  .student-search-bar__button {
    width: 76px;
    padding: 0 12px;
    font-size: 12px;
  }

  .student-advanced-filters {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    margin: 10px 0 0;
    padding: 10px 0 0;
    border: 0;
    border-top: 1px solid #edf1f6;
    border-radius: 0;
    background: transparent;
  }

  .student-advanced-filters :deep(.el-select) {
    width: 100%;
  }

  .student-advanced-filters > .el-button {
    grid-column: 1 / -1;
    justify-self: start;
    min-height: 32px;
    margin: 0;
    padding: 0 4px;
    color: #64748b;
    font-size: 12px;
  }

}

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.student-product { margin-bottom: 10px; border: 1px solid #d9e4ee; border-radius: 6px; overflow: hidden; }
.student-product summary { cursor: pointer; padding: 10px 14px; color: #29425e; font-size: 13px; font-weight: 600; background: #f2f6fa; }
.student-product summary span { float: right; color: #73849a; font-size: 12px; font-weight: 400; }
.student-project > summary { padding-left: 28px; background: #f8fafc; border-top: 1px solid #e5edf4; }
.student-group > summary { padding-left: 42px; background: #fff; border-top: 1px solid #e5edf4; }
.student-groups-empty { padding: 30px; text-align: center; color: #909399; }

.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cell-stack strong {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.cell-stack span {
  color: #909399;
  font-size: 12px;
}

.table-actions {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.deleted-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
}

.balance-due {
  color: var(--el-color-danger);
}

@media (max-width: 920px) {
  .people-table-zoom-scroll {
    max-height: max(260px, calc(100dvh - 340px));
    overflow-x: hidden;
    overflow-y: auto;
    touch-action: pan-x pan-y;
    -webkit-overflow-scrolling: touch;
  }

  /* Keep the accordion headings fixed while only the table body scrolls
     sideways. Fixed left/right columns then use Element Plus' native body
     scroll container, so the Student and More columns stay aligned. */
  .student-product { overflow: visible; }
  .people-main-table { width: 100%; min-width: 0; overflow: visible; }
  .people-main-table :deep(.el-table__header-wrapper),
  .people-main-table :deep(.el-table__fixed-header-wrapper) { position: sticky; top: 0; z-index: 4; }
  .people-main-table :deep(.el-table__header th) { height: 36px; padding: 4px 6px; white-space: nowrap; }
  .people-main-table :deep(.el-table__cell) { padding: 4px 6px; }
  .people-main-table :deep(.cell) { padding: 0; font-size: 11px; white-space: nowrap; }
  .people-main-table :deep(.cell-stack) { gap: 1px; }
  .people-main-table :deep(.cell-stack strong) { font-size: 11px; }
  .people-main-table :deep(.cell-stack span) { font-size: 10px; }
  .people-main-table :deep(.el-table__row) { height: 42px; cursor: pointer; }
  .people-main-table :deep(.el-table-fixed-column--right) { box-shadow: -4px 0 8px rgb(31 63 54 / 8%); }
  .people-main-table :deep(.mobile-table-actions .el-button) { width: 36px; height: 36px; }
}

.student-detail {
  display: grid;
  gap: 16px;
  min-height: 100%;
}

.detail-header,
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.detail-header__actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.detail-header h2 {
  margin: 0;
  color: #303133;
  font-size: 22px;
}

.detail-header p {
  margin: 5px 0 0;
  color: #909399;
  font-size: 13px;
}

.detail-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fff;
}

.detail-card h3,
.payment-detail h4 {
  margin: 0;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.detail-grid,
.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid__wide {
  grid-column: span 2;
}

.detail-grid label,
.metric-grid label {
  display: block;
  margin-bottom: 4px;
  color: #909399;
  font-size: 12px;
}

.detail-grid strong,
.metric-grid strong {
  color: #303133;
  font-size: 13px;
}

.section-title-row span {
  color: #909399;
  font-size: 12px;
}

.muted-text {
  color: #909399;
  font-size: 12px;
}

.payment-timeline {
  display: grid;
  gap: 4px;
  min-width: 480px;
  font-size: 12px;
}

.payment-timeline__header,
.payment-timeline__row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 0.7fr 0.8fr 1fr 1.3fr;
  gap: 8px;
  align-items: center;
}

.payment-timeline__header {
  color: #909399;
  font-weight: 600;
}

.payment-timeline__row {
  color: #606266;
}

.payment-timeline__row strong {
  color: #303133;
  font-weight: 600;
}

.payment-detail {
  display: grid;
  gap: 14px;
}

.payment-rows {
  display: grid;
  gap: 8px;
}

.payment-row {
  display: grid;
  grid-template-columns: minmax(110px, 0.8fr) 110px 140px minmax(110px, 0.8fr) minmax(130px, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.payment-total {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  color: #606266;
  font-size: 13px;
  font-weight: 600;
}

.refund-detail {
  display: grid;
  gap: 14px;
}

.refund-warning {
  margin: 0;
  border: 1px solid #f5c2c7;
  border-radius: 6px;
  background: #fff5f5;
  padding: 10px 12px;
  color: #b42318;
  font-size: 12px;
  line-height: 1.5;
}

.delete-confirm {
  display: grid;
  gap: 14px;
}

.delete-warning-text {
  margin: 0;
  border: 1px solid #f5c2c7;
  border-radius: 6px;
  background: #fff5f5;
  padding: 10px 12px;
  color: #b42318;
  font-size: 13px;
  line-height: 1.5;
}

.delete-cooldown {
  margin: 0;
  color: #b42318;
  font-size: 12px;
  font-weight: 600;
}

/* Student dialog */
.student-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.student-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 14px 20px;
  margin-right: 0;
}

.student-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.dialog-header__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.student-form__row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 14px;
}

.student-form__section-title {
  margin: 18px 0 10px;
  color: #303133;
  font-size: 13px;
}

.student-form__section-title:first-child {
  margin-top: 0;
}

.student-form__full {
  grid-column: 1 / -1;
}

.student-form__third {
  grid-column: span 1;
}

.student-form__row--three {
  grid-template-columns: repeat(3, 1fr);
}

@media (max-width: 640px) {
  :global(.el-dialog.student-dialog:not(.is-fullscreen)) {
    display: flex;
    width: calc(100vw - 24px) !important;
    max-width: 560px;
    max-height: calc(100dvh - 24px);
    flex-direction: column;
    margin: 12px auto !important;
  }
  .student-dialog :deep(.el-dialog__header),
  .student-dialog :deep(.el-dialog__footer) { flex: 0 0 auto; padding: 9px 12px; }
  .student-dialog :deep(.el-dialog__body) {
    min-height: 0;
    max-height: calc(100dvh - 120px);
    flex: 1 1 auto;
    overflow: auto;
    padding: 10px 12px;
    overscroll-behavior: contain;
    -webkit-overflow-scrolling: touch;
  }
  .student-dialog :deep(.dialog-header__title) { font-size: 15px; }
  .student-dialog :deep(.el-form-item) { margin-bottom: 9px; }
  .student-dialog :deep(.el-form-item__label) { padding-bottom: 3px; font-size: 11px; line-height: 1.25; }
  .student-dialog :deep(.el-input__wrapper),
  .student-dialog :deep(.el-select__wrapper),
  .student-dialog :deep(.el-textarea__inner) { min-height: 36px; }
  .student-dialog :deep(.el-input__inner),
  .student-dialog :deep(.el-select__placeholder),
  .student-dialog :deep(.el-select__selected-item) { font-size: 12px; }
  .student-dialog :deep(.el-button:not(.is-text):not(.is-link)) { min-height: 34px; padding: 0 10px; font-size: 12px; }
  .student-dialog :deep(.el-table__header th),
  .student-dialog :deep(.el-table__body td) { height: 36px; padding: 4px 5px; }
  .student-dialog :deep(.el-table .cell) { padding: 0 3px; font-size: 11px; }
  .student-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 8px; align-content: start; }
  .student-form__row,
  .student-form__row--three { display: contents; }
  .student-form__section-title { grid-column: 1 / -1; }
  .student-form__half,
  .student-form__third { grid-column: span 1; min-width: 0; }
  .student-form__full { grid-column: 1 / -1; min-width: 0; }
  .student-form :deep(.el-form-item) { min-width: 0; }
  .student-form__section-title { margin: 12px 0 7px; font-size: 12px; }
  .student-dialog .payment-detail,
  .student-dialog .refund-detail,
  .student-dialog .delete-confirm { gap: 8px; }
  .student-dialog .metric-grid { gap: 7px; }
  .student-dialog .delete-warning-text,
  .student-dialog .refund-warning { padding: 8px 9px; font-size: 11px; }
  .student-dialog .payment-rows { gap: 5px; }
  .student-dialog .payment-total { gap: 10px; font-size: 12px; }
  .student-dialog .dialog-footer { gap: 6px; }
  :global(.el-dialog.student-dialog.is-fullscreen) { display: flex; width: 100vw !important; height: 100dvh; max-height: 100dvh; flex-direction: column; margin: 0 !important; border-radius: 0; }
  :global(.el-dialog.student-dialog.is-fullscreen .el-dialog__header),
  :global(.el-dialog.student-dialog.is-fullscreen .el-dialog__footer) { flex: 0 0 auto; padding: calc(10px + env(safe-area-inset-top)) 14px 10px; }
  :global(.el-dialog.student-dialog.is-fullscreen .el-dialog__footer) { padding: 10px 14px calc(10px + env(safe-area-inset-bottom)); }
  :global(.el-dialog.student-dialog.is-fullscreen .el-dialog__body) { flex: 1 1 0; min-height: 0; max-height: none; overflow: auto; padding: 12px 14px; overscroll-behavior: contain; -webkit-overflow-scrolling: touch; }
  :global(.el-drawer.student-detail-drawer) { width: 100vw !important; max-width: 100vw; }
  .student-detail-drawer :deep(.el-drawer__body) { overflow: auto; padding: 10px; -webkit-overflow-scrolling: touch; }
  .student-detail { gap: 9px; }
  .detail-header { align-items: flex-start; gap: 8px; }
  .detail-header h2 { font-size: 17px; }
  .detail-header__actions { flex-wrap: wrap; justify-content: flex-end; gap: 4px; }
  .detail-header__actions :deep(.el-button) { min-height: 34px; padding: 0 8px; font-size: 11px; }
  .detail-card { gap: 8px; padding: 10px; border-radius: 7px; }
  .detail-card h3 { font-size: 13px; }
}
</style>
