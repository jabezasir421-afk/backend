FROM eclipse-temurin:21-jre-alpine

# Security: Create non-root user and group
RUN addgroup -S -g 1001 bluecollar && \
    adduser -S -u 1001 -G bluecollar bluecollar

WORKDIR /app

# Create persistent storage folder and grant ownership to non-root user
RUN mkdir -p /var/bluecollar/files && \
    chown -R bluecollar:bluecollar /var/bluecollar/files /app

USER bluecollar:bluecollar

COPY --chown=bluecollar:bluecollar target/*.jar /app/app.jar

EXPOSE 8080

VOLUME ["/var/bluecollar/files"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
