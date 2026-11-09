#!/usr/bin/env bash
set -euo pipefail
assembly_ffmpeg="${LOCAL_FFMPEG_PATH:-ffmpeg}"
assembly_ffprobe="${LOCAL_FFPROBE_PATH:-ffprobe}"
for assembly_tool in "$assembly_ffmpeg" "$assembly_ffprobe"; do
  if ! command -v "$assembly_tool" >/dev/null 2>&1; then
    echo "필요한 실행 파일이 없습니다: $assembly_tool" >&2
    echo 'ffmpeg와 ffprobe를 설치하거나 LOCAL_FFMPEG_PATH / LOCAL_FFPROBE_PATH를 지정하세요.' >&2
    exit 1
  fi
  "$assembly_tool" -version >/dev/null
done
echo '로컬 녹음 조립 도구 확인 완료: ffmpeg, ffprobe'
