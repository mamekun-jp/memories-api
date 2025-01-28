package jp.mamekun.memories.api.controller;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import jakarta.servlet.http.HttpServletRequest;
import jp.mamekun.memories.api.model.api.MediaUploadResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static com.drew.metadata.exif.ExifDirectoryBase.TAG_ORIENTATION;

@RestController
@RequestMapping("/api/image")
@Log4j2
public class ImageController {
    // Define the directory where files will be saved
    @Value("${image.path:'/data/memories-img'}")
    private String uploadDir;

    // Define the directory where files will be saved
    @Value("${image.thumbnail-path:'/data/memories-img/th'}")
    private String thUploadDir;

    // Define allowed formats to be accepted by server
    @Value("${image.allowed-formats:'jpg|jpeg|png|gif|bmp|webp'}")
    private String allowedFormats;

    // Define allowed formats to be accepted by server
    @Value("${image.resize-images:true}")
    private Boolean resizeImages;

    // Define allowed formats to be accepted by server
    @Value("${image.max-size:2048}")
    private int maxSize;

    // Define allowed formats to be accepted by server
    @Value("${image.thumb-size:320}")
    private int thumbSize;

    @GetMapping({"/{imgName}.{ext}", "/th/{imgName}.{ext}"})
    public ResponseEntity<Resource> serveImageOrThumb(
            @PathVariable("imgName") String imgName,
            @PathVariable("ext") String ext,
            HttpServletRequest request) {
        try {
            // Determine if the request is for a thumbnail or full image
            boolean isThumbnail = request.getRequestURI().contains("/th/");
            String baseDir = isThumbnail ? thUploadDir : uploadDir;

            // Check if the extension is a valid image format
            if (!ext.matches(allowedFormats)) {
                return ResponseEntity.badRequest().build();
            }

            // Construct the file path
            String filePath = baseDir + "/" + imgName + "." + ext;

            // Create the file resource
            File imageFile = new File(filePath);
            if (!imageFile.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Determine the MIME type of the image file
            String mimeType = Files.probeContentType(Paths.get(filePath));
            if (mimeType == null) {
                mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            // Create resource for the image file
            Resource resource = new FileSystemResource(imageFile);

            // Return the image file as a response
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + imgName + "." + ext + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error(e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<MediaUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Save file to server or cloud storage (e.g., S3, Azure)
            String uploadedFileUrl = saveFile(file); // Implement this function

            // Return the URL of the uploaded file
            return ResponseEntity.ok(new MediaUploadResponse(uploadedFileUrl));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String saveFile(MultipartFile file) {
        // Ensure the directory exists
        File directory = new File(uploadDir);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new RuntimeException("Cannot create directory: " + directory);
        }


        // Construct the file path
        String fileExtension = getFileExtension(file.getOriginalFilename());
        if (!fileExtension.matches("jpg|jpeg|png|gif|bmp|webp")) {
            throw new RuntimeException("File extension not supported: " + file.getOriginalFilename());
        }
        String filename = UUID.randomUUID() + "." + fileExtension;
        String filePath = uploadDir + "/" + filename;
        String thumbnailPath = thUploadDir + "/" + filename;

        try {
            // Save the file to disk
            if (Boolean.TRUE.equals(resizeImages)) {
                saveResized(file, fileExtension, filePath, maxSize);
            } else {
                byte[] bytes = file.getBytes();
                Path path = Paths.get(filePath);
                Files.write(path, bytes);
            }

            // Generate and save the thumbnail
            saveResized(file, fileExtension, thumbnailPath, thumbSize);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }

        return "image/" + filename;
    }

    private void saveResized(MultipartFile file, String fileExtension, String filePath, int maxDimension) throws IOException {
        // Convert MultipartFile to BufferedImage
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        int orientation = 1; // Default orientation (normal)

        // Read orientation metadata (if available)
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.getInputStream());
            ExifIFD0Directory exifDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifDir != null) {
                orientation = exifDir.getInt(TAG_ORIENTATION);
            }
        } catch (Exception e) {
            // Ignore metadata extraction failure; use default orientation
            log.error(e);
        }

        // Handle orientation
        originalImage = applyOrientation(originalImage, orientation);

        // Resize image to thumbnail
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        if (width > maxDimension || height > maxDimension) {
            if (width > height) {
                height = (int) (height * ((double) maxDimension / width));
                width = maxDimension;
            } else {
                width = (int) (width * ((double) maxDimension / height));
                height = maxDimension;
            }
        }

        // Create a resized image
        BufferedImage resizedImage = new BufferedImage(width, height, originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : originalImage.getType());
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(originalImage, 0, 0, width, height, null);
        graphics2D.dispose();

        // Save the thumbnail image to disk
        File savedFile = new File(filePath);
        try {
            ImageIO.write(resizedImage, fileExtension, savedFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save resized file", e);
        }
    }

    private BufferedImage applyOrientation(BufferedImage image, int orientation) {
        int width = image.getWidth();
        int height = image.getHeight();
        AffineTransform transform = new AffineTransform();

        switch (orientation) {
            case 2: // Flip horizontally
                transform.scale(-1, 1);
                transform.translate(-width, 0);
                break;
            case 3: // Rotate 180 degrees
                transform.translate(width, height);
                transform.rotate(Math.PI);
                break;
            case 4: // Flip vertically
                transform.scale(1, -1);
                transform.translate(0, -height);
                break;
            case 5: // Transpose
                transform.scale(1, -1);
                transform.rotate(Math.PI / 2);
                break;
            case 6: // Rotate 90 degrees clockwise
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
                break;
            case 7: // Transverse
                transform.scale(-1, 1);
                transform.rotate(-Math.PI / 2);
                break;
            case 8: // Rotate 90 degrees counterclockwise
                transform.translate(0, width);
                transform.rotate(-Math.PI / 2);
                break;
            default:
                return image; // No transformation needed
        }

        // Apply transformation
        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage transformedImage = new BufferedImage(image.getHeight(), image.getWidth(), image.getType());
        op.filter(image, transformedImage);
        return transformedImage;
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return "";  // Return an empty string if no extension is found
    }
}
