export function beginFeedAttempt(pictureId, currentAttempt, keyFactory = createIdempotencyKey) {
  const normalized = String(pictureId)
  // 同一张图的可恢复重试必须复用 key；切换图片才开启新的业务请求。
  if (currentAttempt?.pictureId === normalized) return currentAttempt
  return { pictureId: normalized, idempotencyKey: keyFactory() }
}

export function createIdempotencyKey() {
  return (globalThis.crypto?.randomUUID?.()
    || `feed-${Date.now()}-${Math.random().toString(36).slice(2, 14)}`).toLowerCase()
}

export function applyFeedResult(home, result) {
  const currentRevision = BigInt(String(home.companion?.revision ?? -1))
  const resultRevision = BigInt(String(result.companion.revision))
  // 后端旧 key 的 replay 会返回当时的快照；合并时间线但不能让 UI 的当前伙伴倒退。
  const mergedGrowth = [
    result.growth,
    ...(home.recentGrowth || []).filter(item => String(item.id) !== String(result.growth.id))
  ].sort((left, right) => {
    const byTime = Date.parse(right.createdTime) - Date.parse(left.createdTime)
    if (byTime !== 0) return byTime
    const leftId = BigInt(String(left.id))
    const rightId = BigInt(String(right.id))
    return leftId === rightId ? 0 : leftId > rightId ? -1 : 1
  })
  return {
    ...home,
    companion: resultRevision >= currentRevision ? result.companion : home.companion,
    recentGrowth: mergedGrowth.slice(0, 20)
  }
}

export function selectOldestPrivateSpace(spaces = [], userId) {
  // 只读取当前用户的私有空间，避免伙伴页无意浏览团队空间或其他成员的图片。
  return spaces
    .filter(space => space.spaceType === 0 && String(space.userId) === String(userId))
    .toSorted((left, right) => {
      const byTime = Date.parse(left.createTime) - Date.parse(right.createTime)
      if (byTime !== 0) return byTime
      const leftId = BigInt(String(left.id))
      const rightId = BigInt(String(right.id))
      return leftId === rightId ? 0 : leftId < rightId ? -1 : 1
    })[0] || null
}

export function buildCompanionPictureQuery(spaceId) {
  return {
    current: 1,
    pageSize: 12,
    spaceId: String(spaceId),
    sortField: 'createTime',
    sortOrder: 'descend'
  }
}

export function traitPosition(value) {
  return Math.min(100, Math.max(0, (Number(value) + 100) / 2))
}

export function describeTrait(value, axis) {
  const amount = Number(value)
  if (Math.abs(amount) < 10) return '保持中性'
  const direction = amount > 0 ? axis.positive : axis.negative
  return `${Math.abs(amount) >= 60 ? '明显' : '略'}偏${direction}`
}

export function shouldRetrySameFeedKey(error) {
  // 参数/登录/权限/不存在是确定失败；其他失败可能发生在服务端已提交之后，应安全重试同一 key。
  return ![400, 401, 403, 404].includes(Number(error?.status))
}

export function formatSignedDelta(value) {
  const amount = Number(value)
  return `${amount > 0 ? '+' : ''}${amount.toFixed(2)}`
}
