# The default deploy instructions (https://biffweb.com/docs/reference/production/) don't
# use Docker, but this file is provided in case you'd like to deploy with containers.
#
# When running the container, make sure you set any environment variables defined in config.env,
# e.g. using whatever tools your deployment platform provides for setting environment variables.
#
# Run these commands to test this file locally:
#
#   docker build -t your-app .
#   docker run --rm -e BIFF_PROFILE=dev -v $PWD/config.env:/app/config.env your-app

# This is the base builder image, construct the jar file in this one
# it uses alpine for a small image
FROM clojure:temurin-21-tools-deps-alpine AS jre-build

ENV TAILWIND_VERSION=v3.2.4

# Install the missing packages and applications in a single layer
RUN apk add curl rlwrap && curl -L -o /usr/local/bin/tailwindcss \
  https://github.com/tailwindlabs/tailwindcss/releases/download/$TAILWIND_VERSION/tailwindcss-linux-x64 \
  && chmod +x /usr/local/bin/tailwindcss

WORKDIR /app
COPY src ./src
COPY dev ./dev
COPY resources ./resources
COPY deps.edn .

# construct the application jar
RUN clj -M:dev uberjar && cp target/jar/app.jar . && rm -r target

# This stage (see multi-stage builds) is a bare Java container
# copy over the uberjar from the builder image and run the application
FROM eclipse-temurin:21-alpine
WORKDIR /app

# Take the uberjar from the base image and put it in the final image
COPY --from=jre-build /app/app.jar /app/app.jar

EXPOSE 8080

# By default, run in PROD profile
ENV BIFF_PROFILE=prod
ENV HOST=0.0.0.0
ENV PORT=8080
CMD ["/opt/java/openjdk/bin/java", "-XX:-OmitStackTraceInFastThrow", "-XX:+CrashOnOutOfMemoryError", "-jar", "app.jar"]
