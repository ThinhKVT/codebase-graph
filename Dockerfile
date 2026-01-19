# Dockerfile to build a runnable image for the CLI tool
FROM eclipse-temurin:17-jre-alpine

# Add a non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the shaded jar (built by Maven) into the image
# Build the jar with: mvn package -DskipTests
ARG JAR_FILE=target/codebase-graph-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} /app/codebase-graph.jar

USER appuser

ENV JAVA_OPTS="-Xms128m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/codebase-graph.jar $@"]
CMD ["serve", "--port", "8080"]

