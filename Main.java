import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.*;
import java.time.*;
import java.time.format.*;
import java.util.regex.*;
import org.json.*;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello and welcome!");
        
        // آدرس کانال تلگرام (عمومی)
        String channelUsername = "proxymtproto"; // بدون @
        
        // روش ۱: استفاده از t.me/s/
        String url = "https://t.me/s/" + channelUsername;
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        
        System.out.println("Fetching posts from: " + channelUsername);
        System.out.println("=================================");
        
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        String html = response.body();
        
        // استخراج پست‌ها از HTML
        Pattern postPattern = Pattern.compile("<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);
        Pattern datePattern = Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"[^>]*>");
        Pattern linkPattern = Pattern.compile("<a class=\"tgme_widget_message_date\" href=\"([^\"]+)\">");
        
        Matcher postMatcher = postPattern.matcher(html);
        Matcher dateMatcher = datePattern.matcher(html);
        Matcher linkMatcher = linkPattern.matcher(html);
        
        int count = 0;
        while (postMatcher.find() && count < 10) {
            count++;
            String post = postMatcher.group(1);
            // پاک کردن تگ‌های HTML
            post = post.replaceAll("<br/?>", "\n");
            post = post.replaceAll("<[^>]+>", "").trim();
            post = org.apache.commons.text.StringEscapeUtils.unescapeHtml4(post);
            
            System.out.println("Post #" + count + ":");
            System.out.println(post);
            System.out.println("---------------------------------");
        }
        
        for (int i = 1; i <= 5; i++) {
            System.out.println("i = " + i);
        }
    }
}
