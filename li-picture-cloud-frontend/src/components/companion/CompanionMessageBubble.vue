<template>
  <div class="companion-message" role="group" :aria-label="speakerLabel">
    <div class="companion-avatar" aria-hidden="true">
      <span></span>
    </div>
    <div class="message-column">
      <span class="speaker-label">{{ speakerLabel }}</span>
      <p class="message-bubble">{{ message }}</p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  message: { type: String, required: true },
  speakerLabel: { type: String, default: '伙伴说' }
})
</script>

<style scoped>
.companion-message { display: flex; gap: .7rem; align-items: flex-start; margin-top: .85rem; }
.companion-avatar { flex: 0 0 2.25rem; width: 2.25rem; height: 2.25rem; display: grid; place-items: center; border: 2px solid var(--black); border-radius: 50%; background: var(--blue); }
.companion-avatar span { width: .72rem; height: .72rem; border: 2px solid var(--white); border-radius: 50%; background: var(--red); box-shadow: 0 0 0 .2rem rgba(255, 255, 255, .2); }
.message-column { min-width: 0; max-width: 42rem; }
.speaker-label { display: block; margin: 0 0 .28rem .65rem; color: var(--gray-600); font-size: .68rem; font-weight: 800; letter-spacing: .06em; }
.message-bubble { position: relative; padding: .75rem .9rem; border: 2px solid var(--black); border-radius: .25rem 1rem 1rem 1rem; background: var(--white); font-size: .9rem; line-height: 1.65; overflow-wrap: anywhere; }
.message-bubble::before { content: ''; position: absolute; left: -.48rem; top: -.12rem; width: .65rem; height: .65rem; border-left: 2px solid var(--black); border-top: 2px solid var(--black); background: var(--white); transform: rotate(-18deg) skew(-18deg); }
@media (max-width: 767px) {
  .companion-message { gap: .55rem; }
  .companion-avatar { flex-basis: 2rem; width: 2rem; height: 2rem; }
  .message-bubble { font-size: .86rem; }
}
</style>
