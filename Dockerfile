FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
# Testes unitarios no build; integracao PostgreSQL deve rodar com ./mvnw verify em CI.
RUN ./mvnw -B -ntp -DskipITs verify

FROM eclipse-temurin:25-jre-jammy AS runtime
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/financeiro-api-0.0.1-SNAPSHOT.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
