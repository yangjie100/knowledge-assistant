# Build stage
FROM maven:21-eclipse-temurin AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
COPY ka-common/pom.xml ka-common/
COPY ka-rag/pom.xml ka-rag/
COPY ka-chat/pom.xml ka-chat/
COPY ka-agent/pom.xml ka-agent/
COPY ka-admin/pom.xml ka-admin/
COPY ka-webapp/pom.xml ka-webapp/
RUN mvn dependency:go-offline -B

# Build
COPY . .
RUN mvn package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/ka-webapp/target/*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
