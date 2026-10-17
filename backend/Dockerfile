# syntax=docker/dockerfile:1

# ---- build -------------------------------------------------------------------------------
FROM eclipse-temurin:24-jdk-noble AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# Tests run in CI (mvnw verify); the image build only packages. The cache mount keeps downloaded
# dependencies between builds without putting them in the image.
RUN --mount=type=cache,target=/root/.m2 \
    sh ./mvnw -B -q -Dmaven.test.skip=true package \
 && cp target/nexus-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --destination extracted

# ---- runtime -----------------------------------------------------------------------------
FROM eclipse-temurin:24-jre-noble
RUN groupadd --system --gid 10001 nexus \
 && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin nexus
WORKDIR /app
# Dependencies change rarely and the application often, so they are separate layers
COPY --from=build /build/extracted/lib/ ./lib/
COPY --from=build /build/extracted/application.jar ./application.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
