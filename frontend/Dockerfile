# .nvmrc를 읽지 못하므로 .nvmrc를 바꿀 때 이 태그도 같이 바꾼다.
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=3000
# 지정하지 않으면 컨테이너 ID 호스트명에만 바인딩되어 Nginx 컨테이너에서 접속하지 못한다.
ENV HOSTNAME=0.0.0.0
# standalone 서버가 .next/cache에 쓰므로 node 소유로 복사한다.
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
