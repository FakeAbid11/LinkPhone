import { useEffect, useRef, useState } from 'react'
import type { CameraConfig, CameraFrame, CameraInfo } from '@shared/protocol'
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
      return 'This phone cannot stream video at those settings.'
    case 'update_pc':
      return 'Update LinkPhone on this PC to 0.1.5 or newer.'
    case 'disconnected':
      return 'Phone disconnected. Reconnect and try again.'
    default:
      return 'The camera could not start.'
  }
}

function codecString(description: string): string {
  try {
    const bytes = Uint8Array.from(atob(description), (c) => c.charCodeAt(0))
    if (bytes.length < 4) return 'avc1.42E02A'
    const hex = [bytes[1], bytes[2], bytes[3]]
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
    return `avc1.${hex}`
  } catch {
    return 'avc1.42E02A'
  }
}

export function CameraPanel({ camera, connected, supported }: Props): JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const decoderRef = useRef<VideoDecoder | null>(null)
  const configRef = useRef<CameraConfig | null>(null)
  const frameCount = useRef(0)
  const lastKeyframeRequest = useRef(0)
  const errorCount = useRef(0)
  const fpsRef = useRef(30)
  const [fps, setFps] = useState(0)
  const [facing, setFacing] = useState<'front' | 'back'>('back')
  const [quality, setQuality] = useState<'1080p' | '720p' | '480p'>('1080p')
  const [captureFps, setCaptureFps] = useState<30 | 60>(30)
  const [waiting, setWaiting] = useState(false)
  const [startError, setStartError] = useState('')
  const [decodeError, setDecodeError] = useState('')

  const streaming = camera?.streaming ?? false
  fpsRef.current = camera?.fps ?? captureFps

  const closeDecoder = (): void => {
    const decoder = decoderRef.current
    decoderRef.current = null
    if (!decoder) return
    try {
      decoder.close()
    } catch {
      /* already closed */
    }
  }

  const requestKeyframe = (force = false): void => {
    const now = Date.now()
    if (!force && now - lastKeyframeRequest.current < 500) return
    lastKeyframeRequest.current = now
    void window.linkphone.requestCameraKeyframe()
  }

  const configureDecoder = async (config: CameraConfig): Promise<void> => {
    configRef.current = config
    let description: Uint8Array
    try {
      description = Uint8Array.from(atob(config.description), (c) => c.charCodeAt(0))
    } catch {
      setDecodeError('The phone sent an invalid video config.')
      return
    }
    const decoderConfig: VideoDecoderConfig = {
      codec: codecString(config.description),
      description: description.buffer as ArrayBuffer,
      codedWidth: config.width || undefined,
      codedHeight: config.height || undefined,
      optimizeForLatency: true
    }
    try {
      const support = await VideoDecoder.isConfigSupported(decoderConfig)
      if (!support.supported) {
        setDecodeError('This PC cannot decode H.264 video.')
        return
      }
      closeDecoder()
      const decoder = new VideoDecoder({
        output: (frame) => {
          const canvas = canvasRef.current
          if (canvas) {
            if (canvas.width !== frame.displayWidth || canvas.height !== frame.displayHeight) {
              canvas.width = frame.displayWidth
              canvas.height = frame.displayHeight
            }
            canvas.getContext('2d')?.drawImage(frame, 0, 0)
            frameCount.current += 1
            setWaiting(false)
            setDecodeError('')
            errorCount.current = 0
          }
          frame.close()
        },
        error: () => {
          closeDecoder()
          errorCount.current += 1
          if (errorCount.current >= 5) {
            setDecodeError('Reconnecting video…')
          }
          requestKeyframe(true)
        }
      })
      decoder.configure(decoderConfig)
      decoderRef.current = decoder
      setWaiting(true)
    } catch {
      setDecodeError('This PC cannot decode H.264 video.')
    }
  }

  useEffect(() => {
    const offConfig = window.linkphone.onCameraConfig((config) => {
      void configureDecoder(config)
    })
    const offFrame = window.linkphone.onCameraFrame((frame: CameraFrame) => {
      const decoder = decoderRef.current
      if (!decoder || decoder.state !== 'configured') {
        requestKeyframe()
        return
      }
      if (decoder.decodeQueueSize > 8) requestKeyframe()
      try {
        const payload = new Uint8Array(frame.data)
        decoder.decode(
          new EncodedVideoChunk({
            type: frame.flags & 1 ? 'key' : 'delta',
            timestamp: frame.pts,
            duration: Math.round(1_000_000 / (fpsRef.current || 30)),
            data: payload.buffer as ArrayBuffer
          })
        )
      } catch {
        requestKeyframe(true)
      }
    })
    return () => {
      offConfig()
      offFrame()
      closeDecoder()
    }
  }, [])

  useEffect(() => {
    if (!streaming) {
      setFps(0)
      closeDecoder()
      const canvas = canvasRef.current
      canvas?.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height)
      return
    }
    const timer = window.setInterval(() => {
      setFps(frameCount.current)
      frameCount.current = 0
    }, 1000)
    return () => window.clearInterval(timer)
  }, [streaming])

  // A renderer reload while the phone is already streaming needs a fresh config+keyframe.
  useEffect(() => {
    if (streaming && !decoderRef.current) requestKeyframe(true)
  }, [streaming])

  useEffect(() => {
    return () => {
      closeDecoder()
      void window.linkphone.stopCamera()
    }
  }, [])

  const start = async (): Promise<void> => {
    setStartError('')
    setDecodeError('')
    setWaiting(true)
    const ok = await window.linkphone.startCamera(facing, quality, captureFps)
    if (!ok) {
      setWaiting(false)
      setStartError('Phone disconnected. Reconnect and try again.')
    }
  }

  const restart = (
    nextFacing: 'front' | 'back',
    nextQuality: '1080p' | '720p' | '480p',
    nextFps: 30 | 60
  ): void => {
    if (!streaming) return
    setWaiting(true)
    void window.linkphone.startCamera(nextFacing, nextQuality, nextFps)
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
          Update the LinkPhone app on your phone to 0.1.5 or newer to use the H.264 camera.
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
        {streaming && waiting && fps === 0 && !decodeError && (
          <div className="camera-placeholder overlay">
            <p>Waiting for the phone camera…</p>
          </div>
        )}
        {streaming && decodeError && (
          <div className="camera-placeholder overlay">
            <p>{decodeError}</p>
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
              restart(next, quality, captureFps)
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
              const next =
                event.target.value === '720p'
                  ? '720p'
                  : event.target.value === '480p'
                    ? '480p'
                    : '1080p'
              setQuality(next)
              restart(facing, next, captureFps)
            }}
          >
            <option value="1080p">1080p</option>
            <option value="720p">720p</option>
            <option value="480p">480p</option>
          </select>
        </label>
        <label>
          Frame rate
          <select
            value={captureFps}
            disabled={!streaming}
            onChange={(event) => {
              const next = event.target.value === '60' ? 60 : 30
              setCaptureFps(next)
              restart(facing, quality, next)
            }}
          >
            <option value="30">30 fps</option>
            <option value="60">60 fps</option>
          </select>
        </label>
      </div>

      {camera?.error && <p className="camera-hint error">{cameraError(camera.error)}</p>}
      <p className="camera-hint">
        In Zoom, Meet or Teams choose <strong>Share window → LinkPhone</strong>. Keep this window
        open; minimizing or leaving this tab stops the camera. Video only — the phone shows a
        notification while the camera runs. 60 fps depends on the phone camera.
      </p>
    </div>
  )
}
