import home from '@/assets/companion/lingye/adult-home-v1.webp?no-inline'
import idle from '@/assets/companion/lingye/adult-idle-v1.webp?no-inline'
import portrait from '@/assets/companion/lingye/adult-neutral-v1.webp?no-inline'
import shell from '@/assets/companion/lingye/adult-shell-v1.webp?no-inline'

// Resource keys are local artwork versions, never database species identifiers.
export const lingyeAssets = Object.freeze({
  home: { src: home, width: 576, height: 768 },
  idle: { src: idle, columns: 2 },
  portrait: { src: portrait, width: 384, height: 384 },
  shell: { src: shell, width: 128, height: 128 }
})
