# Member 5: Supplier Simulator & Crawler Container
FROM node:20-alpine

WORKDIR /app

# Copy crawler and simulator scripts
COPY crawler/supplier_server.js ./
COPY crawler/crawler_engine.js ./

# Set environment defaults
ENV PORT=8085
ENV BACKEND_API_URL=http://backend:8080
ENV SUPPLIER_BASE_URL=http://localhost:8085

EXPOSE 8085

# Default command starts the simulated supplier websites server
CMD ["node", "supplier_server.js"]
