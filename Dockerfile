FROM nginx:1.27-alpine

ARG APK_PATH=app/build/outputs/apk/release/app-release.apk
ARG APK_NAME=financeai-release.apk

LABEL org.opencontainers.image.source="https://github.com/upamanyu92/FinanceAI-android" \
      org.opencontainers.image.description="FinanceAI Android release APK served over HTTP" \
      org.opencontainers.image.licenses="MIT"

COPY ${APK_PATH} /usr/share/nginx/html/${APK_NAME}

RUN printf '<!doctype html><html><head><meta charset="utf-8"><title>FinanceAI Release</title></head><body><h1>FinanceAI Android</h1><p>Download the latest release APK: <a href="%s">%s</a></p></body></html>' "${APK_NAME}" "${APK_NAME}" > /usr/share/nginx/html/index.html

EXPOSE 80
