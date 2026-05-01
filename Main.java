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
    private static final String MEDIA_DIR = "media";
    private static final long MAX_DOWNLOAD_SIZE = 10 * 1024 * 1024; // 10MB
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Telegram channel reader - HTML output...");
        
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
        
        new File(MEDIA_DIR).mkdirs();
        
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Max posts: " + maxPosts);
        
        // Change output to HTML
        String htmlFile = "posts.html";
        
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
        
        // Start HTML document
        StringBuilder htmlOutput = new StringBuilder();
        htmlOutput.append("<!DOCTYPE html>\n<html dir=\"rtl\">\n<head>\n");
        htmlOutput.append("<meta charset=\"UTF-8\">\n");
        htmlOutput.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        htmlOutput.append("<title>@").append(channelUsername).append(" - Posts</title>\n");
        htmlOutput.append("<style>\n");
        htmlOutput.append("body { font-family: Tahoma, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background: #f5f5f5; }\n");
        htmlOutput.append(".post { background: white; border-radius: 10px; padding: 15px; margin: 15px 0; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }\n");
        htmlOutput.append(".post img, .post video { max-width: 100%; border-radius: 8px; margin: 10px 0; }\n");
        htmlOutput.append(".post .text { line-height: 1.8; margin: 10px 0; }\n");
        htmlOutput.append(".post .link { color: #888; font-size: 12px; margin-top: 10px; }\n");
        htmlOutput.append(".post .link a { color: #5699d0; text-decoration: none; }\n");
        htmlOutput.append(".header { text-align: center; padding: 20px; background: #5699d0; color: white; border-radius: 10px; margin-bottom: 20px; }\n");
        htmlOutput.append("</style>\n</head>\n<body>\n");
        
        htmlOutput.append("<div class=\"header\">\n");
        htmlOutput.append("<h2>@").append(channelUsername).append("</h2>\n");
        htmlOutput.append("<p>Updated: ").append(LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )).append("</p>\n");
        htmlOutput.append("</div>\n\n");
        
        int count = 0;
        int newCount = 0;
        int mediaCount = 0;
        
        while (blockMatcher.find() && count < maxPosts) {
            count++;
            String block = blockMatcher.group(1);
            
            // Extract post link
            String postLink = extractPostLink(block);
            
            // Skip if already processed
            if (existingContent.contains(postLink)) {
                continue;
            }
            
            newCount++;
            
            // Extract content
            String text = extractText(block);
            String photoUrl = extractPhotoUrl(block);
            String videoUrl = extractVideoUrl(block);
            String documentUrl = extractDocumentUrl(block);
            String documentName = extractDocumentName(block);
            
            // Build HTML post
            htmlOutput.append("<div class=\"post\">\n");
            
            // Text
            if (!text.isEmpty()) {
                htmlOutput.append("<div class=\"text\">").append(escapeHtml(text)).append("</div>\n");
            }
            
            // Photo inline
            if (photoUrl != null) {
                String savedName = downloadMedia(client, photoUrl, "photo_" + count);
                if (savedName != null) {
                    htmlOutput.append("<img src=\"media/").append(savedName).append("\" alt=\"Photo\">\n");
                    mediaCount++;
                } else {
                    htmlOutput.append("<img src=\"").append(photoUrl).append("\" alt=\"Photo\">\n");
                }
            }
            
            // Video inline
            if (videoUrl != null) {
                String savedName = downloadMedia(client, videoUrl, "video_" + count);
                if (savedName != null) {
                    htmlOutput.append("<video controls><source src=\"media/").append(savedName).append("\"></video>\n");
                    mediaCount++;
                } else {
                    htmlOutput.append("<video controls><source src=\"").append(videoUrl).append("\"></video>\n");
                }
            }
            
            // Document
            if (documentUrl != null) {
                String savedName = downloadMedia(client, documentUrl, "doc_" + count);
                if (savedName != null) {
                    htmlOutput.append("<p>File: <a href=\"media/").append(savedName).append("\">");
                    htmlOutput.append(documentName != null ? escapeHtml(documentName) : "Download");
                    htmlOutput.append("</a></p>\n");
                    mediaCount++;
                } else {
                    htmlOutput.append("<p>File: <a href=\"").append(documentUrl).append("\">");
                    htmlOutput.append(documentName != null ? escapeHtml(documentName) : "Download");
                    htmlOutput.append("</a></p>\n");
                }
            }
            
            // Link to original post
            htmlOutput.append("<div class=\"link\"><a href=\"").append(postLink).append("\" target=\"_blank\">View on Telegram</a></div>\n");
            htmlOutput.append("</div>\n\n");
            
            System.out.println("New post #" + count + (photoUrl != null ? " [photo]" : "") + (videoUrl != null ? " [video]" : ""));
        }
        
        htmlOutput.append("<p style=\"text-align:center;color:#888\">Total posts: ").append(newCount).append(" | Media: ").append(mediaCount).append("</p>\n");
        htmlOutput.append("</body>\n</html>");
        
        // Save HTML
        FileWriter fw = new FileWriter(htmlFile);
        fw.write(htmlOutput.toString());
        fw.close();
        
        System.out.println("\nDone! Posts: " + newCount + ", Media: " + mediaCount);
        System.out.println("HTML saved to: " + htmlFile);
    }
    
    private static String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br>");
    }
    
    private static String extractPostLink(String block) {
        Pattern p = Pattern.compile("<a class=\"tgme_widget_message_date\" href=\"([^\"]+)\">");
        Matcher m = p.matcher(block);
        if (m.find()) {
            return "https://t.me" + m.group(1);
        }
        return "#";
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
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
    
    private static String extractVideoUrl(String block) {
        Pattern p = Pattern.compile("<video[^>]*src=\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
    
    private static String extractDocumentUrl(String block) {
        Pattern p = Pattern.compile(
            "<a\\s+class=\"tgme_widget_message_document[^\"]*\"\\s+href=\"([^\"]+)\""
        );
        Matcher m = p.matcher(block);
        if (m.find()) {
            String link = m.group(1);
            if (!link.startsWith("http")) {
                link = "https://t.me" + link;
            }
            return link;
        }
        return null;
    }
    
    private static String extractDocumentName(String block) {
        Pattern p = Pattern.compile(
            "<span\\s+class=\"tgme_widget_message_document_title[^\"]*\"[^>]*>(.*?)</span>"
        );
        Matcher m = p.matcher(block);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }
    
  private static String downloadMedia(HttpClient client, String mediaUrl, String prefix) {
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
        
        // Check content length to skip huge files
        String contentLength = response.headers().firstValue("Content-Length").orElse("0");
        long size = Long.parseLong(contentLength);
        if (size > MAX_DOWNLOAD_SIZE || size == 0) {
            System.out.println("  Skipping (size: " + size + ")");
            return null;
        }
        
        // Determine extension from Content-Type
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String ext = getExtension(contentType, mediaUrl);
        
        String fileName = prefix + "_" + System.currentTimeMillis() + ext;
        Path filePath = Paths.get(MEDIA_DIR, fileName);
        
        Files.copy(response.body(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        long actualSize = Files.size(filePath);
        System.out.println("  Downloaded: " + fileName + " (" + actualSize + " bytes)");
        
        return fileName;
    } catch (Exception e) {
        System.out.println("  Download failed: " + e.getMessage());
        return null;
    }
}

// متد کمکی برای تشخیص پسوند
private static String getExtension(String contentType, String url) {
    // اول از URL پسوند رو استخراج کن
    try {
        String path = new URI(url).getPath();
        String fileName = Paths.get(path).getFileName().toString();
        // حذف query string
        if (fileName.contains("?")) {
            fileName = fileName.substring(0, fileName.indexOf("?"));
        }
        // اگه پسوند داره، استفاده کن
        if (fileName.contains(".") && fileName.lastIndexOf(".") < fileName.length() - 1) {
            String ext = fileName.substring(fileName.lastIndexOf("."));
            // تمیز کردن پسوند
            ext = ext.replaceAll("[^a-zA-Z0-9.]", "");
            if (ext.length() > 1 && ext.length() < 10) {
                return ext;
            }
        }
    } catch (Exception ignored) {}
    
    // بعد از Content-Type تشخیص بده
    if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
    if (contentType.contains("png")) return ".png";
    if (contentType.contains("gif")) return ".gif";
    if (contentType.contains("webp")) return ".webp";
    if (contentType.contains("mp4") || contentType.contains("video")) return ".mp4";
    if (contentType.contains("pdf")) return ".pdf";
    if (contentType.contains("zip")) return ".zip";
    if (contentType.contains("rar")) return ".rar";
    if (contentType.contains("text") || contentType.contains("plain")) return ".txt";
    if (contentType.contains("octet-stream")) return ".bin";
    if (contentType.contains("json")) return ".json";
    if (contentType.contains("xml")) return ".xml";
    
    // هیچی پیدا نشد - یه پسوند پیش‌فرض نذار، بذار بدون پسوند باشه
    // تا کاربر بتونه هر جور خواست بازش کنه
    return "";
}
    
    private static String htmlUnescape(String input) {
        if (input == null) return "";
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ");
    }
}
