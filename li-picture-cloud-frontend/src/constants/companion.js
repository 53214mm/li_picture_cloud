export const LIFE_STAGE = Object.freeze({
  LIGHT: { label: '光点', description: '刚刚被唤醒，正在形成自己的轮廓。' },
  SEEDLING: { label: '幼体', description: '已经积累了一些稳定倾向与技能。' },
  COMPANION: { label: '伙伴', description: '成长为能够长期陪伴与共同创作的形态。' }
})

export const TRAIT_AXES = Object.freeze([
  { key: 'curiosity', negative: '谨慎', positive: '好奇' },
  { key: 'enthusiasm', negative: '克制', positive: '热情' },
  { key: 'playfulness', negative: '沉稳', positive: '淘气' },
  { key: 'empathy', negative: '理性', positive: '共情' },
  { key: 'creativity', negative: '秩序', positive: '创造' }
])

export const SKILL_LABEL = Object.freeze({
  IMAGE_OBSERVATION: '图片观察',
  STORY_CREATION: '故事创作',
  EMOJI_CREATION: '表情制作',
  IMAGE_FUSION: '图片融合',
  GALLERY_SEARCH: '图库搜索'
})

export const MOOD_AXES = Object.freeze([
  { key: 'energy', label: '精力', description: '伙伴此刻的活跃程度，随时间自然回落。' },
  { key: 'joy', label: '愉悦', description: '伙伴此刻的愉快程度，喂养和互动会带来波动。' },
  { key: 'loneliness', label: '孤独', description: '伙伴此刻的孤单程度，陪伴会让它回落。' },
  { key: 'inspiration', label: '灵感', description: '伙伴此刻的灵感强度，来自图片与思考。' },
  { key: 'irritation', label: '烦躁', description: '伙伴此刻的烦躁程度，安静一会儿就会平复。' }
])

export const RELATIONSHIP_AXES = Object.freeze([
  { key: 'familiarity', label: '熟悉度', description: '伙伴对你和你的图片越来越熟悉。' },
  { key: 'trust', label: '信任', description: '伙伴对你越信任，越愿意分享想法。' },
  { key: 'closeness', label: '亲密度', description: '你们之间的亲近程度，随陪伴缓慢增长。' },
  { key: 'tacit', label: '默契', description: '伙伴越来越懂你的偏好与习惯。' },
  { key: 'recentFeedback', label: '近期反馈', description: '最近互动的整体感受，正向表示相处愉快。' }
])

export const MEMORY_STATUS = Object.freeze({
  PENDING: { label: '待确认', tone: 'pending' },
  CONFIRMED: { label: '已确认', tone: 'confirmed' },
  DISMISSED: { label: '已忽略', tone: 'dismissed' },
  INVALIDATED: { label: '来源不可用', tone: 'invalidated' },
  DELETED: { label: '已删除', tone: 'deleted' }
})
