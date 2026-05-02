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
            
            // ============ DETERMINE FILENAME ============
            String fileName = determineFileName(originalFileName, cleanUrl, response, prefix);
            
            // ============ CLEAN FILENAME - REMOVE ANY SIZE INFO ============
            fileName = cleanFileName(fileName);
            
            System.out.println("    Filename: " + fileName);
            
            Path filePath = Paths.get(destDir, fileName);
            
            // Avoid duplicate filenames
            int counter = 1;
            String baseName = fileName;
            String extension = "";
            if (fileName.contains(".")) {
                int lastDot = fileName.lastIndexOf(".");
                baseName = fileName.substring(0, lastDot);
                extension = fileName.substring(lastDot);
            }
            while (Files.exists(filePath)) {
                fileName = baseName + "_" + counter + extension;
                filePath = Paths.get(destDir, fileName);
                counter++;
            }
            
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
                System.out.println("    Too large (" + formatSize(actualSize) + "), deleted");
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
        // Priority 1: Original name from Telegram (if meaningful and not just a number)
        if (originalName != null && !originalName.isEmpty() && !originalName.matches("\\d+")) {
            String name = originalName.trim();
            // Remove emojis and special characters but keep letters, numbers, dots, dashes, underscores
            name = name.replaceAll("[^\\p{IsArabic}\\p{IsLatin}\\p{Digit}._\\-\\s]", "");
            name = name.trim();
            // Replace spaces with underscore
            name = name.replaceAll("\\s+", "_");
            // Remove multiple underscores/dots
            name = name.replaceAll("_+", "_");
            name = name.replaceAll("\\.+", ".");
            // Remove leading/trailing dots and underscores
            name = name.replaceAll("^[._]+", "").replaceAll("[._]+$", "");
            
            if (!name.isEmpty()) {
                // If no extension, try to get from URL
                if (!name.contains(".")) {
                    String ext = getExtFromUrl(url);
                    if (!ext.isEmpty()) {
                        name += ext;
                    }
                }
                return name;
            }
        }
        
        // Priority 2: Get filename from URL
        String urlName = getFileNameFromUrl(url);
        if (urlName != null && !urlName.isEmpty() && !urlName.matches("\\d+")) {
            return urlName;
        }
        
        // Priority 3: Generate with prefix and timestamp
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String ext = getExtension(contentType, url);
        return prefix + "_" + System.currentTimeMillis() + ext;
    }
    
    private static String cleanFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "file_" + System.currentTimeMillis() + ".bin";
        
        // Separate name and extension
        String name = fileName;
        String ext = "";
        if (fileName.contains(".")) {
            int lastDot = fileName.lastIndexOf(".");
            name = fileName.substring(0, lastDot);
            ext = fileName.substring(lastDot);
        }
        
        // Clean name: remove anything that looks like a size (numbers with KB/MB/GB)
        name = name.replaceAll("[_\\s]*\\d+[._\\s]*(KB|MB|GB|B|kb|mb|gb|b)[_\\s]*$", "");
        name = name.replaceAll("[_\\s]*\\d+[._\\s]*(kilo|mega|giga|byte|bytes)[_\\s]*$", "");
        name = name.replaceAll("[_\\s]*\\d+\\.?\\d*\\s*(KB|MB|GB|B)[_\\s]*$", "");
        
        // Remove trailing garbage
        name = name.replaceAll("[_\\s]+$", "");
        name = name.replaceAll("^\\.+", "");
        
        // Clean extension
        ext = ext.replaceAll("[^a-zA-Z0-9.]", "");
        ext = ext.toLowerCase();
        
        // Ensure extension is valid
        if (ext.length() > 10 || ext.length() < 2) {
            ext = "";
        }
        
        String result = name + ext;
        
        // Final cleanup
        result = result.replaceAll("_+", "_");
        result = result.replaceAll("\\.+", ".");
        
        return result;
    }
    
    private static String getFileNameFromUrl(String url) {
        try {
            String path = new URI(url).getPath();
            // Remove query string and fragments
            if (path.contains("?")) path = path.substring(0, path.indexOf("?"));
            if (path.contains("#")) path = path.substring(0, path.indexOf("#"));
            
            String name = Paths.get(path).getFileName().toString();
            if (name != null && name.length() > 3) {
                // Clean the filename
                name = name.replaceAll("[?&=#].*$", "");
                name = name.replaceAll("[^a-zA-Z0-9\\p{IsArabic}\\p{IsLatin}._\\-]", "_");
                name = name.replaceAll("_+", "_");
                name = name.replaceAll("^\\.+", "");
                name = name.replaceAll("[._]+$", "");
                if (!name.isEmpty()) return name;
            }
        } catch (Exception e) {}
        return null;
    }
    
    private static String getExtFromUrl(String url) {
        String name = getFileNameFromUrl(url);
        if (name != null && name.contains(".")) {
            String ext = name.substring(name.lastIndexOf("."));
            ext = ext.replaceAll("[^a-zA-Z0-9.]", "");
            if (ext.length() >= 2 && ext.length() <= 8 && ext.startsWith(".")) {
                return ext.toLowerCase();
            }
        }
        return "";
    }
    
    private static String getExtension(String contentType, String url) {
        String urlExt = getExtFromUrl(url);
        if (!urlExt.isEmpty()) return urlExt;
        
        if (contentType == null) return "";
        String ct = contentType.toLowerCase();
        
        // VPN config files - important!
        if (ct.contains("npvt")) return ".npvt";
        if (ct.contains("ovpn") || ct.contains("openvpn")) return ".ovpn";
        if (ct.contains("conf") || ct.contains("wireguard")) return ".conf";
        if (ct.contains("v2ray") || ct.contains("vmess")) return ".json";
        if (ct.contains("sing-box") || ct.contains("singbox")) return ".json";
        if (ct.contains("clash")) return ".yaml";
        
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("svg")) return ".svg";
        if (ct.contains("bmp")) return ".bmp";
        
        if (ct.contains("mp4") || ct.contains("video/mp4")) return ".mp4";
        if (ct.contains("webm")) return ".webm";
        if (ct.contains("video/")) return ".mp4";
        
        if (ct.contains("mp3") || ct.contains("mpeg")) return ".mp3";
        if (ct.contains("wav")) return ".wav";
        if (ct.contains("flac")) return ".flac";
        if (ct.contains("audio/")) return ".mp3";
        
        if (ct.contains("pdf")) return ".pdf";
        if (ct.contains("zip")) return ".zip";
        if (ct.contains("rar")) return ".rar";
        if (ct.contains("7z") || ct.contains("7-zip")) return ".7z";
        if (ct.contains("tar")) return ".tar";
        if (ct.contains("gz") || ct.contains("gzip")) return ".gz";
        
        if (ct.contains("json")) return ".json";
        if (ct.contains("xml")) return ".xml";
        if (ct.contains("yaml") || ct.contains("yml")) return ".yaml";
        if (ct.contains("toml")) return ".toml";
        if (ct.contains("ini")) return ".ini";
        if (ct.contains("cfg")) return ".cfg";
        if (ct.contains("html")) return ".html";
        if (ct.contains("css")) return ".css";
        if (ct.contains("javascript")) return ".js";
        if (ct.contains("text/plain")) return ".txt";
        if (ct.contains("text/")) return ".txt";
        
        if (ct.contains("word") || ct.contains("docx")) return ".docx";
        if (ct.contains("excel") || ct.contains("xlsx")) return ".xlsx";
        if (ct.contains("powerpoint") || ct.contains("pptx")) return ".pptx";
        
        if (ct.contains("exe") || ct.contains("x-msdownload")) return ".exe";
        if (ct.contains("apk") || ct.contains("android")) return ".apk";
        
        if (ct.contains("octet-stream") || ct.contains("binary")) return ".bin";
        
        return "";
    }
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
