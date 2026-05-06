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
        // Try multiple patterns
        String name = null;
        
        // Pattern 1: document title span
        Pattern p1 = Pattern.compile(
            "<span\\s+class=\"tgme_widget_message_document_title[^\"]*\"[^>]*>(.*?)</span>",
            Pattern.DOTALL
        );
        Matcher m1 = p1.matcher(block);
        if (m1.find()) {
            name = m1.group(1).trim();
            name = name.replaceAll("<[^>]+>", "");
            name = htmlUnescape(name);
            if (!name.isEmpty() && !name.matches("\\d+")) return name;
        }
        
        // Pattern 2: document link text
        Pattern p2 = Pattern.compile(
            "class=\"tgme_widget_message_document[^\"]*\"[^>]*href=\"[^\"]*\"[^>]*>\\s*<[^>]+>\\s*(.*?)\\s*</a>",
            Pattern.DOTALL
        );
        Matcher m2 = p2.matcher(block);
        if (m2.find()) {
            name = m2.group(1).trim();
            name = name.replaceAll("<[^>]+>", "");
            name = htmlUnescape(name);
            if (!name.isEmpty() && !name.matches("\\d+")) return name;
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
