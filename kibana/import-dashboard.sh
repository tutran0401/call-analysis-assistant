#!/usr/bin/env sh
# Import data view + visualizations + dashboard cho index call-analysis-results (Sprint 3 T9/T11).
# Chạy sau khi Kibana đã lên: docker compose up -d elasticsearch kibana
set -e
KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
DIR="$(cd "$(dirname "$0")" && pwd)"
curl -sS -X POST "$KIBANA_URL/api/saved_objects/_import?overwrite=true" -H "kbn-xsrf: true" -F "file=@$DIR/call-analysis-dashboard.ndjson"
echo
echo "Mở: $KIBANA_URL/app/dashboards#/view/ca-dashboard"
