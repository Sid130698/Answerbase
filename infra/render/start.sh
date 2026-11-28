#!/bin/bash
set -eu
PORT=8080 java -jar /app/application.jar &
api_pid=$!
nginx -c /app/nginx.conf -g 'daemon off;' &
web_pid=$!
trap 'kill "$api_pid" "$web_pid" 2>/dev/null || true' EXIT TERM INT
wait -n "$api_pid" "$web_pid"
