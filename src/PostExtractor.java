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
        // Pattern 1: Direct document link with href
        Pattern p1 = Pattern.compile(
            "<a\\s+class=\"tgme_widget_message_document[^\"]*\"\\s+href=\"([^\"]+)\""
        );
        Matcher m1 = p1.matcher(block);
        if (m1.find()) {
            String link = m1.group(1);
            // Clean double URLs
            link = link.replace("https://t.mehttps://t.me/", "https://t.me/");
            if (!link.startsWith("http")) link = "https://t.me" + link;
            return link;
        }
        
        // Pattern 2: Look for cdn link or download link
        Pattern p2 = Pattern.compile(
            "(?:href|src|data-url)\\s*=\\s*[\"']([^\"']*(?:cdn|telesco|download)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m2 = p2.matcher(block);
        if (m2.find()) {
            return m2.group(1);
        }
        
        return null;
    }
    
    public static String extractDocumentName(String block) {
        // Pattern 1: Exact match for document title span
        Pattern p1 = Pattern.compile(
            "<span\\s+class=\"tgme_widget_message_document_title[^\"]*\"[^>]*>(.*?)</span>",
            Pattern.DOTALL
        );
        Matcher m1 = p1.matcher(block);
        if (m1.find()) {
            String name = m1.group(1).trim();
            name = name.replaceAll("<[^>]+>", "");
            name = htmlUnescape(name);
            // CLEAN: Remove any size information attached at the end
            name = name.replaceAll("\\s+\\d+\\.?\\d*\\s*[KkMmGg][Bb]\\s*$", "");
            name = name.replaceAll("\\s+\\d+\\s*[KkMmGg][Bb]\\s*$", "");
            name = name.trim().replaceAll("[.\\s]+$", "");
            if (!name.isEmpty()) return name;
        }
        
        // Pattern 2: Generic document span
        Pattern p2 = Pattern.compile(
            "class=\"tgme_widget_message_document[^\"]*\"[^>]*href=\"[^\"]*\"[^>]*>\\s*<span[^>]*>(.*?)</span>",
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
