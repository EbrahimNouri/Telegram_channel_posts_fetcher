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
    private static final long MAX_DOWNLOAD_SIZE = 50 * 1024 * 1024;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Telegram Channel Archiver v4 ===");
        
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
        
        String channelDir = "channels/" + channelUsername;
        new File(channelDir).mkdirs();
        String indexPath = channelDir + "/index.html";
        
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Max posts: " + maxPosts);
        
        // ============ LOAD EXISTING POSTS FROM FOLDERS ============
        Map<String, PostData> existingPostsMap = new LinkedHashMap<>();
        
        File channelFolder = new File(channelDir);
        File[] subDirs = channelFolder.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                String dirName = subDir.getName();
                // Extract post ID from folder name: YYYY-MM-DD_ID or post_ID
                Pattern dirIdPattern = Pattern.compile("_(\\d+)$");
                Matcher dirIdMatcher = dirIdPattern.matcher(dirName);
                if (dirIdMatcher.find()) {
                    String id = dirIdMatcher.group(1);
                    
                    PostData pd = new PostData();
                    pd.postId = id;
                    pd.folderName = dirName;
                    
                    File postTxtFile = new File(subDir, "post.txt");
                    if (postTxtFile.exists()) {
                        try {
                            String content = new String(Files.readAllBytes(postTxtFile.toPath()));
                            Pattern dateP = Pattern.compile("Date: ([^\n]+)");
                            Matcher dateM = dateP.matcher(content);
                            if (dateM.find()) pd.dateStr = dateM.group(1).trim();
                            
                            String[] parts = content.split("====+\n+", 2);
                            if (parts.length > 1) pd.text = parts[1].trim();
                        } catch (Exception e) {}
                    }
                    
                    File[] mediaFiles = subDir.listFiles((f) -> !f.getName().equals("post.txt"));
                    if (mediaFiles != null) {
                        for (File mf : mediaFiles) {
                            pd.files.add(mf.getName());
                            String name = mf.getName().toLowerCase();
                            if (name.contains("photo") || name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") || name.endsWith(".webp"))
                                pd.hasPhoto = true;
                            else if (name.contains("video") || name.endsWith(".mp4"))
                                pd.hasVideo = true;
                            else
                                pd.hasDocument = true;
                        }
                    }
                    
                    existingPostsMap.put(id, pd);
                }
            }
        }
        
        System.out.println("Existing posts in archive: " + existingPostsMap.size());
        
        // ============ FETCH TELEGRAM PAGE ============
        String url = "https://t.me/s/" + channelUsername;
        
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .GET()
            .build();
        
        System.out.println("Fetching: " + url);
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        String html = response.body();
        System.out.println("HTML size: " + html.length() + " bytes");
        
        // ============ FIND ALL MESSAGE LINKS ============
        // Direct pattern for message links in t.me/s/
        List<String> postIds = new ArrayList<>();
        List<String> postLinks = new ArrayList<>();
        
        // Pattern 1: <a class="tgme_widget_message_date" href="/channelName/12345">
        Pattern linkPattern1 = Pattern.compile(
            "<a[^>]*class=\"[^\"]*tgme_widget_message_date[^\"]*\"[^>]*href=\"/([^/\"]+)/(\\d+)\""
        );
        Matcher m1 = linkPattern1.matcher(html);
        while (m1.find()) {
            String id = m1.group(2);
            if (!postIds.contains(id)) {
                postIds.add(id);
                postLinks.add("https://t.me/" + m1.group(1) + "/" + id);
            }
        }
        
        // Pattern 2: data-post="channelName/12345"
        Pattern linkPattern2 = Pattern.compile(
            "data-post=\"([^/\"]+)/(\\d+)\""
        );
        Matcher m2 = linkPattern2.matcher(html);
        while (m2.find()) {
            String id = m2.group(2);
            if (!postIds.contains(id)) {
                postIds.add(id);
                postLinks.add("https://t.me/" + m2.group(1) + "/" + id);
            }
        }
        
        System.out.println("Post links found: " + postIds.size());
        
        if (postIds.isEmpty()) {
            // Save debug HTML
            FileWriter debugFw = new FileWriter("debug_" + channelUsername + ".html");
            debugFw.write(html.substring(0, Math.min(50000, html.length())));
            debugFw.close();
            System.out.println("DEBUG: No links found! Saved first 50KB to debug_" + channelUsername + ".html");
        }
        
        // ============ PROCESS EACH POST ============
        int processed = 0;
        
        for (int i = 0; i < postIds.size() && processed < maxPosts; i++) {
            String postId = postIds.get(i);
            String postLink = postLinks.get(i);
            
            // Extract post block from HTML
            String blockPattern = "data-post=\"" + channelUsername + "/" + postId + "\"";
            int blockStart = html.indexOf(blockPattern);
            if (blockStart == -1) {
                // Try alternative: find by message ID in link
                String linkSearch = "/" + channelUsername + "/" + postId;
                blockStart = html.indexOf(linkSearch);
                if (blockStart > 200) blockStart -= 200;
                else blockStart = 0;
            }
            
            int blockEnd = html.indexOf("<div class=\"tgme_widget_message_wrap", blockStart + 100);
            if (blockEnd == -1) blockEnd = html.length();
            
            String block = html.substring(Math.max(0, blockStart), Math.min(blockEnd, html.length()));
            
            // Extract data
            String dateStr = extractDate(block);
            String text = extractText(block);
            String photoUrl = extractPhotoUrl(block);
            String videoUrl = extractVideoUrl(block);
            String documentUrl = extractDocumentUrl(block);
            
            // ============ CREATE/UPDATE POST DIRECTORY ============
            String folderName;
            if (dateStr != null && !dateStr.isEmpty()) {
                folderName = dateStr + "_" + postId;
            } else {
                folderName = "post_" + postId;
            }
            folderName = folderName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            
            String postDir = channelDir + "/" + folderName;
            new File(postDir).mkdirs();
            
            processed++;
            
            System.out.println("\n[" + processed + "/" + Math.min(postIds.size(), maxPosts) + "] ID:" + postId + 
                (photoUrl != null ? " [PHOTO]" : "") + 
                (videoUrl != null ? " [VIDEO]" : "") + 
                (documentUrl != null ? " [FILE]" : ""));
            
            // Check if post.txt already exists and has same content
            File existingPostTxt = new File(postDir, "post.txt");
            boolean needUpdate = true;
            if (existingPostTxt.exists()) {
                String oldContent = new String(Files.readAllBytes(existingPostTxt.toPath()));
                if (oldContent.contains("Post ID: " + postId) && oldContent.contains(text.substring(0, Math.min(50, text.length())))) {
                    needUpdate = false;
                    System.out.println("  Post already up-to-date, skipping text update");
                }
            }
            
            // Save post.txt only if needed
            if (needUpdate) {
                StringBuilder postTxt = new StringBuilder();
                postTxt.append("Channel: @").append(channelUsername).append("\n");
                postTxt.append("Post ID: ").append(postId).append("\n");
                postTxt.append("Date: ").append(dateStr != null ? dateStr : "N/A").append("\n");
                postTxt.append("Link: ").append(postLink).append("\n");
                postTxt.append("Archived: ").append(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                )).append("\n");
                postTxt.append("========================================\n\n");
                if (!text.isEmpty()) postTxt.append(text).append("\n");
                
                FileWriter fw = new FileWriter(postDir + "/post.txt");
                fw.write(postTxt.toString());
                fw.close();
            }
            
            // Download media (always try - will skip if already exists with same size)
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
            
            // Update PostData in map
            PostData pd = existingPostsMap.getOrDefault(postId, new PostData());
            pd.postId = postId;
            pd.folderName = folderName;
            pd.dateStr = dateStr;
            pd.text = text.length() > 150 ? text.substring(0, 150) + "..." : text;
            pd.hasPhoto = photoUrl != null || pd.hasPhoto;
            pd.hasVideo = videoUrl != null || pd.hasVideo;
            pd.hasDocument = documentUrl != null || pd.hasDocument;
            
            if (!downloadedFiles.isEmpty()) {
                Set<String> allFiles = new LinkedHashSet<>(pd.files);
                allFiles.addAll(downloadedFiles);
                pd.files = new ArrayList<>(allFiles);
            }
            
            existingPostsMap.put(postId, pd);
        }
        
        // ============ BUILD INDEX.HTML ============
        List<PostData> allPosts = new ArrayList<>(existingPostsMap.values());
        allPosts.sort((a, b) -> {
            try { return Long.compare(Long.parseLong(b.postId), Long.parseLong(a.postId)); }
            catch (Exception e) { return 0; }
        });
        
        StringBuilder html_content = buildIndexHtml(channelUsername, allPosts);
        
        FileWriter idxFw = new FileWriter(indexPath);
        idxFw.write(html_content.toString());
        idxFw.close();
        
        // ============ SUMMARY ============
        System.out.println("\n==========================================");
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Posts online: " + postIds.size());
        System.out.println("Processed: " + processed);
        System.out.println("Total archived: " + allPosts.size());
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
    
    // ==================== BUILD INDEX HTML ====================
    private static StringBuilder buildIndexHtml(String channelUsername, List<PostData> allPosts) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html>\n<html dir=\"rtl\">\n<head>\n");
        h.append("<meta charset=\"UTF-8\">\n");
        h.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        h.append("<title>@").append(channelUsername).append(" - Archive</title>\n");
        h.append("<style>\n");
        h.append("*{margin:0;padding:0;box-sizing:border-box}\n");
        h.append("body{font-family:Tahoma,sans-serif;max-width:750px;margin:0 auto;padding:12px;background:#e8eaed}\n");
        h.append(".header{background:linear-gradient(135deg,#2a9d8f,#1d7a6e);color:white;padding:22px;border-radius:14px;text-align:center;margin-bottom:18px}\n");
        h.append(".header h1{font-size:20px}\n");
        h.append(".header .stats{font-size:12px;opacity:.9;margin-top:4px}\n");
        h.append(".post-card{background:white;border-radius:10px;padding:14px;margin-bottom:8px;box-shadow:0 1px 2px rgba(0,0,0,.06);display:flex;gap:12px;align-items:flex-start;border-right:3px solid transparent;transition:.2s}\n");
        h.append(".post-card:hover{border-right-color:#2a9d8f;box-shadow:0 3px 10px rgba(0,0,0,.1)}\n");
        h.append(".post-icon{font-size:28px;min-width:36px;text-align:center;line-height:1}\n");
        h.append(".post-info{flex:1;min-width:0}\n");
        h.append(".post-date{font-size:10px;color:#999;margin-bottom:4px;font-weight:bold}\n");
        h.append(".post-text{font-size:12px;color:#444;line-height:1.7;word-wrap:break-word;max-height:80px;overflow:hidden}\n");
        h.append(".post-files{margin-top:7px;display:flex;flex-wrap:wrap;gap:4px}\n");
        h.append(".file-badge{display:inline-block;background:#e8f5e9;color:#2a9d8f;padding:3px 9px;border-radius:10px;font-size:10px;text-decoration:none;border:1px solid #c8e6c9}\n");
        h.append(".file-badge:hover{background:#c8e6c9}\n");
        h.append(".folder-link{display:inline-block;color:#2a9d8f;font-size:10px;margin-top:4px;text-decoration:none}\n");
        h.append("</style>\n</head>\n<body>\n\n");
        
        h.append("<div class=\"header\">\n");
        h.append("<h1>@").append(channelUsername).append("</h1>\n");
        h.append("<div class=\"stats\">").append(allPosts.size()).append(" posts | Updated: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))).append("</div>\n");
        h.append("</div>\n\n");
        
        if (allPosts.isEmpty()) {
            h.append("<p style=\"text-align:center;color:#999;padding:40px\">No posts yet.</p>\n");
        } else {
            for (PostData pd : allPosts) {
                if (pd.postId == null) continue;
                
                String folderName = pd.folderName != null ? pd.folderName : 
                    ((pd.dateStr != null ? pd.dateStr : "post") + "_" + pd.postId).replaceAll("[^a-zA-Z0-9_\\-]", "_");
                
                h.append("<div class=\"post-card\" data-post-id=\"").append(pd.postId).append("\">\n");
                
                String icon = "📝";
                if (pd.hasPhoto) icon = "🖼️";
                else if (pd.hasVideo) icon = "🎬";
                else if (pd.hasDocument) icon = "📎";
                
                h.append("<div class=\"post-icon\">").append(icon).append("</div>\n");
                h.append("<div class=\"post-info\">\n");
                
                if (pd.dateStr != null && !pd.dateStr.isEmpty()) {
                    h.append("<div class=\"post-date\">").append(pd.dateStr).append("</div>\n");
                }
                if (pd.text != null && !pd.text.isEmpty()) {
                    h.append("<div class=\"post-text\">").append(escapeHtml(pd.text)).append("</div>\n");
                }
                if (!pd.files.isEmpty()) {
                    h.append("<div class=\"post-files\">\n");
                    for (String f : pd.files) {
                        h.append("<a class=\"file-badge\" href=\"").append(folderName).append("/").append(f).append("\">")
                            .append(escapeHtml(f)).append("</a>\n");
                    }
                    h.append("</div>\n");
                }
                h.append("<a class=\"folder-link\" href=\"").append(folderName).append("/\">Open folder</a>\n");
                h.append("</div>\n</div>\n\n");
            }
        }
        
        h.append("<p style=\"text-align:center;color:#aaa;font-size:11px;padding:15px\">Total: ")
            .append(allPosts.size()).append(" posts</p>\n");
        h.append("</body>\n</html>");
        
        return h;
    }
    
    // ==================== EXTRACT METHODS ====================
    
    private static String extractDate(String block) {
        Pattern p = Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        if (m.find()) {
            String dt = m.group(1);
            if (dt.contains("T")) return dt.substring(0, dt.indexOf("T"));
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
            text = text.replaceAll("<br\\s*/?>", "\n");
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
    
    // ==================== DOWNLOAD ====================
    
    private static String downloadFile(HttpClient client, String fileUrl, String destDir, String prefix) {
        try {
            String cleanUrl = fileUrl.replace("https://t.mehttps://t.me/", "https://t.me/");
            
            // Check if file with similar name already exists
            File dir = new File(destDir);
            File[] existing = dir.listFiles((f) -> f.getName().startsWith(prefix + "_"));
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cleanUrl))
                .header("User-Agent", "Mozilla/5.0")
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
                        .header("Accept", "*/*")
                        .timeout(java.time.Duration.ofMinutes(5))
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
            
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            String contentLength = response.headers().firstValue("Content-Length").orElse("-1");
            long size = -1;
            try { size = Long.parseLong(contentLength); } catch (Exception e) {}
            
            if (size > MAX_DOWNLOAD_SIZE) {
                System.out.println("    Too large: " + size);
                return null;
            }
            
            String ext = getExtension(contentType, cleanUrl);
            
            // Check if we already have this file (by same prefix and content type)
            if (existing != null && existing.length > 0) {
                System.out.println("    File already exists with this prefix, skipping download");
                return existing[0].getName(); // return existing filename
            }
            
            String fileName = prefix + "_" + System.currentTimeMillis() + ext;
            Path filePath = Paths.get(destDir, fileName);
            
            try (InputStream is = response.body()) {
                Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            long actualSize = Files.size(filePath);
            if (actualSize == 0) {
                Files.delete(filePath);
                return null;
            }
            
            if (actualSize > MAX_DOWNLOAD_SIZE) {
                Files.delete(filePath);
                return null;
            }
            
            System.out.println("    Saved: " + fileName + " (" + actualSize + " bytes)");
            return fileName;
            
        } catch (Exception e) {
            System.out.println("    Error: " + e.getMessage());
            return null;
        }
    }
    
    private static String getExtension(String contentType, String url) {
        try {
            String path = new URI(url).getPath();
            if (path.contains("?")) path = path.substring(0, path.indexOf("?"));
            String fileName = Paths.get(path).getFileName().toString();
            if (fileName.contains(".")) {
                String ext = fileName.substring(fileName.lastIndexOf("."));
                ext = ext.replaceAll("[^a-zA-Z0-9.]", "");
                if (ext.length() >= 2 && ext.length() <= 10) return ext.toLowerCase();
            }
        } catch (Exception ignored) {}
        
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
        if (ct.contains("rar")) return ".rar";
        if (ct.contains("json")) return ".json";
        if (ct.contains("xml")) return ".xml";
        if (ct.contains("text/plain")) return ".txt";
        if (ct.contains("text/")) return ".txt";
        if (ct.contains("npvt")) return ".npvt";
        
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
            .replace("&nbsp;", " ")
            .replace("<br>", "\n");
    }
}
