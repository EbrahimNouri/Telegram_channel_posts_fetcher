import java.util.regex.*;

public class PostExtractor {
    
    public static String extractDate(String block) {
        Pattern p = Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        if (m.find()) {
            String dt = m.group(1);
            if (dt.contains("T")) return dt.substring(0, dt.indexOf("T"));
            return dt;
        }
        return null;
    }
    
    public static String extractText(String block) {
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
    
    public static String extractPhotoUrl(String block) {
        Pattern p = Pattern.compile(
            "tgme_widget_message_photo_wrap[^\"]*\"[^>]*style=\"[^\"]*background-image:url\\('([^']+)'\\)"
        );
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1);
        return null;
    }
    
    public static String extractVideoUrl(String block) {
        Pattern p = Pattern.compile("<video[^>]*src=\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        if (m.find()) return m.group(1);
        return null;
    }
    
    public static String extractDocumentUrl(String block) {
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
public static String extractDocumentName(String block) {
    // Pattern 1: Exact match for document title
    Pattern p1 = Pattern.compile(
        "<span\\s+class=\"tgme_widget_message_document_title[^\"]*\"[^>]*>(.*?)</span>",
        Pattern.DOTALL
    );
    Matcher m1 = p1.matcher(block);
    if (m1.find()) {
        String name = m1.group(1).trim();
        name = name.replaceAll("<[^>]+>", "");
        name = htmlUnescape(name);
        
        // CLEAN: Remove any size information that might be attached
        // Remove patterns like "11.2 KB", "3.6kb", "6 KB" from the end
        name = name.replaceAll("\\s+\\d+\\.?\\d*\\s*[KkMmGg][Bb]\\s*$", "");
        name = name.replaceAll("\\s+\\d+\\s*[KkMmGg][Bb]\\s*$", "");
        // Remove trailing spaces and dots
        name = name.trim().replaceAll("[.\\s]+$", "");
        
        if (!name.isEmpty()) return name;
    }
    
    // Try other patterns if first fails
    Pattern p2 = Pattern.compile(
        "class=\"tgme_widget_message_document[^\"]*\"[^>]*href=\"[^\"]*\"[^>]*>.*?<span[^>]*>(.*?)</span>",
        Pattern.DOTALL
    );
    Matcher m2 = p2.matcher(block);
    if (m2.find()) {
        String name = m2.group(1).trim();
        name = name.replaceAll("<[^>]+>", "");
        name = htmlUnescape(name);
        name = name.replaceAll("\\s+\\d+\\.?\\d*\\s*[KkMmGg][Bb]\\s*$", "");
        name = name.trim().replaceAll("[.\\s]+$", "");
        if (!name.isEmpty()) return name;
    }
    
    return null;
}
    
    public static String htmlUnescape(String input) {
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
