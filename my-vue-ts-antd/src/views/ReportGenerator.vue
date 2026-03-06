<template>
  <div class="report-generate">
    <a-card title="生成报表" :bordered="false" class="card">
      <a-form
          :model="form"
          :label-col="{ style: { width: '120px' } }"
          :wrapper-col="{ style: { width: '500px' } }"
      >
        <a-form-item label="选择模板">
          <a-select
              v-model:value="form.templateId"
              placeholder="请选择模板"
              style="width: 100%"
              :options="templateOptions"
          />
        </a-form-item>

        <a-form-item label="报表数据">
          <a-textarea
              v-model:value="dataJson"
              :rows="10"
              placeholder='请输入 JSON 格式数据，例如：{"title": "销售报表", "amount": 10000}'
          />
        </a-form-item>

        <a-form-item :wrapper-col="{ offset: 12 }">
          <a-button type="primary" @click="handleGenerate" :loading="loading">
            生成报表
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card v-if="reportUrl" title="报表预览" :bordered="false" class="card mt-4">
      <ReportPreview :url="reportUrl" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { templateApi, reportApi, type Template } from '../api/report'
import ReportPreview from '../components/ReportPreview.vue'

const templates = ref<Template[]>([])
const form = ref({
  templateId: 0,
  data: {}
})
const dataJson = ref('')
const loading = ref(false)
const reportUrl = ref('')

const templateOptions = computed(() =>
    templates.value.map(tpl => ({ label: tpl.name, value: tpl.id }))
)

const formData = computed(() => {
  try {
    return JSON.parse(dataJson.value)
  } catch {
    return {}
  }
})

onMounted(async () => {
  try {
    templates.value = await templateApi.list()
  } catch {
    message.error('模板列表加载失败')
  }
})

const handleGenerate = async () => {
  if (!form.value.templateId) {
    message.warning('请选择模板')
    return
  }
  loading.value = true
  try {
    reportUrl.value = await reportApi.generate({
      templateId: form.value.templateId,
      data: formData.value
    })
    message.success('报表生成成功')
  } catch {
    message.error('报表生成失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.report-generate {
  padding: 24px;
  background-color: #f0f2f5;
  min-height: 100vh;
}
.card {
  max-width: 800px;
  margin: 0 auto;
}
.mt-4 {
  margin-top: 24px;
}
</style>