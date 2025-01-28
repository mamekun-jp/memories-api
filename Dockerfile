FROM cimg/openjdk:21.0.5-node

# Switch to root user to install FFmpeg
USER root

# Install FFmpeg
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*

# Switch back to the CircleCI default user
USER circleci

LABEL maintainer=memories-api
COPY build build
ENTRYPOINT ["java","-jar","build/libs/memories-api-0.0.1-SNAPSHOT.jar"]