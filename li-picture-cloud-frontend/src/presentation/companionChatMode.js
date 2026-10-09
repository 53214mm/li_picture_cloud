// Copy for the chat-mode switch. Formats the server's CompanionChatModeView only;
// availability and the model actually used are decided by the backend.

/** @returns {{ note: string, link: string | null }} link is the call to action toward model setup. */
export function describeChatMode(view) {
  if (!view) return { note: '', link: null }
  if (view.mode === 'MODEL') {
    if (view.modelSource === 'OWN' && view.modelBroken) {
      return { note: '你连接的模型不可用了，发送会失败。', link: '去修复模型连接 →' }
    }
    if (view.modelSource === 'OWN') {
      return { note: `真实模型：使用你的 ${view.providerLabel} · ${view.modelCode}。`, link: null }
    }
    return { note: '真实模型：使用平台模型，每条消息消耗 1 次试用额度。', link: '连接自己的模型 →' }
  }
  return view.modelAvailable
    ? { note: '演示回复：不调用模型，不产生费用。', link: null }
    : { note: '演示回复：不调用模型。连接自己的模型后，可以切换到真实模型。', link: '去连接模型 →' }
}
