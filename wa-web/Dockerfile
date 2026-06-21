FROM node:20-slim

WORKDIR /app

# Dépendances (cache de couche)
COPY package.json ./
RUN npm install --omit=dev

# Code
COPY server.js ./

# Données de session persistées (montées en volume en production)
RUN mkdir -p /app/data
VOLUME ["/app/data"]

ENV PORT=3000
ENV WA_WEB_DATA=/app/data
EXPOSE 3000

CMD ["node", "server.js"]
