import test from 'node:test'
import assert from 'node:assert/strict'
import { CollaborationClient } from '../src/services/collaborationClient.js'

class FakeWebSocket {
  static OPEN = 1
  static instances = []

  constructor(url) {
    this.url = url
    this.readyState = 0
    this.sent = []
    FakeWebSocket.instances.push(this)
  }

  send(payload) { this.sent.push(payload) }
  close() { this.onclose?.() }
  open() { this.readyState = FakeWebSocket.OPEN; this.onopen?.() }
  receive(data) { this.onmessage?.({ data: JSON.stringify(data) }) }
}

test('connects, receives authoritative state and sends versioned command', () => {
  FakeWebSocket.instances = []
  const events = []
  const client = new CollaborationClient({
    WebSocketCtor: FakeWebSocket,
    baseUrl: 'ws://test/api/ws/collaboration'
  })
  client.subscribe(event => events.push(event))
  client.connect(7)
  const socket = FakeWebSocket.instances[0]
  socket.open()
  socket.receive({ type: 'STATE', state: { version: 3, rotation: 90, scale: 1.1 } })
  client.send('ZOOM_IN', 3)

  assert.equal(socket.url, 'ws://test/api/ws/collaboration?pictureId=7')
  assert.equal(events.at(-1).state.version, 3)
  assert.deepEqual(JSON.parse(socket.sent[0]).operation, 'ZOOM_IN')
  assert.equal(JSON.parse(socket.sent[0]).baseVersion, 3)
})

test('reconnects after unexpected close but not after user close', () => {
  FakeWebSocket.instances = []
  const scheduled = []
  const client = new CollaborationClient({
    WebSocketCtor: FakeWebSocket,
    baseUrl: 'ws://test/api/ws/collaboration',
    schedule: (task, delay) => scheduled.push({ task, delay })
  })
  client.connect(8)
  FakeWebSocket.instances[0].onclose()
  assert.equal(scheduled[0].delay, 1000)
  scheduled[0].task()
  assert.equal(FakeWebSocket.instances.length, 2)
  client.close()
  assert.equal(scheduled.length, 1)
})
