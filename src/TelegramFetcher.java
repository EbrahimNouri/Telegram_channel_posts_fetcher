import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

public class TelegramFetcher {
    
    public static String fetchHtml(String channelUsername) throws Exception {
        String url = "https://t.me/s/" + channelUsername;
        
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .GET()
            .build();
        
        System.out.println("Fetching: " + url);
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        System.out.println("HTML size: " + response.body().length() + " bytes");
        
        return response.body();
    }
    
    public static List<String> extractPostIds(String html, String channelUsername) {
        List<String> postIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        // Pattern 1: data-post attribute (مطمئن‌ترین روش)
        Pattern p1 = Pattern.compile(
            "data-post=\"" + Pattern.quote(channelUsername) + "/(\\d+)\""
        );
        Matcher m1 = p1.matcher(html);
        while (m1.find()) {
            String id = m1.group(1);
            if (!seen.contains(id)) {
                seen.add(id);
                postIds.add(id);
            }
        }
        
        // Pattern 2: لینک‌های t.me
        Pattern p2 = Pattern.compile(
            "https?://t\\.me/" + Pattern.quote(channelUsername) + "/(\\d+)"
        );
        Matcher m2 = p2.matcher(html);
        while (m2.find()) {
            String id = m2.group(1);
            if (!seen.contains(id)) {
                seen.add(id);
                postIds.add(id);
            }
        }
        
        // Pattern 3: href به پست
        Pattern p3 = Pattern.compile(
            "href=\"/" + Pattern.quote(channelUsername) + "/(\\d+)\""
        );
        Matcher m3 = p3.matcher(html);
        while (m3.find()) {
            String id = m3.group(1);
            if (!seen.contains(id)) {
                seen.add(id);
                postIds.add(id);
            }
        }
        
        // Pattern 4: message container id
        Pattern p4 = Pattern.compile(
            "id=\"message[^\"]*" + Pattern.quote(channelUsername) + "-(\\d+)\""
        );
        Matcher m4 = p4.matcher(html);
        while (m4.find()) {
            String id = m4.group(1);
            if (!seen.contains(id)) {
                seen.add(id);
                postIds.add(id);
            }
        }
        
        // Pattern 5: هر اشاره به channel/id در HTML
        if (postIds.isEmpty()) {
            Pattern p5 = Pattern.compile(
                Pattern.quote(channelUsername) + "/(\\d+)"
            );
            Matcher m5 = p5.matcher(html);
            while (m5.find()) {
                String id = m5.group(1);
                if (!seen.contains(id) && Long.parseLong(id) > 0) {
                    seen.add(id);
                    postIds.add(id);
                }
            }
        }
        
        System.out.println("Post IDs found: " + postIds.size());
        return postIds;
    }
    
    public static String extractPostBlock(String html, String channelUsername, String postId) {
        // روش 1: جستجو با data-post
        String search1 = "data-post=\"" + channelUsername + "/" + postId + "\"";
        int start = html.indexOf(search1);
        
        // روش 2: جستجو با message container
        if (start == -1) {
            String searchMessage = "id=\"message-" + channelUsername + "-" + postId + "\"";
            start = html.indexOf(searchMessage);
        }
        
        // روش 3: جستجو با href
        if (start == -1) {
            String searchHref = "href=\"/" + channelUsername + "/" + postId + "\"";
            start = html.indexOf(searchHref);
            if (start > 200) start -= 200;
        }
        
        if (start < 0) start = 0;
        
        // پیدا کردن انتهای بلاک
        int end = html.indexOf("tgme_widget_message_wrap", start + 100);
        if (end == -1) {
            end = html.indexOf("data-post=\"", start + 10);
        }
        if (end == -1) {
            end = html.indexOf("id=\"message-", start + 10);
        }
        if (end == -1) {
            end = Math.min(start + 15000, html.length());
        }
        
        return html.substring(start, Math.min(end, html.length()));
    }
}
