<template>
  <div class="page-stack">
    <WorkspaceHero :hero="schema.hero" />
    <WorkspaceMetricGrid :metrics="schema.metrics" />

    <section class="form-layout">
      <div class="form-layout__main">
        <article v-for="section in schema.sections" :key="section.title" class="form-card">
          <div class="form-card__header">
            <h3>{{ section.title }}</h3>
            <p>{{ section.description }}</p>
          </div>

          <el-form label-position="top" :model="formModel">
            <el-row :gutter="16">
              <el-col
                v-for="field in section.fields"
                :key="field.key"
                :xs="24"
                :md="field.span ?? 12"
              >
                <el-form-item :label="field.label">
                  <el-input
                    v-if="field.type === 'input' || field.type === 'number'"
                    v-model="formModel[field.key]"
                    :placeholder="field.placeholder"
                  />

                  <el-select
                    v-else-if="field.type === 'select'"
                    v-model="formModel[field.key]"
                    :placeholder="field.placeholder"
                  >
                    <el-option
                      v-for="option in field.options"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>

                  <el-date-picker
                    v-else-if="field.type === 'date'"
                    v-model="formModel[field.key]"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :placeholder="field.placeholder"
                  />

                  <el-input
                    v-else
                    v-model="formModel[field.key]"
                    :rows="3"
                    type="textarea"
                    :placeholder="field.placeholder"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </article>
      </div>

      <aside class="form-layout__aside">
        <WorkspaceInfoBlocks :blocks="schema.summaryBlocks" />

        <article class="form-card">
          <div class="form-card__header">
            <h3>Build notes</h3>
            <p>Once the layout is approved, this area can connect submission flows, validation rules, and receipt generation.</p>
          </div>
          <ul class="step-list">
            <li v-for="step in schema.nextSteps" :key="step">{{ step }}</li>
          </ul>
        </article>
      </aside>
    </section>

    <section class="form-footer">
      <el-button
        v-for="action in schema.footerActions"
        :key="action.label"
        size="large"
        :type="action.buttonType"
        :plain="action.plain"
        @click="handleActionClick(action)"
      >
        {{ action.label }}
      </el-button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { reactive } from 'vue';

import WorkspaceHero from './WorkspaceHero.vue';
import WorkspaceInfoBlocks from './WorkspaceInfoBlocks.vue';
import WorkspaceMetricGrid from './WorkspaceMetricGrid.vue';
import type { FormWorkspaceSchema, WorkspaceAction, WorkspaceFormSection } from '@/modules/shared/workspace-types';

type FormModel = Record<string, string>;

const props = defineProps<{
  schema: FormWorkspaceSchema;
}>();

const formModel = reactive<FormModel>(buildFormModel(props.schema.sections));

// Build the initial form model from the schema so mock pages can render immediately.
function buildFormModel(sections: WorkspaceFormSection[]): FormModel {
  return sections.reduce<FormModel>((result, section) => {
    section.fields.forEach((field) => {
      result[field.key] = field.value ?? '';
    });
    return result;
  }, {});
}

// Footer actions stay in mock mode until the real submit flow is connected.
function handleActionClick(action: WorkspaceAction) {
  ElMessage.success(`${action.label} is wired to the page shell and can be connected to the live workflow next.`);
}
</script>

<style scoped>
.form-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 0.9fr);
  gap: 18px;
}

.form-layout__main,
.form-layout__aside {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-card {
  border: 1px solid rgba(217, 226, 236, 0.92);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.97);
  padding: 24px;
  box-shadow: 0 18px 40px rgba(15, 23, 36, 0.08);
}

.form-card__header h3 {
  margin: 0;
  color: #102a43;
  font-size: 18px;
}

.form-card__header p {
  margin: 8px 0 18px;
  color: #7b8794;
  font-size: 13px;
  line-height: 1.7;
}

.step-list {
  margin: 0;
  padding-left: 18px;
  color: #486581;
}

.step-list li + li {
  margin-top: 12px;
}

.form-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

@media (max-width: 1280px) {
  .form-layout {
    grid-template-columns: 1fr;
  }
}
</style>
