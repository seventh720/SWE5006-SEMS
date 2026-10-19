FROM postgres:17-alpine
# Upstream uses gosu only to switch to postgres; Alpine su-exec avoids its old Go runtime.
RUN apk upgrade --no-cache \
    && apk add --no-cache su-exec \
    && sed -i 's/exec gosu postgres/exec su-exec postgres/' /usr/local/bin/docker-entrypoint.sh \
    && ! grep -q gosu /usr/local/bin/docker-entrypoint.sh \
    && rm /usr/local/bin/gosu
