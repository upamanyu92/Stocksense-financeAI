FROM nginx:1.27-alpine

LABEL org.opencontainers.image.source="https://github.com/upamanyu92/Stocksense-financeAI" \
      org.opencontainers.image.description="FinanceAI Android release APKs served over HTTP" \
      org.opencontainers.image.licenses="MIT"

# Copy all split APKs (arm64-v8a, x86_64, universal) into the web root.
COPY app/build/outputs/apk/release/*.apk /usr/share/nginx/html/
# Public runtime source and metadata only; provider credentials are injected at runtime.
COPY dist/gateway-runtime/*.tar.gz dist/gateway-runtime/*.sha256 /usr/share/nginx/html/runtime/

# Generate a simple index page listing every available APK.
RUN cd /usr/share/nginx/html && \
    printf '<!doctype html><html><head><meta charset="utf-8"><title>StockSense Releases</title></head><body>' > index.html && \
    printf '<h1>StockSense – FinanceAI Android</h1><ul>' >> index.html && \
    for f in *.apk; do printf '<li><a href="%s">%s</a></li>' "$f" "$f" >> index.html; done && \
    printf '</ul><p><a href="runtime/">Gateway runtime bundle and checksum</a></p></body></html>' >> index.html

EXPOSE 80
