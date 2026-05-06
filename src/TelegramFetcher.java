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
        String html = response.body();
        System.out.println("HTML size: " + html.length() + " bytes");
        
        // Debug: save HTML to file to inspect
        try {
            Files.writeString(Paths.get("debug_" + channelUsername + ".html"), html);
            System.out.println("Debug HTML saved to debug_" + channelUsername + ".html");
        } catch (Exception e) {}
        
        return html;
    }
    
    public static List<String> extractPostIds(String html, String channelUsername) {
        List<String> postIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        // Pattern: data-post="channel/id"
        Pattern mainPattern = Pattern.compile("data-post=\"([^\"]+)/(\\d+)\"");
        Matcher matcher = mainPattern.matcher(html);
        
        while (matcher.find()) {
            String ch = matcher.group(1);
            String id = matcher.group(2);
            if (ch.equals(channelUsername) && !seen.contains(id)) {
                seen.add(id);
                postIds.add(id);
                System.out.println("Found post ID: " + id);
            }
        }
        
        // If no posts found with first pattern, try alternative
        if (postIds.isEmpty()) {
            System.out.println("Trying alternative pattern...");
            Pattern altPattern = Pattern.compile("/" + channelUsername + "/(\\d+)\"");
            Matcher altMatcher = altPattern.matcher(html);
            
            while (altMatcher.find()) {
                String id = altMatcher.group(1);
                if (!seen.contains(id)) {
                    seen.add(id);
                    postIds.add(id);
                    System.out.println("Found post ID (alt): " + id);
                }
            }
        }
        
        System.out.println("Post IDs found: " + postIds.size());
        if (!postIds.isEmpty()) {
            System.out.println("First 5 IDs: " + postIds.subList(0, Math.min(5, postIds.size())));
        }
        
        return postIds;
    }
    
    public static String extractPostBlock(String html, String channelUsername, String postId) {
        // Find the post container using data-post attribute
        String postMarker = "data-post=\"" + channelUsername + "/" + postId + "\"";
        int markerIndex = html.indexOf(postMarker);
        
        if (markerIndex == -1) {
            System.err.println("Post marker not found for ID: " + postId);
            return "";
        }
        
        // Find the containing div with class tgme_widget_message
        int startIndex = html.lastIndexOf("<div", markerIndex);
        if (startIndex == -1) startIndex = markerIndex;
        
        // Find the end of this post (next post marker or closing divs)
        int depth = 0;
        int endIndex = html.length();
        boolean inDiv = false;
        
        for (int i = startIndex; i < html.length(); i++) {
            if (i + 4 < html.length() && html.substring(i, i + 4).equals("<div")) {
                depth++;
                inDiv = true;
            } else if (i + 6 < html.length() && html.substring(i, i + 6).equals("</div>")) {
                depth--;
                if (depth == 0 && inDiv) {
                    endIndex = i + 6;
                    break;
                }
            }
        }
        
        String block = html.substring(startIndex, endIndex);
        
        // Ensure we have enough content
        if (block.length() < 100) {
            // Fallback: take 5000 chars after marker
            int fallbackEnd = Math.min(markerIndex + 5000, html.length());
            block = html.substring(markerIndex, fallbackEnd);
        }
        
        return block;
    }
}
