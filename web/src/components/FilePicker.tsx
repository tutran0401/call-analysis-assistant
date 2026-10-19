import { useRef, useState } from 'react'

// Khớp ChatController.MAX_FILES của backend.
export const MAX_FILES = 8

interface Props {
  files: File[]
  onChange: (files: File[]) => void
  disabled?: boolean
}

/**
 * Chọn hoặc kéo-thả nhiều file log; trùng tên + kích thước thì chỉ giữ một. Quá {@link MAX_FILES} thì không nhận
 * phần dư và <b>nói rõ</b> file nào bị bỏ, thay vì cắt im lặng.
 */
export function FilePicker({ files, onChange, disabled }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [rejected, setRejected] = useState<string[]>([])

  function add(incoming: FileList | File[]) {
    const merged = [...files]
    for (const file of Array.from(incoming)) {
      if (!merged.some((f) => f.name === file.name && f.size === file.size)) merged.push(file)
    }
    setRejected(merged.slice(MAX_FILES).map((f) => f.name))
    onChange(merged.slice(0, MAX_FILES))
  }

  return (
    <div>
      <div
        className={`dropzone${dragging ? ' dragging' : ''}`}
        onDragOver={(e) => {
          e.preventDefault()
          if (!disabled) setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          if (!disabled) add(e.dataTransfer.files)
        }}
      >
        <p>Kéo thả file log vào đây (*_endcall.log, *_webrtc.log)</p>
        <button type="button" className="secondary" disabled={disabled} onClick={() => input.current?.click()}>
          Chọn file
        </button>
        <input
          ref={input}
          type="file"
          multiple
          hidden
          aria-label="Đính kèm file log"
          onChange={(e) => {
            if (e.target.files) add(e.target.files)
            e.target.value = ''
          }}
        />
      </div>
      {rejected.length > 0 && (
        <div className="banner banner-warn" role="alert">
          Chỉ nhận tối đa {MAX_FILES} file mỗi lần phân tích. Không đính kèm: {rejected.join(', ')}. Bỏ bớt file
          khác nếu cần phân tích các file này.
        </div>
      )}
      {files.length > 0 && (
        <ul className="files">
          {files.map((file) => (
            <li key={`${file.name}-${file.size}`}>
              <span>
                {file.name} <span className="muted">({Math.max(1, Math.round(file.size / 1024))} KB)</span>
              </span>
              <button
                type="button"
                className="link"
                disabled={disabled}
                aria-label={`Bỏ ${file.name}`}
                onClick={() => {
                  setRejected([])
                  onChange(files.filter((f) => f !== file))
                }}
              >
                Bỏ
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
