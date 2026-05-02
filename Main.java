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
        System.out.println("=== Telegram Channel Archiver v3 - No Skip ===");
        
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
        System.out.println("Mode: Always fetch (no skip)");
        
        // ============ LOAD EXISTING POSTS FOR INDEX MERGING ============
        // We still read old posts to merge them in index.html
        // But we DO NOT skip any post - we re-download everything
        Set<String> oldPostIdsFromFolders = new LinkedHashSet<>();
        List<PostData> oldPostsForIndex = new ArrayList<>();
        
        File channelFolder = new File(channelDir);
        File[] subDirs = channelFolder.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                String dirName = subDir.getName();
                Pattern dirIdPattern = Pattern.compile("_(\\d+)$");
                Matcher dirIdMatcher = dirIdPattern.matcher(dirName);
                if (dirIdMatcher.find()) {
                    String id = dirIdMatcher.group(1);
                    if (!oldPostIdsFromFolders.contains(id)) {
                        oldPostIdsFromFolders.add(id);
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
                            } catch (Exception e) {
                                // ignore corrupted files
                            }
                        }
                        
                        // Check for media files in folder
                        File[] mediaFiles = subDir.listFiles((f) -> !f.getName().equals("post.txt"));
                        if (mediaFiles != null) {
                            for (File mf : mediaFiles) {
                                pd.files.add(mf.getName());
                                String name = mf.getName().toLowerCase();
                                if (name.contains("photo") || name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg")) pd.hasPhoto = true;
                                if (name.contains("video") || name.endsWith(".mp4")) pd.hasVideo = true;
                                if (!pd.hasPhoto && !pd.hasVideo) pd.hasDocument = true;
                            }
                        }
                        
                        oldPostsForIndex.add(pd);
                    }
                }
            }
        }
        
        System.out.println("Old posts found in archive: " + oldPostsForIndex.size());
        
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
        
        // ============ FIND ALL POST LINKS ============
        Pattern allLinksPattern = Pattern.compile(
            "<a class=\"tgme_widget_message_date\" href=\"/([^/]+)/(\\d+)\">"
        );
        
        Matcher linkMatcher = allLinksPattern.matcher(html);
        
        List<String[]> allPostLinks = new ArrayList<>();
        while (linkMatcher.find()) {
            String ch = linkMatcher.group(1);
            String id = linkMatcher.group(2);
            allPostLinks.add(new String[]{ch, id});
        }
        
        System.out.println("Total message links found: " + allPostLinks.size());
        
        // ============ SPLIT HTML INTO MESSAGE BLOCKS ============
        String[] messageBlocks = html.split("<div class=\"tgme_widget_message_wrap");
        
        System.out.println("Message blocks found: " + (messageBlocks.length - 1));
        
        // ============ PROCESS EACH POST - NO SKIPPING ============
        Map<String, PostData> newPostsMap = new LinkedHashMap<>(); // postId -> PostData
        int checked = 0;
        
        for (int i = 1; i < messageBlocks.length && checked < maxPosts; i++) {
            String block = messageBlocks[i];
            
            // Extract post ID from this block
            Pattern idPattern = Pattern.compile("<a class=\"tgme_widget_message_date\" href=\"/[^/]+/(\\d+)\">");
            Matcher idMatcher = idPattern.matcher(block);
            
            if (!idMatcher.find()) continue;
            
            String postId = idMatcher.group(1);
            
            // NEVER SKIP - always process
            checked++;
            
            // Extract post data from this block
            String postLink = "https://t.me/" + channelUsername + "/" + postId;
            String dateStr = extractDate(block);
            String text = extractText(block);
            String photoUrl = extractPhotoUrl(block);
            String videoUrl = extractVideoUrl(block);
            String documentUrl = extractDocumentUrl(block);
            String documentName = extractDocumentName(block);
            
            // ============ CREATE POST DIRECTORY ============
            String folderName;
            if (dateStr != null) {
                folderName = dateStr + "_" + postId;
            } else {
                folderName = "post_" + postId;
            }
            folderName = folderName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            
            String postDir = channelDir + "/" + folderName;
            new File(postDir).mkdirs();
            
            System.out.println("[" + (newPostsMap.size() + 1) + "] " + folderName + 
                (photoUrl != null ? " [PHOTO]" : "") + 
                (videoUrl != null ? " [VIDEO]" : "") + 
                (documentUrl != null ? " [FILE]" : "") + 
                (text.isEmpty() ? "" : " - " + text.substring(0, Math.min(40, text.length())) + "..."));
            
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
            
            // ============ STORE POST DATA ============
            PostData pd = new PostData();
            pd.folderName = folderName;
            pd.postId = postId;
            pd.dateStr = dateStr;
            pd.text = text.length() > 150 ? text.substring(0, 150) + "..." : text;
            pd.files = downloadedFiles;
            pd.hasPhoto = photoUrl != null;
            pd.hasVideo = videoUrl != null;
            pd.hasDocument = documentUrl != null;
            
            newPostsMap.put(postId, pd);
        }
        
        // ============ MERGE ALL POSTS (old + new, prefer new) ============
        List<PostData> allPosts = new ArrayList<>();
        
        // Add new posts first
        allPosts.addAll(newPostsMap.values());
        
        // Add old posts that are not in new posts
        for (PostData oldPd : oldPostsForIndex) {
            if (!newPostsMap.containsKey(oldPd.postId)) {
                allPosts.add(oldPd);
            }
        }
        
        // Sort by post ID descending (newest first)
        allPosts.sort((a, b) -> {
            try {
                return Long.compare(Long.parseLong(b.postId), Long.parseLong(a.postId));
            } catch (Exception e) {
                return 0;
            }
        });
        
        System.out.println("\nMerged: " + newPostsMap.size() + " new + " + 
            (allPosts.size() - newPostsMap.size()) + " old = " + allPosts.size() + " total");
        
        // ============ REBUILD INDEX.HTML ============
        StringBuilder indexHtml = new StringBuilder();
        indexHtml.append("<!DOCTYPE html>\n<html dir=\"rtl\">\n<head>\n");
        indexHtml.append("<meta charset=\"UTF-8\">\n");
        indexHtml.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        indexHtml.append("<title>@").append(channelUsername).append(" - Archive</title>\n");
        indexHtml.append("<style>\n");
        indexHtml.append("* { margin: 0; padding: 0; box-sizing: border-box; }\n");
        indexHtml.append("body { font-family: Tahoma, sans-serif; max-width: 750px; margin: 0 auto; padding: 12px; background: #e8eaed; }\n");
        indexHtml.append(".header { background: linear-gradient(135deg, #2a9d8f, #1d7a6e); color: white; padding: 22px; border-radius: 14px; text-align: center; margin-bottom: 18px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n");
        indexHtml.append(".header h1 { font-size: 20px; }\n");
        indexHtml.append(".header .stats { font-size: 12px; opacity: 0.9; margin-top: 4px; }\n");
        indexHtml.append(".post-card { background: white; border-radius: 10px; padding: 14px; margin-bottom: 8px; box-shadow: 0 1px 2px rgba(0,0,0,0.06); display: flex; gap: 12px; align-items: flex-start; border-right: 3px solid transparent; transition: 0.2s; }\n");
        indexHtml.append(".post-card:hover { border-right-color: #2a9d8f; box-shadow: 0 3px 10px rgba(0,0,0,0.1); }\n");
        indexHtml.append(".post-icon { font-size: 28px; min-width: 36px; text-align: center; line-height: 1; }\n");
        indexHtml.append(".post-info { flex: 1; min-width: 0; }\n");
        indexHtml.append(".post-date { font-size: 10px; color: #999; margin-bottom: 4px; font-weight: bold; }\n");
        indexHtml.append(".post-text { font-size: 12px; color: #444; line-height: 1.7; word-wrap: break-word; max-height: 80px; overflow: hidden; }\n");
        indexHtml.append(".post-files { margin-top: 7px; display: flex; flex-wrap: wrap; gap: 4px; }\n");
        indexHtml.append(".file-badge { display: inline-block; background: #e8f5e9; color: #2a9d8f; padding: 3px 9px; border-radius: 10px; font-size: 10px; text-decoration: none; border: 1px solid #c8e6c9; }\n");
        indexHtml.append(".file-badge:hover { background: #c8e6c9; }\n");
        indexHtml.append(".folder-link { display: inline-block; color: #2a9d8f; font-size: 10px; margin-top: 4px; text-decoration: none; }\n");
        indexHtml.append(".empty { text-align: center; color: #999; padding: 40px; }\n");
        indexHtml.append("</style>\n</head>\n<body>\n\n");
        
        // Header
        indexHtml.append("<div class=\"header\">\n");
        indexHtml.append("<h1>@").append(channelUsername).append("</h1>\n");
        indexHtml.append("<div class=\"stats\">").append(allPosts.size()).append(" posts | Updated: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))).append("</div>\n");
        indexHtml.append("</div>\n\n");
        
        // Post cards
        if (allPosts.isEmpty()) {
            indexHtml.append("<div class=\"empty\">No posts archived yet.</div>\n");
        } else {
            for (PostData pd : allPosts) {
                if (pd.postId == null) continue;
                
                String postIdSafe = pd.postId != null ? pd.postId : "0";
                String dateStr = pd.dateStr != null ? pd.dateStr : "";
                String textPreview = pd.text != null ? pd.text : "";
                
                // Determine folder name
                String folderName = pd.folderName;
                if (folderName == null) {
                    folderName = (dateStr.isEmpty() ? "post_" : dateStr + "_") + postIdSafe;
                    folderName = folderName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                }
                
                indexHtml.append("<div class=\"post-card\" data-post-id=\"").append(postIdSafe).append("\">\n");
                
                // Icon
                String icon = "📝";
                if (pd.hasPhoto) icon = "🖼️";
                else if (pd.hasVideo) icon = "🎬";
                else if (pd.hasDocument) icon = "📎";
                
                indexHtml.append("<div class=\"post-icon\">").append(icon).append("</div>\n");
                indexHtml.append("<div class=\"post-info\">\n");
                
                if (!dateStr.isEmpty()) {
                    indexHtml.append("<div class=\"post-date\">").append(dateStr).append("</div>\n");
                }
                
                if (!textPreview.isEmpty()) {
                    indexHtml.append("<div class=\"post-text\">").append(escapeHtml(textPreview)).append("</div>\n");
                }
                
                // Files
                if (pd.files != null && !pd.files.isEmpty()) {
                    indexHtml.append("<div class=\"post-files\">\n");
                    for (String f : pd.files) {
                        indexHtml.append("<a class=\"file-badge\" href=\"").append(folderName).append("/").append(f).append("\">")
                            .append(escapeHtml(f)).append("</a>\n");
                    }
                    indexHtml.append("</div>\n");
                }
                
                indexHtml.append("<a class=\"folder-link\" href=\"").append(folderName).append("/\">Open folder</a>\n");
                indexHtml.append("</div>\n</div>\n\n");
            }
        }
        
        indexHtml.append("<p style=\"text-align:center;color:#aaa;font-size:11px;padding:15px;\">Total: ")
            .append(allPosts.size()).append(" posts</p>\n");
        indexHtml.append("</body>\n</html>");
        
        // Save index.html
        FileWriter idxFw = new FileWriter(indexPath);
        idxFw.write(indexHtml.toString());
        idxFw.close();
        
        // ============ SUMMARY ============
        System.out.println("\n==========================================");
        System.out.println("Channel: @" + channelUsername);
        System.out.println("HTML message links found: " + allPostLinks.size());
        System.out.println("Message blocks: " + (messageBlocks.length - 1));
        System.out.println("Posts processed this run: " + newPostsMap.size());
        System.out.println("Old posts from archive: " + (allPosts.size() - newPostsMap.size()));
        System.out.println("Total in index: " + allPosts.size());
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
    
    private static String extractDate(String block) {
        Pattern p = Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        if (m.find()) {
            String dt = m.group(1);
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
    
    private static String downloadFile(HttpClient client, String fileUrl, String destDir, String prefix) {
        try {
            String cleanUrl = fileUrl.replace("https://t.mehttps://t.me/", "https://t.me/");
            
            System.out.println("    Downloading: " + cleanUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cleanUrl))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "*/*")
                .timeout(java.time.Duration.ofMinutes(5))
                .GET()
                .build();
            
            HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream()
            );
            
            int statusCode = response.statusCode();
            if (statusCode == 301 || statusCode == 302 || statusCode == 307 || statusCode == 308) {
                String redirectUrl = response.headers().firstValue("Location").orElse(null);
                if (redirectUrl != null) {
                    if (!redirectUrl.startsWith("http")) {
                        redirectUrl = "https://t.me" + redirectUrl;
                    }
                    System.out.println("    Following redirect to: " + redirectUrl);
                    request = HttpRequest.newBuilder()
                        .uri(URI.create(redirectUrl))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
            
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            String contentLength = response.headers().firstValue("Content-Length").orElse("-1");
            long size = -1;
            try { size = Long.parseLong(contentLength); } catch (Exception e) {}
            
            System.out.println("    Content-Type: " + contentType + ", Size: " + (size > 0 ? size + " bytes" : "unknown"));
            
            if (size > MAX_DOWNLOAD_SIZE) {
                System.out.println("    Too large (" + size + " bytes), skipping");
                return null;
            }
            
            String ext = getExtension(contentType, cleanUrl);
            String fileName = prefix + "_" + System.currentTimeMillis() + ext;
            Path filePath = Paths.get(destDir, fileName);
            
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
                System.out.println("    Downloaded file too large (" + actualSize + " bytes), deleted");
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
                if (ext.length() >= 2 && ext.length() <= 10) {
                    System.out.println("    Extension from URL: " + ext);
                    return ext.toLowerCase();
                }
            }
        } catch (Exception ignored) {}
        
        if (contentType == null) return "";
        
        String ct = contentType.toLowerCase();
        
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("svg")) return ".svg";
        if (ct.contains("bmp")) return ".bmp";
        if (ct.contains("ico") || ct.contains("icon")) return ".ico";
        
        if (ct.contains("mp4") || ct.contains("video/mp4")) return ".mp4";
        if (ct.contains("webm")) return ".webm";
        if (ct.contains("ogg") || ct.contains("ogv")) return ".ogv";
        if (ct.contains("video/")) return ".mp4";
        
        if (ct.contains("mp3") || ct.contains("mpeg")) return ".mp3";
        if (ct.contains("wav")) return ".wav";
        if (ct.contains("flac")) return ".flac";
        if (ct.contains("aac")) return ".aac";
        if (ct.contains("audio/")) return ".mp3";
        
        if (ct.contains("pdf")) return ".pdf";
        if (ct.contains("zip")) return ".zip";
        if (ct.contains("rar")) return ".rar";
        if (ct.contains("7z") || ct.contains("7-zip")) return ".7z";
        if (ct.contains("tar")) return ".tar";
        if (ct.contains("gz") || ct.contains("gzip")) return ".gz";
        
        if (ct.contains("json")) return ".json";
        if (ct.contains("xml")) return ".xml";
        if (ct.contains("html")) return ".html";
        if (ct.contains("css")) return ".css";
        if (ct.contains("javascript") || ct.contains("ecmascript")) return ".js";
        if (ct.contains("text/plain")) return ".txt";
        if (ct.contains("text/")) return ".txt";
        
        if (ct.contains("word") || ct.contains("docx")) return ".docx";
        if (ct.contains("excel") || ct.contains("xlsx") || ct.contains("spreadsheet")) return ".xlsx";
        if (ct.contains("powerpoint") || ct.contains("pptx") || ct.contains("presentation")) return ".pptx";
        
        if (ct.contains("opendocument.text")) return ".odt";
        if (ct.contains("opendocument.spreadsheet")) return ".ods";
        
        if (ct.contains("exe") || ct.contains("x-msdownload")) return ".exe";
        if (ct.contains("apk") || ct.contains("android")) return ".apk";
        
        if (ct.contains("octet-stream") || ct.contains("binary")) return ".bin";
        
        System.out.println("    Unknown content type: " + ct);
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
