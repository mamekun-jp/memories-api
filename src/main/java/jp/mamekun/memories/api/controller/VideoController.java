package jp.mamekun.memories.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jp.mamekun.memories.api.model.api.MediaUploadResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/video")
@Log4j2
public class VideoController {
    @Value("${video.path:'/data/memories-vid'}")
    private String uploadDir;

    @Value("${video.thumbnail-path:'/data/memories-vid/th'}")
    private String thUploadDir;

    @Value("${video.allowed-formats:'mp4'}")
    private String allowedFormats;

    // Define allowed formats to be accepted by server
    @Value("${image.allowed-formats:'jpg|jpeg|png|gif|bmp|webp'}")
    private String allowedThFormats;

    @GetMapping({"/{vidName}.{ext}", "/th/{vidName}.{ext}"})
    public ResponseEntity<Resource> serveVideoOrThumb(
            @PathVariable("vidName") String vidName,
            @PathVariable("ext") String ext,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            HttpServletRequest request) {
        try {
            boolean isThumbnail = request.getRequestURI().contains("/th/");
            String baseDir = isThumbnail ? thUploadDir : uploadDir;

            // Ensure valid format (thumbnails vs videos)
            if ((isThumbnail && !ext.matches(allowedThFormats)) || (!isThumbnail && !ext.matches(allowedFormats))) {
                return ResponseEntity.badRequest().build();
            }

            // Locate file
            File file = new File(baseDir, vidName + "." + ext);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Detect MIME type
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) {
                mimeType = ext.equalsIgnoreCase("mp4") ? "video/mp4" : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            // Serve full image or video if not mp4 or no range header
            if (isThumbnail || !ext.equalsIgnoreCase("mp4") || rangeHeader == null) {
                return serveFullFile(file, mimeType, vidName, ext);
            }

            // Serve partial video content for streaming
            return servePartialContent(file, rangeHeader);
        } catch (Exception e) {
            log.error("Error serving file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<Resource> serveFullFile(File file, String mimeType, String vidName, String ext) {
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + vidName + "." + ext + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length())) // Ensure iOS can determine file size
                .body(resource);
    }

    private ResponseEntity<Resource> servePartialContent(File file, String rangeHeader) throws IOException {
        long fileSize = file.length();

        // Parse Range Header
        String[] ranges = rangeHeader.replace("bytes=", "").split("-");
        long start = Long.parseLong(ranges[0]);
        long end = (ranges.length > 1 && !ranges[1].isEmpty()) ? Long.parseLong(ranges[1]) : fileSize - 1;
        if (end >= fileSize) end = fileSize - 1;
        long contentLength = end - start + 1;

        // Use RandomAccessFile for efficient streaming
        byte[] buffer = new byte[(int) contentLength];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            raf.readFully(buffer);
        }

        ByteArrayResource resource = new ByteArrayResource(buffer);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(resource);
    }

    @PostMapping("/upload")
    public ResponseEntity<MediaUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Save file to server
            String uploadedFileUrl = saveFile(file);

            // Generate thumbnail
            generateThumbnail(uploadedFileUrl);

            // Return response with both URLs
            return ResponseEntity.ok(new MediaUploadResponse(uploadedFileUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String saveFile(MultipartFile file) {
        File directory = new File(uploadDir);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new RuntimeException("Cannot create directory: " + directory);
        }

        String fileExtension = getFileExtension(file.getOriginalFilename());
        if (!fileExtension.matches(allowedFormats)) {
            throw new RuntimeException("File extension not supported: " + file.getOriginalFilename());
        }
        String filename = UUID.randomUUID() + "." + fileExtension;
        String filePath = uploadDir + "/" + filename;

        try {
            Files.write(Paths.get(filePath), file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }

        return "video/" + filename;
    }

    private String generateThumbnail(String videoFilename) {
        videoFilename = videoFilename.substring("video/".length());

        File thumbnailDir = new File(thUploadDir);
        if (!thumbnailDir.exists() && !thumbnailDir.mkdirs()) {
            throw new RuntimeException("Cannot create thumbnail directory: " + thumbnailDir);
        }

        String thumbnailPath = thUploadDir + "/" + videoFilename.replace(".mp4", ".jpg");
        String videoPath = uploadDir + "/" + videoFilename;

        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-i", videoPath, "-ss", "00:00:01", "-vframes", "1", thumbnailPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug(line);
            }
            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate thumbnail", e);
        }

        return "video/th/" + videoFilename.replace(".mp4", ".jpg");
    }

    private String getFileExtension(String fileName) {
        return (fileName != null && fileName.contains(".")) ? fileName.substring(fileName.lastIndexOf(".") + 1) : "";
    }
}
