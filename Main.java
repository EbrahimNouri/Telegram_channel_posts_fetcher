import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.*;
import java.time.*;
import java.time.format.*;
import java.util.regex.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    private static final long MAX_DOWNLOAD_SIZE = 10 * 1024 * 1024; // 10MB
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Telegram channel reader - Multi channel...");
        
        String channelUsername = System.getenv("CHANNEL_USERNAME");
        String postCountStr = System.getenv("POST_COUNT");
        
        if (channelUsername == null || channelUsername.trim().isEmpty()) {
            channelUsername = "proxymtproto";
        }
        
        int maxPosts = 100;
        if (postCountStr != null && !postCountStr.trim().isEmpty()) {
            try {
                maxPosts = Integer.parseInt(postCountStr.trim());
            } catch (NumberFormatException e) {
                maxPosts = 100;
            }
        }
        
        // ============ ساختار پوشه ============
        // channels/
        //   ├── proxymtproto/
        //   │   ├── posts.html
        //   │   └── media/
        //   │       ├── photo_1.jpg
        //   │       └── video_2.mp4
        //   └── mitivpn/
        //       ├── posts.html
        //       └── media/
        
        String channelDir = "channels/" + channelUsername;
        String mediaDir = channelDir + "/media";
        String htmlFile = channelDir + "/posts.html";
        
        new File(mediaDir).mkdirs();
        
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Max posts: " + maxPosts);
        System.out.println("Output: " + htmlFile);
        
        // Read existing HTML for duplicate detection
        String existingContent = "";
        File file = new File(htmlFile);
        if (file.exists()) {
            existingContent = new String(Files.readAllBytes(file.toPath()));
        }
        
        String url = "https://t.me/s/" + channelUsername;
        
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .GET()
            .build();
        
        System.out.println("URL: " + url);
        
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        String html = response.body();
        
        // Extract post blocks
        Pattern postBlockPattern = Pattern.compile(
            "<div class=\"tgme_widget_message_wrap[^\"]*\"[^>]*>(.*?)</div>\\s*</div>\\s*</div>\\s*</div>\\s*</div>",
            Pattern.DOTALL
        );
        
        Matcher blockMatcher = postBlockPattern.matcher(html);
        
        // Build HTML
        StringBuilder htmlOutput = new StringBuilder();
        htmlOutput.append("<!DOCTYPE html>\n<html dir=\"rtl\">\n<head>\n");
        htmlOutput.append("<meta charset=\"UTF-8\">\n");
        htmlOutput.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        htmlOutput.append("<title>@").append(channelUsername).append("</title>\n");
        htmlOutput.append("<style>\n");
        htmlOutput.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        htmlOutput.append("body { font-family: Tahoma, sans-serif; max-width: 600px; margin: 0 auto; padding: 10px; background: #f0f2f5; }\n");
        htmlOutput.append(".header { background: #3a9eea; color: white; padding: 20px; border-radius: 12px; text-align: center; margin-bottom: 15px; position: sticky; top: 10px; z-index: 100; }\n");
        htmlOutput.append(".header h2 { font-size: 18px; margin-bottom: 5px; }\n");
        htmlOutput.append(".header p { font-size: 11px; opacity: 0.8; }\n");
        htmlOutput.append(".post { background: white; border-radius: 12px; padding: 15px; margin-bottom: 12px; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }\n");
        htmlOutput.append(".post .text { line-height: 1.9; font-size: 14px; color: #222; margin-bottom: 10px; word-wrap: break-word; }\n");
        htmlOutput.append(".post img { max-width: 100%; border-radius: 8px; display: block; margin: 8px 0; }\n");
        htmlOutput.append(".post video { max-width: 100%; border-radius: 8px; display: block; margin: 8px 0; }\n");
        htmlOutput.append(".post .file-link { display: inline-block; background: #e8f0fe; color: #1a73e8; padding: 8px 15px; border-radius: 20px; text-decoration: none; font-size: 13px; margin: 5px 0; }\n");
        htmlOutput.append(".post .file-link:hover { background: #d2e3fc; }\n");
        htmlOutput.append(".post .meta { font-size: 11px; color: #888; margin-top: 8px; border-top: 1px solid #eee; padding-top: 8px; }\n");
        htmlOutput.append(".post .meta a { color: #3a9eea; text-decoration: none; }\n");
        htmlOutput.append(".stats { text-align: center; padding: 15px; color: #666; font-size: 12px; }\n");
        htmlOutput.append(".nav { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; margin-bottom: 15px; }\n");
        htmlOutput.append(".nav a { background: white; padding: 6px 12px; border-radius: 15px; text-decoration: none; color: #3a9eea; font-size: 12px; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }\n");
        htmlOutput.append(".nav a:hover { background: #e8f0fe; }\n");
        htmlOutput.append("</style>\n</head>\n<body>\n\n");
        
        // Header
        htmlOutput.append("<div class=\"header\">\n");
        htmlOutput.append("<h2>@").append(channelUsername).append("</h2>\n");
        htmlOutput.append("<p>").append(LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        )).append("</p>\n");
        htmlOutput.append("</div>\n\n");
        
        // Navigation links to other channels (if any)
        htmlOutput.append("<div class=\"nav\">\n");
        File channelsRoot = new File("channels");
        if (channelsRoot.exists()) {
            File[] otherChannels = channelsRoot.listFiles(File::isDirectory);
            if (otherChannels != null) {
                for (File ch : otherChannels) {
                    if (ch.getName().equals(channelUsername)) continue;
                    htmlOutput.append("<a href=\"../").append(ch.getName()).append("/posts.html\">@")
                        .append(ch.getName()).append("</a>\n");
                }
            }
        }
        htmlOutput.append("</div>\n\n");
        
        int count = 0;
        int newCount = 0;
        int photoCount = 0;
        int videoCount = 0;
        int fileCount = 0;
        
        while (blockMatcher.find() && count < maxPosts) {
            count++;
            String block = blockMatcher.group(1);
            
            String postLink = extractPostLink(block);
            
            // Skip duplicates
            if (postLink != null && existingContent.contains(postLink)) {
                continue;
            }
            
            newCount++;
            
            String text = extractText(block);
            String photoUrl = extractPhotoUrl(block);
            String videoUrl = extractVideoUrl(block);
            String documentUrl = extractDocumentUrl(block);
            String documentName = extractDocumentName(block);
            
            // Start post div
            htmlOutput.append("<div class=\"post\">\n");
            
            // Text
            if (!text.isEmpty()) {
                htmlOutput.append("<div class=\"text\">").append(escapeHtml(text)).append("</div>\n");
            }
            
            // Photo
            if (photoUrl != null) {
                String saved = downloadMedia(client, photoUrl, mediaDir, "photo_" + count);
                if (saved != null) {
                    htmlOutput.append("<img src=\"media/").append(saved).append("\" alt=\"Photo\" loading=\"lazy\">\n");
                    photoCount++;
                } else {
                    htmlOutput.append("<img src=\"").append(photoUrl).append("\" alt=\"Photo\" loading=\"lazy\">\n");
                }
            }
            
            // Video
            if (videoUrl != null) {
                String saved = downloadMedia(client, videoUrl, mediaDir, "video_" + count);
                if (saved != null) {
                    htmlOutput.append("<video controls preload=\"metadata\"><source src=\"media/").append(saved).append("\"></video>\n");
                    videoCount++;
                } else {
                    htmlOutput.append("<video controls preload=\"metadata\"><source src=\"").append(videoUrl).append("\"></video>\n");
                }
            }
            
            // Document/File
            if (documentUrl != null) {
                String saved = downloadMedia(client, documentUrl, mediaDir, "file_" + count);
                String displayName = documentName != null ? escapeHtml(documentName) : "Download File";
                if (saved != null) {
                    htmlOutput.append("<a class=\"file-link\" href=\"media/").append(saved).append("\" download>")
                        .append(displayName).append("</a>\n");
                    fileCount++;
                } else {
                    htmlOutput.append("<a class=\"file-link\" href=\"").append(documentUrl).append("\" target=\"_blank\">")
                        .append(displayName).append("</a>\n");
                }
            }
            
            // Meta
            htmlOutput.append("<div class=\"meta\">\n");
            if (postLink != null) {
                htmlOutput.append("<a href=\"").append(postLink).append("\" target=\"_blank\">View on Telegram</a>\n");
            }
            htmlOutput.append("</div>\n");
            
            htmlOutput.append("</div>\n\n");
            
            System.out.println("Post #" + count + 
                (photoUrl != null ? " [photo]" : "") + 
                (videoUrl != null ? " [video]" : "") + 
                (documentUrl != null ? " [file]" : ""));
        }
        
        // Stats
        htmlOutput.append("<div class=\"stats\">\n");
        htmlOutput.append("Posts: ").append(newCount);
        htmlOutput.append(" | Photos: ").append(photoCount);
        htmlOutput.append(" | Videos: ").append(videoCount);
        htmlOutput.append(" | Files: ").append(fileCount);
        htmlOutput.append("<br>Updated: ").append(LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        )).append("\n");
        htmlOutput.append("</div>\n\n");
        
        htmlOutput.append("</body>\n</html>");
        
        // Save HTML
        FileWriter fw = new FileWriter(htmlFile);
        fw.write(htmlOutput.toString());
        fw.close();
        
        // Summary
        System.out.println("\n==========================================");
        System.out.println("Channel: @" + channelUsername);
        System.out.println("New posts: " + newCount);
        System.out.println("Photos: " + photoCount);
        System.out.println("Videos: " + videoCount);
        System.out.println("Files: " + fileCount);
        System.out.println("Output: " + htmlFile);
        System.out.println("Media: " + mediaDir + "/ (" + new File(mediaDir).listFiles().length + " files)");
        System.out.println("==========================================");
    }
    
    // ==================== EXTRACT METHODS ====================
    
    private static String extractPostLink(String block) {
        Pattern p = Pattern.compile("<a class=\"tgme_widget_message_date\" href=\"([^\"]+)\">");
        Matcher m = p.matcher(block);
        if (m.find()) return "https://t.me" + m.group(1);
        return null;
    }
    
    private static String extractText(String block) {
        Pattern p = Pattern.compile(
            "<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>",
            Pattern.DOTALL
        );
        Matcher m = p.matcher(block);
        if (m.find()) {
            String text = m.group(1);
            text = text.replaceAll("<br/?>", "\n");
            text = text.replaceAll("<[^>]+>", "");
            text = htmlUnescape(text).trim();
            return text;
        }
        return "";
    }
    
    private static String extractPhotoUrl(String block) {
        Pattern p = Pattern.compile(
            "tgme_widget_message_photo_wrap[^\"]*\"[^>]*style=\"[^\"]*background-image:url\\('([^']+)'\\)"
        );
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1);
        return null;
    }
    
    private static String extractVideoUrl(String block) {
        Pattern p = Pattern.compile("<video[^>]*src=\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1);
        return null;
    }
    
    private static String extractDocumentUrl(String block) {
        Pattern p = Pattern.compile(
            "<a\\s+class=\"tgme_widget_message_document[^\"]*\"\\s+href=\"([^\"]+)\""
        );
        Matcher m = p.matcher(block);
        if (m.find()) {
            String link = m.group(1);
            if (!link.startsWith("http")) link = "https://t.me" + link;
            return link;
        }
        return null;
    }
    
    private static String extractDocumentName(String block) {
        Pattern p = Pattern.compile(
            "<span\\s+class=\"tgme_widget_message_document_title[^\"]*\"[^>]*>(.*?)</span>"
        );
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1).trim();
        return null;
    }
    
    // ==================== DOWNLOAD METHOD ====================
    
    private static String downloadMedia(HttpClient client, String mediaUrl, String mediaDir, String prefix) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mediaUrl))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(java.time.Duration.ofMinutes(2))
                .GET()
                .build();
            
            HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream()
            );
            
            if (response.statusCode() != 200) return null;
            
            String contentLength = response.headers().firstValue("Content-Length").orElse("0");
            long size = Long.parseLong(contentLength);
            if (size > MAX_DOWNLOAD_SIZE || size == 0) return null;
            
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String ext = getExtension(contentType, mediaUrl);
            
            String fileName = prefix + "_" + System.currentTimeMillis() + ext;
            Path filePath = Paths.get(mediaDir, fileName);
            
            Files.copy(response.body(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            return fileName;
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String getExtension(String contentType, String url) {
        // Try from URL first
        try {
            String path = new URI(url).getPath();
            String fileName = Paths.get(path).getFileName().toString();
            if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf("?"));
            if (fileName.contains(".")) {
                String ext = fileName.substring(fileName.lastIndexOf("."));
                ext = ext.replaceAll("[^a-zA-Z0-9.]", "");
                if (ext.length() > 1 && ext.length() < 10) return ext;
            }
        } catch (Exception ignored) {}
        
        // Then from Content-Type
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
        if (contentType.contains("png")) return ".png";
        if (contentType.contains("gif")) return ".gif";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("mp4") || contentType.contains("video")) return ".mp4";
        if (contentType.contains("pdf")) return ".pdf";
        if (contentType.contains("zip")) return ".zip";
        if (contentType.contains("rar")) return ".rar";
        if (contentType.contains("octet-stream")) return ".bin";
        if (contentType.contains("text") || contentType.contains("plain")) return ".txt";
        
        return "";
    }
    
    // ==================== UTILITY METHODS ====================
    
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br>");
    }
    
    private static String htmlUnescape(String input) {
        if (input == null) return "";
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replaceAll("&#\\d+;", "")
            .replaceAll("&[a-z]+;", "");
    }
}
