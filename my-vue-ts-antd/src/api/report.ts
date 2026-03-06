import axios from 'axios'

const request = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 30000
})

// 响应拦截器（简化错误处理）
request.interceptors.response.use(
    res => res.data,
    err => {
        console.error('请求失败:', err)
        throw err
    }
)

export interface Template {
    id: number
    templateCode: string
    name: string
    type: 'word' | 'excel' | 'ppt'
    fileUrl: string
}

export interface ReportGenerateReq {
    templateId: number
    data: Record<string, any>
}

export const templateApi = {
    list: () => request.get<Template[]>('/template/list'),
    upload: (file: File, name: string, type: string) => {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('name', name)
        formData.append('type', type)
        return request.post('/template/upload', formData)
    }
}

export const reportApi = {
    generate: (req: ReportGenerateReq) => request.post<string>('/report/generate', req)
}