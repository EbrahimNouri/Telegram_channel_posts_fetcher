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
        System.out.println("Hello and welcome!");
        
        String channelUsername = "your_channel"; // بدون @
        String logFile = "posts_log.txt";
        
        // لاگ فعلی رو می‌خونیم که پست‌های تکراری رو تشخیص بدیم
        String existingContent = "";
        File file = new File(logFile);
        if (file.exists()) {
            existingContent = new String(Files.readAllBytes(file.toPath()));
        }
        
        String url = "https://t.me/s/" + channelUsername;
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build();
        
        System.out.println("Fetching posts from: " + channelUsername);
        
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        String html = response.body();
        
        // استخراج متن و لینک پست‌ها
        Pattern postPattern = Pattern.compile(
            "<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", 
            Pattern.DOTALL
        );
        Pattern datePattern = Pattern.compile(
            "<time[^>]*datetime=\"([^\"]+)\"[^>]*>"
        );
        Pattern linkPattern = Pattern.compile(
            "<a class=\"tgme_widget_message_date\" href=\"([^\"]+)\">"
        );
        
        Matcher postMatcher = postPattern.matcher(html);
        Matcher linkMatcher = linkPattern.matcher(html);
        
        StringBuilder newPosts = new StringBuilder();
        newPosts.append("\n========================================\n");
        newPosts.append("📅 اجرای جدید: " + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        ));
        newPosts.append("\n========================================\n\n");
        
        int count = 0;
        int newCount = 0;
        
        while (postMatcher.find() && linkMatcher.find() && count < 10) {
            count++;
            String post = postMatcher.group(1);
            String link = "https://t.me" + linkMatcher.group(1);
            
            // پاک کردن تگ‌های HTML
            post = post.replaceAll("<br/?>", "\n");
            post = post.replaceAll("<[^>]+>", "").trim();
            post = htmlUnescape(post);
            
            // چک کردن تکراری نبودن
            if (!existingContent.contains(link)) {
                newCount++;
                newPosts.append("📝 پست #").append(count).append(":\n");
                newPosts.append(post).append("\n");
                newPosts.append("🔗 ").append(link).append("\n");
                newPosts.append("----------------------------------------\n\n");
                
                System.out.println("New post found: #" + count);
            }
        }
        
        if (newCount == 0) {
            newPosts.append("⚠️ هیچ پست جدیدی پیدا نشد!\n\n");
            System.out.println("No new posts found.");
        } else {
            newPosts.append("✅ تعداد پست‌های جدید: ").append(newCount).append("\n\n");
            System.out.println("Added " + newCount + " new posts.");
        }
        
        // اضافه کردن به فایل
        FileWriter fw = new FileWriter(logFile, true); // true = append mode
        fw.write(newPosts.toString());
        fw.close();
        
        System.out.println("Log saved to: " + logFile);
        
        // Upload artifact for GitHub Actions
        System.out.println("::set-output name=logfile::" + logFile);
        
        for (int i = 1; i <= 5; i++) {
            System.out.println("i = " + i);
        }
    }
    
    private static String htmlUnescape(String input) {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ");
    }
}
