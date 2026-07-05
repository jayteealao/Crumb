FROM node:22-alpine

WORKDIR /app

# Pin itteco/iframely to a specific release tag for reproducible builds.
# Override with --build-arg IFRAMELY_VERSION=<tag> if needed.
ARG IFRAMELY_VERSION=1.8.2

RUN apk add --no-cache git && \
    git clone --depth 1 --branch ${IFRAMELY_VERSION} \
        https://github.com/itteco/iframely.git . && \
    npm install --production && \
    apk del git

# Local config is copied after npm install so it does not get clobbered.
COPY config.local.js /app/config.local.js

# Cloud Run injects PORT at runtime; iframely reads the PORT env var since v1.6.
# Default to 8061 for local docker-run smoke tests.
ENV PORT=8061
EXPOSE $PORT

# Run as the non-root node user (present in the node:22-alpine base image).
USER node

CMD ["node", "server.js"]
