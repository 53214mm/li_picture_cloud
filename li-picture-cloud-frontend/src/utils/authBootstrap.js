export function isTerminalAuthFailure(error) {
  return [401, 403].includes(Number(error?.status))
}

export function createSingleFlightLoader(loader) {
  let inFlight = null
  return function load() {
    // 路由守卫和 App 初始化可同时触发，复用一个 Promise 避免并发请求互相覆盖状态。
    if (inFlight) return inFlight
    inFlight = Promise.resolve()
      .then(loader)
      .finally(() => { inFlight = null })
    return inFlight
  }
}

export function createAuthSessionGate() {
  let generation = 0
  return {
    capture: () => generation,
    // 登录/退出后使旧请求失效，防止它晚到后把新会话覆盖成旧用户或 null。
    invalidate: () => { generation += 1 },
    isCurrent: captured => captured === generation
  }
}
