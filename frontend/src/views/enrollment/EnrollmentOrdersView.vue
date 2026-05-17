<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <span class="panel-header__title">Enrollment Orders</span>
        <div class="panel-header__actions">
          <el-button type="primary" @click="openCreateDialog()">
            <el-icon><Plus /></el-icon>
            New enrollment
          </el-button>
          <el-button type="success" @click="openClassEnrollDialog()">
            <el-icon><Plus /></el-icon>
            New class enrollment
          </el-button>
          <el-button @click="discountPresetDialogVisible = true">
            Discount presets
          </el-button>
          <el-button @click="openPaymentMethodDialog()">
            Payment methods
          </el-button>
        </div>
      </div>

      <el-form class="filters" :model="filters" inline>
        <el-form-item label="Keyword" class="filters__item">
          <el-input
            v-model="filters.keyword"
            placeholder="Order no. or student name"
            clearable
            style="width: 180px;"
            @input="handleFilterChange"
          />
        </el-form-item>
        <el-form-item label="Order status" class="filters__item">
          <el-select
            v-model="filters.orderStatus"
            placeholder="All"
            clearable
            style="width: 140px;"
            @change="handleFilterChange"
          >
            <el-option label="Draft" value="DRAFT" />
            <el-option label="Confirmed" value="CONFIRMED" />
            <el-option label="Cancelled" value="CANCELLED" />
            <el-option label="Closed" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="Payment status" class="filters__item">
          <el-select
            v-model="filters.paymentStatus"
            placeholder="All"
            clearable
            style="width: 140px;"
            @change="handleFilterChange"
          >
            <el-option label="Unpaid" value="UNPAID" />
            <el-option label="Partial" value="PARTIAL" />
            <el-option label="Paid" value="PAID" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- Tabs: 1:1 / Group Class -->
      <el-tabs v-model="activeTab" class="enrollment-tabs">
        <el-tab-pane label="1:1 Private" name="ONE_ON_ONE">
          <el-table
            v-loading="loading"
            :data="tableData"
            row-key="id"
            class="product-table"
            :show-empty="true"
          >
            <el-table-column label="Student" min-width="150">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong>{{ row.studentName }}</strong>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Product" min-width="140">
              <template #default="{ row }">
                {{ row.courseProductName ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="Original price" min-width="110" align="right">
              <template #default="{ row }">${{ row.originalAmount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Discount" min-width="90" align="right">
              <template #default="{ row }">
                <span class="discount-text">-${{ row.discountAmount?.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Received" min-width="100" align="right">
              <template #default="{ row }">
                <strong>${{ row.receivedAmount?.toFixed(2) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="Payment" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="paymentStatusType(row.paymentStatus)">
                  {{ paymentStatusLabel(row.paymentStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Status" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="orderStatusType(row.orderStatus)">
                  {{ orderStatusLabel(row.orderStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" min-width="140" align="center" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="openDetailDialog(row)">Detail</el-button>
                <el-button v-if="row.orderStatus === 'CONFIRMED' && row.paymentStatus !== 'PAID'" text type="success" @click="openPayDialog(row)">
                  Pay
                </el-button>
                <el-button v-if="row.orderStatus === 'CONFIRMED'" text type="danger" @click="handleCancel(row)">
                  Cancel
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="table-footer">
            <span>Showing {{ pagination.start }}–{{ pagination.end }} of {{ pagination.total }}</span>
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              background
              layout="prev, pager, next, sizes"
              :page-sizes="[10, 20, 50]"
              :total="pagination.total"
              @current-change="loadData"
              @size-change="loadData"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="Group Class" name="GROUP">
          <el-table
            v-loading="loadingClasses"
            :data="classEnrollmentData"
            row-key="id"
            class="product-table"
            :show-empty="true"
            @expand-change="handleExpandChange"
          >
            <el-table-column type="expand">
              <template #default="{ row }">
                <div v-if="expandedClassLoading[row.id]" class="expand-loading">
                  Loading orders...
                </div>
                <div v-else class="expand-orders-container">
                  <el-table
                    :data="(expandedClassOrders[row.id] ?? [])"
                    row-key="id"
                    size="small"
                    class="nested-table"
                    empty-text="No enrollment orders for this class."
                  >
                    <el-table-column label="Student" min-width="130">
                      <template #default="{ row: order }">
                        <strong>{{ order.studentName }}</strong>
                      </template>
                    </el-table-column>
                    <el-table-column label="Original price" min-width="100" align="right">
                      <template #default="{ row: order }">${{ order.originalAmount?.toFixed(2) }}</template>
                    </el-table-column>
                    <el-table-column label="Discount" min-width="80" align="right">
                      <template #default="{ row: order }">
                        <span class="discount-text">-${{ order.discountAmount?.toFixed(2) }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="Received" min-width="90" align="right">
                      <template #default="{ row: order }">
                        <strong>${{ order.receivedAmount?.toFixed(2) }}</strong>
                      </template>
                    </el-table-column>
                    <el-table-column label="Payment" min-width="90" align="center">
                      <template #default="{ row: order }">
                        <el-tag size="small" :type="paymentStatusType(order.paymentStatus)">
                          {{ paymentStatusLabel(order.paymentStatus) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="Status" min-width="90" align="center">
                      <template #default="{ row: order }">
                        <el-tag size="small" :type="orderStatusType(order.orderStatus)">
                          {{ orderStatusLabel(order.orderStatus) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="Actions" min-width="130" align="center">
                      <template #default="{ row: order }">
                        <el-button text type="primary" size="small" @click="openDetailDialog(order)">Detail</el-button>
                        <el-button v-if="order.orderStatus === 'CONFIRMED' && order.paymentStatus !== 'PAID'" text type="success" size="small" @click="openPayDialog(order)">Pay</el-button>
                        <el-button v-if="order.orderStatus === 'CONFIRMED'" text type="danger" size="small" @click="handleCancel(order)">Cancel</el-button>
                      </template>
                    </el-table-column>
                  </el-table>

                  <div v-if="(expandedClassPagination[row.id]?.total ?? 0) > 0" class="expand-pagination">
                    <el-pagination
                      background
                      layout="prev, pager, next"
                      small
                      :total="expandedClassPagination[row.id]?.total ?? 0"
                      :page-size="expandedClassPagination[row.id]?.size ?? 10"
                      :current-page="expandedClassPagination[row.id]?.page ?? 1"
                      @current-change="(p: number) => loadExpandedClassOrders(Number(row.id), p)"
                    />
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="Class name" min-width="160">
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong>{{ row.className }}</strong>
                  <span v-if="row.description">{{ row.description }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Teacher" min-width="120">
              <template #default="{ row }">
                {{ row.teacherName ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="Classroom" min-width="120">
              <template #default="{ row }">
                {{ row.classroomName ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="Schedule" min-width="160">
              <template #default="{ row }">
                <span v-if="row.scheduleDesc">{{ row.scheduleDesc }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="Enrolled" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="success">{{ row.activeStudentCount }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- Enrollment detail dialog -->
    <el-dialog
      v-model="detailDialogVisible"
      width="640px"
      :close-on-click-modal="false"
      class="enrollment-dialog"
    >
      <template #header>
        <h3>Enrollment detail</h3>
      </template>

      <div v-if="detailData" class="enrollment-detail">
        <div class="detail-section">
          <h4 class="detail-section__title">Order info</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Order no.</label>
              <span>{{ detailData.enrollmentNo }}</span>
            </div>
            <div class="detail-field">
              <label>Student</label>
              <span>{{ detailData.studentName }}</span>
            </div>
            <div v-if="detailData.className" class="detail-field">
              <label>Class</label>
              <span>{{ detailData.className }}</span>
            </div>
            <div class="detail-field">
              <label>Handled by</label>
              <span>{{ detailData.handledByName ?? '—' }}</span>
            </div>
            <div class="detail-field">
              <label>Created at</label>
              <span>{{ detailData.createdAt }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="detail-section__title">Payment summary</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Original price</label>
              <span>${{ detailData.originalAmount?.toFixed(2) }}</span>
            </div>
            <div class="detail-field">
              <label>Discount</label>
              <span class="discount-text">-${{ detailData.discountAmount?.toFixed(2) }}</span>
            </div>
            <div class="detail-field">
              <label>Received</label>
              <strong>${{ detailData.receivedAmount?.toFixed(2) }}</strong>
            </div>
            <div class="detail-field">
              <label>Outstanding</label>
              <strong class="outstanding-badge">
                ${{ Math.max(0, (detailData.originalAmount ?? 0) - (detailData.discountAmount ?? 0) - (detailData.receivedAmount ?? 0)).toFixed(2) }}
              </strong>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="detail-section__title">Status</h4>
          <div class="detail-grid">
            <div class="detail-field">
              <label>Order status</label>
              <el-tag size="small" :type="orderStatusType(detailData.orderStatus)">
                {{ orderStatusLabel(detailData.orderStatus) }}
              </el-tag>
            </div>
            <div class="detail-field">
              <label>Payment status</label>
              <el-tag size="small" :type="paymentStatusType(detailData.paymentStatus)">
                {{ paymentStatusLabel(detailData.paymentStatus) }}
              </el-tag>
            </div>
          </div>
        </div>

        <!-- Payment records -->
        <div class="detail-section">
          <h4 class="detail-section__title">Payment records</h4>
          <el-table v-if="paymentRecords.length > 0" :data="paymentRecords" size="small" class="nested-table">
            <el-table-column label="Payment no." min-width="140" prop="paymentNo" />
            <el-table-column label="Method" min-width="100">
              <template #default="{ row }">{{ getPaymentMethodName(row.paymentMethod) }}</template>
            </el-table-column>
            <el-table-column label="Handler" min-width="110" prop="handledByName" />
            <el-table-column label="Amount" min-width="90" align="right">
              <template #default="{ row }">${{ row.amount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Status" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="paymentStatusType(row.paymentStatus)">
                  {{ paymentStatusLabel(row.paymentStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Paid at" min-width="140" prop="paidAt" />
            <el-table-column label="Actions" min-width="110" align="center">
              <template #default="{ row: payRow }">
                <el-button text type="primary" size="small" @click="showPaymentReceipt(detailData!)">
                  View Receipt
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="No payment records." :image-size="60" />
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailDialogVisible = false">Close</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Payment dialog -->
    <el-dialog
      v-model="payDialogVisible"
      width="560px"
      :close-on-click-modal="false"
      class="enrollment-dialog"
    >
      <template #header>
        <h3>Record Payment</h3>
      </template>

      <div v-if="payOrder" class="payment-summary">
        <div class="payment-summary__row">
          <span class="payment-summary__label">Enrollment</span>
          <span class="payment-summary__value">{{ payOrder.enrollmentNo }}</span>
        </div>
        <div class="payment-summary__row">
          <span class="payment-summary__label">Student</span>
          <span class="payment-summary__value">{{ payOrder.studentName }}</span>
        </div>
        <div class="payment-summary__row">
          <span class="payment-summary__label">Product</span>
          <span class="payment-summary__value">{{ payOrder.courseProductName ?? '—' }}</span>
        </div>
        <div class="payment-summary__divider" />
        <div class="payment-summary__row">
          <span class="payment-summary__label">Original price</span>
          <span class="payment-summary__value">${{ payOrder.originalAmount?.toFixed(2) }}</span>
        </div>
        <div class="payment-summary__row">
          <span class="payment-summary__label">Discount</span>
          <span class="payment-summary__value discount-text">-${{ payOrder.discountAmount?.toFixed(2) }}</span>
        </div>
        <div class="payment-summary__row payment-summary__row--due">
          <span class="payment-summary__label">Amount Due</span>
          <strong class="payment-summary__value">${{ payAmountDue.toFixed(2) }}</strong>
        </div>
        <div v-if="payOrder.receivedAmount > 0" class="payment-summary__row">
          <span class="payment-summary__label">Already Paid</span>
          <span class="payment-summary__value">${{ payOrder.receivedAmount?.toFixed(2) }}</span>
        </div>
      </div>

      <el-form
        ref="payFormRef"
        label-position="top"
        :model="payForm"
        class="enrollment-form"
      >
        <!-- Payment splits -->
        <el-form-item label="Payment splits">
          <div style="width: 100%;">
            <div
              v-for="(split, index) in payForm.splits"
              :key="index"
              class="payment-split-row"
            >
              <el-select
                v-model="split.paymentMethod"
                placeholder="Method"
                style="flex: 1;"
              >
                <el-option
                  v-for="m in paymentMethods"
                  :key="m.methodCode"
                  :label="m.methodName"
                  :value="m.methodCode"
                />
              </el-select>
              <el-input-number
                v-model="split.amount"
                :min="0.01"
                :precision="2"
                :controls="false"
                style="flex: 1;"
                placeholder="Amount"
              />
              <el-button
                v-if="payForm.splits.length > 1"
                type="danger"
                text
                @click="removePaySplit(index)"
              >
                Remove
              </el-button>
            </div>
            <el-button type="primary" text size="small" @click="addPaySplit" style="margin-top: 4px;">
              + Add payment method
            </el-button>
          </div>
        </el-form-item>

        <!-- Split total vs remaining -->
        <div class="payment-split-total">
          <span>Total: ${{ paySplitTotal.toFixed(2) }}</span>
          <span v-if="paySplitRemaining > 0" class="payment-split-remaining">
            Remaining: ${{ paySplitRemaining.toFixed(2) }}
          </span>
          <span v-else-if="paySplitRemaining < 0" class="payment-split-over">
            Over by: ${{ Math.abs(paySplitRemaining).toFixed(2) }}
          </span>
          <span v-else class="payment-split-balanced">Balanced</span>
        </div>

        <div class="enrollment-form__row">
          <el-form-item label="Handler" prop="handledByName">
            <el-input v-model="payForm.handledByName" placeholder="Your name" />
          </el-form-item>
          <el-form-item label="Issue receipt">
            <el-switch v-model="payForm.issueReceipt" />
          </el-form-item>
        </div>
        <el-form-item label="Notes">
          <el-input v-model="payForm.remark" type="textarea" :rows="2" placeholder="Optional notes" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="payDialogVisible = false">Cancel</el-button>
          <el-button
            type="primary"
            :loading="paying"
            :disabled="paySplitRemaining !== 0"
            @click="handlePaySubmit"
          >Record Payment</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Create enrollment dialog -->
    <el-dialog
      v-model="createDialogVisible"
      width="560px"
      :close-on-click-modal="false"
      class="enrollment-dialog"
    >
      <template #header>
        <h3>New enrollment</h3>
      </template>

      <el-form
        ref="createFormRef"
        label-position="top"
        :model="createForm"
        :rules="createFormRules"
        class="enrollment-form"
      >
        <div class="enrollment-form__row">
          <el-form-item label="Student" prop="studentId" class="enrollment-form__full">
            <el-select
              v-model="createForm.studentId"
              placeholder="Select student"
              filterable
              style="width: 100%;"
            >
              <el-option v-for="s in students" :key="s.id" :label="s.studentName" :value="s.id" />
            </el-select>
          </el-form-item>
        </div>

        <div class="enrollment-form__row">
          <el-form-item label="Course product" prop="courseProductId" class="enrollment-form__full">
            <el-select
              v-model="createForm.courseProductId"
              placeholder="Select course product"
              filterable
              style="width: 100%;"
              @change="createForm.slots = []"
            >
              <el-option
                v-for="p in courseProducts"
                :key="p.id"
                :label="`${p.productName} (${p.subjectName ?? 'General'})`"
                :value="p.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <!-- Product info card -->
        <div v-if="selectedProduct" class="product-info-card">
          <div class="product-info-card__item">
            <span class="product-info-card__label">Subject</span>
            <span class="product-info-card__value">{{ selectedProduct.subjectName ?? 'General' }}</span>
          </div>
          <div class="product-info-card__item">
            <span class="product-info-card__label">Total hours</span>
            <span class="product-info-card__value">{{ selectedProduct.defaultTotalUnits ?? 40 }}h</span>
          </div>
          <div class="product-info-card__item">
            <span class="product-info-card__label">Original price</span>
            <span class="product-info-card__value">${{ (selectedProduct.listPrice ?? 0).toFixed(2) }}</span>
          </div>
        </div>

        <div class="enrollment-form__row">
          <el-form-item label="Primary teacher" prop="primaryTeacherId">
            <el-select
              v-model="createForm.primaryTeacherId"
              placeholder="Select teacher"
              filterable
              clearable
              style="width: 100%;"
            >
              <el-option v-for="t in teachers" :key="t.id" :label="t.teacherName" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="Handled by" prop="handledByName">
            <el-input v-model="createForm.handledByName" placeholder="Your name" />
          </el-form-item>
        </div>

        <div class="enrollment-form__row">
          <el-form-item label="Start date" prop="startDate">
            <el-date-picker
              v-model="createForm.startDate"
              type="date"
              placeholder="First class date"
              style="width: 100%;"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledStartDate"
            />
          </el-form-item>
          <el-form-item label="Default classroom">
            <el-select
              v-model="createForm.defaultClassroomId"
              placeholder="Select classroom"
              clearable
              style="width: 100%;"
            >
              <el-option v-for="c in classrooms" :key="c.id" :label="c.roomName" :value="c.id" />
            </el-select>
          </el-form-item>
        </div>

        <div class="enrollment-form__row">
          <el-form-item label="Discount type">
            <el-select v-model="createDiscountPresetId" placeholder="Select discount" clearable style="width: 100%;">
              <el-option
                v-for="p in discountPresets"
                :key="p.id"
                :label="`${p.presetName} (${p.discountType === 'PERCENTAGE' ? p.discountValue + '% off' : '$' + p.discountValue + ' off'})`"
                :value="p.id"
              />
              <el-option label="Custom" value="__custom__" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="createDiscountPresetId === '__custom__'" label="Discount amount" prop="discountAmount">
            <el-input-number
              v-model="createForm.discountAmount"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%;"
              placeholder="0.00"
            />
          </el-form-item>
          <el-form-item v-else label="Discount amount">
            <div class="discount-display">${{ createForm.discountAmount.toFixed(2) }}</div>
          </el-form-item>
        </div>

        <!-- Weekly Class Schedule -->
        <el-divider />
        <div class="enrollment-form__section-title">Weekly class schedule</div>

        <!-- Hint: missing prerequisites -->
        <div v-if="!canShowCalendar" class="schedule-placeholder">
          <el-icon><InfoFilled /></el-icon>
          <span>Select <strong>student</strong>, <strong>course product</strong> and <strong>start date</strong> to configure the weekly schedule.</span>
        </div>

        <!-- Calendar area -->
        <template v-else>
          <div v-if="loadingSchedule" class="schedule-loading-hint">
            Loading existing schedule...
          </div>

          <div v-if="scheduleLoadError" class="schedule-error-banner">
            <el-icon><WarningFilled /></el-icon>
            <span>{{ scheduleLoadError }}</span>
          </div>

          <!-- DEBUG: raw occupied data -->
          <div style="font-size:11px;color:#909399;margin-bottom:4px;line-height:1.5;">
            occupiedSlots.length = {{ occupiedSlots.length }},
            uniqueOccupiedCount = {{ uniqueOccupiedCount }}
            <span v-if="occupiedSlots.length > 0">
              | sample: weekday={{ occupiedSlots[0].weekday }}
              {{ occupiedSlots[0].startTime }}-{{ occupiedSlots[0].endTime }}
              "{{ occupiedSlots[0].courseLabel }}"
            </span>
          </div>

          <div v-if="uniqueOccupiedCount > 0" class="existing-classes-banner">
            <el-icon><InfoFilled /></el-icon>
            <span>Student has <strong>{{ uniqueOccupiedCount }}</strong> existing weekly class slot(s) shown in gray below.</span>
          </div>
          <div v-else class="no-classes-banner">
            No existing classes found for this student in upcoming schedule.
          </div>
          <div class="enrollment-form__row">
            <el-form-item class="enrollment-form__full">
              <WeeklyTimeSelector
                v-model="createForm.slots"
                :occupied-slots="occupiedSlots"
                :week-start-date="weekMonday"
              />
            </el-form-item>
          </div>
        </template>

        <!-- Schedule Preview -->
        <div v-if="schedulePreview" class="schedule-preview">
          <div class="schedule-preview__title">Schedule Preview</div>
          <div class="schedule-preview__stats">
            <span>{{ schedulePreview.weeklySessions }} session(s)/week</span>
            <span>{{ (schedulePreview.weeklyUnits * 0.5).toFixed(1) }} hr(s)/week</span>
            <span>~{{ schedulePreview.totalSessions }} total sessions</span>
            <span><strong>{{ schedulePreview.weeks }} weeks</strong></span>
            <span v-if="schedulePreview.endDate" class="schedule-preview__end-date">
              Estimated end: <strong>{{ schedulePreview.endDate }}</strong>
            </span>
          </div>
          <div v-if="!createForm.startDate" class="schedule-preview__hint">
            Select a start date above to calculate the estimated end date.
          </div>
        </div>

        <el-divider />
        <div class="enrollment-form__row">
          <el-form-item label="Notes" class="enrollment-form__full">
            <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="Optional notes" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="createDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="handleCreateSubmit">Submit</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Class enrollment dialog -->
    <el-dialog
      v-model="classEnrollDialogVisible"
      width="560px"
      :close-on-click-modal="false"
      class="enrollment-dialog"
    >
      <template #header>
        <h3>New class enrollment</h3>
      </template>

      <el-form
        ref="classEnrollFormRef"
        label-position="top"
        :model="classEnrollForm"
        :rules="classEnrollFormRules"
        class="enrollment-form"
      >
        <el-form-item label="Student" prop="studentId">
          <el-select
            v-model="classEnrollForm.studentId"
            placeholder="Select student"
            filterable
            style="width: 100%;"
          >
            <el-option v-for="s in students" :key="s.id" :label="s.studentName" :value="s.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="Class" prop="classId">
          <el-select
            v-model="classEnrollForm.classId"
            placeholder="Select class"
            filterable
            style="width: 100%;"
          >
            <el-option
              v-for="c in classes"
              :key="c.id"
              :label="`${c.className} (${c.teacherName || '—'})`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <!-- Class price display -->
        <div v-if="selectedClassPrice != null" class="product-info-card" style="margin-bottom: 12px;">
          <div class="product-info-card__item">
            <span class="product-info-card__label">Original price</span>
            <span class="product-info-card__value">${{ selectedClassPrice.toFixed(2) }}</span>
          </div>
        </div>

        <div class="enrollment-form__row">
          <el-form-item label="Enrollment date" prop="enrollmentDate">
            <el-date-picker
              v-model="classEnrollForm.enrollmentDate"
              type="date"
              placeholder="Enrollment date"
              style="width: 100%;"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledFutureDateOnly"
            />
          </el-form-item>
          <el-form-item label="Expiry date" prop="expiryDate">
            <el-date-picker
              v-model="classEnrollForm.expiryDate"
              type="date"
              placeholder="Expiry date"
              style="width: 100%;"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledFutureDate"
            />
          </el-form-item>
        </div>

        <el-divider />

        <div class="enrollment-form__row">
          <el-form-item label="Discount type">
            <el-select v-model="classEnrollDiscountPresetId" placeholder="Select discount" clearable style="width: 100%;">
              <el-option
                v-for="p in discountPresets"
                :key="p.id"
                :label="`${p.presetName} (${p.discountType === 'PERCENTAGE' ? p.discountValue + '% off' : '$' + p.discountValue + ' off'})`"
                :value="p.id"
              />
              <el-option label="Custom" value="__custom__" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="classEnrollDiscountPresetId === '__custom__'" label="Discount amount" prop="discountAmount">
            <el-input-number
              v-model="classEnrollForm.discountAmount"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%;"
              placeholder="0.00"
            />
          </el-form-item>
          <el-form-item v-else label="Discount amount">
            <div class="discount-display">${{ classEnrollForm.discountAmount.toFixed(2) }}</div>
          </el-form-item>
        </div>

        <div class="enrollment-form__row">
          <el-form-item label="Received amount" prop="receivedAmount">
            <el-input-number
              v-model="classEnrollForm.receivedAmount"
              :min="0"
              :precision="2"
              :controls="false"
              style="width: 100%;"
              placeholder="0.00"
            />
          </el-form-item>
          <el-form-item label="Handled by" prop="handledByName">
            <el-input v-model="classEnrollForm.handledByName" placeholder="Your name" />
          </el-form-item>
        </div>

        <el-form-item label="Notes" class="enrollment-form__full">
          <el-input v-model="classEnrollForm.remark" type="textarea" :rows="2" placeholder="Optional notes" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="classEnrollDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submittingClassEnroll" @click="submitClassEnroll">Submit</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Discount presets management dialog -->
    <el-dialog
      v-model="discountPresetDialogVisible"
      width="680px"
      :close-on-click-modal="false"
      class="enrollment-dialog"
    >
      <template #header>
        <div class="dialog-header-row">
          <h3>Discount presets</h3>
          <el-button type="primary" size="small" @click="openPresetEditDialog()">
            <el-icon><Plus /></el-icon>
            Add preset
          </el-button>
        </div>
      </template>

      <el-table :data="discountPresets" v-loading="discountPresetsLoading" size="small" empty-text="No presets.">
        <el-table-column label="Name" min-width="160" prop="presetName" />
        <el-table-column label="Type" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.discountType === 'PERCENTAGE' ? 'warning' : 'primary'">
              {{ row.discountType === 'PERCENTAGE' ? `${row.discountValue}% off` : `$${row.discountValue} off` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Sort" min-width="60" align="center" prop="sortOrder" />
        <el-table-column label="Status" min-width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" min-width="120" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openPresetEditDialog(row)">Edit</el-button>
            <el-button text type="danger" size="small" @click="handleDeletePreset(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- Add/Edit discount preset dialog -->
    <el-dialog
      v-model="presetEditDialogVisible"
      width="480px"
      :close-on-click-modal="false"
      :title="editingPresetId ? 'Edit preset' : 'Add preset'"
      class="enrollment-dialog"
    >
      <el-form
        ref="presetFormRef"
        label-position="top"
        :model="presetForm"
        :rules="presetFormRules"
      >
        <el-form-item label="Preset name" prop="presetName">
          <el-input v-model="presetForm.presetName" placeholder="e.g. Sibling 10%" />
        </el-form-item>
        <div class="enrollment-form__row">
          <el-form-item label="Discount type" prop="discountType">
            <el-select v-model="presetForm.discountType" style="width: 100%;">
              <el-option label="Percentage (%)" value="PERCENTAGE" />
              <el-option label="Fixed amount ($)" value="FIXED" />
            </el-select>
          </el-form-item>
          <el-form-item label="Value" prop="discountValue">
            <el-input-number
              v-model="presetForm.discountValue"
              :min="0.01"
              :precision="2"
              :controls="false"
              style="width: 100%;"
              :placeholder="presetForm.discountType === 'PERCENTAGE' ? '10 = 10%' : '100 = $100'"
            />
          </el-form-item>
        </div>
        <div class="enrollment-form__row">
          <el-form-item label="Sort order">
            <el-input-number v-model="presetForm.sortOrder" :min="0" :controls="false" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="Status">
            <el-select v-model="presetForm.status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="presetEditDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="savingPreset" @click="submitPreset">Save</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Payment method management dialog -->
    <el-dialog
      v-model="paymentMethodDialogVisible"
      width="600px"
      :close-on-click-modal="false"
      class="enrollment-dialog"
    >
      <template #header>
        <div class="dialog-header-row">
          <h3>Payment methods</h3>
          <el-button type="primary" size="small" @click="openMethodEditDialog()">
            <el-icon><Plus /></el-icon>
            Add method
          </el-button>
        </div>
      </template>

      <el-table :data="paymentMethodList" v-loading="paymentMethodLoading" size="small" empty-text="No payment methods.">
        <el-table-column label="Code" min-width="120" prop="methodCode" />
        <el-table-column label="Name" min-width="160" prop="methodName" />
        <el-table-column label="Sort" min-width="60" align="center" prop="sortOrder" />
        <el-table-column label="Status" min-width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Actions" min-width="120" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openMethodEditDialog(row)">Edit</el-button>
            <el-button text type="danger" size="small" @click="handleDeleteMethod(row.id)">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- Add/Edit payment method dialog -->
    <el-dialog
      v-model="methodEditDialogVisible"
      width="480px"
      :close-on-click-modal="false"
      :title="editingMethodId ? 'Edit payment method' : 'Add payment method'"
      class="enrollment-dialog"
    >
      <el-form
        ref="methodFormRef"
        label-position="top"
        :model="methodForm"
        :rules="methodFormRules"
      >
        <el-form-item label="Method name" prop="methodName">
          <el-input v-model="methodForm.methodName" placeholder="e.g. WeChat Pay" @input="autoGenerateMethodCode" />
        </el-form-item>
        <div class="enrollment-form__row">
          <el-form-item label="Sort order">
            <el-input-number v-model="methodForm.sortOrder" :min="0" :controls="false" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="Status">
            <el-select v-model="methodForm.status" style="width: 100%;">
              <el-option label="Active" value="ACTIVE" />
              <el-option label="Inactive" value="INACTIVE" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="methodEditDialogVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="savingMethod" @click="handleMethodSave">Save</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { InfoFilled, Plus, WarningFilled } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, reactive, ref, watch } from 'vue';
import dayjs from 'dayjs';

import { createEnrollmentOrder, cancelEnrollmentOrder, fetchEnrollmentOrders, fetchEnrollmentOrder, createPaymentRecord, createClassEnrollmentOrder, fetchActivePaymentMethods, fetchPaymentMethods, createPaymentMethod, updatePaymentMethod, deletePaymentMethod, type EnrollmentOrderQuery } from '@/api/enrollment';
import { fetchAllStudents, fetchAllTeachers, fetchAllClassrooms, fetchCourseProducts, fetchDiscountPresets, createDiscountPreset, updateDiscountPreset, deleteDiscountPreset } from '@/api/base-data';
import { fetchLessonOccurrences, fetchClassGroups } from '@/api/schedule';
import type { DiscountPresetItem, EnrollmentOrderItem, EnrollmentOrderDetailItem, PaymentMethodItem, PaymentRecordItem } from '@/types/domain';
import WeeklyTimeSelector from '@/components/WeeklyTimeSelector.vue';
import type { SlotDef, OccupiedSlotDef } from '@/components/WeeklyTimeSelector.vue';

const loading = ref(false);
const submitting = ref(false);
const detailDialogVisible = ref(false);
const createDialogVisible = ref(false);
const createFormRef = ref();
const activeTab = ref('ONE_ON_ONE');
const loadingClasses = ref(false);
const classEnrollmentData = ref<any[]>([]);

// ─── Group Class expandable rows ──────────────────
const expandedClassOrders = reactive<Record<number, EnrollmentOrderItem[]>>({});
const expandedClassLoading = reactive<Record<number, boolean>>({});
const expandedClassPagination = reactive<Record<number, { page: number; size: number; total: number }>>({});

async function loadExpandedClassOrders(classId: number, pageNum = 1) {
  expandedClassLoading[classId] = true;
  try {
    if (!expandedClassPagination[classId]) {
      expandedClassPagination[classId] = { page: 1, size: 10, total: 0 };
    }
    expandedClassPagination[classId].page = pageNum;
    const res = await fetchEnrollmentOrders({
      classId,
      pageNum,
      pageSize: expandedClassPagination[classId].size,
    });
    const data = res.data.data;
    expandedClassOrders[classId] = data.records.filter((o: EnrollmentOrderItem) => o.orderStatus !== 'CANCELLED');
    expandedClassPagination[classId].total = data.records.filter((o: EnrollmentOrderItem) => o.orderStatus !== 'CANCELLED').length;
  } catch {
    expandedClassOrders[classId] = [];
    expandedClassPagination[classId] = { page: 1, size: 10, total: 0 };
  } finally {
    expandedClassLoading[classId] = false;
  }
}

function handleExpandChange(row: any, expandedRows: any[]) {
  const isExpanded = expandedRows.some(r => r.id === row.id);
  if (isExpanded) {
    loadExpandedClassOrders(row.id);
  }
}

const detailData = ref<EnrollmentOrderDetailItem | null>(null);
const paymentRecords = computed(() => detailData.value?.payments ?? []);
const students = ref<{ id: number; studentName: string }[]>([]);
const teachers = ref<{ id: number; teacherName: string }[]>([]);
const courseProducts = ref<{ id: number; productName: string; subjectName?: string; defaultTotalUnits?: number; defaultLessonUnits?: number; listPrice?: number }[]>([]);
const classrooms = ref<{ id: number; roomName: string }[]>([]);
const classes = ref<{ id: number; className: string; teacherName?: string; listPrice?: number }[]>([]);
const occupiedSlots = ref<OccupiedSlotDef[]>([]);
const loadingSchedule = ref(false);
const scheduleLoadError = ref('');

// ─── Discount Presets ──────────────────────────────────
const discountPresets = ref<DiscountPresetItem[]>([]);
const discountPresetsLoading = ref(false);
const discountPresetDialogVisible = ref(false);
const presetEditDialogVisible = ref(false);
const savingPreset = ref(false);
const editingPresetId = ref<number | null>(null);
const presetFormRef = ref();
const presetForm = reactive({
  presetName: '',
  discountType: 'PERCENTAGE',
  discountValue: 0,
  sortOrder: 0,
  status: 'ACTIVE',
});
const presetFormRules = {
  presetName: [{ required: true, message: 'Preset name is required' }],
  discountType: [{ required: true, message: 'Discount type is required' }],
  discountValue: [
    { required: true, message: 'Value is required' },
    { type: 'number', min: 0.01, message: 'Must be greater than 0' },
  ],
};

// ─── Discount preset selection for dialogs ──────────
const createDiscountPresetId = ref<number | '__custom__' | null>(null);
const classEnrollDiscountPresetId = ref<number | '__custom__' | null>(null);

async function loadDiscountPresets() {
  try {
    const res = await fetchDiscountPresets();
    discountPresets.value = res.data.data;
  } catch {
    // silent fail
  }
}

function openPresetEditDialog(preset?: DiscountPresetItem) {
  if (preset) {
    editingPresetId.value = preset.id;
    presetForm.presetName = preset.presetName;
    presetForm.discountType = preset.discountType;
    presetForm.discountValue = preset.discountValue;
    presetForm.sortOrder = preset.sortOrder;
    presetForm.status = preset.status;
  } else {
    editingPresetId.value = null;
    presetForm.presetName = '';
    presetForm.discountType = 'PERCENTAGE';
    presetForm.discountValue = 0;
    presetForm.sortOrder = 0;
    presetForm.status = 'ACTIVE';
  }
  presetEditDialogVisible.value = true;
}

async function submitPreset() {
  const valid = await presetFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  savingPreset.value = true;
  try {
    const payload = {
      presetName: presetForm.presetName,
      discountType: presetForm.discountType as 'PERCENTAGE' | 'FIXED',
      discountValue: presetForm.discountValue,
      sortOrder: presetForm.sortOrder,
      status: presetForm.status,
    };
    if (editingPresetId.value) {
      await updateDiscountPreset(editingPresetId.value, payload);
    } else {
      await createDiscountPreset(payload);
    }
    ElMessage.success(editingPresetId.value ? 'Preset updated.' : 'Preset created.');
    presetEditDialogVisible.value = false;
    loadDiscountPresets();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    savingPreset.value = false;
  }
}

async function handleDeletePreset(id: number) {
  try {
    await ElMessageBox.confirm('Delete this discount preset?', 'Confirm', { type: 'warning' });
    await deleteDiscountPreset(id);
    ElMessage.success('Preset deleted.');
    loadDiscountPresets();
  } catch {
    // user cancelled
  }
}

// ─── Payment method management ────────────────────
async function loadPaymentMethodList() {
  paymentMethodLoading.value = true;
  try {
    const res = await fetchPaymentMethods();
    paymentMethodList.value = res.data.data;
  } catch {
    // silent fail
  } finally {
    paymentMethodLoading.value = false;
  }
}

async function loadActivePaymentMethods() {
  try {
    const res = await fetchActivePaymentMethods();
    paymentMethods.value = res.data.data;
  } catch {
    paymentMethods.value = [];
  }
}

function openPaymentMethodDialog() {
  paymentMethodDialogVisible.value = true;
  if (paymentMethodList.value.length === 0) {
    loadPaymentMethodList();
  }
}

function methodNameToCode(name: string): string {
  return name
    .trim()
    .replace(/[^a-zA-Z0-9\s_-]/g, '')
    .replace(/[\s-]+/g, '_')
    .replace(/_+/g, '_')
    .toUpperCase();
}

function autoGenerateMethodCode() {
  if (!editingMethodId.value) {
    methodForm.methodCode = methodNameToCode(methodForm.methodName);
  }
}

function getPaymentMethodName(code: string): string {
  const m = paymentMethods.value.find(p => p.methodCode === code);
  return m ? m.methodName : code;
}

function getPaymentSplitSummary(): string {
  return payForm.splits
    .filter(s => s.amount > 0)
    .map(s => getPaymentMethodName(s.paymentMethod))
    .join(', ');
}

function openMethodEditDialog(method?: PaymentMethodItem) {
  if (method) {
    editingMethodId.value = method.id;
    methodForm.methodCode = method.methodCode;
    methodForm.methodName = method.methodName;
    methodForm.sortOrder = method.sortOrder;
    methodForm.status = method.status;
  } else {
    editingMethodId.value = null;
    methodForm.methodCode = '';
    methodForm.methodName = '';
    methodForm.sortOrder = 0;
    methodForm.status = 'ACTIVE';
  }
  methodEditDialogVisible.value = true;
}

async function handleMethodSave() {
  const valid = await methodFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  savingMethod.value = true;
  try {
    // Auto-generate methodCode from methodName for new methods
    if (!editingMethodId.value) {
      methodForm.methodCode = methodNameToCode(methodForm.methodName);
    }
    const payload = {
      methodCode: methodForm.methodCode,
      methodName: methodForm.methodName,
      sortOrder: methodForm.sortOrder,
      status: methodForm.status,
    };
    if (editingMethodId.value) {
      await updatePaymentMethod(editingMethodId.value, payload);
    } else {
      await createPaymentMethod(payload);
    }
    ElMessage.success(editingMethodId.value ? 'Method updated.' : 'Method created.');
    methodEditDialogVisible.value = false;
    loadPaymentMethodList();
    loadActivePaymentMethods();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    savingMethod.value = false;
  }
}

async function handleDeleteMethod(id: number) {
  try {
    await ElMessageBox.confirm('Delete this payment method?', 'Confirm', { type: 'warning' });
    await deletePaymentMethod(id);
    ElMessage.success('Method deleted.');
    loadPaymentMethodList();
    loadActivePaymentMethods();
  } catch {
    // user cancelled
  }
}

// ─── Compute discount amount from selected preset ──
function applyPresetDiscount(presetId: number | '__custom__' | null, form: { originalAmount: number; discountAmount: number }) {
  if (!presetId || presetId === '__custom__') return;
  const preset = discountPresets.value.find(p => p.id === presetId);
  if (!preset) return;
  if (preset.discountType === 'PERCENTAGE') {
    form.discountAmount = Number(((form.originalAmount * preset.discountValue) / 100).toFixed(2));
  } else {
    form.discountAmount = preset.discountValue;
  }
}

// ─── Payment dialog state ──────────────────────────
const payDialogVisible = ref(false);
const paying = ref(false);
const payFormRef = ref();
const payOrder = ref<EnrollmentOrderItem | null>(null);

interface PaySplit {
  paymentMethod: string;
  amount: number;
}

const paymentMethods = ref<PaymentMethodItem[]>([]);
const payForm = reactive({
  splits: [] as PaySplit[],
  handledByName: '',
  issueReceipt: true,
  remark: '',
});

const payAmountDue = computed(() => {
  if (!payOrder.value) return 0;
  const due = (payOrder.value.originalAmount ?? 0) - (payOrder.value.discountAmount ?? 0) - (payOrder.value.receivedAmount ?? 0);
  return Math.max(0, due);
});

const paySplitTotal = computed(() => {
  return payForm.splits.reduce((sum, s) => sum + (s.amount || 0), 0);
});

const paySplitRemaining = computed(() => {
  return payAmountDue.value - paySplitTotal.value;
});

function addPaySplit() {
  payForm.splits.push({ paymentMethod: '', amount: 0 });
}

function removePaySplit(index: number) {
  payForm.splits.splice(index, 1);
}

// ─── Payment method management state ──────────────
const paymentMethodDialogVisible = ref(false);
const paymentMethodList = ref<PaymentMethodItem[]>([]);
const paymentMethodLoading = ref(false);
const methodEditDialogVisible = ref(false);
const methodFormRef = ref();
const editingMethodId = ref<number | null>(null);
const savingMethod = ref(false);
const methodForm = reactive({
  methodCode: '',
  methodName: '',
  sortOrder: 0,
  status: 'ACTIVE',
});
const methodFormRules: any = {
  methodName: [{ required: true, message: 'Method name is required', trigger: 'blur' }],
};

// ─── Filters & Pagination ───────────────────────────
const filters = reactive({
  keyword: '',
  orderStatus: '',
  paymentStatus: '',
});

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
  get start() {
    return this.total === 0 ? 0 : (this.page - 1) * this.size + 1;
  },
  get end() {
    return Math.min(this.page * this.size, this.total);
  },
});

const tableData = ref<EnrollmentOrderItem[]>([]);

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

function orderStatusType(status: string) {
  if (status === 'CONFIRMED') return 'success';
  if (status === 'CANCELLED') return 'danger';
  if (status === 'CLOSED') return 'info';
  return 'warning';
}

function orderStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'Draft',
    CONFIRMED: 'Confirmed',
    CANCELLED: 'Cancelled',
    CLOSED: 'Closed',
  };
  return map[status] ?? status;
}

function paymentStatusType(status: string) {
  if (status === 'PAID') return 'success';
  if (status === 'PARTIAL') return 'warning';
  return 'info';
}

function paymentStatusLabel(status: string) {
  const map: Record<string, string> = {
    UNPAID: 'Unpaid',
    PARTIAL: 'Partial',
    PAID: 'Paid',
  };
  return map[status] ?? status;
}

// ─── Load Data ──────────────────────────────────────
async function loadData() {
  loading.value = true;
  try {
    const params: EnrollmentOrderQuery = {
      keyword: filters.keyword || undefined,
      orderStatus: filters.orderStatus || undefined,
      paymentStatus: filters.paymentStatus || undefined,
      excludeClassOrders: true,
      pageNum: pagination.page,
      pageSize: pagination.size,
    };
    const res = await fetchEnrollmentOrders(params);
    const data = res.data.data;
    tableData.value = data.records;
    pagination.total = data.total;
  } catch {
    ElMessage.error('Failed to load enrollment orders.');
  } finally {
    loading.value = false;
  }
}

async function loadStudents() {
  try {
    const res = await fetchAllStudents();
    students.value = res.data.data.map((s) => ({ id: s.id, studentName: s.studentName }));
  } catch {
    // Silent fail
  }
}

async function loadTeachers() {
  try {
    const res = await fetchAllTeachers();
    teachers.value = res.data.data.map((t: any) => ({ id: t.id, teacherName: t.teacherName }));
  } catch {
    // Silent fail
  }
}

async function loadCourseProducts() {
  try {
    const res = await fetchCourseProducts({ status: 'ACTIVE', teachingMode: 'ONE_ON_ONE', pageSize: 100 });
    courseProducts.value = res.data.data.records.map((p: any) => ({
      id: p.id,
      productName: p.productName,
      subjectName: p.subjectName,
      defaultTotalUnits: p.defaultTotalUnits,
      defaultLessonUnits: p.defaultLessonUnits,
      listPrice: p.listPrice,
    }));
  } catch {
    // Silent fail
  }
}

async function loadClassrooms() {
  try {
    const res = await fetchAllClassrooms();
    const data = res.data.data as any[];
    classrooms.value = data.map((r: any) => ({
      id: r.id,
      roomName: r.roomName,
    }));
  } catch {
    // silent fail
  }
}

async function loadClasses() {
  try {
    const res = await fetchClassGroups();
    const data = res.data.data as any[];
    classes.value = data.map((r: any) => ({
      id: r.id,
      className: r.className,
      teacherName: r.teacherName,
      listPrice: r.listPrice ?? 0,
    }));
  } catch {
    // silent fail
  }
}

async function loadClassEnrollments() {
  loadingClasses.value = true;
  try {
    const res = await fetchClassGroups();
    classEnrollmentData.value = res.data.data as any[];
  } catch {
    classEnrollmentData.value = [];
  } finally {
    loadingClasses.value = false;
  }
}

// ─── Detail Dialog ──────────────────────────────────
async function openDetailDialog(row: EnrollmentOrderItem) {
  try {
    const res = await fetchEnrollmentOrder(row.id);
    detailData.value = res.data.data;
    detailDialogVisible.value = true;
  } catch {
    ElMessage.error('Failed to load order detail.');
  }
}

async function handleCancel(row: EnrollmentOrderItem) {
  try {
    await ElMessageBox.confirm(
      `Cancel enrollment order ${row.enrollmentNo}?`,
      'Confirm cancel',
      { type: 'warning' },
    );
    await cancelEnrollmentOrder(row.id);
    ElMessage.success('Order cancelled.');
    loadData();
  } catch {
    // User cancelled
  }
}

// ─── Payment Dialog ──────────────────────────────────
function openPayDialog(row: EnrollmentOrderItem) {
  payOrder.value = row;
  payForm.splits = [{ paymentMethod: 'CASH', amount: payAmountDue.value }];
  payForm.handledByName = '';
  payForm.issueReceipt = true;
  payForm.remark = '';
  payDialogVisible.value = true;
}

async function handlePaySubmit() {
  if (!payForm.handledByName.trim()) {
    ElMessage.warning('Please enter the handler name.');
    return;
  }
  if (payForm.splits.length === 0 || payForm.splits.every(s => !s.amount || s.amount <= 0)) {
    ElMessage.warning('Please add at least one payment split with an amount.');
    return;
  }
  if (paySplitRemaining.value !== 0) {
    ElMessage.warning('Payment splits must total the amount due.');
    return;
  }
  if (!payOrder.value) return;

  paying.value = true;
  try {
    await createPaymentRecord({
      enrollmentOrderId: payOrder.value.id,
      splits: payForm.splits.map(s => ({
        paymentMethod: s.paymentMethod,
        amount: s.amount,
      })),
      paidAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
      handledByName: payForm.handledByName,
      remark: payForm.remark || undefined,
    });

    // If receipt requested, show receipt preview
    if (payForm.issueReceipt) {
      showReceiptPreview(payOrder.value, paySplitTotal.value);
    }

    ElMessage.success('Payment recorded successfully.');
    payDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Payment failed.');
  } finally {
    paying.value = false;
  }
}

function openReceiptWindow(html: string) {
  const w = window.open('', '_blank');
  if (w) {
    w.document.write(html);
    w.document.close();
  }
}

function showReceiptPreview(order: EnrollmentOrderItem, paidAmount: number) {
  const dueAmount = (order.originalAmount ?? 0) - (order.discountAmount ?? 0);
  const newReceived = (order.receivedAmount ?? 0) + paidAmount;
  const html = receiptHtml({
    title: 'Payment Receipt',
    infoRows: [
      ['Order No.', order.enrollmentNo],
      ['Student', order.studentName],
      ['Payment Date', dayjs().format('YYYY-MM-DD')],
      ['Handler', payForm.handledByName],
      ['Payment Method', getPaymentSplitSummary()],
    ],
    tableRows: [
      ['Original price', `$${(order.originalAmount ?? 0).toFixed(2)}`],
      ['Discount', `-$${(order.discountAmount ?? 0).toFixed(2)}`],
      ['Amount Due', `$${dueAmount.toFixed(2)}`],
      ['Previously Paid', `$${(order.receivedAmount ?? 0).toFixed(2)}`],
      ['This Payment', `$${paidAmount.toFixed(2)}`],
      ['Total Paid', `$${newReceived.toFixed(2)}`, true],
    ],
    remark: payForm.remark || undefined,
  });
  openReceiptWindow(html);
}

function showPaymentReceipt(order: EnrollmentOrderDetailItem) {
  const methods = [...new Set(order.payments.map(p => getPaymentMethodName(p.paymentMethod)))].join(', ');
  const totalPaid = order.payments.reduce((sum, p) => sum + (p.amount ?? 0), 0);
  const infoRows: [string, string][] = [
    ['Order No.', order.enrollmentNo],
    ['Student', order.studentName],
    ['Payment Methods', methods],
    ['Total Paid', `$${totalPaid.toFixed(2)}`],
  ];
  const tableRows: [string, string, boolean?][] = order.payments.map(p => [
    getPaymentMethodName(p.paymentMethod),
    `$${(p.amount ?? 0).toFixed(2)}`,
  ]);
  tableRows.push(['Total', `$${totalPaid.toFixed(2)}`, true]);
  const html = receiptHtml({
    title: 'Payment Receipt',
    infoRows,
    tableRows,
  });
  openReceiptWindow(html);
}

interface ReceiptConfig {
  title: string;
  infoRows: [string, string][];
  tableRows: [string, string, boolean?][];
  remark?: string;
}

function receiptHtml(cfg: ReceiptConfig): string {
    const logoBase64 = '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';
  const infoHtml = cfg.infoRows
    .map(([label, value]) => `<div class="info-row"><span class="info-label">${label}</span><span class="info-value">${value}</span></div>`)
    .join('');
  const tableHtml = cfg.tableRows
    .map(([label, value, highlight]) =>
      `<tr${highlight ? ' class="row-highlight"' : ''}><td>${label}</td><td>${value}</td></tr>`)
    .join('');
  const remarkHtml = cfg.remark
    ? `<div class="remark-section"><strong>Notes:</strong> ${cfg.remark}</div>`
    : '';

  return `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${cfg.title}</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: Arial, sans-serif; max-width: 750px; margin: 0 auto; padding: 0; color: #222; position: relative; }
  .btn-wrap { text-align: right; padding: 12px 20px 0; }
  .btn-download { padding: 7px 18px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 13px; }
  .btn-download:hover { background: #337ecc; }
  .page { display: flex; min-height: 100vh; }
  .sidebar { width: 80px; background: #123764; flex-shrink: 0; }
  .content { flex: 1; padding: 20px 24px 30px; }
  .header { display: flex; align-items: center; gap: 16px; margin-bottom: 6px; }
  .header-logo { width: 80px; height: 80px; flex-shrink: 0; }
  .header-logo img { width: 100%; height: 100%; object-fit: contain; }
  .header-text h1 { font-size: 20px; font-weight: 800; letter-spacing: 1px; color: #123764; }
  .header-text .tagline { font-size: 11px; color: #666; margin-top: 2px; }
  .header-text .contact { font-size: 10px; color: #888; margin-top: 4px; line-height: 1.5; }
  .divider { border: none; border-top: 2px solid #123764; margin: 10px 0; }
  .title { font-size: 15px; font-weight: 700; margin: 12px 0 10px; color: #123764; }
  .info-section { margin-bottom: 8px; }
  .info-row { display: flex; padding: 3px 0; font-size: 12px; }
  .info-label { flex: 0 0 130px; color: #666; }
  .info-value { flex: 1; font-weight: 600; color: #222; }
  table { width: 100%; border-collapse: collapse; margin: 10px 0; }
  thead th { background: #123764; color: #fff; border: 1px solid #123764; padding: 6px 10px; font-size: 12px; text-align: left; }
  thead th:last-child { text-align: right; }
  tbody td { border: 1px solid #ccc; padding: 6px 10px; font-size: 12px; }
  tbody td:last-child { text-align: right; font-weight: 600; }
  .row-highlight td { font-size: 14px; font-weight: 700; background: #f0f4f8; }
  .remark-section { font-size: 11px; color: #666; margin-top: 4px; }
  .policies { margin-top: 16px; font-size: 9px; color: #999; }
  .policies h4 { font-size: 10px; color: #123764; margin: 6px 0 3px; }
  .policies p { margin: 2px 0; line-height: 1.4; }
  .footer { margin-top: 16px; text-align: center; font-size: 9px; color: #aaa; }
  @media print { .btn-wrap { display: none; } body { -webkit-print-color-adjust: exact; } }
</style></head><body>
<div class="btn-wrap"><button class="btn-download" onclick="window.print()">Download / Print</button></div>
<div class="page">
  <div class="content">
    <div class="header">
      <div class="header-logo"><img src="data:image/png;base64,${logoBase64}" alt="JIN BEST TUTORING"></div>
      <div class="header-text">
        <h1>JIN BEST TUTORING</h1>
        <div class="tagline">JIN 补习 — 不止提分，更帮助孩子建立自信，掌握方法</div>
        <div class="contact">Ms. Jin (929) 528-1060 | Office (929) 701-9872<br>144-56 Roosevelt Ave, Flushing, NY 11354</div>
      </div>
    </div>
    <hr class="divider">
    <div class="title">${cfg.title}</div>
    <div class="info-section">${infoHtml}</div>
    <table>
      <thead><tr><th>Item</th><th>Amount</th></tr></thead>
      <tbody>${tableHtml}</tbody>
    </table>
    ${remarkHtml}
    <div class="policies">
      <h4>Refund Policy</h4>
      <p>Before class starts: 50% refund; remaining becomes tuition credit. After class starts: No refund; remaining becomes credit for future courses.</p>
      <h4>Course Suspension Policy</h4>
      <p>Mid-course suspension: $50 fee; remaining tuition becomes credit.</p>
      <h4>Material Policy</h4>
      <p>Materials included. If lost, each item requires a $10 reprint fee.</p>
      <h4>Discipline Policy</h4>
      <p>Students must follow rules. 3 serious violations (e.g. disruption, bullying) may lead to dismissal.</p>
    </div>
    <div class="footer">JIN BEST TUTORING &middot; Generated on ${dayjs().format('YYYY-MM-DD HH:mm')}</div>
  </div>
  <div class="sidebar"></div>
</div>
</body></html>`;
}

// ─── Create Dialog ──────────────────────────────────
const createForm = reactive({
  studentId: undefined as number | undefined,
  courseProductId: undefined as number | undefined,
  primaryTeacherId: undefined as number | undefined,
  handledByName: '',
  startDate: '',
  originalAmount: 0,
  discountAmount: 0,
  remark: '',
  slots: [] as SlotDef[],
  defaultClassroomId: undefined as number | undefined,
});

const createFormRules = {
  studentId: [{ required: true, message: 'Student is required' }],
  courseProductId: [{ required: true, message: 'Course product is required' }],
  handledByName: [{ required: true, message: 'Handler name is required' }],
};

// ─── Class Enrollment Dialog ──────────────────────
const classEnrollDialogVisible = ref(false);
const submittingClassEnroll = ref(false);
const classEnrollFormRef = ref();
const classEnrollForm = reactive({
  studentId: undefined as number | undefined,
  classId: undefined as number | undefined,
  enrollmentDate: dayjs().format('YYYY-MM-DD'),
  expiryDate: '',
  originalAmount: 0,
  discountAmount: 0,
  receivedAmount: 0,
  handledByName: '',
  remark: '',
});
const classEnrollFormRules = {
  studentId: [{ required: true, message: 'Student is required' }],
  classId: [{ required: true, message: 'Class is required' }],
  enrollmentDate: [{ required: true, message: 'Enrollment date is required' }],
  expiryDate: [{ required: true, message: 'Expiry date is required' }],
};

// ─── Selected class price display ──
const selectedClassPrice = computed(() => {
  if (!classEnrollForm.classId) return null;
  const cls = classes.value.find(c => c.id === classEnrollForm.classId);
  return cls?.listPrice ?? null;
});

// ─── Auto-fill original price when class is selected ──
watch(() => classEnrollForm.classId, (classId) => {
  if (!classId) return;
  const cls = classes.value.find(c => c.id === classId);
  if (cls && cls.listPrice != null) {
    classEnrollForm.originalAmount = cls.listPrice;
  }
});

// ─── Discount preset watchers ──
watch(() => createDiscountPresetId.value, (id) => {
  if (!id || id === '__custom__') return;
  applyPresetDiscount(id, createForm);
});

watch(() => classEnrollDiscountPresetId.value, (id) => {
  if (!id || id === '__custom__') return;
  applyPresetDiscount(id, classEnrollForm);
});

// When originalAmount changes, recalculate preset discount for 1:1
watch(() => createForm.originalAmount, () => {
  if (createDiscountPresetId.value && createDiscountPresetId.value !== '__custom__') {
    applyPresetDiscount(createDiscountPresetId.value, createForm);
  }
});

// When originalAmount changes, recalculate preset discount for class
watch(() => classEnrollForm.originalAmount, () => {
  if (classEnrollDiscountPresetId.value && classEnrollDiscountPresetId.value !== '__custom__') {
    applyPresetDiscount(classEnrollDiscountPresetId.value, classEnrollForm);
  }
});

function openClassEnrollDialog() {
  classEnrollFormRef.value?.resetFields();
  Object.assign(classEnrollForm, {
    studentId: undefined,
    classId: undefined,
    enrollmentDate: dayjs().format('YYYY-MM-DD'),
    expiryDate: '',
    originalAmount: 0,
    discountAmount: 0,
    receivedAmount: 0,
    handledByName: '',
    remark: '',
  });
  classEnrollDiscountPresetId.value = null;
  classEnrollDialogVisible.value = true;
}

async function submitClassEnroll() {
  const valid = await classEnrollFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  submittingClassEnroll.value = true;
  try {
    await createClassEnrollmentOrder({
      studentId: classEnrollForm.studentId!,
      classId: classEnrollForm.classId!,
      enrollmentDate: classEnrollForm.enrollmentDate,
      expiryDate: classEnrollForm.expiryDate,
      originalAmount: classEnrollForm.originalAmount,
      discountAmount: classEnrollForm.discountAmount,
      receivedAmount: classEnrollForm.receivedAmount,
      handledByName: classEnrollForm.handledByName || undefined,
      remark: classEnrollForm.remark || undefined,
    });
    ElMessage.success('Class enrollment created.');
    classEnrollDialogVisible.value = false;
    loadClassEnrollments();
    // If the enrolled class is currently expanded, reload its orders
    const classId = classEnrollForm.classId;
    if (classId && expandedClassOrders[classId] !== undefined) {
      loadExpandedClassOrders(classId);
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submittingClassEnroll.value = false;
  }
}

// ─── Calendar visibility ─────────────────────────
const canShowCalendar = computed(() =>
  createForm.studentId && createForm.courseProductId && createForm.startDate
);

const weekMonday = computed(() => {
  if (!createForm.startDate) return undefined;
  const d = dayjs(createForm.startDate);
  return d.day(1).startOf('day').format('YYYY-MM-DD');
});

function disabledStartDate(date: Date) {
  return dayjs(date).startOf('day').isBefore(dayjs().startOf('day'));
}

function disabledFutureDate(date: Date) {
  return dayjs(date).startOf('day').isBefore(dayjs().add(1, 'day').startOf('day'));
}

function disabledFutureDateOnly(date: Date) {
  return dayjs(date).startOf('day').isAfter(dayjs().startOf('day'));
}

// ─── Schedule Preview (computed) ────────────────────
const schedulePreview = computed(() => {
  const slots = createForm.slots;
  if (!slots || slots.length === 0) return null;

  const weeklySessions = slots.length;
  const weeklyUnits = slots.reduce((s, slot) => s + slot.plannedUnits, 0);

  const totalUnits = selectedProduct.value?.defaultTotalUnits ?? 40;     // 总课时（小时）
  const totalHalfHourUnits = totalUnits * 2;                              // 转为半课时单位（1单位=0.5h）
  const weeks = Math.ceil(totalHalfHourUnits / weeklyUnits);

  let endDate = '';
  if (createForm.startDate && weeks > 0) {
    endDate = dayjs(createForm.startDate).add(weeks * 7, 'day').format('YYYY-MM-DD');
  }

  const totalSessions = weeks * weeklySessions;

  return { weeklyUnits, weeklySessions, totalSessions, weeks, endDate };
});

// ─── Unique occupied slots (deduplicated by weekday+time) ──
const uniqueOccupiedCount = computed(() => {
  const seen = new Set<string>();
  for (const s of occupiedSlots.value) {
    seen.add(`${s.weekday}:${s.startTime}-${s.endTime}`);
  }
  return seen.size;
});

// ─── Product info (computed) ─────────────────────
const selectedProduct = computed(() =>
  courseProducts.value.find(p => p.id === createForm.courseProductId)
);

// ─── Auto-fill original price when product is selected ──
watch(() => createForm.courseProductId, (productId) => {
  if (!productId) {
    createForm.originalAmount = 0;
    return;
  }
  const product = courseProducts.value.find(p => p.id === productId);
  if (product && product.listPrice != null) {
    createForm.originalAmount = product.listPrice;
  }
});

// ─── Load student's existing schedule ──────────
async function loadExistingSchedule() {
  const studentId = createForm.studentId;
  console.log('[loadExistingSchedule] called, studentId =', studentId);
  if (!studentId) {
    occupiedSlots.value = [];
    return;
  }
  loadingSchedule.value = true;
  try {
    const startDate = createForm.startDate ? dayjs(createForm.startDate) : dayjs();
    const baseDate = startDate.isBefore(dayjs(), 'day') ? dayjs() : startDate;
    const startAt = baseDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss');
    const endAt = baseDate.add(180, 'day').format('YYYY-MM-DDTHH:mm:ss');
    console.log('[loadExistingSchedule] fetching for student', studentId, 'window', startAt, '–', endAt);
    const res = await fetchLessonOccurrences({
      studentId,
      startAt,
      endAt,
      pageSize: 200,
    });
    const records = res.data.data.records || [];
    console.log('[loadExistingSchedule] got', records.length, 'records, total =', res.data.data.total);
    if (records.length > 0) {
      console.log('[loadExistingSchedule] first record:', JSON.stringify(records[0]));
    }
    occupiedSlots.value = records
      .filter(o => o.planningStatus !== 'CANCELLED')
      .map(o => {
        const start = dayjs(o.plannedStartAt);
        const dayNum = start.day(); // 0=Sun..6=Sat
        const weekday = dayNum === 0 ? 7 : dayNum; // 1=Mon..7=Sun
        return {
          weekday,
          startTime: start.format('HH:mm'),
          endTime: dayjs(o.plannedEndAt).format('HH:mm'),
          courseLabel: o.packageName || o.lessonNo,
          studentName: o.studentName,
          teacherName: o.plannedTeacherName,
          classroomName: o.plannedClassroomName,
        };
      });
    console.log('[loadExistingSchedule] mapped to', occupiedSlots.value.length, 'occupiedSlots');
    if (occupiedSlots.value.length > 0) {
      console.table(occupiedSlots.value.slice(0, 10));
    }
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || 'Unknown error';
    console.error('[loadExistingSchedule] FAILED:', err?.response?.status, msg);
    scheduleLoadError.value = `Failed to load student schedule: ${msg}`;
    occupiedSlots.value = [];
  } finally {
    loadingSchedule.value = false;
  }
}

watch(() => createForm.studentId, (newVal, oldVal) => {
  console.log('[EnrollmentForm] studentId changed:', oldVal, '->', newVal);
  occupiedSlots.value = [];
  scheduleLoadError.value = '';
  loadExistingSchedule();
});

watch(() => createForm.startDate, (newVal) => {
  console.log('[EnrollmentForm] startDate changed:', newVal);
  scheduleLoadError.value = '';
  if (newVal && createForm.studentId && createForm.courseProductId) {
    loadExistingSchedule();
  }
});

watch(() => occupiedSlots.value, (val) => {
  console.log('[EnrollmentForm] occupiedSlots ref changed, now', val.length, 'items');
});

function openCreateDialog() {
  createFormRef.value?.resetFields();
  Object.assign(createForm, {
    studentId: undefined,
    courseProductId: undefined,
    primaryTeacherId: undefined,
    handledByName: '',
    startDate: '',
    originalAmount: 0,
    discountAmount: 0,
    remark: '',
    slots: [],
    defaultClassroomId: undefined,
  });
  createDiscountPresetId.value = null;
  occupiedSlots.value = [];
  scheduleLoadError.value = '';
  createDialogVisible.value = true;
}

async function handleCreateSubmit() {
  const valid = await createFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  try {
    await createEnrollmentOrder({
      studentId: createForm.studentId!,
      courseProductId: createForm.courseProductId!,
      primaryTeacherId: createForm.primaryTeacherId,
      handledByName: createForm.handledByName,
      startDate: createForm.startDate || undefined,
      originalAmount: createForm.originalAmount,
      discountAmount: createForm.discountAmount,
      receivedAmount: 0,
      issueReceipt: false,
      remark: createForm.remark || undefined,
      slots: createForm.slots.length > 0 ? createForm.slots.map(s => ({
        weekday: s.weekday,
        startTime: s.startTime,
        endTime: s.endTime,
        plannedUnits: s.plannedUnits,
      })) : undefined,
      defaultClassroomId: createForm.defaultClassroomId || undefined,
    });
    ElMessage.success('Enrollment created.');
    createDialogVisible.value = false;
    loadData();
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Operation failed.');
  } finally {
    submitting.value = false;
  }
}

// ─── Reload class enrollments when entering GROUP tab ──
watch(activeTab, () => {
  if (activeTab.value === 'GROUP') {
    loadClassEnrollments();
  }
});

// ─── Init ───────────────────────────────────────────
loadData();
loadStudents();
loadTeachers();
loadCourseProducts();
loadClassrooms();
loadClasses();
loadClassEnrollments();
loadDiscountPresets();
loadActivePaymentMethods();
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

.filters {
  margin-bottom: 12px;
}

.filters__item {
  margin-right: 16px;
  margin-bottom: 0;
}

.filters__item :deep(.el-form-item__label) {
  color: #606266;
  font-size: 13px;
}

.product-table {
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

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

.discount-text {
  color: #e6a23c;
}

/* Dialog */
.enrollment-dialog :deep(.el-dialog__body) {
  max-height: 65vh;
  overflow-y: auto;
  padding: 16px 20px;
}

.enrollment-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

.enrollment-dialog :deep(.el-dialog__header h3) {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.enrollment-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #ebeef5;
  padding: 12px 20px;
}

.enrollment-form__row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 14px;
}

.enrollment-form__full {
  grid-column: 1 / -1;
}

/* Detail layout */
.enrollment-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-section__title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 20px;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-field label {
  color: #909399;
  font-size: 12px;
}

.detail-field span {
  color: #303133;
  font-size: 13px;
}

.detail-field strong {
  color: #303133;
  font-size: 13px;
}

.outstanding-badge {
  color: #f56c6c !important;
}

.nested-table {
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

/* Payment summary */
.payment-summary {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.payment-summary__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}
.payment-summary__label {
  font-size: 13px;
  color: #909399;
}
.payment-summary__value {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.payment-summary__row--due {
  border-top: 1px solid #dcdfe6;
  margin-top: 4px;
  padding-top: 8px;
}
.payment-summary__row--due .payment-summary__value {
  font-size: 16px;
  color: #409eff;
}
.payment-summary__divider {
  height: 1px;
  background: #dcdfe6;
  margin: 6px 0;
}

/* Product info card */
.product-info-card {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 4px;
}
.product-info-card__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.product-info-card__label {
  font-size: 11px;
  color: #909399;
}
.product-info-card__value {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

/* Schedule section */
.enrollment-form__section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.schedule-preview {
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  border-radius: 6px;
  padding: 10px 14px;
  margin-top: 8px;
}
.schedule-preview__title {
  font-weight: 600;
  font-size: 12px;
  color: #409eff;
  margin-bottom: 6px;
}
.schedule-preview__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #606266;
}
.schedule-preview__stats strong {
  color: #409eff;
}
.schedule-preview__end-date {
  color: #303133;
}
.schedule-preview__end-date strong {
  color: #409eff;
}
.schedule-preview__hint {
  margin-top: 6px;
  font-size: 11px;
  color: #909399;
}

.schedule-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f5f7fa;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 16px 20px;
  color: #909399;
  font-size: 13px;
}
.schedule-placeholder strong {
  color: #606266;
}

.schedule-loading-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.schedule-error-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #f56c6c;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  padding: 6px 10px;
  margin-bottom: 8px;
}

.existing-classes-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #409eff;
  margin-bottom: 6px;
}

.no-classes-banner {
  font-size: 12px;
  color: #c0c4cc;
  margin-bottom: 6px;
}

/* Discount preset dialog header */
.dialog-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.dialog-header-row h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* Discount display (read-only) */
.discount-display {
  width: 100%;
  height: 32px;
  line-height: 32px;
  padding: 0 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  color: #e6a23c;
  font-weight: 600;
  font-size: 13px;
}

/* Expand slot styles */
.expand-loading {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.expand-orders-container {
  padding: 8px 0;
}
.expand-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.expand-empty {
  padding: 12px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

/* Payment split rows */
.payment-split-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.payment-split-row > * {
  min-width: 0;
}
.payment-split-total {
  display: flex;
  gap: 12px;
  font-size: 13px;
  padding: 4px 0 8px;
}
.payment-split-remaining {
  color: #e6a23c;
}
.payment-split-over {
  color: #f56c6c;
}
.payment-split-balanced {
  color: #67c23a;
  font-weight: 600;
}
</style>