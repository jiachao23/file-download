<template>
  <div class="report-preview">
    <div v-if="isWord" class="word-preview">
      <VueOfficeDocx :src="docxUrl" />
    </div>
    <div v-else-if="isExcel" class="excel-preview">
      <iframe :src="excelUrl" width="100%" height="600px" />
    </div>
    <div v-else class="download-tip">
      <a-button type="primary" @click="handleDownload">
        下载报表
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VueOfficeDocx from '@vue-office/docx'
import '@vue-office/docx/lib/index.css'

interface Props {
  url: string
}

const props = defineProps<Props>()

const isWord = computed(() => props.url.endsWith('.docx'))
const isExcel = computed(() => props.url.endsWith('.xlsx'))
const docxUrl = computed(() => props.url)
const excelUrl = computed(() => props.url)

const handleDownload = () => {
  window.open(props.url)
}
</script>

<style scoped>
.report-preview {
  min-height: 600px;
}
.download-tip {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}
</style>