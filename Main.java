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
        System.out.println("=== Telegram Channel Archiver v5 ===");
        
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
                            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || 
                                name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp")) {
                                pd.hasPhoto = true;
                            } else if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".ogv") || name.endsWith(".mov")) {
                                pd.hasVideo = true;
                            } else {
                                pd.hasDocument = true;
                            }
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
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .GET()
            .build();
        
        System.out.println("Fetching: " + url);
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        String html = response.body();
        System.out.println("HTML size: " + html.length() + " bytes");
        
        // ============ FIND ALL POST IDS AND LINKS ============
        List<String> postIds = new ArrayList<>();
        List<String> postLinks = new ArrayList<>();
        
        // Pattern 1: <a class="tgme_widget_message_date" href="/channelName/12345">
        Pattern linkPattern1 = Pattern.compile(
            "<a[^>]*class=\"[^\"]*tgme_widget_message_date[^\"]*\"[^>]*href=\"/([^/\"]+)/(\\d+)\""
        );
        Matcher m1 = linkPattern1.matcher(html);
        while (m1.find()) {
            String ch = m1.group(1);
            String id = m1.group(2);
            if (!postIds.contains(id)) {
                postIds.add(id);
                postLinks.add("https://t.me/" + ch + "/" + id);
            }
        }
        
        // Pattern 2: data-post="channelName/12345"
        Pattern linkPattern2 = Pattern.compile(
            "data-post=\"([^/\"]+)/(\\d+)\""
        );
        Matcher m2 = linkPattern2.matcher(html);
        while (m2.find()) {
            String ch = m2.group(1);
            String id = m2.group(2);
            if (!postIds.contains(id)) {
                postIds.add(id);
                postLinks.add("https://t.me/" + ch + "/" + id);
            }
        }
        
        // Pattern 3: /channelName/12345 in any href
        Pattern linkPattern3 = Pattern.compile(
            "href=\"/([^/\"]+)/(\\d+)\""
        );
        Matcher m3 = linkPattern3.matcher(html);
        while (m3.find()) {
            String ch = m3.group(1);
            String id = m3.group(2);
            if (ch.equals(channelUsername) && !postIds.contains(id)) {
                postIds.add(id);
                postLinks.add("https://t.me/" + ch + "/" + id);
            }
        }
        
        System.out.println("Post links found: " + postIds.size());
        
        if (postIds.isEmpty()) {
            // Save debug HTML
            FileWriter debugFw = new FileWriter("debug_" + channelUsername + ".html");
            debugFw.write(html.substring(0, Math.min(100000, html.length())));
            debugFw.close();
            System.out.println("DEBUG: No links found! Saved first 100KB to debug_" + channelUsername + ".html");
            System.out.println("Looking for any href patterns in HTML...");
            
            // Print first 20 hrefs for debugging
            Pattern anyHref = Pattern.compile("href=\"([^\"]+)\"");
            Matcher hrefMatcher = anyHref.matcher(html);
            int hrefCount = 0;
            while (hrefMatcher.find() && hrefCount < 20) {
                String href = hrefMatcher.group(1);
                if (href.contains("/") && !href.startsWith("#") && !href.startsWith("http")) {
                    System.out.println("  href: " + href);
                    hrefCount++;
                }
            }
        }
        
        // ============ PROCESS EACH POST ============
        int processed = 0;
        
        for (int i = 0; i < postIds.size() && processed < maxPosts; i++) {
            String postId = postIds.get(i);
            String postLink = postLinks.get(i);
            
            // Extract post block from HTML
            String blockSearch1 = "data-post=\"" + channelUsername + "/" + postId + "\"";
            String blockSearch2 = "/" + channelUsername + "/" + postId;
            
            int blockStart = html.indexOf(blockSearch1);
            if (blockStart == -1) {
                blockStart = html.indexOf(blockSearch2);
                if (blockStart > 300) blockStart -= 300;
                else blockStart = 0;
            }
            if (blockStart < 0) blockStart = 0;
            
            int blockEnd = html.indexOf("<div class=\"tgme_widget_message_wrap", blockStart + 100);
            if (blockEnd == -1) {
                blockEnd = html.indexOf("<div class=\"tgme_widget_message", blockStart + 100);
            }
            if (blockEnd == -1) {
                blockEnd = html.indexOf(blockSearch1, blockStart + 10);
                if (blockEnd == -1) blockEnd = html.indexOf(blockSearch2, blockStart + 10);
            }
            if (blockEnd == -1) blockEnd = Math.min(blockStart + 10000, html.length());
            
            String block = html.substring(blockStart, Math.min(blockEnd, html.length()));
            
            // Extract data
            String dateStr = extractDate(block);
            String text = extractText(block);
            String photoUrl = extractPhotoUrl(block);
            String videoUrl = extractVideoUrl(block);
            String documentUrl = extractDocumentUrl(block);
            String documentName = extractDocumentName(block);
            
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
            
            System.out.print("[" + processed + "/" + Math.min(postIds.size(), maxPosts) + "] ID:" + postId);
            if (photoUrl != null) System.out.print(" [PHOTO]");
            if (videoUrl != null) System.out.print(" [VIDEO]");
            if (documentUrl != null) {
                System.out.print(" [FILE");
                if (documentName != null) System.out.print(":" + documentName);
                System.out.print("]");
            }
            if (!text.isEmpty()) {
                System.out.print(" - " + text.substring(0, Math.min(50, text.length())).replace("\n", " "));
            }
            System.out.println();
            
            // ============ SAVE POST.TXT ============
            StringBuilder postTxt = new StringBuilder();
            postTxt.append("Channel: @").append(channelUsername).append("\n");
            postTxt.append("Post ID: ").append(postId).append("\n");
            postTxt.append("Date: ").append(dateStr != null ? dateStr : "N/A").append("\n");
            postTxt.append("Link: ").append(postLink).append("\n");
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
                String saved = downloadFile(client, photoUrl, postDir, "photo", null);
                if (saved != null) downloadedFiles.add(saved);
            }
            
            if (videoUrl != null) {
                String saved = downloadFile(client, videoUrl, postDir, "video", null);
                if (saved != null) downloadedFiles.add(saved);
            }
            
            if (documentUrl != null) {
                String saved = downloadFile(client, documentUrl, postDir, "file", documentName);
                if (saved != null) downloadedFiles.add(saved);
            }
            
            // ============ STORE POST DATA ============
            PostData pd = existingPostsMap.getOrDefault(postId, new PostData());
            pd.postId = postId;
            pd.folderName = folderName;
            pd.dateStr = dateStr;
            pd.text = text.length() > 150 ? text.substring(0, 150) + "..." : text;
            
            if (photoUrl != null) pd.hasPhoto = true;
            if (videoUrl != null) pd.hasVideo = true;
            if (documentUrl != null) pd.hasDocument = true;
            
            // Add new files
            if (!downloadedFiles.isEmpty()) {
                Set<String> allFiles = new LinkedHashSet<>(pd.files);
                allFiles.addAll(downloadedFiles);
                pd.files = new ArrayList<>(allFiles);
            }
            
            // Also scan folder for any existing files we might have missed
            File currentPostDir = new File(postDir);
            File[] allFilesInDir = currentPostDir.listFiles((f) -> !f.getName().equals("post.txt"));
            if (allFilesInDir != null) {
                Set<String> allFiles = new LinkedHashSet<>(pd.files);
                for (File f : allFilesInDir) {
                    allFiles.add(f.getName());
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || 
                        name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp")) {
                        pd.hasPhoto = true;
                    } else if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".ogv") || name.endsWith(".mov")) {
                        pd.hasVideo = true;
                    } else if (!name.equals("post.txt")) {
                        pd.hasDocument = true;
                    }
                }
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
        
        StringBuilder indexHtml = buildIndexHtml(channelUsername, allPosts);
        
        FileWriter idxFw = new FileWriter(indexPath);
        idxFw.write(indexHtml.toString());
        idxFw.close();
        
        // ============ SUMMARY ============
        System.out.println("\n==========================================");
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Posts found online: " + postIds.size());
        System.out.println("Posts processed this run: " + processed);
        System.out.println("Total posts in archive: " + allPosts.size());
        System.out.println("Total files in archive: " + allPosts.stream().mapToInt(p -> p.files.size()).sum());
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
        h.append(".header{background:linear-gradient(135deg,#2a9d8f,#1d7a6e);color:white;padding:22px;border-radius:14px;text-align:center;margin-bottom:18px;box-shadow:0 2px 8px rgba(0,0,0,0.1)}\n");
        h.append(".header h1{font-size:20px}\n");
        h.append(".header .stats{font-size:12px;opacity:0.9;margin-top:4px}\n");
        h.append(".post-card{background:white;border-radius:10px;padding:14px;margin-bottom:8px;box-shadow:0 1px 2px rgba(0,0,0,0.06);display:flex;gap:12px;align-items:flex-start;border-right:3px solid transparent;transition:0.2s}\n");
        h.append(".post-card:hover{border-right-color:#2a9d8f;box-shadow:0 3px 10px rgba(0,0,0,0.1)}\n");
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
                if (pd.hasPhoto && pd.hasVideo) icon = "🎬";
                else if (pd.hasPhoto) icon = "🖼️";
                else if (pd.hasVideo) icon = "🎥";
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
    
    private static String extractDocumentName(String block) {
        Pattern p = Pattern.compile(
            "<span\\s+class=\"tgme_widget_message_document_title[^\"]*\"[^>]*>(.*?)</span>"
        );
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1).trim();
        return null;
    }
    
    // ==================== DOWNLOAD ====================
    
    private static String downloadFile(HttpClient client, String fileUrl, String destDir, String prefix, String originalFileName) {
        try {
            String cleanUrl = fileUrl.replace("https://t.mehttps://t.me/", "https://t.me/");
            
            System.out.println("    Downloading: " + cleanUrl);
            
            // Check if file already exists in this directory
            File dir = new File(destDir);
            File[] existing = dir.listFiles((f) -> !f.getName().equals("post.txt"));
            
            if (existing != null && existing.length > 0) {
                boolean hasValidFile = false;
                for (File f : existing) {
                    if (f.length() > 0) {
                        hasValidFile = true;
                        break;
                    }
                }
                if (hasValidFile) {
                    System.out.println("    Already have file(s), skipping download");
                    return existing[0].getName();
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
            
            // Follow redirects manually if needed
            if (statusCode == 301 || statusCode == 302 || statusCode == 307 || statusCode == 308) {
                String redirectUrl = response.headers().firstValue("Location").orElse(null);
                if (redirectUrl != null) {
                    if (!redirectUrl.startsWith("http")) redirectUrl = "https://t.me" + redirectUrl;
                    System.out.println("    Following redirect: " + redirectUrl);
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
                System.out.println("    HTTP " + statusCode + " - skipping");
                return null;
            }
            
            // ============ DETERMINE FILENAME ============
            String fileName;
            
            // Priority 1: Use original filename from Telegram document title
            if (originalFileName != null && !originalFileName.isEmpty()) {
                fileName = originalFileName.replaceAll("[^a-zA-Z0-9آ-ی_\\-.]", "_");
                // Remove double underscores
                fileName = fileName.replaceAll("_+", "_");
                // If no extension, try to get from URL
                if (!fileName.contains(".")) {
                    String extFromUrl = getExtensionFromUrl(cleanUrl);
                    if (!extFromUrl.isEmpty()) {
                        fileName += extFromUrl;
                    }
                }
            } else {
                // Priority 2: Use filename from URL
                String urlFileName = getFileNameFromUrl(cleanUrl);
                if (urlFileName != null && !urlFileName.isEmpty()) {
                    fileName = urlFileName;
                } else {
                    // Priority 3: Generate with prefix and timestamp
                    String contentType = response.headers().firstValue("Content-Type").orElse("");
                    String ext = getExtension(contentType, cleanUrl);
                    fileName = prefix + "_" + System.currentTimeMillis() + ext;
                }
            }
            
            System.out.println("    Filename: " + fileName);
            
            Path filePath = Paths.get(destDir, fileName);
            
            // Download file
            try (InputStream is = response.body()) {
                Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            long actualSize = Files.size(filePath);
            
            // Check if empty
            if (actualSize == 0) {
                Files.delete(filePath);
                System.out.println("    Empty file, deleted");
                return null;
            }
            
            // Check if too large
            if (actualSize > MAX_DOWNLOAD_SIZE) {
                Files.delete(filePath);
                System.out.println("    Too large (" + actualSize + " bytes), deleted");
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
            String fileName = Paths.get(path).getFileName().toString();
            if (fileName != null && !fileName.isEmpty() && fileName.length() > 3) {
                // Clean filename
                fileName = fileName.replaceAll("[^a-zA-Z0-9آ-ی_\\-.]", "_");
                return fileName;
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    private static String getExtensionFromUrl(String url) {
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
        return "";
    }
    
    private static String getExtension(String contentType, String url) {
        // First try to get extension from URL
        String urlExt = getExtensionFromUrl(url);
        if (!urlExt.isEmpty()) return urlExt;
        
        if (contentType == null) return "";
        
        String ct = contentType.toLowerCase();
        
        // VPN config files
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
        if (ct.contains("ogg") || ct.contains("ogv")) return ".ogv";
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
        if (ct.contains("javascript") || ct.contains("ecmascript")) return ".js";
        if (ct.contains("text/plain")) return ".txt";
        if (ct.contains("text/")) return ".txt";
        
        // Office
        if (ct.contains("word") || ct.contains("docx")) return ".docx";
        if (ct.contains("excel") || ct.contains("xlsx") || ct.contains("spreadsheet")) return ".xlsx";
        if (ct.contains("powerpoint") || ct.contains("pptx") || ct.contains("presentation")) return ".pptx";
        
        // Open Document
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
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
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
