import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;

public class FileDownloader {
    private static final long MAX_DOWNLOAD_SIZE = 50 * 1024 * 1024;
    
    public static String download(HttpClient client, String fileUrl, String destDir, String prefix, String originalFileName) {
        try {
            String cleanUrl = fileUrl.replace("https://t.mehttps://t.me/", "https://t.me/");
            
            System.out.println("    Downloading: " + cleanUrl);
            
            // Check existing files
            File dir = new File(destDir);
            File[] existing = dir.listFiles((f) -> !f.getName().equals("post.txt"));
            if (existing != null && existing.length > 0) {
                for (File f : existing) {
                    if (f.length() > 0) {
                        System.out.println("    Already have: " + f.getName());
                        return f.getName();
                    }
                }
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cleanUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "*/*")
                .timeout(java.time.Duration.ofMinutes(5))
                .GET()
                .build();
            
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            int statusCode = response.statusCode();
            if (statusCode == 301 || statusCode == 302 || statusCode == 307 || statusCode == 308) {
                String redirectUrl = response.headers().firstValue("Location").orElse(null);
                if (redirectUrl != null) {
                    if (!redirectUrl.startsWith("http")) redirectUrl = "https://t.me" + redirectUrl;
                    request = HttpRequest.newBuilder()
                        .uri(URI.create(redirectUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(java.time.Duration.ofMinutes(5))
                        .GET()
                        .build();
                    response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    statusCode = response.statusCode();
                }
            }
            
            if (statusCode != 200) {
                System.out.println("    HTTP " + statusCode + " - skipping");
                return null;
            }
            
            // ============ FILENAME: Use original name from Telegram, nothing else ============
            String fileName;
            
            if (originalFileName != null && !originalFileName.trim().isEmpty()) {
                // Use EXACTLY the name from Telegram, just remove characters illegal for filesystem
                fileName = originalFileName.trim();
                // Only replace characters that are illegal in filenames
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
                // Replace multiple spaces with one underscore
                fileName = fileName.replaceAll("\\s+", "_");
                // Remove leading/trailing dots and spaces
                fileName = fileName.replaceAll("^[.\\s]+", "").replaceAll("[.\\s]+$", "");
                
                System.out.println("    Using Telegram name: " + fileName);
            } else {
                // Fallback: use URL filename or generate one
                String urlName = getFileNameFromUrl(cleanUrl);
                if (urlName != null && !urlName.isEmpty()) {
                    fileName = urlName;
                } else {
                    String contentType = response.headers().firstValue("Content-Type").orElse("");
                    String ext = getExtensionSimple(contentType, cleanUrl);
                    fileName = prefix + "_" + System.currentTimeMillis() + ext;
                }
                System.out.println("    Generated name: " + fileName);
            }
            
            Path filePath = Paths.get(destDir, fileName);
            
            // If file exists with same name, add number
            int counter = 1;
            String baseName = fileName;
            String ext = "";
            if (fileName.contains(".")) {
                int lastDot = fileName.lastIndexOf(".");
                baseName = fileName.substring(0, lastDot);
                ext = fileName.substring(lastDot);
            }
            while (Files.exists(filePath)) {
                fileName = baseName + "_" + counter + ext;
                filePath = Paths.get(destDir, fileName);
                counter++;
            }
            
            // Download
            try (InputStream is = response.body()) {
                Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            long actualSize = Files.size(filePath);
            if (actualSize == 0) {
                Files.delete(filePath);
                System.out.println("    Empty file, deleted");
                return null;
            }
            if (actualSize > MAX_DOWNLOAD_SIZE) {
                Files.delete(filePath);
                System.out.println("    Too large, deleted");
                return null;
            }
            
            System.out.println("    Saved: " + fileName + " (" + formatSize(actualSize) + ")");
            return fileName;
            
        } catch (Exception e) {
            System.out.println("    Error: " + e.getMessage());
            return null;
        }
    }
    
    private static String getFileNameFromUrl(String url) {
        try {
            String path = new URI(url).getPath();
            if (path.contains("?")) path = path.substring(0, path.indexOf("?"));
            String name = Paths.get(path).getFileName().toString();
            if (name != null && name.length() > 2) {
                name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                return name;
            }
        } catch (Exception e) {}
        return null;
    }
    
    private static String getExtensionSimple(String contentType, String url) {
        // Try URL first
        try {
            String path = new URI(url).getPath();
            String name = Paths.get(path).getFileName().toString();
            if (name.contains(".")) {
                String ext = name.substring(name.lastIndexOf(".")).toLowerCase();
                ext = ext.replaceAll("[^a-z0-9.]", "");
                if (ext.length() >= 2 && ext.length() <= 8) return ext;
            }
        } catch (Exception e) {}
        
        if (contentType == null) return "";
        String ct = contentType.toLowerCase();
        
        if (ct.contains("npvt")) return ".npvt";
        if (ct.contains("ovpn")) return ".ovpn";
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("mp4")) return ".mp4";
        if (ct.contains("mp3")) return ".mp3";
        if (ct.contains("pdf")) return ".pdf";
        if (ct.contains("zip")) return ".zip";
        if (ct.contains("json")) return ".json";
        if (ct.contains("text")) return ".txt";
        
        return "";
    }
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
