<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-card__brand">
        <div class="login-card__logo">J</div>
        <h1>{{ APP_SYSTEM_NAME }}</h1>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submitLogin">
        <el-form-item label="Account" prop="username">
          <el-input v-model="form.username" placeholder="admin / staff" @keyup.enter="submitLogin" />
        </el-form-item>
        <el-form-item label="Password" prop="password">
          <el-input v-model="form.password" placeholder="Enter password" show-password @keyup.enter="submitLogin" />
        </el-form-item>
        <el-button type="primary" class="login-card__submit" :loading="authStore.loading" @click="submitLogin">
          Sign in
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { AxiosError } from 'axios';
import { useRoute, useRouter } from 'vue-router';

import { APP_SYSTEM_NAME } from '@/config/app-config';
import { useAuthStore } from '@/stores/auth';
import { isTeacherUser, type LoginPayload } from '@/types/auth';
import type { ApiResponse } from '@/types/http';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const formRef = ref<FormInstance>();

const form = reactive<LoginPayload>({
  username: 'admin',
  password: 'Admin@123456',
});

const rules: FormRules<LoginPayload> = {
  username: [
    {
      required: true,
      message: 'Please enter your username.',
      trigger: 'blur',
    },
  ],
  password: [
    {
      required: true,
      message: 'Please enter your password.',
      trigger: 'blur',
    },
  ],
};

async function submitLogin() {
  try {
    await formRef.value?.validate();
  } catch {
    return; // form validation failed, do not submit
  }
  try {
    await authStore.signIn(form);
    ElMessage.success('Signed in successfully.');
    const fallbackPath = isTeacherUser(authStore.currentUser) ? '/schedule/teacher' : '/dashboard';
    const redirectPath = typeof route.query.redirect === 'string' ? route.query.redirect : fallbackPath;
    await router.push(redirectPath);
  } catch (error) {
    const apiError = error as AxiosError<ApiResponse<null>>;
    ElMessage.error(apiError.response?.data?.message ?? 'Unable to sign in right now.');
  }
}
</script>
