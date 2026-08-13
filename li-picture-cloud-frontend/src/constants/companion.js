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
