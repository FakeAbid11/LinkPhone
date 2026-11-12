import { useEffect, useRef, useState } from 'react'
import type { CameraInfo } from '@shared/protocol'
import { Icon } from './Icon'

interface Props {
  camera: CameraInfo | null
  connected: boolean
  supported: boolean
}

function cameraError(code: string): string {
  switch (code) {
    case 'open_app':
      return 'Open LinkPhone on your phone, then tap Start here again.'
    case 'permission':
      return 'Allow camera access in LinkPhone on your phone.'
    case 'busy':
      return 'The phone camera is in use by another app.'
    case 'unsupported':
      return 'This phone cannot stream video.'
    case 'disconnected':
      return 'Phone disconnected. Reconnect and try again.'
    default:
      return 'The camera could not start.'
  }
}

export function CameraPanel({ camera, connected, supported }: Props): JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const decoding = useRef(false)
  const frameCount = useRef(0)
  const [fps, setFps] = useState(0)
  const [facing, setFacing] = useState<'front' | 'back'>('back')
  const [quality, setQuality] = useState<'720p' | '480p'>('720p')
  const [waiting, setWaiting] = useState(false)
  const [startError, setStartError] = useState('')

  const streaming = camera?.streaming ?? false

  useEffect(() => {
    const off = window.linkphone.onCameraFrame((data) => {
      if (decoding.current) return
      decoding.current = true
      const bytes = new Uint8Array(data)
      createImageBitmap(new Blob([bytes.buffer], { type: 'image/jpeg' }))
        .then((bitmap) => {
          const canvas = canvasRef.current
          if (canvas) {
            if (canvas.width !== bitmap.width || canvas.height !== bitmap.height) {
              canvas.width = bitmap.width
              canvas.height = bitmap.height
            }
            canvas.getContext('2d')?.drawImage(bitmap, 0, 0)
            frameCount.current += 1
            setWaiting(false)
          }
          bitmap.close()
        })
        .catch(() => undefined)
        .finally(() => {
          decoding.current = false
        })
    })
    return () => {
      off()
    }
  }, [])

  useEffect(() => {
    if (!streaming) {
      setFps(0)
      return
    }
    const timer = window.setInterval(() => {
      setFps(frameCount.current)
      frameCount.current = 0
    }, 1000)
    return () => window.clearInterval(timer)
  }, [streaming])

  useEffect(() => {
    if (streaming) return
    const canvas = canvasRef.current
    canvas?.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height)
  }, [streaming])

  // Leaving the tab unmounts the panel; stop the phone camera with it.
  useEffect(() => {
    return () => {
      void window.linkphone.stopCamera()
    }
  }, [])

  const start = async (): Promise<void> => {
    setStartError('')
    setWaiting(true)
    const ok = await window.linkphone.startCamera(facing, quality)
    if (!ok) {
      setWaiting(false)
      setStartError('Phone disconnected. Reconnect and try again.')
    }
  }

  const restart = (nextFacing: 'front' | 'back', nextQuality: '720p' | '480p'): void => {
    if (!streaming) return
    setWaiting(true)
    void window.linkphone.startCamera(nextFacing, nextQuality)
  }

  if (!connected) {
    return (
      <div className="empty">
        <h2>No phone connected</h2>
        <p className="muted">Connect your phone to use its camera.</p>
      </div>
    )
  }

  if (!supported) {
    return (
      <div className="empty">
        <h2>Update your phone</h2>
        <p className="muted">
          Update the LinkPhone app on your phone to 0.1.3 or newer to use the camera.
        </p>
      </div>
    )
  }

  return (
    <div className="camera-panel">
      <div className="camera-stage">
        <canvas ref={canvasRef} className="camera-canvas" aria-label="Phone camera preview" />
        {!streaming && (
          <div className="camera-placeholder">
            <Icon name="videocam" size={40} />
            <p>{startError || 'Start the camera to see this phone here.'}</p>
          </div>
        )}
        {streaming && waiting && fps === 0 && (
          <div className="camera-placeholder overlay">
            <p>Waiting for the phone camera…</p>
          </div>
        )}
        {streaming && (
          <div className="camera-live">
            <span className="camera-dot" /> Live · {fps} fps
          </div>
        )}
      </div>

      <div className="camera-controls">
        {streaming ? (
          <button className="btn" onClick={() => void window.linkphone.stopCamera()}>
            Stop camera
          </button>
        ) : (
          <button className="btn" onClick={() => void start()}>
            Start camera
          </button>
        )}
        <label>
          Camera
          <select
            value={facing}
            disabled={!streaming}
            onChange={(event) => {
              const next = event.target.value === 'front' ? 'front' : 'back'
              setFacing(next)
              restart(next, quality)
            }}
          >
            <option value="back">Back</option>
            <option value="front">Front</option>
          </select>
        </label>
        <label>
          Quality
          <select
            value={quality}
            disabled={!streaming}
            onChange={(event) => {
              const next = event.target.value === '480p' ? '480p' : '720p'
              setQuality(next)
              restart(facing, next)
            }}
          >
            <option value="720p">720p</option>
            <option value="480p">480p</option>
          </select>
        </label>
      </div>

      {camera?.error && <p className="camera-hint error">{cameraError(camera.error)}</p>}
      <p className="camera-hint">
        In Zoom, Meet or Teams choose <strong>Share window → LinkPhone</strong>. Keep this window
        open; minimizing or leaving this tab stops the camera. Video only — the phone shows a
        notification while the camera runs.
      </p>
    </div>
  )
}
