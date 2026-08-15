# Builds the bundle inside the image rather than copying a dist/ built on someone's laptop.
# The previous version was a bare `COPY frontend/dist`, which silently shipped whatever happened
# to be lying in that directory — stale code if you forgot to run `npm run build` first, and
# nothing at all on a clean clone, since frontend/dist is gitignored.
#
# Build context is the repo root (see docker-compose.yml), so every path here is repo-relative.
FROM node:20-alpine AS build

WORKDIR /app

# Dependencies first: this layer is cached and only reinstalls when the manifests change, so an
# ordinary source edit does not pay for a full npm ci.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# Copied file by file rather than as the whole directory: the host's node_modules (installed for
# a different platform) and its dist/ must not land on top of what this stage just built.
COPY frontend/tsconfig.json frontend/tsconfig.node.json frontend/vite.config.ts ./
COPY frontend/tailwind.config.js frontend/postcss.config.js frontend/index.html ./
# Vite inlines VITE_* at build time — inside the image now, which is the point of this stage.
COPY frontend/.env ./.env
COPY frontend/public ./public
COPY frontend/src ./src

# Overrides frontend/.env when supplied, for a deployment whose gateway is not on localhost.
# Empty by default so the committed .env stays the single source of truth for local runs.
ARG VITE_API_BASE_URL=""
ARG VITE_RAZORPAY_KEY_ID=""
ARG VITE_GOOGLE_MAPS_API_KEY=""
RUN : > .env.production.local && \
    for pair in "VITE_API_BASE_URL=$VITE_API_BASE_URL" \
                "VITE_RAZORPAY_KEY_ID=$VITE_RAZORPAY_KEY_ID" \
                "VITE_GOOGLE_MAPS_API_KEY=$VITE_GOOGLE_MAPS_API_KEY"; do \
      # Only non-empty args are written, so an unset one falls through to .env instead of
      # overriding it with a blank.
      [ -n "${pair#*=}" ] && echo "$pair" >> .env.production.local; \
    done; \
    npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
