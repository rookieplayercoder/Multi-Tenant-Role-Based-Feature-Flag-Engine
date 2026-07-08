# ---- Build stage ----
# Uses an official Maven+JDK image rather than assuming a checked-in
# ./mvnw wrapper exists (it wasn't present in the project structure this
# was written against). If you have a wrapper, swap the RUN lines for
# ./mvnw equivalents and COPY it in above pom.xml.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Dependencies are resolved in their own layer, cached separately from
# source changes — editing a .java file won't force re-downloading the
# whole dependency tree on the next build.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

# Tests are skipped here deliberately: the @SpringBootTest/MockMvc suite
# needs a live Postgres connection, which doesn't exist during an image
# build. Run `mvn clean test` separately (see README) — this build only
# packages what's already been verified.
RUN mvn clean package -DskipTests -B

# ---- Runtime stage ----
# JRE only, not the full JDK+Maven build toolchain, and alpine for a small
# final image — no reason to ship a compiler in the deployed artifact.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root: the JVM has no legitimate reason to run as root in a container.
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/*.jar app.jar
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
