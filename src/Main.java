import java.net.http.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

public class Main {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Telegram Channel Archiver v6 ===");
        
        String channelUsername = System.getenv("CHANNEL_USERNAME");
        String postCountStr = System.getenv("POST_COUNT");
        
        if (channelUsername == null || channelUsername.trim().isEmpty()) {
            channelUsername = "proxymtproto";
        }
        
        int maxPosts = 100;
        if (postCountStr != null && !postCountStr.trim().isEmpty()) {
            try { maxPosts = Integer.parseInt(postCountStr.trim()); }
            catch (NumberFormatException e) { maxPosts = 100; }
        }
        
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Max posts: " + maxPosts);
        
        String channelDir = "channels/" + channelUsername;
        new File(channelDir).mkdirs();
        String indexPath = channelDir + "/index.html";
        
        Map<String, PostData> existingPosts = loadExistingPosts(channelDir);
        System.out.println("Existing posts: " + existingPosts.size());
        
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();
        
        String html = TelegramFetcher.fetchHtml(channelUsername);
        List<String> postIds = TelegramFetcher.extractPostIds(html, channelUsername);
        
        int processed = 0;
        for (String postId : postIds) {
            if (processed >= maxPosts) break;
            
            String block = TelegramFetcher.extractPostBlock(html, channelUsername, postId);
            
            String dateStr = PostExtractor.extractDate(block);
            String text = PostExtractor.extractText(block);
            String photoUrl = PostExtractor.extractPhotoUrl(block);
            String videoUrl = PostExtractor.extractVideoUrl(block);
            String documentUrl = PostExtractor.extractDocumentUrl(block);
            String documentName = PostExtractor.extractDocumentName(block);
            
            String folderName = (dateStr != null ? dateStr : "post") + "_" + postId;
            folderName = folderName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String postDir = channelDir + "/" + folderName;
            new File(postDir).mkdirs();
            
            processed++;
            System.out.print("[" + processed + "/" + Math.min(postIds.size(), maxPosts) + "] ID:" + postId);
            if (photoUrl != null) System.out.print(" [PHOTO]");
            if (videoUrl != null) System.out.print(" [VIDEO]");
            if (documentUrl != null) System.out.print(" [FILE:" + (documentName != null ? documentName : "?") + "]");
            System.out.println();
            
            savePostTxt(postDir, channelUsername, postId, dateStr, text);
            
            List<String> dl = new ArrayList<>();
            if (photoUrl != null) { String s = FileDownloader.download(client, photoUrl, postDir, "photo", null); if (s != null) dl.add(s); }
            if (videoUrl != null) { String s = FileDownloader.download(client, videoUrl, postDir, "video", null); if (s != null) dl.add(s); }
            if (documentUrl != null) { String s = FileDownloader.download(client, documentUrl, postDir, "file", documentName); if (s != null) dl.add(s); }
            
            PostData pd = existingPosts.getOrDefault(postId, new PostData());
            pd.postId = postId;
            pd.folderName = folderName;
            pd.dateStr = dateStr;
            pd.text = text.length() > 150 ? text.substring(0, 150) + "..." : text;
            if (photoUrl != null) pd.hasPhoto = true;
            if (videoUrl != null) pd.hasVideo = true;
            if (documentUrl != null) pd.hasDocument = true;
            if (!dl.isEmpty()) { Set<String> s = new LinkedHashSet<>(pd.files); s.addAll(dl); pd.files = new ArrayList<>(s); }
            
            File[] files = new File(postDir).listFiles(f -> !f.getName().equals("post.txt"));
            if (files != null) {
                Set<String> all = new LinkedHashSet<>(pd.files);
                for (File f : files) { all.add(f.getName()); }
                pd.files = new ArrayList<>(all);
            }
            
            existingPosts.put(postId, pd);
        }
        
        List<PostData> allPosts = new ArrayList<>(existingPosts.values());
        allPosts.sort((a, b) -> {
            try { return Long.compare(Long.parseLong(b.postId), Long.parseLong(a.postId)); }
            catch (Exception e) { return 0; }
        });
        
        IndexBuilder.build(channelUsername, allPosts, indexPath);
        
        System.out.println("\n==========================================");
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Online posts: " + postIds.size());
        System.out.println("Processed: " + processed);
        System.out.println("Total archived: " + allPosts.size());
        System.out.println("==========================================");
    }
    
    private static Map<String, PostData> loadExistingPosts(String channelDir) {
        Map<String, PostData> map = new LinkedHashMap<>();
        File dir = new File(channelDir);
        File[] subs = dir.listFiles(File::isDirectory);
        if (subs == null) return map;
        
        for (File sub : subs) {
            String name = sub.getName();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("_(\\d+)$").matcher(name);
            if (m.find()) {
                String id = m.group(1);
                PostData pd = new PostData();
                pd.postId = id;
                pd.folderName = name;
                
                File txt = new File(sub, "post.txt");
                if (txt.exists()) {
                    try {
                        String content = new String(Files.readAllBytes(txt.toPath()));
                        java.util.regex.Matcher dm = java.util.regex.Pattern.compile("Date: ([^\n]+)").matcher(content);
                        if (dm.find()) pd.dateStr = dm.group(1).trim();
                        String[] parts = content.split("====+\n+", 2);
                        if (parts.length > 1) pd.text = parts[1].trim();
                    } catch (Exception e) {}
                }
                
                File[] files = sub.listFiles(f -> !f.getName().equals("post.txt"));
                if (files != null) {
                    for (File f : files) {
                        pd.files.add(f.getName());
                        String n = f.getName().toLowerCase();
                        if (n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".webp")) pd.hasPhoto = true;
                        else if (n.endsWith(".mp4")) pd.hasVideo = true;
                        else pd.hasDocument = true;
                    }
                }
                map.put(id, pd);
            }
        }
        return map;
    }
    
    private static void savePostTxt(String dir, String channel, String postId, String dateStr, String text) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Channel: @").append(channel).append("\n");
        sb.append("Post ID: ").append(postId).append("\n");
        sb.append("Date: ").append(dateStr != null ? dateStr : "N/A").append("\n");
        sb.append("Link: https://t.me/").append(channel).append("/").append(postId).append("\n");
        sb.append("Archived: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("========================================\n\n");
        if (!text.isEmpty()) sb.append(text).append("\n");
        
        FileWriter fw = new FileWriter(dir + "/post.txt");
        fw.write(sb.toString());
        fw.close();
    }
}
