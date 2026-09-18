#!/bin/sh
# Steady, mixed traffic through the frontend so that the dashboards have something to show.
# Mix: mostly valid orders, some out-of-stock / insufficient-stock, a few unknown SKUs.
TARGET=${TARGET:-http://localhost:8000}
echo "loadgen -> $TARGET"
while true; do
  r=$(awk 'BEGIN { srand(); print int(rand() * 100) }')
  if   [ "$r" -lt 60 ]; then sku=SKU-001
  elif [ "$r" -lt 75 ]; then sku=SKU-002
  elif [ "$r" -lt 92 ]; then sku=SKU-003
  else                       sku=SKU-999
  fi
  qty=$(( r % 4 + 1 ))
  curl -s -o /dev/null -w "%{http_code} $sku x$qty\n" -X POST "$TARGET/api/orders" \
    -H 'Content-Type: application/json' -d "{\"sku\":\"$sku\",\"quantity\":$qty}"
  curl -s -o /dev/null "$TARGET/api/orders"
  curl -s -o /dev/null "$TARGET/api/stock"
  sleep 0.$(( r % 9 + 1 ))
done
