import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

interface ApiEnvelope<T> { code: string; message: string; data: T; traceId: string }

export const http = axios.create({
  baseURL: '/sp-ai-portal/api/v1',
  timeout: 20_000,
  withCredentials: true,
})

let accessToken = sessionStorage.getItem('portal_access_token') || ''
let refreshPromise: Promise<string> | null = null

export function setAccessToken(token: string) {
  accessToken = token
  token ? sessionStorage.setItem('portal_access_token', token) : sessionStorage.removeItem('portal_access_token')
}

http.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

http.interceptors.response.use(
  response => response,
  async (error: AxiosError) => {
    const request = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    if (error.response?.status !== 401 || !request || request._retried || request.url?.includes('/auth/')) throw error
    request._retried = true
    refreshPromise ||= http.post<ApiEnvelope<{ accessToken: string }>>('/auth/refresh').then(({ data }) => {
      setAccessToken(data.data.accessToken)
      return data.data.accessToken
    }).finally(() => { refreshPromise = null })
    request.headers.Authorization = `Bearer ${await refreshPromise}`
    return http(request)
  },
)

export async function apiGet<T>(url: string, params?: object) {
  return (await http.get<ApiEnvelope<T>>(url, { params })).data.data
}

export async function apiPost<T>(url: string, data?: object) {
  return (await http.post<ApiEnvelope<T>>(url, data)).data.data
}

export async function apiPut<T>(url: string, data?: object) {
  return (await http.put<ApiEnvelope<T>>(url, data)).data.data
}
