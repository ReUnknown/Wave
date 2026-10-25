#!/bin/sh
# FFmpeg's native libraries unpack into the app's own cache (the home folder is off limits).
export JAVA_TOOL_OPTIONS="-Dorg.bytedeco.javacpp.cachedir=${XDG_CACHE_HOME:-$HOME/.cache}/javacpp"
exec /app/lib/wave/bin/Wave "$@"
