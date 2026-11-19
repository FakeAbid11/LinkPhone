import { useEffect, useRef } from 'react'

const FOCUSABLE = [
  'a[href]',
  'button:not(:disabled)',
  'input:not(:disabled)',
  'textarea:not(:disabled)',
  'select:not(:disabled)',
  'video[controls]',
  '[tabindex]:not([tabindex="-1"])'
].join(',')

/**
 * Keeps Tab inside the returned element while `active`, and restores focus to whatever had it
 * before the dialog opened. A dialog marked aria-modal has to do this or Tab walks the page behind.
 */
export function useFocusTrap<T extends HTMLElement>(
  active: boolean
): React.MutableRefObject<T | null> {
  const ref = useRef<T | null>(null)

  useEffect(() => {
    if (!active) return
    const host = ref.current
    if (!host) return

    const previous = document.activeElement as HTMLElement | null
    const focusables = (): HTMLElement[] =>
      Array.from(host.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
        (el) => el.offsetParent !== null || el === document.activeElement
      )

    // Move focus in, so Escape works and screen readers announce the dialog.
    const first = focusables()[0] ?? host
    first.focus()

    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key !== 'Tab') return
      const items = focusables()
      if (items.length === 0) {
        event.preventDefault()
        host.focus()
        return
      }
      const head = items[0]
      const tail = items[items.length - 1]
      const current = document.activeElement
      if (event.shiftKey && (current === head || current === host)) {
        event.preventDefault()
        tail.focus()
      } else if (!event.shiftKey && current === tail) {
        event.preventDefault()
        head.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown, true)
    return () => {
      document.removeEventListener('keydown', onKeyDown, true)
      previous?.focus?.()
    }
  }, [active])

  return ref
}
