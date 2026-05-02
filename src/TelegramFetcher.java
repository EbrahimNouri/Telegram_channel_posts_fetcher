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
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
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
        
        // Pattern 1: tgme_widget_message_date
        Pattern p1 = Pattern.compile(
            "<a[^>]*class=\"[^\"]*tgme_widget_message_date[^\"]*\"[^>]*href=\"/([^/\"]+)/(\\d+)\""
        );
        Matcher m1 = p1.matcher(html);
        while (m1.find()) {
            String ch = m1.group(1);
            String id = m1.group(2);
            if (ch.equals(channelUsername) && !seen.contains(id)) {
                seen.add(id);
                postIds.add(id);
            }
        }
        
        // Pattern 2: data-post
        Pattern p2 = Pattern.compile("data-post=\"([^/\"]+)/(\\d+)\"");
        Matcher m2 = p2.matcher(html);
        while (m2.find()) {
            String ch = m2.group(1);
            String id = m2.group(2);
            if (ch.equals(channelUsername) && !seen.contains(id)) {
                seen.add(id);
                postIds.add(id);
            }
        }
        
        System.out.println("Post IDs found: " + postIds.size());
        return postIds;
    }
    
    public static String extractPostBlock(String html, String channelUsername, String postId) {
        String search1 = "data-post=\"" + channelUsername + "/" + postId + "\"";
        String search2 = "/" + channelUsername + "/" + postId;
        
        int start = html.indexOf(search1);
        if (start == -1) {
            start = html.indexOf(search2);
            if (start > 300) start -= 300;
            else start = 0;
        }
        if (start < 0) start = 0;
        
        int end = html.indexOf("<div class=\"tgme_widget_message_wrap", start + 100);
        if (end == -1) {
            end = html.indexOf(search1, start + 10);
            if (end == -1) end = html.indexOf(search2, start + 10);
        }
        if (end == -1) end = Math.min(start + 10000, html.length());
        
        return html.substring(start, Math.min(end, html.length()));
    }
}
