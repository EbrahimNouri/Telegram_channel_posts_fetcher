import java.time.*;
import java.time.format.*;
import java.util.*;
import java.io.*;

public class IndexBuilder {
    
    public static void build(String channelUsername, List<PostData> allPosts, String indexPath) throws Exception {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html>\n<html dir=\"rtl\">\n<head>\n");
        h.append("<meta charset=\"UTF-8\">\n");
        h.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        h.append("<title>@").append(channelUsername).append(" - Archive</title>\n");
        h.append("<style>\n");
        h.append("*{margin:0;padding:0;box-sizing:border-box}\n");
        h.append("body{font-family:Tahoma,sans-serif;max-width:750px;margin:0 auto;padding:12px;background:#e8eaed}\n");
        h.append(".header{background:linear-gradient(135deg,#2a9d8f,#1d7a6e);color:white;padding:22px;border-radius:14px;text-align:center;margin-bottom:18px}\n");
        h.append(".header h1{font-size:20px}\n");
        h.append(".header .stats{font-size:12px;opacity:.9;margin-top:4px}\n");
        h.append(".post-card{background:white;border-radius:10px;padding:14px;margin-bottom:8px;box-shadow:0 1px 2px rgba(0,0,0,.06);display:flex;gap:12px;align-items:flex-start;border-right:3px solid transparent;transition:.2s}\n");
        h.append(".post-card:hover{border-right-color:#2a9d8f}\n");
        h.append(".post-icon{font-size:28px;min-width:36px;text-align:center}\n");
        h.append(".post-info{flex:1;min-width:0}\n");
        h.append(".post-date{font-size:10px;color:#999;margin-bottom:4px;font-weight:bold}\n");
        h.append(".post-text{font-size:12px;color:#444;line-height:1.7;word-wrap:break-word;max-height:80px;overflow:hidden}\n");
        h.append(".post-files{margin-top:7px;display:flex;flex-wrap:wrap;gap:4px}\n");
        h.append(".file-badge{display:inline-block;background:#e8f5e9;color:#2a9d8f;padding:3px 9px;border-radius:10px;font-size:10px;text-decoration:none;border:1px solid #c8e6c9}\n");
        h.append(".folder-link{display:inline-block;color:#2a9d8f;font-size:10px;margin-top:4px;text-decoration:none}\n");
        h.append("</style>\n</head>\n<body>\n\n");
        
        h.append("<div class=\"header\">\n");
        h.append("<h1>@").append(channelUsername).append("</h1>\n");
        h.append("<div class=\"stats\">").append(allPosts.size()).append(" posts | Updated: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))).append("</div>\n");
        h.append("</div>\n\n");
        
        if (allPosts.isEmpty()) {
            h.append("<p style=\"text-align:center;color:#999;padding:40px\">No posts yet.</p>\n");
        } else {
            for (PostData pd : allPosts) {
                if (pd.postId == null) continue;
                
                String fn = pd.folderName != null ? pd.folderName : 
                    ((pd.dateStr != null ? pd.dateStr : "post") + "_" + pd.postId).replaceAll("[^a-zA-Z0-9_\\-]", "_");
                
                String icon = "📝";
                if (pd.hasPhoto && pd.hasVideo) icon = "🎬";
                else if (pd.hasPhoto) icon = "🖼️";
                else if (pd.hasVideo) icon = "🎥";
                else if (pd.hasDocument) icon = "📎";
                
                h.append("<div class=\"post-card\" data-post-id=\"").append(pd.postId).append("\">\n");
                h.append("<div class=\"post-icon\">").append(icon).append("</div>\n");
                h.append("<div class=\"post-info\">\n");
                if (pd.dateStr != null && !pd.dateStr.isEmpty())
                    h.append("<div class=\"post-date\">").append(pd.dateStr).append("</div>\n");
                if (pd.text != null && !pd.text.isEmpty())
                    h.append("<div class=\"post-text\">").append(escapeHtml(pd.text)).append("</div>\n");
                if (!pd.files.isEmpty()) {
                    h.append("<div class=\"post-files\">\n");
                    for (String f : pd.files)
                        h.append("<a class=\"file-badge\" href=\"").append(fn).append("/").append(f).append("\">").append(escapeHtml(f)).append("</a>\n");
                    h.append("</div>\n");
                }
                h.append("<a class=\"folder-link\" href=\"").append(fn).append("/\">Open folder</a>\n");
                h.append("</div>\n</div>\n\n");
            }
        }
        
        h.append("<p style=\"text-align:center;color:#aaa;font-size:11px;padding:15px\">Total: ")
            .append(allPosts.size()).append(" posts</p>\n");
        h.append("</body>\n</html>");
        
        FileWriter fw = new FileWriter(indexPath);
        fw.write(h.toString());
        fw.close();
    }
    
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "<br>");
    }
}
