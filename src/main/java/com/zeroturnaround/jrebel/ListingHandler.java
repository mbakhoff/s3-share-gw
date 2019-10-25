package com.zeroturnaround.jrebel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class ListingHandler extends AbstractHandler {

  private final AmazonS3 s3;
  private final TemplateEngine engine;

  public ListingHandler(AmazonS3 s3) {
    this.s3 = s3;
    this.engine = getTemplateEngine();
  }

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    if (!request.getPathInfo().endsWith("/"))
      return;

    String marker = null;
    List<Item> items = new ArrayList<>();
    while (true) {
      ObjectListing listing = s3.listObjects(new ListObjectsRequest("share.jc.zt", key(request), marker, "/", null));
      for (String dir : listing.getCommonPrefixes()) {
        items.add(new Item("/" + dir, name(dir) + "/"));
      }
      for (S3ObjectSummary file : listing.getObjectSummaries()) {
        items.add(new Item("/" + file.getKey(), name(file.getKey())));
      }
      marker = listing.getNextMarker();
      if (!listing.isTruncated())
        break;
    }

    Context context = new Context();
    context.setVariable("current", request.getRequestURI());
    context.setVariable("items", items);
    String listing = engine.process("templates/listing.html", context);

    byte[] bytes = listing.getBytes(StandardCharsets.UTF_8);
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentLength(bytes.length);
    response.setContentType("text/html; charset=utf-8");
    response.getOutputStream().write(bytes);
    baseRequest.setHandled(true);
  }

  private String key(HttpServletRequest request) {
    String key = request.getPathInfo();
    while (key.startsWith("/"))
      key = key.substring(1);
    return key;
  }

  private String name(String dir) {
    return Paths.get(dir).getFileName().toString();
  }

  private TemplateEngine getTemplateEngine() {
    ClassLoaderTemplateResolver res = new ClassLoaderTemplateResolver(getClass().getClassLoader());
    TemplateEngine engine = new TemplateEngine();
    engine.addTemplateResolver(res);
    return engine;
  }

  static class Item {

    public final String path;
    public final String title;

    Item(String path, String title) {
      this.path = path;
      this.title = title;
    }

    public String getPath() {
      return path;
    }

    public String getTitle() {
      return title;
    }
  }
}
