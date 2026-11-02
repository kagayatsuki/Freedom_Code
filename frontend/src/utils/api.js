class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
    this.name = 'ApiError'
  }
}

async function request(url, options = {}) {
  const res = await fetch(url, options)

  if (!res.ok) {
    let message = '请求失败'
    try {
      const data = await res.json()
      message = data.message || data.msg || message
    } catch {
      // response body is not JSON
    }
    throw new ApiError(res.status, message)
  }

  return res.json()
}

export { request, ApiError }
