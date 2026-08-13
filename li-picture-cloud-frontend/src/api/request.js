import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true
})

function toApiError(message, status, code) {
  const error = new Error(message || '请求失败')
  error.status = status
  error.code = code
  return error
}

// 响应拦截：统一提取 data
request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body.code === 0) return body.data
    return Promise.reject(toApiError(body.message, res.status, body.code))
  },
  (err) => {
    const msg = err.response?.data?.message || err.message || '网络异常'
    return Promise.reject(toApiError(msg, err.response?.status, err.response?.data?.code))
  }
)

export default request
