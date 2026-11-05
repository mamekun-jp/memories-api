FROM cimg/openjdk:21.0

# Switch to root user to install FFmpeg
USER root

# Install FFmpeg
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*

# Create the data directories used by the mounted volumes and hand them to the runtime user,
# so both bind mounts and named volumes are writable inside the container.
RUN mkdir -p /data/memories-img/th /data/memories-vid/th /data/memories-db \
    && chown -R circleci:circleci /data

# Switch back to the CircleCI default user
USER circleci

LABEL maintainer=memories-api

# All runtime parameters are read from environment variables (see .env.sample and
# docker-compose.yml). Defaults for the volume-backed paths are set here; everything
# else falls back to the defaults in application.properties, e.g.:
#   SERVER_PORT, DATASOURCE_URL/USERNAME/PASSWORD, JWT_SECRET, JWT_EXPIRATION,
#   MAIL_*, MAIL_FROM, PASSWORD_RESET_BASE_URL,
#   IMAGE_PATH, IMAGE_THUMBNAIL_PATH, IMAGE_ALLOWED_FORMATS, IMAGE_RESIZE_IMAGES,
#   IMAGE_MAX_SIZE, IMAGE_THUMB_SIZE, VIDEO_PATH, VIDEO_THUMBNAIL_PATH, VIDEO_ALLOWED_FORMATS,
#   APP_BROADCAST_POSTS, APP_STORY_LIFETIME_HOURS, APP_E2E_ENABLED,
#   APP_ENABLE_PUSH, APNS_KEY_ID, APNS_TEAM_ID, APNS_BUNDLE_ID,
#   APNS_PRIVATE_KEY_BASE64, APNS_PRODUCTION   (native push via APNs)
ENV DATASOURCE_URL=jdbc:h2:file:/data/memories-db/memories.db \
    IMAGE_PATH=/data/memories-img \
    IMAGE_THUMBNAIL_PATH=/data/memories-img/th \
    VIDEO_PATH=/data/memories-vid \
    VIDEO_THUMBNAIL_PATH=/data/memories-vid/th

# The boot jar produced by ./gradlew build; override with --build-arg if the version changes.
ARG JAR_FILE=build/libs/memories-api-0.2.1-SNAPSHOT.jar
COPY ${JAR_FILE} /home/circleci/app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/home/circleci/app.jar"]
