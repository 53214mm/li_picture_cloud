export class CollaborationClient {
  constructor({ WebSocketCtor = globalThis.WebSocket, baseUrl, schedule = setTimeout } = {}) {
    this.WebSocketCtor = WebSocketCtor
    this.baseUrl = baseUrl
    this.schedule = schedule
    this.listeners = new Set()
    this.socket = null
    this.pictureId = null
    this.closedByUser = false
    this.reconnectAttempt = 0
  }

  connect(pictureId) {
    if (!pictureId) throw new Error('pictureId is required')
    this.pictureId = pictureId
    this.closedByUser = false
    this.openSocket()
  }

  subscribe(listener) {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  send(operation, baseVersion) {
    if (!this.socket || this.socket.readyState !== this.WebSocketCtor.OPEN) {
      throw new Error('协同连接尚未就绪')
    }
    const command = {
      commandId: globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`,
      operation,
      baseVersion
    }
    this.socket.send(JSON.stringify(command))
    return command.commandId
  }

  close() {
    this.closedByUser = true
    this.socket?.close()
    this.socket = null
  }

  openSocket() {
    const socket = new this.WebSocketCtor(this.buildUrl())
    this.socket = socket
    socket.onopen = () => {
      this.reconnectAttempt = 0
      this.emit({ type: 'CONNECTION', status: 'connected' })
    }
    socket.onmessage = (message) => {
      try {
        this.emit(JSON.parse(message.data))
      } catch {
        this.emit({ type: 'ERROR', message: '收到无法解析的协同消息' })
      }
    }
    socket.onerror = () => this.emit({ type: 'CONNECTION', status: 'error' })
    socket.onclose = () => {
      this.emit({ type: 'CONNECTION', status: 'disconnected' })
      if (!this.closedByUser) this.scheduleReconnect()
    }
  }

  scheduleReconnect() {
    const delay = Math.min(1000 * 2 ** this.reconnectAttempt, 10000)
    this.reconnectAttempt += 1
    this.schedule(() => {
      if (!this.closedByUser) this.openSocket()
    }, delay)
  }

  buildUrl() {
    if (this.baseUrl) return `${this.baseUrl}?pictureId=${encodeURIComponent(this.pictureId)}`
    const protocol = globalThis.location?.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = globalThis.location?.host || 'localhost:5173'
    return `${protocol}//${host}/api/ws/collaboration?pictureId=${encodeURIComponent(this.pictureId)}`
  }

  emit(event) {
    for (const listener of this.listeners) listener(event)
  }
}
