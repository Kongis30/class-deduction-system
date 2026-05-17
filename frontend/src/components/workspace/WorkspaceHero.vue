<template>
  <section class="workspace-hero">
    <div class="workspace-hero__content">
      <span class="workspace-hero__eyebrow">{{ hero.eyebrow }}</span>
      <h2>{{ hero.title }}</h2>
      <p>{{ hero.description }}</p>
    </div>
    <div class="workspace-hero__badge">{{ hero.badge }}</div>
  </section>
</template>

<script setup lang="ts">
import type { WorkspaceHeroData } from '@/modules/shared/workspace-types';

defineProps<{
  hero: WorkspaceHeroData;
}>();
</script>

<style scoped>
.workspace-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  border: 1px solid rgba(217, 226, 236, 0.92);
  border-radius: 28px;
  background:
    radial-gradient(circle at right top, rgba(24, 181, 138, 0.1), transparent 26%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.98) 100%);
  padding: 28px;
  box-shadow: 0 18px 40px rgba(15, 23, 36, 0.08);
}

.workspace-hero__content {
  max-width: 760px;
}

.workspace-hero__eyebrow {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(24, 181, 138, 0.12);
  color: #0d8f6c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.workspace-hero h2 {
  margin: 10px 0 8px;
  color: #102a43;
  font-size: 28px;
}

.workspace-hero p {
  margin: 0;
  color: #486581;
  line-height: 1.75;
}

.workspace-hero__badge {
  flex-shrink: 0;
  border-radius: 999px;
  background: #18b58a;
  padding: 9px 14px;
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 920px) {
  .workspace-hero {
    flex-direction: column;
  }
}
</style>
