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
        System.out.println("Starting Telegram channel reader with media...");
        
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
        
        String logFile = "posts_log.txt";
        
        String existingContent = "";
        File file = new File(logFile);
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
        
        // ============ Extract post blocks ============
        // Each post is wrapped in tgme_widget_message_wrap
        Pattern postBlockPattern = Pattern.compile(
            "<div class=\"tgme_widget_message_wrap[^\"]*\"[^>]*>(.*?)</div>\\s*</div>\\s*</div>\\s*</div>\\s*</div>",
            Pattern.DOTALL
        );
        
        Matcher blockMatcher = postBlockPattern.matcher(html);
        
        StringBuilder newPosts = new StringBuilder();
        newPosts.append("\n========================================\n");
        newPosts.append("Channel: @").append(channelUsername).append("\n");
        newPosts.append("Run time: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        ));
        newPosts.append("\n========================================\n\n");
        
        int count = 0;
        int newCount = 0;
        int mediaCount = 0;
        
        while (blockMatcher.find() && count < maxPosts) {
            count++;
            String block = blockMatcher.group(1);
            
            // Extract post link (unique identifier)
            String postLink = extractPostLink(block);
            
            // Skip if already logged
            if (existingContent.contains(postLink)) {
                continue;
            }
            
            newCount++;
            
            // Extract text
            String text = extractText(block);
            
            // Extract media links
            String photoUrl = extractPhotoUrl(block);
            String videoUrl = extractVideoUrl(block);
            String documentUrl = extractDocumentUrl(block);
            String documentName = extractDocumentName(block);
            
            // Build post entry
            newPosts.append("Post #").append(count).append(":\n");
            newPosts.append("Link: ").append(postLink).append("\n");
            
            if (!text.isEmpty()) {
                newPosts.append("Text: ").append(text).append("\n");
            }
            
            // Download and log media
            if (photoUrl != null) {
                String savedName = downloadMedia(client, photoUrl, "photo_" + count);
                newPosts.append("Photo: ").append(photoUrl).append("\n");
                if (savedName != null) {
                    newPosts.append("  Saved: media/").append(savedName).append("\n");
                    mediaCount++;
                }
            }
            
            if (videoUrl != null) {
                newPosts.append("Video: ").append(videoUrl).append("\n");
                String savedName = downloadMedia(client, videoUrl, "video_" + count);
                if (savedName != null) {
                    newPosts.append("  Saved: media/").append(savedName).append("\n");
                    mediaCount++;
                }
            }
            
            if (documentUrl != null) {
                newPosts.append("Document: ").append(documentUrl).append("\n");
                if (documentName != null) {
                    newPosts.append("  Name: ").append(documentName).append("\n");
                }
                String savedName = downloadMedia(client, documentUrl, "doc_" + count);
                if (savedName != null) {
                    newPosts.append("  Saved: media/").append(savedName).append("\n");
                    mediaCount++;
                }
            }
            
            newPosts.append("----------------------------------------\n\n");
            System.out.println("New post #" + count);
        }
        
        if (newCount == 0) {
            newPosts.append("No new posts. Checked: ").append(count).append("\n\n");
        } else {
            newPosts.append("New posts: ").append(newCount).append(" / Checked: ").append(count).append("\n");
            newPosts.append("Media files saved: ").append(mediaCount).append("\n\n");
        }
        
        // Save log
        FileWriter fw = new FileWriter(logFile, true);
        fw.write(newPosts.toString());
        fw.close();
        
        // Show media folder
        System.out.println("\n=== Media Files ===");
        File mediaDir = new File(MEDIA_DIR);
        File[] mediaFiles = mediaDir.listFiles();
        if (mediaFiles != null) {
            for (File f : mediaFiles) {
                System.out.println("  " + f.getName() + " (" + f.length() + " bytes)");
            }
        }
        
        System.out.println("\nDone! Posts: " + newCount + ", Media: " + mediaCount);
    }
    
    private static String extractPostLink(String block) {
        Pattern p = Pattern.compile("<a class=\"tgme_widget_message_date\" href=\"([^\"]+)\">");
        Matcher m = p.matcher(block);
        if (m.find()) {
            return "https://t.me" + m.group(1);
        }
        return "https://t.me/unknown";
    }
    
    private static String extractText(String block) {
        Pattern p = Pattern.compile(
            "<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>",
            Pattern.DOTALL
        );
        Matcher m = p.matcher(block);
        if (m.find()) {
            String text = m.group(1);
            text = text.replaceAll("<br/?>", " ");
            text = text.replaceAll("<[^>]+>", "");
            text = htmlUnescape(text).trim();
            return text;
        }
        return "";
    }
    
    private static String extractPhotoUrl(String block) {
        // Background image in photo wrap
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
            
            if (response.statusCode() != 200) {
                return null;
            }
            
            // Check content length
            String contentLength = response.headers().firstValue("Content-Length").orElse("0");
            long size = Long.parseLong(contentLength);
            
            if (size > MAX_DOWNLOAD_SIZE) {
                System.out.println("  Skipping large file: " + size + " bytes");
                return null;
            }
            
            // Determine extension
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String ext = ".bin";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) ext = ".jpg";
            else if (contentType.contains("png")) ext = ".png";
            else if (contentType.contains("gif")) ext = ".gif";
            else if (contentType.contains("webp")) ext = ".webp";
            else if (contentType.contains("mp4")) ext = ".mp4";
            else if (contentType.contains("pdf")) ext = ".pdf";
            else if (contentType.contains("zip")) ext = ".zip";
            
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
    
    private static String htmlUnescape(String input) {
        if (input == null) return "";
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&zwnj;", "")
            .replaceAll("&#\\d+;", "")
            .replaceAll("&[a-z]+;", "");
    }
}
