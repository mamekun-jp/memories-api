# Memories API
Memories API is the backend source code for [MyMemoriesApp](https://apps.apple.com/us/app/mymemoriesapp/id6740176333).

Check the app at Apple Store: https://apps.apple.com/us/app/mymemoriesapp/id6740176333

[MyMemoriesApp](https://apps.apple.com/us/app/mymemoriesapp/id6740176333) is an iOS project which allows you to create closed/public server to share photos and videoes within the audience of your choice.

[MyMemoriesApp](https://apps.apple.com/us/app/mymemoriesapp/id6740176333) source code will become available soon!

You may create your own server within a few steps by using Docker!

```bash
docker run -d \
  --name memories-api \
  -p 9090:9090 \
  -v /data/memories-img:/data/memories-img \
  -v /data/memories-vid:/data/memories-vid \
  -v /data/memories-db:/data/memories-db \
  mamekunjp/memories-api
```

/data/memories-img is the place where images will be stored.
You may change the path on the left for your local path

/data/memories-vid is the place where videos will be stored.
You may change the path on the left for your local path

/data/memories-db is the place where H2 database file will be stored.
You may change the path on the left for your local path

For more options please check out the sample docker-compose.yml file:

```yaml
services:
  memories-server:
    restart: unless-stopped
    container_name: memories-server
    ports:
      - 9090:9090
    image: mamekun/memories-server
    environment:
      # Database Profile (Switch between H2 and PostgreSQL)
      - SPRING_PROFILES_ACTIVE=h2  # Change to "postgres" to use PostgreSQL

      # Data sources settings
      #- DATASOURCE_URL=jdbc:postgresql://localhost
      #- DATASOURCE_USERNAME=memories
      #- DATASOURCE_PASSWORD=Passw0rd

      # Video settings
      - VIDEO_PATH=/data/memories-vid
      - VIDEO_THUMBNAIL_PATH=/data/memories-vid/th
      - VIDEO_ALLOWED_FORMATS=mp4

      # Image settings
      - IMAGE_PATH=/data/memories-img
      - IMAGE_THUMBNAIL_PATH=/data/memories-img/th
      - IMAGE_ALLOWED_FORMATS=jpg|jpeg|png|gif|bmp|webp
      - IMAGE_RESIZE_IMAGES=true
      - IMAGE_MAX_SIZE=2048
      - IMAGE_THUMB_SIZE=320

      # App settings
      - APP_BROADCAST_POSTS=true
    volumes:
      - type: bind
        source: /data/memories-img
        target: /data/memories-img
      - type: bind
        source: /data/memories-vid
        target: /data/memories-vid
      - type: bind
        source: /data/memories-db
        target: /data/memories-db
```