import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true
})

// 响应拦截：统一提取 data
request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body.code === 0) return body.data
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (err) => {
    const msg = err.response?.data?.message || err.message || '网络异常'
    return Promise.reject(new Error(msg))
  }
)

export default request
