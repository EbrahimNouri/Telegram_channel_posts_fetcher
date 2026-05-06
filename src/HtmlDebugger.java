package com.telegram.archive;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class HtmlDebugger {
    
    public static void debugHtml(String html, String channelUsername) {
        System.out.println("\n=== HTML DEBUGGING ===");
        
        // Check if page is loaded correctly
        if (html.contains("Page not found")) {
            System.out.println("ERROR: Channel not found!");
        } else if (html.contains("tgme_page_title")) {
            System.out.println("Page loaded successfully");
        } else {
            System.out.println("WARNING: Page may not be loaded correctly");
        }
        
        // Check for common patterns
        String[] patternsToCheck = {
            "data-post=\"" + channelUsername,
            "/" + channelUsername + "/",
            "tgme_widget_message",
            "tgme_widget_message_text"
        };
        
        for (String pattern : patternsToCheck) {
            int count = countOccurrences(html, pattern);
            System.out.println("Pattern '" + pattern + "': " + count + " occurrences");
        }
        
        // Show first 500 chars
        System.out.println("\nFirst 500 chars of HTML:");
        System.out.println(html.substring(0, Math.min(500, html.length())));
        
        // Show a sample of post pattern
        Pattern postPattern = Pattern.compile("data-post=\"[^\"]+\"");
        Matcher matcher = postPattern.matcher(html);
        int found = 0;
        while (matcher.find() && found < 3) {
            System.out.println("Sample post attribute: " + matcher.group());
            found++;
        }
        
        System.out.println("=== END DEBUG ===\n");
    }
    
    private static int countOccurrences(String html, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = html.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
