import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.*;
import java.time.*;
import java.time.format.*;
import java.util.regex.*;
import java.io.*;
import java.nio.file.*;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Telegram channel reader...");
        
        String channelUsername = System.getenv("CHANNEL_USERNAME");
        String postCountStr = System.getenv("POST_COUNT");
        
        // اگه ورودی نداد، پیش‌فرض proxymtproto و ۱۰۰ پست
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
        
        System.out.println("Channel: @" + channelUsername);
        System.out.println("Fetching up to: " + maxPosts + " posts");
        
        String logFile = "posts_log.txt";
        
        // Read existing log
        String existingContent = "";
        File file = new File(logFile);
        if (file.exists()) {
            existingContent = new String(Files.readAllBytes(file.toPath()));
        }
        
        String url = "https://t.me/s/" + channelUsername;
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .GET()
            .build();
        
        System.out.println("URL: " + url);
        
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        String html = response.body();
        
        // Extract posts
        Pattern postPattern = Pattern.compile(
            "<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", 
            Pattern.DOTALL
        );
        Pattern linkPattern = Pattern.compile(
            "<a class=\"tgme_widget_message_date\" href=\"([^\"]+)\">"
        );
        
        Matcher postMatcher = postPattern.matcher(html);
        Matcher linkMatcher = linkPattern.matcher(html);
        
        StringBuilder newPosts = new StringBuilder();
        newPosts.append("\n========================================\n");
        newPosts.append("Channel: @").append(channelUsername).append("\n");
        newPosts.append("Run time: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        ));
        newPosts.append("\n========================================\n\n");
        
        int count = 0;
        int newCount = 0;
        
        while (postMatcher.find() && linkMatcher.find() && count < maxPosts) {
            count++;
            String post = postMatcher.group(1);
            String link = "https://t.me" + linkMatcher.group(1);
            
            // Clean HTML
            post = post.replaceAll("<br/?>", " ");
            post = post.replaceAll("<[^>]+>", "").trim();
            post = htmlUnescape(post);
            
            // Check for duplicates
            if (!existingContent.contains(link)) {
                newCount++;
                newPosts.append("Post #").append(count).append(":\n");
                newPosts.append(post).append("\n");
                newPosts.append("Link: ").append(link).append("\n");
                newPosts.append("----------------------------------------\n\n");
                
                System.out.println("New: #" + count + " -> " + post.substring(0, Math.min(50, post.length())) + "...");
            }
        }
        
        if (newCount == 0) {
            newPosts.append("No new posts. Checked: ").append(count).append(" posts\n\n");
        } else {
            newPosts.append("New: ").append(newCount).append(" / Checked: ").append(count).append("\n\n");
        }
        
        // Save
        FileWriter fw = new FileWriter(logFile, true);
        fw.write(newPosts.toString());
        fw.close();
        
        System.out.println("\nDone! New: " + newCount + ", Checked: " + count);
        System.out.println("Saved to: " + logFile);
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
