package com.zeroturnaround.jrebel;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.QuotedCSV;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;

public class DirectTransferHandler extends AbstractHandler {

  private static final Logger log = LoggerFactory.getLogger(DirectTransferHandler.class);

  private static final DateTimeFormatter httpDate = DateTimeFormatter
      .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
      .withZone(ZoneId.of("GMT"));

  private final AmazonS3 s3;
  private final List<String> directPrefixes;

  public DirectTransferHandler(AmazonS3 s3, List<String> directPrefixes) {
    this.s3 = s3;
    this.directPrefixes = directPrefixes;
  }

  @Override
  public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse response) throws IOException {
    if (!anyMatch(directPrefixes, request.getPathInfo()))
      return;

    String method = httpServletRequest.getMethod().toUpperCase();
    if (!Arrays.asList("GET", "HEAD").contains(method)) {
      response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      request.setHandled(true);
      return;
    }

    try (S3Object object = s3.getObject("share.jc.zt", toKey(request))) {
      if (handleWithCache(httpServletRequest, response, object)) {
        request.setHandled(true);
        return;
      }

      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentLengthLong(object.getObjectMetadata().getContentLength());
      response.setHeader("ETag", object.getObjectMetadata().getETag());
      response.setHeader("Last-Modified", httpDate.format(object.getObjectMetadata().getLastModified().toInstant()));
      response.setHeader("Cache-Control", "no-cache");
      if (method.equals("GET")) {
        IOUtils.copy(object.getObjectContent(), response.getOutputStream());
      }
    } catch (AmazonS3Exception e) {
      if (e.getStatusCode() != 404)
        log.warn(httpServletRequest.getRequestURI() + ": " + e.toString());
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.getWriter().write(e.getMessage());
    }
    request.setHandled(true);
  }

  private boolean handleWithCache(HttpServletRequest req, HttpServletResponse resp, S3Object object) {
    String tag = object.getObjectMetadata().getETag();

    List<String> ifNoneMatch = getETags(req, "If-None-Match");
    if (ifNoneMatch != null && ifNoneMatch.contains(tag)) {
      resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      return true;
    }

    List<String> ifMatch = getETags(req, "If-Match");
    if (ifMatch != null && !ifMatch.contains(tag)) {
      resp.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
      return true;
    }

    String modifiedSince = req.getHeader("If-Modified-Since");
    if (modifiedSince != null) {
      if (object.getObjectMetadata().getLastModified().toInstant().compareTo(
          ZonedDateTime.parse(modifiedSince, httpDate).toInstant()) <= 0) {
        resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        return true;
      }
    }

    String unmodifiedSince = req.getHeader("If-Unmodified-Since");
    if (unmodifiedSince != null) {
      if (object.getObjectMetadata().getLastModified().toInstant().compareTo(
          ZonedDateTime.parse(unmodifiedSince, httpDate).toInstant()) > 0) {
        resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
        return true;
      }
    }

    return false;
  }

  private List<String> getETags(HttpServletRequest req, String headerName) {
    String header = req.getHeader(headerName);
    return header != null ? new QuotedCSV(false, header).getValues() : null;
  }

  private boolean anyMatch(List<String> prefixes, String path) {
    for (String prefix : prefixes) {
      if (path.startsWith(prefix))
        return true;
    }
    return false;
  }

  private String toKey(Request request) {
    String path = request.getPathInfo();
    while (path.startsWith("/"))
      path = path.substring(1);
    return path;
  }
}
