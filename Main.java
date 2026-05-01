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
    private static final long MAX_DOWNLOAD_SIZE = 50 * 1024 * 1024; // 50MB
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Telegram channel archiver...");
        
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
        //   └── channelName/
        //       └── YYYY-MM-DD_POSTID/
        //           ├── post.txt
        //           └── attached_files...
        
        String channelDir = "channels/" + channelUsername;
        new File(channelDir).mkdirs();
        
        // فایل ایندکس برای لیست همه پست‌ها
        String indexPath = channelDir + "/index.html";
        
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Max posts: " + maxPosts);
        System.out.println("Output: " + channelDir);
        
        // Read existing index for duplicate detection
        Set<String> existingPostIds = new HashSet<>();
        File indexFile = new File(indexPath);
        if (indexFile.exists()) {
            String indexContent = new String(Files.readAllBytes(indexFile.toPath()));
            Pattern idPattern = Pattern.compile("data-post-id=\"([^\"]+)\"");
            Matcher idMatcher = idPattern.matcher(indexContent);
            while (idMatcher.find()) {
                existingPostIds.add(idMatcher.group(1));
            }
        }
        
        // Fetch Telegram page
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
        
        System.out.println("Fetching: " + url);
        
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        String html = response.body();
        
        // Extract post blocks
        Pattern postBlockPattern = Pattern.compile(
            "<div class=\"tgme_widget_message_wrap[^\"]*\"[^>]*>(.*?)</div>\\s*</div>\\s*</div>\\s*</div>\\s*</div>",
            Pattern.DOTALL
        );
        
        Matcher blockMatcher = postBlockPattern.matcher(html);
        
        // Collect post data for index
        List<PostData> posts = new ArrayList<>();
        
        int count = 0;
        int newCount = 0;
        
        while (blockMatcher.find() && count < maxPosts) {
            count++;
            String block = blockMatcher.group(1);
            
            // Extract data
            String postLink = extractPostLink(block);
            String postId = extractPostId(postLink);
            String dateStr = extractDate(block);
            String text = extractText(block);
            String photoUrl = extractPhotoUrl(block);
            String videoUrl = extractVideoUrl(block);
            String documentUrl = extractDocumentUrl(block);
            String documentName = extractDocumentName(block);
            
            // Skip if already archived
            if (postId != null && existingPostIds.contains(postId)) {
                continue;
            }
            
            newCount++;
            
            // ============ CREATE POST DIRECTORY ============
            // Folder name: YYYY-MM-DD_POSTID
            String folderName;
            if (dateStr != null && postId != null) {
                folderName = dateStr + "_" + postId;
            } else if (postId != null) {
                folderName = "post_" + postId;
            } else {
                folderName = "post_" + count + "_" + System.currentTimeMillis();
            }
            // Clean folder name
            folderName = folderName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            
            String postDir = channelDir + "/" + folderName;
            new File(postDir).mkdirs();
            
            System.out.println("\n[" + newCount + "] Creating: " + folderName);
            
            // ============ SAVE POST.TXT ============
            StringBuilder postTxt = new StringBuilder();
            postTxt.append("Channel: @").append(channelUsername).append("\n");
            postTxt.append("Post ID: ").append(postId != null ? postId : "N/A").append("\n");
            postTxt.append("Date: ").append(dateStr != null ? dateStr : "N/A").append("\n");
            postTxt.append("Link: ").append(postLink != null ? postLink : "N/A").append("\n");
            postTxt.append("Archived: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            )).append("\n");
            postTxt.append("========================================\n\n");
            
            if (!text.isEmpty()) {
                postTxt.append(text).append("\n");
            }
            
            FileWriter fw = new FileWriter(postDir + "/post.txt");
            fw.write(postTxt.toString());
            fw.close();
            
            // ============ DOWNLOAD MEDIA ============
            List<String> downloadedFiles = new ArrayList<>();
            
            if (photoUrl != null) {
                String saved = downloadFile(client, photoUrl, postDir, "photo");
                if (saved != null) downloadedFiles.add(saved);
            }
            
            if (videoUrl != null) {
                String saved = downloadFile(client, videoUrl, postDir, "video");
                if (saved != null) downloadedFiles.add(saved);
            }
            
            if (documentUrl != null) {
                String saved = downloadFile(client, documentUrl, postDir, "file");
                if (saved != null) downloadedFiles.add(saved);
            }
            
            // ============ STORE POST DATA FOR INDEX ============
            PostData pd = new PostData();
            pd.folderName = folderName;
            pd.postId = postId;
            pd.dateStr = dateStr;
            pd.text = text.length() > 100 ? text.substring(0, 100) + "..." : text;
            pd.files = downloadedFiles;
            pd.hasPhoto = photoUrl != null;
            pd.hasVideo = videoUrl != null;
            pd.hasDocument = documentUrl != null;
            
            posts.add(pd);
            
            System.out.println("  Text: " + (text.isEmpty() ? "no" : "yes (" + Math.min(text.length(), 50) + " chars)"));
            System.out.println("  Files: " + downloadedFiles.size());
        }
        
        // ============ REBUILD INDEX.HTML ============
        StringBuilder indexHtml = new StringBuilder();
        indexHtml.append("<!DOCTYPE html>\n<html dir=\"rtl\">\n<head>\n");
        indexHtml.append("<meta charset=\"UTF-8\">\n");
        indexHtml.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        indexHtml.append("<title>@").append(channelUsername).append(" - Archive</title>\n");
        indexHtml.append("<style>\n");
        indexHtml.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        indexHtml.append("body { font-family: Tahoma, sans-serif; max-width: 800px; margin: 0 auto; padding: 15px; background: #f5f5f5; }\n");
        indexHtml.append(".header { background: linear-gradient(135deg, #3a9eea, #2b7ec4); color: white; padding: 25px; border-radius: 15px; text-align: center; margin-bottom: 20px; }\n");
        indexHtml.append(".header h1 { font-size: 22px; margin-bottom: 5px; }\n");
        indexHtml.append(".header p { font-size: 13px; opacity: 0.9; }\n");
        indexHtml.append(".post-card { background: white; border-radius: 12px; padding: 15px; margin-bottom: 10px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); display: flex; gap: 15px; align-items: flex-start; }\n");
        indexHtml.append(".post-card:hover { box-shadow: 0 3px 10px rgba(0,0,0,0.12); }\n");
        indexHtml.append(".post-icon { font-size: 24px; min-width: 40px; text-align: center; }\n");
        indexHtml.append(".post-info { flex: 1; min-width: 0; }\n");
        indexHtml.append(".post-date { font-size: 11px; color: #888; margin-bottom: 3px; }\n");
        indexHtml.append(".post-text { font-size: 13px; color: #333; line-height: 1.6; word-wrap: break-word; }\n");
        indexHtml.append(".post-files { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 5px; }\n");
        indexHtml.append(".file-badge { display: inline-block; background: #e8f0fe; color: #1a73e8; padding: 4px 10px; border-radius: 12px; font-size: 11px; text-decoration: none; }\n");
        indexHtml.append(".file-badge:hover { background: #d2e3fc; }\n");
        indexHtml.append(".folder-link { display: inline-block; color: #3a9eea; font-size: 11px; margin-top: 5px; text-decoration: none; }\n");
        indexHtml.append(".folder-link:hover { text-decoration: underline; }\n");
        indexHtml.append(".media-icons { display: flex; gap: 4px; font-size: 14px; }\n");
        indexHtml.append("</style>\n</head>\n<body>\n\n");
        
        // Header
        indexHtml.append("<div class=\"header\">\n");
        indexHtml.append("<h1>@").append(channelUsername).append("</h1>\n");
        indexHtml.append("<p>").append(posts.size()).append(" posts archived | Updated: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))).append("</p>\n");
        indexHtml.append("</div>\n\n");
        
        // Post cards
        for (int i = 0; i < posts.size(); i++) {
            PostData pd = posts.get(i);
            
            indexHtml.append("<div class=\"post-card\" data-post-id=\"").append(pd.postId != null ? pd.postId : "").append("\">\n");
            
            // Icon
            indexHtml.append("<div class=\"post-icon\">\n");
            String icon = "📄";
            if (pd.hasPhoto) icon = "🖼️";
            if (pd.hasVideo) icon = "🎬";
            indexHtml.append(icon);
            indexHtml.append("</div>\n");
            
            // Info
            indexHtml.append("<div class=\"post-info\">\n");
            if (pd.dateStr != null) {
                indexHtml.append("<div class=\"post-date\">").append(pd.dateStr).append("</div>\n");
            }
            if (pd.text != null && !pd.text.isEmpty()) {
                indexHtml.append("<div class=\"post-text\">").append(escapeHtml(pd.text)).append("</div>\n");
            }
            
            // Media icons
            indexHtml.append("<div class=\"media-icons\">");
            if (pd.hasPhoto) indexHtml.append("📷");
            if (pd.hasVideo) indexHtml.append("🎥");
            if (pd.hasDocument) indexHtml.append("📎");
            indexHtml.append("</div>\n");
            
            // Files
            if (!pd.files.isEmpty()) {
                indexHtml.append("<div class=\"post-files\">\n");
                for (String f : pd.files) {
                    indexHtml.append("<a class=\"file-badge\" href=\"").append(pd.folderName).append("/").append(f).append("\">")
                        .append(f).append("</a>\n");
                }
                indexHtml.append("</div>\n");
            }
            
            // Link to folder
            indexHtml.append("<a class=\"folder-link\" href=\"").append(pd.folderName).append("/\">View Folder</a>\n");
            
            indexHtml.append("</div>\n</div>\n\n");
        }
        
        indexHtml.append("<p style=\"text-align:center;color:#888;font-size:12px;padding:20px;\">Total: ")
            .append(posts.size()).append(" posts</p>\n");
        indexHtml.append("</body>\n</html>");
        
        // Save index.html
        FileWriter idxFw = new FileWriter(indexPath);
        idxFw.write(indexHtml.toString());
        idxFw.close();
        
        // ============ SUMMARY ============
        System.out.println("\n==========================================");
        System.out.println("Channel: @" + channelUsername);
        System.out.println("New posts archived: " + newCount);
        System.out.println("Total in index: " + posts.size());
        System.out.println("Output: " + channelDir);
        System.out.println("Index: " + indexPath);
        System.out.println("==========================================");
    }
    
    // ==================== DATA CLASS ====================
    static class PostData {
        String folderName;
        String postId;
        String dateStr;
        String text;
        List<String> files = new ArrayList<>();
        boolean hasPhoto;
        boolean hasVideo;
        boolean hasDocument;
    }
    
    // ==================== EXTRACT METHODS ====================
    
    private static String extractPostLink(String block) {
        Pattern p = Pattern.compile("<a class=\"tgme_widget_message_date\" href=\"([^\"]+)\">");
        Matcher m = p.matcher(block);
        if (m.find()) return "https://t.me" + m.group(1);
        return null;
    }
    
    private static String extractPostId(String link) {
        if (link == null) return null;
        // Extract ID from https://t.me/channel/12345
        Pattern p = Pattern.compile("/t\\.me/[^/]+/(\\d+)");
        Matcher m = p.matcher(link);
        if (m.find()) return m.group(1);
        return null;
    }
    
    private static String extractDate(String block) {
        Pattern p = Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        if (m.find()) {
            String dt = m.group(1);
            // Convert to YYYY-MM-DD
            if (dt.contains("T")) {
                dt = dt.substring(0, dt.indexOf("T"));
            }
            return dt;
        }
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
    
    // ==================== DOWNLOAD ====================
    
    private static String downloadFile(HttpClient client, String fileUrl, String destDir, String prefix) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(java.time.Duration.ofMinutes(3))
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
            String ext = getExtension(contentType, fileUrl);
            
            String fileName = prefix + "_" + System.currentTimeMillis() + ext;
            Path filePath = Paths.get(destDir, fileName);
            
            Files.copy(response.body(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            long actualSize = Files.size(filePath);
            System.out.println("    Downloaded: " + fileName + " (" + actualSize + " bytes)");
            
            return fileName;
        } catch (Exception e) {
            System.out.println("    Download failed: " + e.getMessage());
            return null;
        }
    }
    
    private static String getExtension(String contentType, String url) {
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
        
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
        if (contentType.contains("png")) return ".png";
        if (contentType.contains("gif")) return ".gif";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("mp4") || contentType.contains("video")) return ".mp4";
        if (contentType.contains("pdf")) return ".pdf";
        if (contentType.contains("zip")) return ".zip";
        if (contentType.contains("rar")) return ".rar";
        if (contentType.contains("octet-stream")) return ".bin";
        if (contentType.contains("text")) return ".txt";
        
        return "";
    }
    
    // ==================== UTILITY ====================
    
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
            .replace("&nbsp;", " ");
    }
}
