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
                        .GET()
                        .build();
                    response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    statusCode = response.statusCode();
                }
            }
            
            if (statusCode != 200) {
                System.out.println("    HTTP " + statusCode);
                return null;
            }
            
            String fileName = determineFileName(originalFileName, cleanUrl, response, prefix);
            System.out.println("    Filename: " + fileName);
            
            Path filePath = Paths.get(destDir, fileName);
            
            try (InputStream is = response.body()) {
                Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            long actualSize = Files.size(filePath);
            if (actualSize == 0 || actualSize > MAX_DOWNLOAD_SIZE) {
                Files.delete(filePath);
                return null;
            }
            
            System.out.println("    Saved: " + fileName + " (" + formatSize(actualSize) + ")");
            return fileName;
            
        } catch (Exception e) {
            System.out.println("    Error: " + e.getMessage());
            return null;
        }
    }
    
    private static String determineFileName(String originalName, String url, HttpResponse<?> response, String prefix) {
        // Priority 1: Original name from Telegram (if not just a number)
        if (originalName != null && !originalName.isEmpty() && !originalName.matches("\\d+")) {
            String name = originalName.trim();
            name = name.replaceAll("[^\\p{IsArabic}\\p{IsLatin}a-zA-Z0-9._\\-\\s]", "");
            name = name.trim().replaceAll("\\s+", "_").replaceAll("_+", "_");
            if (!name.contains(".")) name += getExtFromUrl(url);
            if (!name.isEmpty()) return name;
        }
        
        // Priority 2: URL filename
        String urlName = getFileNameFromUrl(url);
        if (urlName != null && !urlName.isEmpty() && !urlName.matches("\\d+")) {
            return urlName;
        }
        
        // Priority 3: Generated name
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String ext = getExtension(contentType, url);
        return prefix + "_" + System.currentTimeMillis() + ext;
    }
    
    private static String getFileNameFromUrl(String url) {
        try {
            String path = new URI(url).getPath();
            if (path.contains("?")) path = path.substring(0, path.indexOf("?"));
            String name = Paths.get(path).getFileName().toString();
            if (name != null && name.length() > 3) {
                return name.replaceAll("[^a-zA-Z0-9آ-ی_\\-.]", "_");
            }
        } catch (Exception e) {}
        return null;
    }
    
    private static String getExtFromUrl(String url) {
        String name = getFileNameFromUrl(url);
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf("."));
            ext = ext.replaceAll("[^a-zA-Z0-9.]", "");
            if (ext.length() >= 2 && ext.length() <= 10) return ext.toLowerCase();
        }
        return "";
    }
    
    private static String getExtension(String contentType, String url) {
        String urlExt = getExtFromUrl(url);
        if (!urlExt.isEmpty()) return urlExt;
        
        if (contentType == null) return "";
        String ct = contentType.toLowerCase();
        
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("mp4") || ct.contains("video/")) return ".mp4";
        if (ct.contains("mp3") || ct.contains("audio/")) return ".mp3";
        if (ct.contains("pdf")) return ".pdf";
        if (ct.contains("zip")) return ".zip";
        if (ct.contains("json")) return ".json";
        if (ct.contains("text/plain")) return ".txt";
        if (ct.contains("text/")) return ".txt";
        if (ct.contains("octet-stream")) return ".bin";
        
        return "";
    }
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
