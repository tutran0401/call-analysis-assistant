import { useRef, useState } from 'react'

export const MAX_FILES = 8

interface Props {
  files: File[]
  onChange: (files: File[]) => void
  disabled?: boolean
}

/** Chọn hoặc kéo-thả nhiều file log; trùng tên + kích thước thì chỉ giữ một. */
export function FilePicker({ files, onChange, disabled }: Props) {
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  function add(incoming: FileList | File[]) {
    const merged = [...files]
    for (const file of Array.from(incoming)) {
      if (!merged.some((f) => f.name === file.name && f.size === file.size)) merged.push(file)
    }
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
                onClick={() => onChange(files.filter((f) => f !== file))}
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
