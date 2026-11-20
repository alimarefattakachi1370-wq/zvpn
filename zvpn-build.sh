#!/data/data/com.termux/files/usr/bin/bash
# ZVPN: push -> GitHub Actions build -> save both APKs in phone Download folder
cd ~/v2rayNG || { echo "پوشه ~/v2rayNG پیدا نشد"; exit 1; }

# access to phone storage
[ -d ~/storage/downloads ] || { termux-setup-storage; sleep 5; }
DEST=~/storage/downloads
[ -d "$DEST" ] || DEST=/sdcard/Download

git add -A
git commit -m "ZVPN: speed meter, admin banner color fix, actions update" || true
git push || { echo "push ناموفق بود"; exit 1; }

SHA=$(git rev-parse HEAD)
echo "منتظر شروع بیلد..."
RUN=""
for i in $(seq 1 40); do
  RUN=$(gh run list --workflow build.yml --commit "$SHA" --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null)
  [ -n "$RUN" ] && break
  sleep 5
done
[ -z "$RUN" ] && { echo "بیلدی پیدا نشد"; exit 1; }

gh run watch "$RUN" --interval 10 --exit-status
if [ $? -ne 0 ]; then
  echo "=== بیلد خطا داد؛ علت: ==="
  gh run view "$RUN" --log-failed | grep -E "error|Error|FAILED|e: " | head -30
  exit 1
fi

OUT=~/zvpn-out
rm -rf "$OUT"; mkdir -p "$OUT"
gh run download "$RUN" -D "$OUT" || exit 1

U=$(find "$OUT/zvpn-apk" -name '*.apk' | head -n1)
A=$(find "$OUT/zvpn-admin-apk" -name '*.apk' | head -n1)
[ -n "$U" ] && cp "$U" "$DEST/ZVPN.apk" && echo "OK: $DEST/ZVPN.apk"
[ -n "$A" ] && cp "$A" "$DEST/ZVPN-Admin.apk" && echo "OK: $DEST/ZVPN-Admin.apk"
echo "تمام شد. هر دو فایل در پوشه Download هستند."
