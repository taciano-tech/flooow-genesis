FROM node:22.12.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY . .
ARG VITE_API_BASE_URL=http://127.0.0.1:8080
ARG VITE_DEMO_MODE=false
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_DEMO_MODE=${VITE_DEMO_MODE}
RUN npm run build

FROM nginx:1.27.3-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
