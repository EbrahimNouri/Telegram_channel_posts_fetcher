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
            
            // ============ BUILD FILENAME ============
            String fileName;
            
            if (originalFileName != null && !originalFileName.trim().isEmpty()) {
                // Use original name from Telegram
                fileName = originalFileName.trim();
                
                // Replace characters illegal in filesystem
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
                // Replace multiple spaces with one underscore
                fileName = fileName.replaceAll("\\s+", "_");
                // Remove leading/trailing dots and spaces
                fileName = fileName.replaceAll("^[.\\s]+", "").replaceAll("[.\\s]+$", "");
                
                // Check if filename already has a valid extension
                String existingExt = "";
                if (fileName.contains(".")) {
                    int lastDot = fileName.lastIndexOf(".");
                    existingExt = fileName.substring(lastDot).toLowerCase();
                    existingExt = existingExt.replaceAll("[^a-z0-9.]", "");
                }
                
                boolean hasValidExt = isValidExtension(existingExt);
                
                if (hasValidExt) {
                    // HAS valid extension -> strip everything after extension
                    String baseName = fileName.substring(0, fileName.lastIndexOf("."));
                    fileName = baseName + existingExt;
                } else {
                    // NO valid extension -> try to add one from URL or content-type
                    String newExt = getExtensionFromUrl(cleanUrl);
                    if (newExt.isEmpty()) {
                        String contentType = response.headers().firstValue("Content-Type").orElse("");
                        newExt = getExtensionSimple(contentType, cleanUrl);
                    }
                    if (!newExt.isEmpty() && !fileName.toLowerCase().endsWith(newExt.toLowerCase())) {
                        fileName += newExt;
                    }
                }
                
                System.out.println("    Using Telegram name: " + fileName);
            } else {
                // Fallback: generate filename
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
            
            // Final safety check
            if (fileName == null || fileName.isEmpty()) {
                fileName = prefix + "_" + System.currentTimeMillis() + ".bin";
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
            
            // Download the file
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
    
    // ==================== HELPER METHODS ====================
    
    private static boolean isValidExtension(String ext) {
        if (ext == null || ext.isEmpty()) return false;
        
        // VPN config files
        if (ext.equals(".npvt")) return true;
        if (ext.equals(".ovpn")) return true;
        if (ext.equals(".conf")) return true;
        if (ext.equals(".json")) return true;
        if (ext.equals(".yaml") || ext.equals(".yml")) return true;
        if (ext.equals(".toml")) return true;
        if (ext.equals(".ini")) return true;
        if (ext.equals(".cfg")) return true;
        
        // Images
        if (ext.equals(".jpg") || ext.equals(".jpeg")) return true;
        if (ext.equals(".png")) return true;
        if (ext.equals(".gif")) return true;
        if (ext.equals(".webp")) return true;
        if (ext.equals(".svg")) return true;
        if (ext.equals(".bmp")) return true;
        if (ext.equals(".ico")) return true;
        
        // Video
        if (ext.equals(".mp4")) return true;
        if (ext.equals(".webm")) return true;
        if (ext.equals(".ogv")) return true;
        if (ext.equals(".mov")) return true;
        if (ext.equals(".avi")) return true;
        if (ext.equals(".mkv")) return true;
        
        // Audio
        if (ext.equals(".mp3")) return true;
        if (ext.equals(".wav")) return true;
        if (ext.equals(".flac")) return true;
        if (ext.equals(".aac")) return true;
        if (ext.equals(".ogg")) return true;
        
        // Documents
        if (ext.equals(".pdf")) return true;
        if (ext.equals(".zip")) return true;
        if (ext.equals(".rar")) return true;
        if (ext.equals(".7z")) return true;
        if (ext.equals(".tar")) return true;
        if (ext.equals(".gz")) return true;
        
        // Text
        if (ext.equals(".txt")) return true;
        if (ext.equals(".html") || ext.equals(".htm")) return true;
        if (ext.equals(".css")) return true;
        if (ext.equals(".js")) return true;
        if (ext.equals(".xml")) return true;
        if (ext.equals(".csv")) return true;
        
        // Office
        if (ext.equals(".docx") || ext.equals(".doc")) return true;
        if (ext.equals(".xlsx") || ext.equals(".xls")) return true;
        if (ext.equals(".pptx") || ext.equals(".ppt")) return true;
        if (ext.equals(".odt")) return true;
        if (ext.equals(".ods")) return true;
        
        // Executables
        if (ext.equals(".exe")) return true;
        if (ext.equals(".apk")) return true;
        if (ext.equals(".dmg")) return true;
        if (ext.equals(".deb")) return true;
        if (ext.equals(".msi")) return true;
        
        // Other
        if (ext.equals(".bin")) return true;
        if (ext.equals(".dat")) return true;
        if (ext.equals(".iso")) return true;
        if (ext.equals(".dll")) return true;
        if (ext.equals(".so")) return true;
        
        // If extension is 2-5 letters, probably valid
        if (ext.length() >= 2 && ext.length() <= 5) return true;
        
        return false;
    }
    
    private static String getExtensionFromUrl(String url) {
        try {
            String path = new URI(url).getPath();
            if (path.contains("?")) path = path.substring(0, path.indexOf("?"));
            String name = Paths.get(path).getFileName().toString();
            if (name.contains(".")) {
                String ext = name.substring(name.lastIndexOf(".")).toLowerCase();
                ext = ext.replaceAll("[^a-z0-9.]", "");
                if (ext.length() >= 2 && ext.length() <= 8) return ext;
            }
        } catch (Exception e) {}
        return "";
    }
    
    private static String getExtensionSimple(String contentType, String url) {
        // Try URL first
        String urlExt = getExtensionFromUrl(url);
        if (!urlExt.isEmpty()) return urlExt;
        
        if (contentType == null) return "";
        String ct = contentType.toLowerCase();
        
        // VPN configs
        if (ct.contains("npvt")) return ".npvt";
        if (ct.contains("ovpn") || ct.contains("openvpn")) return ".ovpn";
        if (ct.contains("conf") || ct.contains("wireguard")) return ".conf";
        if (ct.contains("v2ray") || ct.contains("vmess")) return ".json";
        if (ct.contains("sing-box") || ct.contains("singbox")) return ".json";
        if (ct.contains("clash")) return ".yaml";
        
        // Images
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("svg")) return ".svg";
        if (ct.contains("bmp")) return ".bmp";
        if (ct.contains("ico") || ct.contains("icon")) return ".ico";
        
        // Video
        if (ct.contains("mp4") || ct.contains("video/mp4")) return ".mp4";
        if (ct.contains("webm")) return ".webm";
        if (ct.contains("video/")) return ".mp4";
        
        // Audio
        if (ct.contains("mp3") || ct.contains("mpeg")) return ".mp3";
        if (ct.contains("wav")) return ".wav";
        if (ct.contains("flac")) return ".flac";
        if (ct.contains("aac")) return ".aac";
        if (ct.contains("audio/")) return ".mp3";
        
        // Documents
        if (ct.contains("pdf")) return ".pdf";
        if (ct.contains("zip")) return ".zip";
        if (ct.contains("rar")) return ".rar";
        if (ct.contains("7z") || ct.contains("7-zip")) return ".7z";
        if (ct.contains("tar")) return ".tar";
        if (ct.contains("gz") || ct.contains("gzip")) return ".gz";
        
        // Text/Config
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
        
        // Office
        if (ct.contains("word") || ct.contains("docx")) return ".docx";
        if (ct.contains("excel") || ct.contains("xlsx") || ct.contains("spreadsheet")) return ".xlsx";
        if (ct.contains("powerpoint") || ct.contains("pptx") || ct.contains("presentation")) return ".pptx";
        if (ct.contains("opendocument.text")) return ".odt";
        if (ct.contains("opendocument.spreadsheet")) return ".ods";
        
        // Executables
        if (ct.contains("exe") || ct.contains("x-msdownload")) return ".exe";
        if (ct.contains("apk") || ct.contains("android")) return ".apk";
        if (ct.contains("dmg")) return ".dmg";
        if (ct.contains("deb")) return ".deb";
        
        // Binary
        if (ct.contains("octet-stream") || ct.contains("binary")) return ".bin";
        
        return "";
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
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
