FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Doppler CLI for runtime secret injection.
# At container start, `doppler run` reads DOPPLER_TOKEN from the environment,
# fetches all secrets from your Doppler config, and exports them as env vars
# before launching the JVM. Spring Boot then resolves ${ANTHROPIC_API_KEY},
# ${OPENAI_API_KEY}, ${PINECONE_API_KEY}, etc. from those env vars.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gnupg apt-transport-https ca-certificates \
    && curl -sLf --retry 3 --tlsv1.2 --proto "=https" https://packages.doppler.com/public/cli/gpg.DE2A7741A397C129.key \
       | gpg --dearmor -o /usr/share/keyrings/doppler-archive-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/doppler-archive-keyring.gpg] https://packages.doppler.com/public/cli/deb/debian any-version main" \
       > /etc/apt/sources.list.d/doppler-cli.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends doppler \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["doppler", "run", "--", "java", "-jar", "app.jar"]
