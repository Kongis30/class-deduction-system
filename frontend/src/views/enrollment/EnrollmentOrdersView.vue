<template>
  <div class="page-stack">
    <section class="course-product-panel">
      <div class="panel-header">
        <div>
          <span class="panel-header__title">Enrollment history</span>
          <p class="panel-header__hint">New registrations are created from Registration or a student's profile. Use this page to review orders, payments, and saved receipts.</p>
        </div>
        <div class="panel-header__actions">
          <el-button type="primary" @click="router.push({ name: 'registration' })">New registration</el-button>
          <el-button size="small" @click="discountPresetDialogVisible = true">
            Discount presets
          </el-button>
          <el-button size="small" @click="openPaymentMethodDialog()">
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
            <el-option label="Refunded" value="REFUNDED" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- Tabs: 1:1 / Group Class -->
      <el-tabs v-model="activeTab" class="enrollment-tabs">
        <el-tab-pane label="1 on 1" name="ONE_ON_ONE">
          <el-table
            v-loading="loading"
            :data="tableData"
            :row-key="orderRowKey"
            class="product-table"
            :show-empty="true"
          >
            <el-table-column min-width="170">
              <template #header>
                <div class="table-header-filter">
                  <span>Course</span>
                  <el-input
                    v-model="filters.keyword"
                    size="small"
                    clearable
                    placeholder="Course / student / order no."
                    @input="handleFilterChange"
                  />
                </div>
              </template>
              <template #default="{ row }">
                <div class="cell-stack">
                  <strong>{{ row.courseProductName ?? '—' }}</strong>
                  <el-tag v-if="sharedLessonLabel(row)" size="small" type="warning" effect="plain" class="shared-lesson-tag">
                    {{ sharedLessonLabel(row) }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Student" min-width="140">
              <template #default="{ row }">
                <span>{{ row.studentName ?? '—' }}</span>
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
            <el-table-column min-width="120" align="center">
              <template #header>
                <div class="table-header-filter table-header-filter--center">
                  <span>Payment</span>
                  <el-select
                    v-model="filters.paymentStatus"
                    size="small"
                    placeholder="All"
                    clearable
                    @change="handleFilterChange"
                  >
                    <el-option
                      v-for="option in paymentStatusOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </div>
              </template>
              <template #default="{ row }">
                <el-tag size="small" :type="paymentStatusType(row.paymentStatus)">
                  {{ paymentStatusLabel(row.paymentStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column min-width="120" align="center">
              <template #header>
                <div class="table-header-filter table-header-filter--center">
                  <span>Status</span>
                  <el-select
                    v-model="filters.orderStatus"
                    size="small"
                    placeholder="All"
                    clearable
                    @change="handleFilterChange"
                  >
                    <el-option
                      v-for="option in orderStatusOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </div>
              </template>
              <template #default="{ row }">
                <el-tag size="small" :type="orderStatusType(row.orderStatus)">
                  {{ orderStatusLabel(row.orderStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" width="76" align="center" fixed="right">
              <template #default="{ row }">
                <el-dropdown trigger="hover" @command="handleOrderAction($event, row)">
                  <el-button text type="primary" class="order-action-more">&hellip;</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="detail">Detail</el-dropdown-item>
                      <el-dropdown-item command="receipt">View receipt</el-dropdown-item>
                      <el-dropdown-item v-if="canPayOrder(row, rowParticipant(row))" command="pay">Pay</el-dropdown-item>
                      <el-dropdown-item v-if="canRefundOrder(row, rowParticipant(row))" command="refund">Refund</el-dropdown-item>
                      <el-dropdown-item v-if="canCancelOrder(row)" command="cancel">Cancel</el-dropdown-item>
                      <el-dropdown-item v-if="canDeleteOrder(row)" divided command="delete">Delete</el-dropdown-item>
                      <el-dropdown-item v-if="canForceVoidOrder(row)" divided command="forceVoid">Force void (admin)</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
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
          <div class="group-toolbar">
            <div class="group-toolbar__search">
              <span class="group-toolbar__label">Classes</span>
              <el-input
                v-model="groupFilters.keyword"
                size="small"
                clearable
                placeholder="Search product, class, teacher or room"
                class="group-toolbar__input"
              />
            </div>
            <div v-if="groupClassTree.length" class="group-toolbar__summary">
              <span class="group-toolbar__summary-item">
                <strong>{{ groupClassTree.length }}</strong> products
              </span>
              <span class="group-toolbar__summary-divider" aria-hidden="true" />
              <span class="group-toolbar__summary-item">
                <strong>{{ groupClassCount }}</strong> classes
              </span>
            </div>
          </div>

          <el-table
            ref="groupTableRef"
            v-loading="loadingClasses"
            :data="groupFlatRows"
            row-key="id"
            class="product-table group-table"
            :show-empty="true"
            empty-text="No active group classes."
            :span-method="groupSpanMethod"
            :row-class-name="groupRowClassName"
            @expand-change="handleExpandChange"
            @row-click="handleGroupRowClick"
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
                            <el-table-column min-width="150">
                              <template #header>
                                <div class="table-header-filter">
                                  <span>Student</span>
                                  <el-input
                                    v-model="groupOrderFilters.keyword"
                                    size="small"
                                    clearable
                                    placeholder="Student / order no."
                                    @input="handleGroupOrderFilterChange"
                                  />
                                </div>
                              </template>
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
                            <el-table-column min-width="112" align="center">
                              <template #header>
                                <div class="table-header-filter table-header-filter--center">
                                  <span>Payment</span>
                                  <el-select
                                    v-model="groupOrderFilters.paymentStatus"
                                    size="small"
                                    placeholder="All"
                                    clearable
                                    @change="handleGroupOrderFilterChange"
                                  >
                                    <el-option
                                      v-for="option in paymentStatusOptions"
                                      :key="option.value"
                                      :label="option.label"
                                      :value="option.value"
                                    />
                                  </el-select>
                                </div>
                              </template>
                              <template #default="{ row: order }">
                                <el-tag size="small" :type="paymentStatusType(order.paymentStatus)">
                                  {{ paymentStatusLabel(order.paymentStatus) }}
                                </el-tag>
                              </template>
                            </el-table-column>
                            <el-table-column min-width="112" align="center">
                              <template #header>
                                <div class="table-header-filter table-header-filter--center">
                                  <span>Status</span>
                                  <el-select
                                    v-model="groupOrderFilters.orderStatus"
                                    size="small"
                                    placeholder="All"
                                    clearable
                                    @change="handleGroupOrderFilterChange"
                                  >
                                    <el-option
                                      v-for="option in orderStatusOptions"
                                      :key="option.value"
                                      :label="option.label"
                                      :value="option.value"
                                    />
                                  </el-select>
                                </div>
                              </template>
                              <template #default="{ row: order }">
                                <el-tag size="small" :type="orderStatusType(order.orderStatus)">
                                  {{ orderStatusLabel(order.orderStatus) }}
                                </el-tag>
                              </template>
                            </el-table-column>
                            <el-table-column label="Actions" width="76" align="center">
                              <template #default="{ row: order }">
                                <el-dropdown trigger="hover" @command="handleOrderAction($event, order)">
                                  <el-button text type="primary" size="small" class="order-action-more">&hellip;</el-button>
                                  <template #dropdown>
                                    <el-dropdown-menu>
                                      <el-dropdown-item command="detail">Detail</el-dropdown-item>
                                      <el-dropdown-item command="receipt">View receipt</el-dropdown-item>
                                      <el-dropdown-item v-if="canPayOrder(order)" command="pay">Pay</el-dropdown-item>
                                      <el-dropdown-item v-if="canRefundOrder(order)" command="refund">Refund</el-dropdown-item>
                                      <el-dropdown-item v-if="canCancelOrder(order)" command="cancel">Cancel</el-dropdown-item>
                                      <el-dropdown-item v-if="canDeleteOrder(order)" divided command="delete">Delete</el-dropdown-item>
                                      <el-dropdown-item v-if="canForceVoidOrder(order)" divided command="forceVoid">Force void (admin)</el-dropdown-item>
                                    </el-dropdown-menu>
                                  </template>
                                </el-dropdown>
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
                              @current-change="(p: number) => loadExpandedClassOrders(row.id, p)"
                            />
                          </div>
                        </div>
                      </template>
                    </el-table-column>

                    <el-table-column label="Product / grade / class" min-width="280">
                      <template #default="{ row }">
                        <div
                          v-if="row.rowType === 'product'"
                          class="group-cell"
                          @click="toggleProduct(row.key)"
                        >
                          <span class="group-caret" aria-hidden="true">{{ row.expanded ? '▾' : '▸' }}</span>
                          <strong>{{ row.name }}</strong>
                          <span class="group-meta">
                            {{ row.gradeCount }} {{ row.gradeCount === 1 ? 'grade' : 'grades' }}
                            <span class="group-meta__dot" aria-hidden="true">·</span>
                            {{ row.classCount }} {{ row.classCount === 1 ? 'class' : 'classes' }}
                          </span>
                        </div>
                        <div
                          v-else-if="row.rowType === 'grade'"
                          class="group-cell group-cell--grade"
                          @click="toggleGrade(row.key)"
                        >
                          <span class="group-caret" aria-hidden="true">{{ row.expanded ? '▾' : '▸' }}</span>
                          <strong>{{ row.name }}</strong>
                          <span class="group-meta">{{ row.classCount }} {{ row.classCount === 1 ? 'class' : 'classes' }}</span>
                        </div>
                        <div v-else class="group-cell group-cell--class">
                          <button
                            type="button"
                            class="group-caret group-caret--button"
                            :aria-label="isClassExpanded(row.id) ? 'Collapse class orders' : 'Expand class orders'"
                          >
                            {{ isClassExpanded(row.id) ? '▾' : '▸' }}
                          </button>
                          <span class="group-class-marker" aria-hidden="true" />
                          <strong>{{ row.className }}</strong>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="Teacher" min-width="120">
                      <template #default="{ row }">
                        <span :class="['group-value', { 'group-value--empty': !row.teacherName }]">
                          {{ row.teacherName || 'Not assigned' }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column label="Classroom" min-width="120">
                      <template #default="{ row }">
                        <span :class="['group-value', { 'group-value--empty': !row.classroomName }]">
                          {{ row.classroomName || 'Not assigned' }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column label="Schedule" min-width="160">
                      <template #default="{ row }">
                        <el-tooltip v-if="row.scheduleDesc" :content="row.scheduleDesc" placement="top">
                          <span class="group-schedule">{{ row.scheduleDesc }}</span>
                        </el-tooltip>
                        <span v-else class="group-value group-value--empty">Not set</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="Students" min-width="96" align="center">
                      <template #default="{ row }">
                        <span class="enrolled-count">{{ row.activeStudentCount }}</span>
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
          <p v-if="detailParticipants.length > 1" class="detail-section__hint">
            Shared lesson ({{ detailParticipants.length === 2 ? '1-on-2' : '1-on-3' }}): the totals below are the whole order; each student's split is listed in the Students section.
          </p>
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
              <label>Amount due</label>
              <strong>${{ detailData.amountDue?.toFixed(2) }}</strong>
            </div>
            <div class="detail-field">
              <label>Received</label>
              <strong>${{ detailData.receivedAmount?.toFixed(2) }}</strong>
            </div>
            <div class="detail-field">
              <label>Balance</label>
              <strong class="outstanding-badge">
                ${{ detailData.balanceAmount?.toFixed(2) }}
              </strong>
            </div>
          </div>
        </div>

        <div v-if="detailParticipants.length > 1" class="detail-section">
          <h4 class="detail-section__title">Students</h4>
          <el-table :data="detailParticipants" size="small" class="nested-table">
            <el-table-column label="Student" min-width="140">
              <template #default="{ row }">{{ row.studentName || '—' }}</template>
            </el-table-column>
            <el-table-column label="Split price" min-width="110" align="right">
              <template #default="{ row }">${{ (row.grossAmount ?? 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Discount" min-width="90" align="right">
              <template #default="{ row }">
                <span class="discount-text">-${{ (row.discountAmount ?? 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Due" min-width="100" align="right">
              <template #default="{ row }">${{ (row.amountDue ?? 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Paid" min-width="100" align="right">
              <template #default="{ row }">${{ (row.paidAmount ?? 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="Balance" min-width="100" align="right">
              <template #default="{ row }">
                <strong>${{ (row.balanceAmount ?? 0).toFixed(2) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="Payment" min-width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="paymentStatusType(participantPaymentStatus(row))">
                  {{ paymentStatusLabel(participantPaymentStatus(row)) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Actions" min-width="110" align="center">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="openDetailPayForParticipant(row)">Pay</el-button>
                <el-button
                  v-if="(row.paidAmount ?? 0) > 0"
                  text
                  type="danger"
                  size="small"
                  @click="openDetailRefundForParticipant(row)"
                >
                  Refund
                </el-button>
              </template>
            </el-table-column>
          </el-table>
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
          <p class="payment-records-hint">
            This is the order-level payment timeline. Package received amounts only show allocations captured at enrollment; later payments update the order balance only.
          </p>
          <el-table v-if="paymentRecords.length > 0" :data="paymentRecords" size="small" class="nested-table">
            <el-table-column label="Payment no." min-width="140" prop="paymentNo" />
            <el-table-column label="Method" min-width="100">
              <template #default="{ row }">{{ getPaymentMethodName(row.paymentMethod) }}</template>
            </el-table-column>
            <el-table-column label="Type" min-width="90" align="center">
              <template #default="{ row }">{{ paymentTypeLabel(row.paymentType) }}</template>
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
            <el-table-column label="Notes" min-width="160">
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
            <el-table-column label="Actions" min-width="110" align="center">
              <template #default="{ row: payRow }">
                <el-button text type="primary" size="small" @click="showPaymentStatement(detailData!)">
                  View statement
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="No payment records." :image-size="60" />
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="detailData" @click="openReceipt(detailData)">View receipt</el-button>
          <el-button v-if="detailData && canPayOrder(detailData)" type="primary" @click="openPayDialog(detailData)">
            Record payment
          </el-button>
          <el-button @click="detailDialogVisible = false">Close</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Payment dialog -->
    <el-dialog
      v-model="payDialogVisible"
      width="760px"
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
          <span class="payment-summary__value">{{ payStudentName }}</span>
        </div>
        <div class="payment-summary__row">
          <span class="payment-summary__label">Product</span>
          <span class="payment-summary__value">{{ payOrder.courseProductName ?? '—' }}</span>
        </div>
        <div class="payment-summary__divider" />
        <div class="payment-summary__row">
          <span class="payment-summary__label">Original price</span>
          <span class="payment-summary__value">${{ payOriginalAmount.toFixed(2) }}</span>
        </div>
        <div class="payment-summary__row">
          <span class="payment-summary__label">Discount</span>
          <span class="payment-summary__value discount-text">-${{ payDiscountAmount.toFixed(2) }}</span>
        </div>
        <div class="payment-summary__row payment-summary__row--due">
          <span class="payment-summary__label">Amount Due</span>
          <strong class="payment-summary__value">${{ payAmountDueTotal.toFixed(2) }}</strong>
        </div>
        <div class="payment-summary__row payment-summary__row--due">
          <span class="payment-summary__label">Outstanding Balance</span>
          <strong class="payment-summary__value">${{ payAmountDue.toFixed(2) }}</strong>
        </div>
        <div v-if="payReceivedAmount > 0" class="payment-summary__row">
          <span class="payment-summary__label">Already Paid</span>
          <span class="payment-summary__value">${{ payReceivedAmount.toFixed(2) }}</span>
        </div>
        <div v-if="payParticipant" class="payment-summary__scope">
          This payment applies to this student only. The receipt remains shared by the whole order.
        </div>
      </div>

      <el-form
        ref="payFormRef"
        label-position="top"
        :model="payForm"
        class="enrollment-form"
      >
        <!-- Payment splits -->
        <el-form-item label="New payments">
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
              <el-date-picker
                v-model="split.paidAt"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="Date"
                style="flex: 1;"
              />
              <el-input v-model="split.handledByName" placeholder="Handler" style="flex: 1;" />
              <el-input v-model="split.remark" placeholder="Notes" style="flex: 1;" />
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
        </div>

        <div v-if="!payParticipant && payCourseItems.length > 1" class="payment-allocation-preview">
          <div class="payment-allocation-preview__title">Assign each payment to courses</div>
          <div v-for="(split, splitIndex) in payForm.splits" :key="`allocation-${splitIndex}`" class="course-payment-allocation">
            <strong>Payment {{ splitIndex + 1 }} · {{ paymentMethodName(split.paymentMethod) }} · ${{ split.amount.toFixed(2) }}</strong>
            <el-table :data="payCourseItems" size="small">
              <el-table-column label="Course" min-width="180">
                <template #default="{ row }">{{ row.detail || '—' }}</template>
              </el-table-column>
              <el-table-column label="Outstanding" width="120" align="right">
                <template #default="{ row }">${{ courseBalance(row).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="This payment" width="170" align="right">
                <template #default="{ row }">
                  <el-input-number
                    :model-value="split.allocations[String(row.id)] ?? 0"
                    :min="0"
                    :max="courseBalance(row)"
                    :precision="2"
                    :controls="false"
                    @update:model-value="setSplitCourseAllocation(split, row.id, $event)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="Remaining after" width="140" align="right">
                <template #default="{ row }">
                  ${{ Math.max(0, courseBalance(row) - courseAllocated(row.id)).toFixed(2) }}
                </template>
              </el-table-column>
            </el-table>
            <div :class="['course-payment-allocation__total', { 'payment-split-over': splitAllocationRemaining(split) < 0 }]">
              Assigned ${{ splitAllocationTotal(split).toFixed(2) }} / ${{ split.amount.toFixed(2) }}
            </div>
          </div>
        </div>

      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="payDialogVisible = false">Cancel</el-button>
          <el-button
            type="primary"
            :loading="paying"
            :disabled="paySplitTotal <= 0 || paySplitRemaining < 0"
            @click="handlePaySubmit"
          >Record Payment</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Refund dialog -->
    <el-dialog
      v-model="refundDialogVisible"
      width="460px"
      :close-on-click-modal="false"
      class="enrollment-dialog"
    >
      <template #header>
        <h3>Refund</h3>
      </template>

      <el-form
        ref="refundFormRef"
        label-position="top"
        :model="refundForm"
        :rules="refundFormRules"
        class="enrollment-form"
      >
        <div v-if="refundOrder" class="product-info-card" style="margin-bottom: 12px;">
          <div v-if="refundParticipant" class="product-info-card__item">
            <span class="product-info-card__label">Student</span>
            <span class="product-info-card__value">{{ refundStudentName }}</span>
          </div>
          <div class="product-info-card__item">
            <span class="product-info-card__label">{{ refundParticipant ? 'Currently received for this student' : 'Currently received for order' }}</span>
            <span class="product-info-card__value">${{ refundReceivedAmount.toFixed(2) }}</span>
          </div>
        </div>
        <p class="refund-hint">
          A partial refund keeps the enrollment and schedule active. A refund from a student row applies only to that student's payments; the shared receipt and order totals are updated together.
        </p>

        <el-form-item label="Refund amount" prop="refundAmount">
          <el-input-number
            v-model="refundForm.refundAmount"
            :min="0.01"
            :max="refundReceivedAmount"
            :precision="2"
            :controls="false"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="Refund method" prop="paymentMethod">
          <el-select v-model="refundForm.paymentMethod" placeholder="Select method" style="width: 100%;">
            <el-option v-for="m in paymentMethods" :key="m.methodCode" :label="m.methodName" :value="m.methodCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="Handled by" prop="handledByName">
          <el-input v-model="refundForm.handledByName" placeholder="Your name" />
        </el-form-item>
        <el-form-item label="Reason / Notes">
          <el-input v-model="refundForm.remark" type="textarea" :rows="2" placeholder="Optional reason" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="refundDialogVisible = false">Cancel</el-button>
          <el-button type="warning" :loading="refunding" @click="handleRefundSubmit">Confirm Refund</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Delete enrollment order dialog -->
    <el-dialog
      v-model="deleteDialogVisible"
      width="480px"
      :close-on-click-modal="false"
      class="enrollment-dialog"
      @closed="resetDeleteDialog"
    >
      <template #header>
        <h3>Delete enrollment order</h3>
      </template>

      <div class="delete-warning">
        <p>
          This removes the order from the list together with its course package, scheduled lessons,
          confirmed lesson records, hour ledger, and payment/receipt records.
        </p>
        <p v-if="deleteTarget?.classId">
          For class orders the student's seat and personal lesson records are also removed;
          the shared class schedule is untouched.
        </p>
        <p>
          Data is soft-deleted and retained for audit, but no longer displayed.
          This action cannot be recovered through the UI.
        </p>
        <p v-if="deleteTarget">
          Order: <strong>{{ deleteTarget.enrollmentNo }}</strong>
        </p>
        <p v-if="deleteConfirmRemaining > 0">
          Please wait {{ deleteConfirmRemaining }} second(s) before confirming.
        </p>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="deleteDialogVisible = false">Cancel</el-button>
          <el-button
            type="danger"
            :disabled="deleteConfirmRemaining > 0"
            :loading="deletingOrder"
            @click="confirmDeleteOrder"
          >
            {{ deleteConfirmRemaining > 0 ? `Confirm in ${deleteConfirmRemaining}s` : 'Confirm delete' }}
          </el-button>
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
          <el-input v-model="methodForm.methodName" placeholder="e.g. WeChat Pay" />
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
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import dayjs from 'dayjs';

import { cancelEnrollmentOrder, deleteEnrollmentOrder, forceVoidEnrollmentOrder, refundEnrollmentOrder, fetchEnrollmentOrders, fetchEnrollmentOrder, createPaymentRecord, fetchActivePaymentMethods, fetchPaymentMethods, createPaymentMethod, updatePaymentMethod, deletePaymentMethod, type EnrollmentOrderQuery } from '@/api/enrollment';
import { fetchAllStudents, fetchDiscountPresets, createDiscountPreset, updateDiscountPreset, deleteDiscountPreset } from '@/api/base-data';
import { fetchClassGroups } from '@/api/schedule';
import { useAuthStore } from '@/stores/auth';
import { isAdminUser } from '@/types/auth';
import type { DiscountPresetItem, EntityId, EnrollmentOrderItem, EnrollmentOrderDetailItem, EnrollmentOrderLineItem, EnrollmentOrderParticipantItem, PaymentMethodItem, PaymentRecordItem } from '@/types/domain';

const authStore = useAuthStore();
const isAdmin = computed(() => isAdminUser(authStore.currentUser));

const orderStatusOptions = [
  { label: 'Draft', value: 'DRAFT' },
  { label: 'Confirmed', value: 'CONFIRMED' },
  { label: 'Cancelled', value: 'CANCELLED' },
  { label: 'Closed', value: 'CLOSED' },
];

const paymentStatusOptions = [
  { label: 'Unpaid', value: 'UNPAID' },
  { label: 'Partial', value: 'PARTIAL' },
  { label: 'Paid', value: 'PAID' },
  { label: 'Refunded', value: 'REFUNDED' },
];

const loading = ref(false);
const route = useRoute();
const router = useRouter();
const detailDialogVisible = ref(false);
const activeTab = ref('ONE_ON_ONE');
const loadingClasses = ref(false);
const groupTableRef = ref<any>();
const classEnrollmentData = ref<any[]>([]);
const groupFilters = reactive({
  keyword: '',
});
const groupOrderFilters = reactive({
  keyword: '',
  orderStatus: '',
  paymentStatus: '',
});
const filteredClassEnrollmentData = computed(() => {
  const keyword = groupFilters.keyword.trim().toLowerCase();
  if (!keyword) return classEnrollmentData.value;
  return classEnrollmentData.value.filter((row) => [
    row.className,
    row.productName,
    row.teacherName,
    row.classroomName,
    row.scheduleDesc,
  ].some((value) => String(value ?? '').toLowerCase().includes(keyword)));
});

// ─── Group Class hierarchical grouping (product → grade → class) ──
interface GroupClassGrade {
  key: string;
  gradeBand: string;
  classes: any[];
}
interface GroupClassProduct {
  productId: string;
  productName: string;
  grades: GroupClassGrade[];
}

// 默认全部折叠：只记录已展开的产品/grade。
const expandedProducts = reactive(new Set<string>());
const expandedGrades = reactive(new Set<string>());

const groupClassCount = computed(() =>
  groupClassTree.value.reduce((sum, product) =>
    sum + product.grades.reduce((inner, grade) => inner + grade.classes.length, 0), 0)
);

const groupClassTree = computed<GroupClassProduct[]>(() => {
  const productMap = new Map<string, GroupClassProduct>();
  for (const row of filteredClassEnrollmentData.value) {
    const pid = String(row.productId ?? 'unassigned');
    let product = productMap.get(pid);
    if (!product) {
      product = { productId: pid, productName: row.productName ?? 'Unassigned', grades: [] };
      productMap.set(pid, product);
    }
    const gradeBand = String(row.gradeBand ?? 'Grade not set');
    const gkey = `${pid}::${gradeBand.toLowerCase()}`;
    let grade = product.grades.find((item) => item.key === gkey);
    if (!grade) {
      grade = { key: gkey, gradeBand, classes: [] };
      product.grades.push(grade);
    }
    grade.classes.push(row);
  }
  const products = Array.from(productMap.values());
  products.sort((left, right) => left.productName.localeCompare(right.productName));
  products.forEach((product) => {
    product.grades.sort((left, right) => left.gradeBand.localeCompare(right.gradeBand));
  });
  return products;
});

function toggleProduct(productId: string) {
  const key = String(productId);
  if (expandedProducts.has(key)) expandedProducts.delete(key);
  else expandedProducts.add(key);
}
function toggleGrade(gradeKey: string) {
  if (expandedGrades.has(gradeKey)) expandedGrades.delete(gradeKey);
  else expandedGrades.add(gradeKey);
}
function isProductExpanded(productId: string) {
  if (groupFilters.keyword.trim()) return true;
  return expandedProducts.has(String(productId));
}
function isGradeExpanded(gradeKey: string) {
  if (groupFilters.keyword.trim()) return true;
  return expandedGrades.has(gradeKey);
}

// 扁平化为「产品行 / grade 行 / class 行」交替的单表数据，产品与 grade 作为通栏分组行。
const GROUP_TABLE_COLUMN_COUNT = 6;

interface GroupFlatRow {
  rowType: 'product' | 'grade' | 'class';
  id: string;
  key?: string;
  name?: string;
  gradeCount?: number;
  classCount?: number;
  expanded?: boolean;
  [key: string]: any;
}

const groupFlatRows = computed<GroupFlatRow[]>(() => {
  const rows: GroupFlatRow[] = [];
  for (const product of groupClassTree.value) {
    const productExpanded = isProductExpanded(product.productId);
    rows.push({
      rowType: 'product',
      id: `P-${product.productId}`,
      key: product.productId,
      name: product.productName,
      gradeCount: product.grades.length,
      classCount: product.grades.reduce((sum, grade) => sum + grade.classes.length, 0),
      expanded: productExpanded,
    });
    if (!productExpanded) continue;
    for (const grade of product.grades) {
      const gradeExpanded = isGradeExpanded(grade.key);
      rows.push({
        rowType: 'grade',
        id: `G-${grade.key}`,
        key: grade.key,
        name: grade.gradeBand,
        classCount: grade.classes.length,
        expanded: gradeExpanded,
      });
      if (!gradeExpanded) continue;
      for (const cls of grade.classes) {
        rows.push({ ...cls, rowType: 'class' });
      }
    }
  }
  return rows;
});

function groupSpanMethod({ row, columnIndex }: { row: GroupFlatRow; columnIndex: number }): [number, number] {
  if (row.rowType === 'class') return [1, 1];
  // 分组行：expand 列保留占位（图标由 CSS 隐藏），Course 列横向合并铺满其余 5 列。
  if (columnIndex === 0) return [1, 1];
  if (columnIndex === 1) return [1, GROUP_TABLE_COLUMN_COUNT - 1];
  return [1, 0];
}

function groupRowClassName({ row }: { row: GroupFlatRow }) {
  if (row.rowType === 'product') return 'group-row--product';
  if (row.rowType === 'grade') return 'group-row--grade';
  return '';
}

// ─── Group Class expandable rows ──────────────────
const expandedClassOrders = reactive<Record<string, EnrollmentOrderItem[]>>({});
const expandedClassLoading = reactive<Record<string, boolean>>({});
const expandedClassPagination = reactive<Record<string, { page: number; size: number; total: number }>>({});
const expandedClassIds = reactive(new Set<string>());

async function loadExpandedClassOrders(classId: string | number, pageNum = 1) {
  const classKey = String(classId);
  expandedClassLoading[classKey] = true;
  try {
    if (!expandedClassPagination[classKey]) {
      expandedClassPagination[classKey] = { page: 1, size: 10, total: 0 };
    }
    expandedClassPagination[classKey].page = pageNum;
    const res = await fetchEnrollmentOrders({
      classId,
      keyword: groupOrderFilters.keyword || undefined,
      orderStatus: groupOrderFilters.orderStatus || undefined,
      paymentStatus: groupOrderFilters.paymentStatus || undefined,
      pageNum,
      pageSize: expandedClassPagination[classKey].size,
    });
    const data = res.data.data;
    expandedClassOrders[classKey] = data.records;
    expandedClassPagination[classKey].total = data.total;
  } catch {
    expandedClassOrders[classKey] = [];
    expandedClassPagination[classKey] = { page: 1, size: 10, total: 0 };
  } finally {
    expandedClassLoading[classKey] = false;
  }
}

function handleExpandChange(row: any, expandedRows: any[]) {
  if (row.rowType !== 'class') return;
  const rowKey = String(row.id);
  const isExpanded = expandedRows.some(r => r.id === row.id);
  if (isExpanded) {
    const alreadyExpanded = expandedClassIds.has(rowKey);
    expandedClassIds.add(rowKey);
    if (!alreadyExpanded && !expandedClassOrders[rowKey]) {
      loadExpandedClassOrders(row.id);
    }
  } else {
    expandedClassIds.delete(rowKey);
  }
}

function isClassExpanded(classId: string | number) {
  return expandedClassIds.has(String(classId));
}

function toggleClassOrders(row: GroupFlatRow) {
  if (row.rowType !== 'class') return;
  const rowKey = String(row.id);
  const nextExpanded = !expandedClassIds.has(rowKey);
  groupTableRef.value?.toggleRowExpansion(row, nextExpanded);
  if (nextExpanded) {
    expandedClassIds.add(rowKey);
    loadExpandedClassOrders(row.id);
  } else {
    expandedClassIds.delete(rowKey);
  }
}

function handleGroupRowClick(row: GroupFlatRow) {
  if (row.rowType === 'class') {
    toggleClassOrders(row);
  }
}

const detailData = ref<EnrollmentOrderDetailItem | null>(null);

const detailParticipants = computed(() =>
  (detailData.value?.participants ?? [])
    .slice()
    .sort((a, b) => (a.participantIndex ?? 0) - (b.participantIndex ?? 0)),
);
const paymentRecords = computed(() => detailData.value?.payments ?? []);
const students = ref<{ id: string; studentName: string }[]>([]);

// ─── Discount Presets ──────────────────────────────────
const discountPresets = ref<DiscountPresetItem[]>([]);
const discountPresetsLoading = ref(false);
const discountPresetDialogVisible = ref(false);
const presetEditDialogVisible = ref(false);
const savingPreset = ref(false);
const editingPresetId = ref<EntityId | null>(null);
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

watch(
  () => route.query.manageDiscountPresets,
  (value) => {
    if (value === '1') {
      discountPresetDialogVisible.value = true;
    }
  },
  { immediate: true },
);

// ─── Discount preset selection for dialogs ──────────
type DiscountSelectionId = EntityId | '__none__' | '__custom__' | null;

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

async function handleDeletePreset(id: EntityId) {
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


function getPaymentMethodName(code: string): string {
  const m = paymentMethods.value.find(p => p.methodCode === code);
  return m ? m.methodName : code;
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
    const payload = {
      methodCode: editingMethodId.value ? methodForm.methodCode : methodForm.methodName
        .replace(/([a-z])([A-Z])/g, '$1_$2')
        .replace(/[\s-]+/g, '_')
        .replace(/[^a-zA-Z0-9_]/g, '')
        .toUpperCase(),
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

async function handleDeleteMethod(id: EntityId) {
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
function applyPresetDiscount(presetId: DiscountSelectionId, form: { originalAmount: number; discountAmount: number }) {
  if (presetId === '__none__') {
    form.discountAmount = 0;
    return;
  }
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
const payParticipant = ref<EnrollmentOrderParticipantItem | null>(null);
const payCourseItems = ref<EnrollmentOrderLineItem[]>([]);

interface PaySplit {
  paymentMethod: string;
  amount: number;
  paidAt: string;
  handledByName: string;
  remark: string;
  allocations: Record<string, number>;
}

const paymentMethods = ref<PaymentMethodItem[]>([]);
const payForm = reactive({
  splits: [] as PaySplit[],
});

const payAmountDue = computed(() => {
  return Math.max(0, payParticipant.value?.balanceAmount ?? payOrder.value?.balanceAmount ?? 0);
});

const payStudentName = computed(() => payParticipant.value?.studentName ?? payOrder.value?.studentName ?? '—');
const payOriginalAmount = computed(() => payParticipant.value?.grossAmount ?? payOrder.value?.originalAmount ?? 0);
const payDiscountAmount = computed(() => payParticipant.value?.discountAmount ?? payOrder.value?.discountAmount ?? 0);
const payAmountDueTotal = computed(() => payParticipant.value?.amountDue ?? payOrder.value?.amountDue ?? 0);
const payReceivedAmount = computed(() => payParticipant.value?.paidAmount ?? payOrder.value?.receivedAmount ?? 0);

const paySplitTotal = computed(() => {
  return payForm.splits.reduce((sum, s) => sum + (s.amount || 0), 0);
});

const paySplitRemaining = computed(() => {
  return payAmountDue.value - paySplitTotal.value;
});

function addPaySplit() {
  payForm.splits.push(createPaySplit());
}

function removePaySplit(index: number) {
  payForm.splits.splice(index, 1);
}

function createPaySplit(amount = 0, allocations: Record<string, number> = {}): PaySplit {
  return {
    paymentMethod: paymentMethods.value[0]?.methodCode ?? 'CASH',
    amount,
    paidAt: dayjs().format('YYYY-MM-DD'),
    handledByName: '',
    remark: '',
    allocations,
  };
}

// ─── Payment method management state ──────────────
const paymentMethodDialogVisible = ref(false);
const paymentMethodList = ref<PaymentMethodItem[]>([]);
const paymentMethodLoading = ref(false);
const methodEditDialogVisible = ref(false);
const methodFormRef = ref();
const editingMethodId = ref<EntityId | null>(null);
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

// 共享私教订单（1-on-2 / 1-on-3）在列表里按学员展开为多行，每行显示该学员在 DB 里持久化的平摊金额。
function participantPaymentStatus(participant: EnrollmentOrderParticipantItem) {
  const due = participant.amountDue ?? 0;
  const paid = participant.paidAmount ?? 0;
  if (paid <= 0 && due > 0) return 'UNPAID';
  if (due > 0 && paid >= due) return 'PAID';
  return paid > 0 ? 'PARTIAL' : 'UNPAID';
}

function expandSharedPrivateLessonRows(records: EnrollmentOrderItem[]): EnrollmentOrderItem[] {
  const rows: EnrollmentOrderItem[] = [];
  for (const order of records) {
    const participants = order.participants ?? [];
    if (participants.length > 1) {
      participants.forEach((participant) => {
        rows.push({
          ...order,
          studentId: participant.studentId,
          studentName: participant.studentName || order.studentName,
          originalAmount: participant.grossAmount ?? order.originalAmount,
          discountAmount: participant.discountAmount ?? 0,
          receivedAmount: participant.paidAmount ?? 0,
          amountDue: participant.amountDue ?? 0,
          balanceAmount: participant.balanceAmount ?? 0,
          paymentStatus: participantPaymentStatus(participant),
          participantIndex: participant.participantIndex,
        });
      });
    } else {
      rows.push(order);
    }
  }
  return rows;
}

function orderRowKey(row: EnrollmentOrderItem) {
  return `${row.id}-${row.participantIndex ?? 0}`;
}

function rowParticipant(row: EnrollmentOrderItem): EnrollmentOrderParticipantItem | null {
  if (row.participantIndex == null || !row.participants?.length) return null;
  return row.participants.find((item) => item.participantIndex === row.participantIndex) ?? null;
}

function sharedLessonLabel(row: EnrollmentOrderItem): string | null {
  const count = row.participants?.length ?? 0;
  return count > 1 ? `1-on-${count}` : null;
}

function handleFilterChange() {
  pagination.page = 1;
  loadData();
}

function handleGroupOrderFilterChange() {
  Object.keys(expandedClassOrders).forEach((classId) => {
    loadExpandedClassOrders(classId, 1);
  });
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

function paymentTypeLabel(type?: string) {
  return type === 'REFUND' ? 'REFUND' : 'PAYMENT';
}

function paymentStatusType(status: string) {
  if (status === 'PAID') return 'success';
  if (status === 'PARTIAL') return 'warning';
  if (status === 'REFUNDED') return 'danger';
  return 'info';
}

function paymentStatusLabel(status: string) {
  const map: Record<string, string> = {
    UNPAID: 'Unpaid',
    PARTIAL: 'Partial',
    PAID: 'Paid',
    REFUNDED: 'Refunded',
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
    tableData.value = expandSharedPrivateLessonRows(data.records);
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

function openDetailPayForParticipant(participant: EnrollmentOrderParticipantItem) {
  if (!detailData.value) return;
  const order = orderSummaryFromDetail(detailData.value);
  const stored = order.participants?.find((item) => item.participantIndex === participant.participantIndex) ?? participant;
  if (!canPayOrder(order, stored)) {
    ElMessage.warning('This student does not have an outstanding balance.');
    return;
  }
  openPayDialog(order, stored);
}

function openDetailRefundForParticipant(participant: EnrollmentOrderParticipantItem) {
  if (!detailData.value) return;
  const order = orderSummaryFromDetail(detailData.value);
  const stored = order.participants?.find((item) => item.participantIndex === participant.participantIndex) ?? participant;
  if (!canRefundOrder(order, stored)) {
    ElMessage.warning('This student has no refundable payment.');
    return;
  }
  openRefundDialog(order, stored);
}

async function handleCancel(row: EnrollmentOrderItem) {
  try {
    const orderReceived = row.participants?.length
      ? row.participants.reduce((sum, item) => sum + (item.paidAmount ?? 0), 0)
      : (row.receivedAmount ?? 0);
    const paymentNotice = orderReceived > 0
      ? '\n\nPayment records will be retained for manual follow-up.'
      : '';
    await ElMessageBox.confirm(
      `Cancel enrollment order ${row.enrollmentNo}?\n\nFuture scheduled lessons and occupancy will be cancelled.${paymentNotice}`,
      'Confirm cancel',
      { type: 'warning' },
    );
    await cancelEnrollmentOrder(row.id);
    ElMessage.success('Order cancelled.');
    if (row.classId) {
      await loadExpandedClassOrders(row.classId, expandedClassPagination[row.classId]?.page ?? 1);
    } else {
      await loadData();
    }
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') {
      return;
    }
    ElMessage.error(err?.response?.data?.message ?? 'Failed to cancel order.');
  }
}

// ─── Row Action Menu ────────────────────────────────
type OrderAction = 'detail' | 'receipt' | 'pay' | 'refund' | 'cancel' | 'delete' | 'forceVoid';

function canPayOrder(row: EnrollmentOrderItem, participant: EnrollmentOrderParticipantItem | null = null) {
  const balanceAmount = participant?.balanceAmount ?? row.balanceAmount ?? 0;
  return row.orderStatus === 'CONFIRMED'
    && balanceAmount > 0
    && (participant != null || row.paymentStatus !== 'PAID');
}

function canRefundOrder(row: EnrollmentOrderItem, participant: EnrollmentOrderParticipantItem | null = null) {
  const receivedAmount = participant?.paidAmount ?? row.receivedAmount ?? 0;
  return row.paymentStatus !== 'REFUNDED' && receivedAmount > 0;
}

function canCancelOrder(row: EnrollmentOrderItem) {
  const orderReceived = row.participants?.length
    ? row.participants.reduce((sum, item) => sum + (item.paidAmount ?? 0), 0)
    : (row.receivedAmount ?? 0);
  return row.orderStatus === 'CONFIRMED' && orderReceived === 0;
}

// 全量级联软删：任意状态订单（CONFIRMED / CANCELLED / 已付款）均可删除。
function canDeleteOrder(row: EnrollmentOrderItem) {
  return true;
}

// 强制作废：业务上不需要，按需求隐藏该按钮（恒 false，前端不再渲染下拉项）。
function canForceVoidOrder(_row: EnrollmentOrderItem) {
  return false;
}

function handleOrderAction(command: OrderAction, row: EnrollmentOrderItem) {
  const participant = rowParticipant(row);
  const actions: Record<OrderAction, () => void> = {
    detail: () => openDetailDialog(row),
    receipt: () => openReceipt(row),
    pay: () => openPayDialog(row, participant),
    refund: () => openRefundDialog(row, participant),
    cancel: () => handleCancel(row),
    delete: () => openDeleteDialog(row),
    forceVoid: () => handleForceVoid(row),
  };
  actions[command]?.();
}

async function handleForceVoid(row: EnrollmentOrderItem) {
  try {
    const { value } = await ElMessageBox.prompt(
      'This permanently voids the order and removes it from lists and stats. Payment and lesson history are kept for audit. This cannot be undone. Enter a reason:',
      'Force void order',
      {
        confirmButtonText: 'Void order',
        cancelButtonText: 'Cancel',
        inputPlaceholder: 'Reason (e.g. legacy record before system update)',
        confirmButtonClass: 'el-button--danger',
      },
    );
    await forceVoidEnrollmentOrder(row.id, value?.trim() || undefined);
    ElMessage.success('Order voided.');
    if (row.classId) {
      await loadExpandedClassOrders(row.classId, expandedClassPagination[row.classId]?.page ?? 1);
      await loadClassEnrollments();
    } else {
      await loadData();
    }
  } catch (err: any) {
    if (err === 'cancel' || err === 'close') return;
    ElMessage.error(err?.response?.data?.message ?? 'Failed to void order.');
  }
}

function openReceipt(row: EnrollmentOrderItem) {
  router.push({ name: 'registration', query: { orderId: row.id } });
}

function orderSummaryFromDetail(detail: EnrollmentOrderDetailItem): EnrollmentOrderItem {
  return {
    id: detail.id,
    enrollmentNo: detail.enrollmentNo,
    studentId: detail.studentId,
    studentName: detail.studentName,
    classId: detail.classId,
    className: detail.className,
    courseProductId: detail.items?.[0]?.courseProductId,
    courseProductName: String(route.query.productName ?? '') || undefined,
    campusId: detail.campusId,
    campusNameSnapshot: detail.campusNameSnapshot,
    handledByName: detail.handledByName,
    signupDate: detail.signupDate,
    periodStartDate: detail.periodStartDate,
    periodEndDate: detail.periodEndDate,
    studentNameSnapshot: detail.studentNameSnapshot,
    studentClassSnapshot: detail.studentClassSnapshot,
    gradeSnapshot: detail.gradeSnapshot,
    schoolSnapshot: detail.schoolSnapshot,
    parentPhoneSnapshot: detail.parentPhoneSnapshot,
    parentPhone2Snapshot: detail.parentPhone2Snapshot,
    pickupRequiredSnapshot: detail.pickupRequiredSnapshot,
    pickupAddressSnapshot: detail.pickupAddressSnapshot,
    dropoffRequiredSnapshot: detail.dropoffRequiredSnapshot,
    dropoffAddressSnapshot: detail.dropoffAddressSnapshot,
    currencyCode: detail.currencyCode,
    tuitionSubtotalAmount: detail.tuitionSubtotalAmount,
    pickupFeeAmount: detail.pickupFeeAmount,
    dropoffFeeAmount: detail.dropoffFeeAmount,
    extraFeeAmount: detail.extraFeeAmount,
    originalAmount: detail.originalAmount,
    discountAmount: detail.discountAmount,
    receivedAmount: detail.receivedAmount,
    amountDue: detail.amountDue,
    balanceAmount: detail.balanceAmount,
    paymentStatus: detail.paymentStatus,
    orderStatus: detail.orderStatus,
    packageCount: detail.packages?.length,
    items: detail.items,
    participants: detail.participants,
    createdAt: detail.createdAt,
  };
}

async function openRequestedOrderAction() {
  const orderId = String(route.query.orderId ?? '').trim();
  const action = String(route.query.action ?? '');
  const participantIndexText = String(route.query.participantIndex ?? '').trim();
  const participantIndex = participantIndexText ? Number(participantIndexText) : null;
  const orderItemId = String(route.query.orderItemId ?? '').trim();
  if (!orderId || (action !== 'pay' && action !== 'refund')) {
    return;
  }

  try {
    const response = await fetchEnrollmentOrder(orderId);
    const order = orderSummaryFromDetail(response.data.data);
    const participant = Number.isInteger(participantIndex)
      ? response.data.data.participants?.find((item) => item.participantIndex === participantIndex) ?? null
      : null;
    if (participantIndexText && !participant) {
      ElMessage.warning('The selected student is not part of this enrollment order.');
      return;
    }
    if (order.classId) {
      activeTab.value = 'GROUP';
    }
    if (action === 'pay') {
      if (!canPayOrder(order, participant)) {
        ElMessage.warning('This order does not have an outstanding balance.');
      } else {
        await openPayDialog(order, participant, orderItemId || undefined);
      }
    } else if (!canRefundOrder(order, participant)) {
      ElMessage.warning('This order has no refundable payment.');
    } else {
      openRefundDialog(order, participant);
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to load the selected order.');
  } finally {
    await router.replace({
      query: {
        ...route.query,
        orderId: undefined,
        action: undefined,
        productName: undefined,
        orderItemId: undefined,
        participantIndex: undefined,
      },
    });
  }
}

// ─── Delete Dialog ──────────────────────────────────
const DELETE_COOLDOWN_SECONDS = 10;
const deleteDialogVisible = ref(false);
const deleteTarget = ref<EnrollmentOrderItem | null>(null);
const deleteConfirmRemaining = ref(DELETE_COOLDOWN_SECONDS);
const deletingOrder = ref(false);
let deleteCooldownTimer: number | undefined;

function openDeleteDialog(row: EnrollmentOrderItem) {
  deleteTarget.value = row;
  deleteDialogVisible.value = true;
  startDeleteCooldown();
}

function startDeleteCooldown() {
  clearDeleteCooldown();
  deleteConfirmRemaining.value = DELETE_COOLDOWN_SECONDS;
  deleteCooldownTimer = window.setInterval(() => {
    deleteConfirmRemaining.value = Math.max(0, deleteConfirmRemaining.value - 1);
    if (deleteConfirmRemaining.value === 0) {
      clearDeleteCooldown();
    }
  }, 1000);
}

function clearDeleteCooldown() {
  if (deleteCooldownTimer != null) {
    window.clearInterval(deleteCooldownTimer);
    deleteCooldownTimer = undefined;
  }
}

function resetDeleteDialog() {
  clearDeleteCooldown();
  deleteTarget.value = null;
  deleteConfirmRemaining.value = DELETE_COOLDOWN_SECONDS;
  deletingOrder.value = false;
}

async function confirmDeleteOrder() {
  if (!deleteTarget.value || deleteConfirmRemaining.value > 0) {
    return;
  }
  deletingOrder.value = true;
  const order = deleteTarget.value;
  try {
    await deleteEnrollmentOrder(order.id);
    ElMessage.success('Order deleted.');
    deleteDialogVisible.value = false;
    if (order.classId) {
      await loadExpandedClassOrders(order.classId, expandedClassPagination[order.classId]?.page ?? 1);
      await loadClassEnrollments();
    } else {
      await loadData();
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Failed to delete order.');
  } finally {
    deletingOrder.value = false;
  }
}

onBeforeUnmount(() => {
  clearDeleteCooldown();
});

// ─── Refund Dialog ──────────────────────────────────
const refundDialogVisible = ref(false);
const refunding = ref(false);
const refundFormRef = ref();
const refundOrder = ref<EnrollmentOrderItem | null>(null);
const refundParticipant = ref<EnrollmentOrderParticipantItem | null>(null);
const refundForm = reactive({
  refundAmount: 0,
  paymentMethod: 'CASH',
  handledByName: '',
  remark: '',
});
const refundFormRules = {
  refundAmount: [
    { required: true, message: 'Refund amount is required' },
    { type: 'number', min: 0.01, message: 'Refund amount must be greater than zero' },
  ],
  paymentMethod: [{ required: true, message: 'Refund method is required' }],
  handledByName: [{ required: true, message: 'Handler name is required' }],
};

const refundReceivedAmount = computed(() => Math.max(
  0,
  refundParticipant.value?.paidAmount ?? refundOrder.value?.receivedAmount ?? 0,
));
const refundStudentName = computed(() => refundParticipant.value?.studentName ?? refundOrder.value?.studentName ?? '—');

function openRefundDialog(row: EnrollmentOrderItem, participant: EnrollmentOrderParticipantItem | null = null) {
  refundOrder.value = row;
  refundParticipant.value = participant;
  refundFormRef.value?.resetFields();
  Object.assign(refundForm, {
    refundAmount: participant?.paidAmount ?? row.receivedAmount ?? 0,
    paymentMethod: 'CASH',
    handledByName: '',
    remark: '',
  });
  refundDialogVisible.value = true;
}

async function handleRefundSubmit() {
  const valid = await refundFormRef.value?.validate().catch(() => false);
  if (!valid || !refundOrder.value) return;
  refunding.value = true;
  try {
    await refundEnrollmentOrder(refundOrder.value.id, {
      refundAmount: refundForm.refundAmount,
      participantIndex: refundParticipant.value?.participantIndex,
      paymentMethod: refundForm.paymentMethod,
      handledByName: refundForm.handledByName,
      remark: refundForm.remark || undefined,
    });
    ElMessage.success('Refund completed.');
    refundDialogVisible.value = false;
    const order = refundOrder.value;
    if (order.classId) {
      await loadExpandedClassOrders(order.classId, expandedClassPagination[order.classId]?.page ?? 1);
    } else {
      await loadData();
    }
    openRegistrationReceipt(order.id);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Refund failed.');
  } finally {
    refunding.value = false;
  }
}

// ─── Payment Dialog ──────────────────────────────────
async function openPayDialog(row: EnrollmentOrderItem,
                             participant: EnrollmentOrderParticipantItem | null = null,
                             targetOrderItemId?: string | number) {
  let items = row.items ?? [];
  if (items.length === 0 && !participant) {
    try {
      items = (await fetchEnrollmentOrder(row.id)).data.data.items ?? [];
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message ?? 'Failed to load course balances.');
      return;
    }
  }
  payOrder.value = row;
  payParticipant.value = participant;
  payCourseItems.value = items;
  const targetCourse = targetOrderItemId == null
    ? null
    : items.find((item) => String(item.id) === String(targetOrderItemId)) ?? null;
  if (targetOrderItemId != null && !targetCourse) {
    ElMessage.warning('The selected course is not part of this enrollment order.');
    return;
  }
  const amount = targetCourse ? Math.min(payAmountDue.value, courseBalance(targetCourse)) : payAmountDue.value;
  const allocations: Record<string, number> = {};
  if (!participant && items.length === 1) {
    allocations[String(items[0].id)] = amount;
  } else if (!participant && targetCourse && amount > 0) {
    allocations[String(targetCourse.id)] = amount;
  }
  payForm.splits = [createPaySplit(amount, allocations)];
  payDialogVisible.value = true;
}

function courseBalance(item: EnrollmentOrderLineItem) {
  return Math.max(0, Number(item.balanceAmount ?? (Number(item.amountDue ?? 0) - Number(item.paidAmount ?? 0))));
}

function courseAllocated(orderItemId: EntityId) {
  const key = String(orderItemId);
  return payForm.splits.reduce((total, split) => total + Number(split.allocations[key] ?? 0), 0);
}

function splitAllocationTotal(split: PaySplit) {
  return Object.values(split.allocations).reduce((total, amount) => total + Number(amount || 0), 0);
}

function splitAllocationRemaining(split: PaySplit) {
  return Number((split.amount - splitAllocationTotal(split)).toFixed(2));
}

function setSplitCourseAllocation(split: PaySplit, orderItemId: EntityId, value: number | undefined) {
  split.allocations[String(orderItemId)] = Number(Number(value ?? 0).toFixed(2));
}

function paymentMethodName(methodCode: string) {
  return paymentMethods.value.find((method) => method.methodCode === methodCode)?.methodName ?? methodCode;
}

async function handlePaySubmit() {
  const rows = payForm.splits
    .map((row) => ({
      ...row,
      handledByName: row.handledByName.trim(),
      remark: row.remark.trim(),
    }))
    .filter((row) => row.amount > 0);
  if (rows.length === 0) {
    ElMessage.warning('Please add at least one payment split with an amount.');
    return;
  }
  if (rows.some((row) => !row.paymentMethod || !row.paidAt || !row.handledByName)) {
    ElMessage.warning('Payment method, date, and handler are required for every payment.');
    return;
  }
  if (paySplitRemaining.value < 0) {
    ElMessage.warning('Payment splits cannot exceed the current balance.');
    return;
  }
  if (!payOrder.value) return;
  if (!payParticipant.value && payCourseItems.value.length > 1) {
    if (rows.some((row) => splitAllocationRemaining(row) !== 0)) {
      ElMessage.warning('Assign the full amount of each payment to one or more courses.');
      return;
    }
    const allocatedByCourse = new Map<string, number>();
    rows.forEach((row) => Object.entries(row.allocations).forEach(([itemId, amount]) => {
      allocatedByCourse.set(itemId, (allocatedByCourse.get(itemId) ?? 0) + Number(amount || 0));
    }));
    if (payCourseItems.value.some((item) => (allocatedByCourse.get(String(item.id)) ?? 0) > courseBalance(item) + 0.001)) {
      ElMessage.warning('A course allocation cannot exceed that course’s outstanding balance.');
      return;
    }
  }

  paying.value = true;
  try {
    await createPaymentRecord({
      enrollmentOrderId: payOrder.value.id,
      splits: rows.map((row) => ({
        paymentMethod: row.paymentMethod,
        amount: row.amount,
        paidAt: `${row.paidAt}T00:00:00`,
        participantIndex: payParticipant.value?.participantIndex,
        allocations: !payParticipant.value
          ? Object.entries(row.allocations)
            .filter(([, amount]) => amount > 0)
            .map(([orderItemId, amount]) => ({ enrollmentOrderItemId: orderItemId, amount }))
          : undefined,
        handledByName: row.handledByName,
        remark: row.remark || undefined,
      })),
    });

    ElMessage.success('Payment recorded successfully.');
    payDialogVisible.value = false;
    const orderId = payOrder.value.id;
    await refreshAfterPayment(payOrder.value);
    openRegistrationReceipt(orderId);
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message ?? 'Payment failed.');
  } finally {
    paying.value = false;
  }
}

async function refreshAfterPayment(order: EnrollmentOrderItem) {
  if (order.classId) {
    await loadExpandedClassOrders(order.classId, expandedClassPagination[order.classId]?.page ?? 1);
  } else {
    await loadData();
  }
  if (detailData.value?.id === order.id) {
    const res = await fetchEnrollmentOrder(order.id);
    detailData.value = res.data.data;
  }
}

// 支付或退款成功后复用 View Receipt 的注册页；页面会重新读取订单，确保收据和付款状态一致。
function openRegistrationReceipt(orderId: EntityId) {
  void router.push({ name: 'registration', query: { orderId: String(orderId) } });
}

function openReceiptWindow(html: string) {
  const w = window.open('', '_blank');
  if (w) {
    w.document.write(html);
    w.document.close();
  }
}

function showPaymentStatement(order: EnrollmentOrderDetailItem) {
  const methods = [...new Set(order.payments.map(p => getPaymentMethodName(p.paymentMethod)))].join(', ');
  const totalPaid = order.payments.reduce((sum, p) => sum + (p.amount ?? 0), 0);
  const infoRows: [string, string][] = [
    ['Order No.', order.enrollmentNo],
    ['Student', order.studentName],
    ['Payment Methods', methods],
    ['Total Paid', `$${totalPaid.toFixed(2)}`],
  ];
  const tableRows: [string, string, boolean?][] = order.payments.map(p => [
    `${paymentTypeLabel(p.paymentType)} · ${getPaymentMethodName(p.paymentMethod)}`,
    `$${(p.amount ?? 0).toFixed(2)}`,
  ]);
  tableRows.push(['Total', `$${totalPaid.toFixed(2)}`, true]);
  const html = receiptHtml({
    title: 'Payment Statement',
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
        <div class="tagline">JIN BEST TUTORING — Building confidence, skills, and stronger academic results</div>
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
      <p>Partial refunds create a separate refund record and update the order balance without changing active courses. Refunding the full currently received amount cancels the enrollment, future lessons, and class eligibility.</p>
      <h4>Course Suspension Policy</h4>
      <p>Mid-course suspension and tuition credit require staff review.</p>
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

// ─── Reload class enrollments when entering GROUP tab ──
watch(activeTab, () => {
  if (activeTab.value === 'GROUP') {
    loadClassEnrollments();
  }
});

// ─── Init ───────────────────────────────────────────
async function initializePage() {
  await Promise.all([
    loadData(),
    loadStudents(),
    loadClassEnrollments(),
    loadDiscountPresets(),
    loadActivePaymentMethods(),
  ]);
  await openRequestedOrderAction();
}

void initializePage();
</script>

<style scoped>
.course-product-panel {
  border: 1px solid #e8edf2;
  border-radius: 8px;
  background: #fff;
  padding: 20px 24px 24px;
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

.panel-header__hint {
  max-width: 680px;
  margin: 3px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.45;
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

.shared-lesson-tag {
  align-self: flex-start;
  margin-top: 2px;
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

.payment-records-hint {
  margin: 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.detail-section__hint {
  margin: 0 0 10px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
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
.payment-summary__scope {
  margin-top: 10px;
  padding: 8px 10px;
  color: #606266;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
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

.refund-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.delete-warning {
  padding: 12px 14px;
  color: #606266;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 6px;
  line-height: 1.6;
}
.delete-warning p {
  margin: 0 0 8px;
}
.delete-warning p:last-child {
  margin-bottom: 0;
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
  display: grid;
  grid-template-columns: minmax(110px, 0.8fr) 110px 140px minmax(110px, 0.8fr) minmax(130px, 1fr) auto;
  gap: 8px;
  align-items: center;
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
.payment-allocation-preview {
  margin-top: 4px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}
.payment-allocation-preview__title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
  font-weight: 600;
}
.course-payment-allocation {
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.course-payment-allocation > strong {
  display: block;
  margin-bottom: 6px;
  color: var(--el-text-color-primary);
  font-size: 12px;
}
.course-payment-allocation__total {
  padding-top: 5px;
  color: var(--el-color-success);
  font-size: 12px;
  font-weight: 600;
  text-align: right;
}

/* Group Class hierarchy */
.group-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 4px 0 8px;
  padding: 8px 12px;
  border: 1px solid #e7edf4;
  border-radius: 8px;
  background: #f8fafc;
}
.group-toolbar__search {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.group-toolbar__label {
  color: #1f3553;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
.group-toolbar__input {
  width: 280px;
}
.group-toolbar__summary {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #6b7b90;
  font-size: 11px;
  white-space: nowrap;
}
.group-toolbar__summary-item strong {
  color: #213b5b;
  font-size: 13px;
}
.group-toolbar__summary-divider {
  width: 1px;
  height: 14px;
  background: #d6e0ea;
}
.group-cell {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  min-height: 28px;
  cursor: pointer;
  user-select: none;
}
.group-cell--grade {
  padding-left: 10px;
}
.group-cell--class {
  padding-left: 16px;
  cursor: pointer;
}
/* 使用自定义展开控件，内置 expand 列只负责承载展开内容。 */
.group-table :deep(.el-table__expand-icon) {
  display: none;
}
.group-caret {
  flex: none;
  width: 12px;
  color: #71839a;
  font-size: 12px;
  line-height: 1;
}
.group-caret--button {
  display: inline-flex;
  height: 20px;
  align-items: center;
  justify-content: center;
  border: 0;
  padding: 0;
  cursor: pointer;
  font: inherit;
  background: transparent;
}
.group-caret--button:hover,
.group-caret--button:focus-visible {
  color: #2f8df4;
  outline: none;
}
.group-cell strong {
  min-width: 0;
  overflow: hidden;
  color: #263b57;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.group-table :deep(tr.group-row--product .group-cell strong) {
  color: #203957;
  font-size: 14px;
  font-weight: 700;
}
.group-table :deep(tr.group-row--grade .group-cell strong) {
  color: #3b506a;
  font-size: 13px;
  font-weight: 600;
}
.group-meta {
  margin-left: auto;
  color: #7b8a9e;
  font-size: 11px;
  font-weight: 400;
  white-space: nowrap;
}
.group-meta__dot {
  margin: 0 4px;
  color: #b0bcc9;
}
.group-class-marker {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
  background: #5c9bd5;
  box-shadow: 0 0 0 3px #e9f2fb;
}
.group-value {
  display: block;
  overflow: hidden;
  color: #354961;
  font-size: 12px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.group-value--empty {
  color: #7d8da0;
  font-weight: 400;
}
.group-schedule {
  display: block;
  max-width: 230px;
  overflow: hidden;
  color: #536b86;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.enrolled-count {
  display: inline-flex;
  min-width: 28px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 1px solid #cfe4d7;
  border-radius: 12px;
  padding: 0 8px;
  color: #237247;
  background: #f0f9f3;
  font-size: 12px;
  font-weight: 700;
}
.group-table :deep(tr.group-row--product > td) {
  height: 40px;
  background: #f1f6fb;
  border-top: 1px solid #d8e3ee;
  border-bottom: 1px solid #dce6ef;
}
.group-table :deep(tr.group-row--product > td:first-child) {
  border-left: 2px solid #5b9bd5;
}
.group-table :deep(tr.group-row--grade > td) {
  height: 32px;
  background: #fbfcfe;
  border-bottom: 1px solid #e9eef4;
}
.group-table :deep(tr.group-row--grade > td:first-child) {
  border-left: 2px solid #c9d5e2;
}
.group-table :deep(tr:not(.group-row--product):not(.group-row--grade) > td) {
  height: 38px;
  background: #fff;
  cursor: pointer;
}
.group-table :deep(tr:not(.group-row--product):not(.group-row--grade):hover > td) {
  background: #f7fbff;
}
.group-table :deep(.el-table__header-wrapper th) {
  height: 32px;
  color: #60738a;
  background: #f5f8fb;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .01em;
  text-transform: none;
}
.group-table :deep(.el-table__body td) {
  padding-top: 0;
  padding-bottom: 0;
}
.group-table :deep(.el-table__body-wrapper td) {
  border-bottom-color: #e8eef4;
}
.group-table :deep(.el-table__expand-column) {
  width: 24px !important;
  min-width: 24px !important;
  padding: 0 !important;
}
.group-table :deep(.el-table__expand-column .cell) {
  padding: 0;
}
.expand-orders-container {
  margin: 0 12px 8px 24px;
  padding: 8px;
  border: 1px solid #dce7f1;
  border-radius: 8px;
  background: #f8fbfe;
}
.group-table :deep(.el-table__expanded-cell) {
  padding: 0 !important;
  cursor: default;
  background: #f1f6fb;
  border-bottom: 1px solid #dce7f1;
}
.group-table :deep(.el-table__expanded-cell .nested-table) {
  border-color: #dce7f1;
  background: #fff;
}
.group-table :deep(.el-table__expanded-cell .nested-table th) {
  color: #71839a;
  background: #f8fafc;
  font-size: 11px;
}
.group-table :deep(.nested-table .el-table__header-wrapper th) {
  height: 38px;
  padding: 4px 8px;
}
.group-table :deep(.nested-table .el-table__body-wrapper td) {
  padding: 8px;
}
.group-table :deep(.nested-table .el-table__empty-block) {
  min-height: 52px;
  height: 52px;
}
.group-table :deep(.nested-table .el-table__empty-text) {
  color: #8a98a8;
  font-size: 12px;
}
.group-table :deep(.nested-table .table-header-filter) {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}
.group-table :deep(.nested-table .table-header-filter > span) {
  flex: none;
  font-size: 11px;
}
.group-table :deep(.nested-table .table-header-filter .el-input),
.group-table :deep(.nested-table .table-header-filter .el-select) {
  min-width: 0;
}
@media (max-width: 860px) {
  .course-product-panel {
    padding: 16px;
  }

  .group-toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .group-toolbar__input {
    width: min(100%, 360px);
  }

  .group-toolbar__summary {
    justify-content: flex-end;
  }
}
</style>
