// Display helpers for the one-step model connection. No IO; the API key never
// passes through here.

/** Status card copy for the current language model. */
export function describeQuickConnection(view) {
  if (view?.status === 'CONNECTED') {
    return {
      tone: 'connected',
      title: `${view.providerLabel} · ${view.modelCode}`,
      detail: '伙伴聊天等语言任务会使用这个模型，费用由你的厂商账户承担。'
    }
  }
  if (view?.status === 'BROKEN') {
    return {
      tone: 'broken',
      title: '之前连接的模型不可用了',
      detail: '请重新连接一个模型，或改用平台默认。'
    }
  }
  return {
    tone: 'platform',
    title: '伙伴聊天正在使用平台默认',
    detail: '可在下方选用已保存的连接，或添加新模型用于聊天。'
  }
}

/** Case-insensitive model filter; keeps the catalog order. */
export function filterModels(models, query) {
  const list = Array.isArray(models) ? models : []
  const needle = typeof query === 'string' ? query.trim().toLowerCase() : ''
  return needle ? list.filter(model => model.toLowerCase().includes(needle)) : list
}

/** Prefer the backend message; never echo request payloads. */
export function quickConnectError(failure, fallback) {
  return failure?.response?.data?.message || failure?.message || fallback
}
