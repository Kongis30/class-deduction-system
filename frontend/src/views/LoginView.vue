<template>
  <div class="login-page">
    <div class="login-page__stripe"></div>
    <div class="login-page__deco-1"></div>
    <div class="login-page__deco-2"></div>
    <div class="login-page__deco-3"></div>
    <div class="login-page__deco-4"></div>
    <div class="login-page__dots"></div>
    <div class="login-card">
      <div class="login-card__brand">
        <img :src="logoUrl" alt="JIN BEST TUTORING" class="login-card__logo-img" />
        <h1>JIN BEST TUTORING</h1>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submitLogin">
        <el-form-item label="Account" prop="username">
          <el-input
            v-model="form.username"
            placeholder="Enter your username"
            autocomplete="username"
            autocapitalize="none"
            autocorrect="off"
            spellcheck="false"
            @keyup.enter="submitLogin"
          />
        </el-form-item>
        <el-form-item label="Password" prop="password">
          <el-input
            v-model="form.password"
            placeholder="Enter password"
            autocomplete="current-password"
            autocapitalize="none"
            autocorrect="off"
            show-password
            @keyup.enter="submitLogin"
          />
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

import { useAuthStore } from '@/stores/auth';
import { getDefaultLandingPath, type LoginPayload } from '@/types/auth';
import type { ApiResponse } from '@/types/http';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const formRef = ref<FormInstance>();
const logoUrl = `${import.meta.env.BASE_URL}logo.jpg`;

const form = reactive<LoginPayload>({
  username: '',
  password: '',
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
    const fallbackPath = getDefaultLandingPath(authStore.currentUser);
    const redirectPath = typeof route.query.redirect === 'string' ? route.query.redirect : fallbackPath;
    await router.push(redirectPath);
  } catch (error) {
    const apiError = error as AxiosError<ApiResponse<null>>;
    const message = apiError.response?.data?.message
      ?? (apiError.response
        ? `Sign-in request failed (HTTP ${apiError.response.status}).`
        : apiError.request
          ? 'Cannot reach the sign-in service. Check this device’s Wi-Fi connection and try again.'
          : 'Unable to sign in right now.');
    ElMessage.error(message);
  }
}
</script>
