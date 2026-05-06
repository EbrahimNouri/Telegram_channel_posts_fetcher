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
    String body = response.body();
    System.out.println("HTML size: " + body.length() + " bytes");
    
    // DEBUG: ذخیره HTML برای بررسی
    try {
        java.nio.file.Files.write(java.nio.file.Paths.get("debug.html"), body.getBytes());
        System.out.println("DEBUG: HTML saved to debug.html");
    } catch (Exception e) {}
    
    return body;
}
