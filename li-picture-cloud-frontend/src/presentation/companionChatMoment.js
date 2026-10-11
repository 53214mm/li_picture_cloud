// Presentation only: retain the complete shared session for context and recovery.
export function splitChatMoment(messages) {
  const lastQuestion = messages.findLastIndex(message => message.role === 'USER')
  const start = lastQuestion < 0 ? Math.max(0, messages.length - 1) : lastQuestion
  return { current: messages.slice(start), previous: messages.slice(0, start) }
}
